package pw.c0re.stream.service

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer.DecoderInitializationException
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import pw.c0re.stream.BuildConfig
import pw.c0re.stream.C0reStreamApp
import pw.c0re.stream.data.AppSettings
import pw.c0re.stream.data.NetworkPolicy
import pw.c0re.stream.data.player.DecoderFailureClass
import pw.c0re.stream.data.player.DecoderFailurePolicy
import pw.c0re.stream.data.player.PerformanceProfile
import pw.c0re.stream.data.player.PlaybackDiagnostics
import pw.c0re.stream.data.player.PlaybackPreferences
import pw.c0re.stream.data.player.PlaybackSettings
import pw.c0re.stream.ui.player.PlayerHub
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.min

/**
 * Foreground playback service: MediaSession (lock screen, headphones, TV remotes),
 * notification, Media3-managed wake lock, decoder selection and buffering.
 *
 * HTTPS-to-HTTP redirects stay blocked by [NetworkPolicy.playbackClient], which also disables
 * the absolute call deadline so a continuous live stream survives beyond 90 s. No DRM bypass is
 * implemented: decoder selection is delegated to MediaCodec capabilities reported by the device,
 * with the packaged ffmpeg decoder extension available only as a fallback for formats it was
 * built to support.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    private val defaultUserAgent = "C0reStream/${BuildConfig.VERSION_NAME}"
    private val sourceLock = Any()
    private var cachedSourcePlaylistId = Long.MIN_VALUE
    private var cachedPlaybackClient: OkHttpClient? = null

    private var lastVideoFormat: Format? = null
    private var lastAudioFormat: Format? = null
    private var videoWidth = 0
    private var videoHeight = 0
    private var pixelWidthHeightRatio = 1f
    private var videoDecoder = ""
    private var audioDecoder = ""
    private var videoMime = ""
    private var audioMime = ""
    private var audioSampleRate = 0
    private var audioChannelCount = 0
    private var videoFrameRate = 0f
    private var colorInfoText = ""
    private var videoSizeInfoText = ""
    private var bitrateEstimate = 0L
    private var droppedFrames = 0L
    private var framesInWindow = 0
    private var lastPublishAtMs = 0L
    private var currentDiagnostics = PlaybackDiagnostics()

    // Per-item (playback session) decoder-init counters. Media3's fallback already walks a
    // finite decoder list; these counters only classify repeated failures and are saturated,
    // so no retry/fallback loop is ever started by this service.
    private var audioInitFailures = 0
    private var videoInitFailures = 0
    private var audioFailureClass = DecoderFailureClass.NONE
    private var videoFailureClass = DecoderFailureClass.NONE
    private var lastAudioFailureAtMs = 0L
    private var lastVideoFailureAtMs = 0L

    private val diagnosticsHandler = Handler(Looper.getMainLooper())
    private val publishDiagnostics = object : Runnable {
        override fun run() {
            val activePlayer = player
            val session = mediaSession
            if (activePlayer != null && session != null) {
                val now = SystemClock.elapsedRealtime()
                val elapsedMs = (now - lastPublishAtMs).coerceIn(250L, 10_000L)
                val fps = framesInWindow * 1_000f / elapsedMs
                framesInWindow = 0
                lastPublishAtMs = now
                val bitrateKbps = maxOf(
                    bitrateEstimate,
                    lastVideoFormat?.bitrate?.toLong() ?: 0L,
                ) / 1_000L
                val snapshot = PlaybackDiagnostics(
                    fps = fps,
                    bitrateKbps = bitrateKbps,
                    videoWidth = videoWidth,
                    videoHeight = videoHeight,
                    pixelWidthHeightRatio = pixelWidthHeightRatio,
                    codec = lastVideoFormat?.codecs?.takeIf { it.isNotBlank() } ?: videoMime,
                    decoder = videoFailureClass.decorate(videoDecoder),
                    bufferMs = activePlayer.totalBufferedDuration,
                    droppedFrames = droppedFrames,
                    audioFormat = audioFailureClass.decorate(describeAudio(lastAudioFormat)),
                    colorInfo = colorInfoText,
                    videoSizeInfo = videoSizeInfoText,
                    audioMime = audioMime,
                    videoMime = videoMime,
                    audioDecoder = audioFailureClass.decorate(audioDecoder),
                    audioSampleRate = audioSampleRate,
                    audioChannelCount = audioChannelCount,
                    videoFrameRate = videoFrameRate,
                )
                currentDiagnostics = snapshot
                session.setSessionExtras(snapshot.toBundle())
            }
            diagnosticsHandler.removeCallbacks(this)
            diagnosticsHandler.postDelayed(this, 1_000L)
        }
    }

    private val diagnosticsListener = object : AnalyticsListener {
        override fun onVideoInputFormatChanged(
            eventTime: EventTime,
            format: Format,
            decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?,
        ) {
            lastVideoFormat = format
            videoMime = format.sampleMimeType.orEmpty()
            videoFrameRate = format.frameRate.takeIf { it.isFinite() && it > 0f } ?: 0f
            colorInfoText = describeColor(format.colorInfo)
        }

        override fun onAudioInputFormatChanged(
            eventTime: EventTime,
            format: Format,
            decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?,
        ) {
            lastAudioFormat = format
            audioMime = format.sampleMimeType.orEmpty()
            audioSampleRate = format.sampleRate.coerceAtLeast(0)
            audioChannelCount = format.channelCount.coerceAtLeast(0)
        }

        override fun onVideoSizeChanged(eventTime: EventTime, videoSize: androidx.media3.common.VideoSize) {
            videoWidth = videoSize.width
            videoHeight = videoSize.height
            pixelWidthHeightRatio = if (videoSize.pixelWidthHeightRatio > 0f) videoSize.pixelWidthHeightRatio else 1f
            val dar = if (videoHeight > 0 && pixelWidthHeightRatio > 0f) {
                videoWidth * pixelWidthHeightRatio / videoHeight.toFloat()
            } else 0f
            videoSizeInfoText = if (dar > 0f) String.format(java.util.Locale.ROOT, "DAR %.2f:1", dar) else ""
        }

        override fun onVideoDecoderInitialized(
            eventTime: EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            videoDecoder = describeDecoder(decoderName, lastVideoFormat?.sampleMimeType.orEmpty())
        }

        override fun onAudioDecoderInitialized(
            eventTime: EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            // The same extension renderer selection applies to audio: system decoder first,
            // FFmpeg fallback only for formats the built extension actually supports.
            audioDecoder = describeDecoder(decoderName, lastAudioFormat?.sampleMimeType.orEmpty())
        }

        override fun onVideoDecoderReleased(eventTime: EventTime, decoderName: String) {
            if (videoDecoder.startsWith(decoderName.take(120))) videoDecoder = ""
        }

        override fun onAudioDecoderReleased(eventTime: EventTime, decoderName: String) {
            if (audioDecoder.startsWith(decoderName.take(120))) audioDecoder = ""
        }

        override fun onAudioCodecError(eventTime: EventTime, audioCodecError: Exception) {
            if (audioCodecError is DecoderInitializationException) recordFailure(audio = true)
        }

        override fun onVideoCodecError(eventTime: EventTime, videoCodecError: Exception) {
            if (videoCodecError is DecoderInitializationException) recordFailure(audio = false)
        }

        override fun onPlayerError(eventTime: EventTime, error: PlaybackException) {
            if (error.errorCode != PlaybackException.ERROR_CODE_DECODER_INIT_FAILED) return
            // Codec errors above attribute recoverable failures to the right renderer; a fatal
            // init failure without a prior codec error is attributed from the active formats.
            val audioOnly = lastAudioFormat != null && lastVideoFormat == null
            recordFailure(audio = audioOnly)
        }

        override fun onDroppedVideoFrames(eventTime: EventTime, droppedFrames: Int, elapsedMs: Long) {
            this@PlaybackService.droppedFrames += droppedFrames.coerceAtLeast(0).toLong()
        }

        override fun onVideoFrameProcessingOffset(
            eventTime: EventTime,
            totalProcessingOffsetUs: Long,
            frameCount: Int,
        ) {
            framesInWindow += frameCount.coerceAtLeast(0)
        }

        override fun onRenderedFirstFrame(eventTime: EventTime, output: Any, renderTimeMs: Long) {
            framesInWindow += 1
        }

        override fun onBandwidthEstimate(
            eventTime: EventTime,
            totalLoadTimeMs: Int,
            totalBytesLoaded: Long,
            bitrateEstimate: Long,
        ) {
            this@PlaybackService.bitrateEstimate = bitrateEstimate.coerceAtLeast(0L)
        }

        override fun onMediaItemTransition(
            eventTime: EventTime,
            mediaItem: androidx.media3.common.MediaItem?,
            reason: Int,
        ) {
            lastVideoFormat = null
            lastAudioFormat = null
            videoWidth = 0
            videoHeight = 0
            videoDecoder = ""
            audioDecoder = ""
            videoMime = ""
            audioMime = ""
            audioSampleRate = 0
            audioChannelCount = 0
            videoFrameRate = 0f
            colorInfoText = ""
            videoSizeInfoText = ""
            audioInitFailures = 0
            videoInitFailures = 0
            audioFailureClass = DecoderFailureClass.NONE
            videoFailureClass = DecoderFailureClass.NONE
            lastAudioFailureAtMs = 0L
            lastVideoFailureAtMs = 0L
            bitrateEstimate = 0L
            droppedFrames = 0L
            framesInWindow = 0
        }
    }

    override fun onCreate() {
        super.onCreate()
        val app = application as C0reStreamApp
        // Bounded read: keep player creation synchronous so controllers always get a session,
        // while still honouring saved preferences. Defaults are safe if the store is slow.
        val prefs = runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(1_000L) { PlaybackPreferences(this@PlaybackService).snapshot() }
                ?: PlaybackSettings()
        }
        buildPlayer(prefs, app.cachedSettings.value)
        lastPublishAtMs = SystemClock.elapsedRealtime()
        diagnosticsHandler.postDelayed(publishDiagnostics, 1_000L)
    }

    private fun buildPlayer(prefs: PlaybackSettings, appSettings: AppSettings) {
        // A fresh OkHttpDataSource is created per load; the underlying client is cached and
        // carries the active source's sanitized User-Agent and proxy into every datasource call.
        val dataSourceFactory = object : DataSource.Factory {
            override fun createDataSource(): DataSource =
                OkHttpDataSource.Factory(playbackClient()).createDataSource()
        }
        val defaultMediaSourceFactory = DefaultMediaSourceFactory(
            dataSourceFactory,
            DefaultExtractorsFactory(),
        ).apply {
            if (prefs.performanceProfile == PerformanceProfile.LOW_LATENCY) setLiveTargetOffsetMs(0)
        }
        val rtspFactory = RtspMediaSource.Factory().setUserAgent(defaultUserAgent)
        val rtspOverTcpFactory = RtspMediaSource.Factory()
            .setUserAgent(defaultUserAgent)
            .setForceUseRtpTcp(true)
        val mediaSourceFactory = object : MediaSource.Factory {
            override fun createMediaSource(mediaItem: androidx.media3.common.MediaItem): MediaSource =
                when (mediaItem.localConfiguration?.uri?.scheme?.lowercase()) {
                    "rtsp" -> rtspFactory.createMediaSource(mediaItem)
                    "rtspu" -> rtspOverTcpFactory.createMediaSource(mediaItem)
                    else -> defaultMediaSourceFactory.createMediaSource(mediaItem)
                }

            override fun getSupportedTypes(): IntArray =
                (defaultMediaSourceFactory.supportedTypes + rtspFactory.supportedTypes)
                    .distinct().toIntArray()

            override fun setDrmSessionManagerProvider(
                provider: androidx.media3.exoplayer.drm.DrmSessionManagerProvider,
            ): MediaSource.Factory {
                defaultMediaSourceFactory.setDrmSessionManagerProvider(provider)
                rtspFactory.setDrmSessionManagerProvider(provider)
                rtspOverTcpFactory.setDrmSessionManagerProvider(provider)
                return this
            }

            override fun setLoadErrorHandlingPolicy(
                policy: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy,
            ): MediaSource.Factory {
                defaultMediaSourceFactory.setLoadErrorHandlingPolicy(policy)
                rtspFactory.setLoadErrorHandlingPolicy(policy)
                rtspOverTcpFactory.setLoadErrorHandlingPolicy(policy)
                return this
            }
        }
        val renderersFactory = PlaybackRenderersFactory(
            this,
            prefs.hardwareDecoder,
            prefs.decoderFallback,
            prefs.audioOffsetMs,
            prefs.performanceProfile != PerformanceProfile.POWER_SAVE,
        )
        val loadControl = DefaultLoadControl.Builder()
            .apply { configure(this, prefs.performanceProfile, appSettings) }
            .build()
        val trackSelector = DefaultTrackSelector(this)
        if (prefs.preferredAudioLanguage.isNotBlank()) {
            trackSelector.setParameters(
                trackSelector.parameters.buildUpon()
                    .setPreferredAudioLanguage(prefs.preferredAudioLanguage)
                    .build(),
            )
        }
        val newPlayer = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true, // Media3 handles audio focus, transient loss and resume.
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK) // Media3-managed partial + Wi-Fi wake locks.
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            .setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS)
            .build()
        newPlayer.addAnalyticsListener(diagnosticsListener)
        player = newPlayer
        val session = MediaSession.Builder(this, newPlayer).build()
        mediaSession = session
        addSession(session)
    }

    /**
     * Resolves the continuous-stream client for the active source. PlaylistEntity owns the
     * source-specific User-Agent and proxy; AppSettings has no such fields, so no new ones are
     * invented. The playlist id is read-only from PlayerHub state already published by the
     * player screen, and the bounded lookup keeps player construction responsive.
     */
    private fun playbackClient(): OkHttpClient {
        val playlistId = runCatching { PlayerHub.playlistId }.getOrDefault(0L)
        synchronized(sourceLock) {
            val cached = cachedPlaybackClient
            if (cached != null && cachedSourcePlaylistId == playlistId) return cached
            val app = application as C0reStreamApp
            val playlist = if (playlistId > 0L) {
                runBlocking(Dispatchers.IO) {
                    withTimeoutOrNull(1_000L) { app.database.playlistDao().byId(playlistId) }
                }
            } else {
                null
            }
            val client = NetworkPolicy.playbackClient(
                userAgent = NetworkPolicy.userAgent(playlist?.userAgent) ?: defaultUserAgent,
                useProxy = playlist?.useProxy == true,
                proxyHost = playlist?.proxyHost,
                proxyPort = playlist?.proxyPort,
            )
            cachedSourcePlaylistId = playlistId
            cachedPlaybackClient = client
            return client
        }
    }

    private fun configure(
        builder: DefaultLoadControl.Builder,
        profile: PerformanceProfile,
        appSettings: AppSettings,
    ) {
        val (minMs, maxMs) = when (profile) {
            PerformanceProfile.AUTO ->
                appSettings.minBufferSec * 1_000L to appSettings.maxBufferSec * 1_000L
            PerformanceProfile.PERFORMANCE -> 3_000L to 20_000L
            PerformanceProfile.QUALITY -> 20_000L to 120_000L
            PerformanceProfile.LOW_LATENCY -> 1_000L to 6_000L
            PerformanceProfile.STABLE -> 15_000L to 90_000L
            PerformanceProfile.POWER_SAVE -> 10_000L to 45_000L
        }
        val min = minMs.coerceIn(1_000L, 300_000L).toInt()
        val max = maxMs.coerceIn(min.toLong(), 600_000L).toInt()
        val playbackMs = minOf(2_500, min)
        builder.setBufferDurationsMs(min, max, playbackMs, playbackMs)
        builder.setPrioritizeTimeOverSizeThresholds(
            profile != PerformanceProfile.LOW_LATENCY && profile != PerformanceProfile.PERFORMANCE,
        )
        builder.setBackBuffer(
            if (profile == PerformanceProfile.LOW_LATENCY) 0 else DefaultLoadControl.DEFAULT_BACK_BUFFER_DURATION_MS,
            false,
        )
    }

    private fun describeDecoder(name: String, mimeType: String?): String {
        val clean = name.take(120)
        if (mimeType.isNullOrBlank()) return clean
        val accelerated = runCatching {
            MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, false, false)
                .firstOrNull { it.name == name }
                ?.hardwareAccelerated
        }.getOrNull()
        return when (accelerated) {
            true -> "$clean (HW)"
            false -> "$clean (SW)"
            null -> clean
        }
    }

    private fun describeAudio(format: Format?): String {
        if (format == null) return ""
        val codec = (format.codecs?.takeIf { it.isNotBlank() } ?: format.sampleMimeType).orEmpty().take(60)
        val details = buildString {
            if (format.sampleRate > 0) append(' ').append(format.sampleRate).append("Hz")
            if (format.channelCount > 0) append(' ').append(format.channelCount).append("ch")
            if (format.bitrate > 0) append(' ').append(format.bitrate / 1_000L).append("kbps")
        }
        return (codec + details).take(160)
    }

    /** Human-readable color/HDR description from measured Format.colorInfo values only. */
    private fun describeColor(info: ColorInfo?): String {
        if (info == null || (
            info.colorSpace == Format.NO_VALUE &&
                info.colorTransfer == Format.NO_VALUE &&
                info.colorRange == Format.NO_VALUE
            )
        ) return ""
        val parts = mutableListOf(
            when (info.colorSpace) {
                C.COLOR_SPACE_BT2020 -> "BT.2020"
                C.COLOR_SPACE_BT709 -> "BT.709"
                C.COLOR_SPACE_BT601 -> "BT.601"
                else -> "CS ${info.colorSpace}"
            },
        )
        if (ColorInfo.isTransferHdr(info)) {
            parts += when (info.colorTransfer) {
                C.COLOR_TRANSFER_ST2084 -> "PQ HDR"
                C.COLOR_TRANSFER_HLG -> "HLG HDR"
                else -> "HDR"
            }
        }
        when (info.colorRange) {
            C.COLOR_RANGE_FULL -> parts += "full range"
            C.COLOR_RANGE_LIMITED -> parts += "limited range"
            Format.NO_VALUE -> Unit
            else -> Unit
        }
        return parts.joinToString(" ").take(120)
    }

    /**
     * Counts one decoder-init failure for the current media item and keeps the class bounded.
     * The timestamp guard avoids counting the same failure twice when Media3 reports it both as
     * a codec error and as a fatal player error. No retry is triggered here.
     */
    private fun recordFailure(audio: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (audio) {
            if (lastAudioFailureAtMs != 0L && now - lastAudioFailureAtMs < 500L) return
            lastAudioFailureAtMs = now
            audioInitFailures = DecoderFailurePolicy.nextCount(audioInitFailures)
            audioFailureClass = DecoderFailurePolicy.classify(
                audioInitFailures,
                isInitFailure = true,
                isAudioRenderer = true,
            )
        } else {
            if (lastVideoFailureAtMs != 0L && now - lastVideoFailureAtMs < 500L) return
            lastVideoFailureAtMs = now
            videoInitFailures = DecoderFailurePolicy.nextCount(videoInitFailures)
            videoFailureClass = DecoderFailurePolicy.classify(
                videoInitFailures,
                isInitFailure = true,
                isAudioRenderer = false,
            )
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val active = player
        if (active == null || !active.playWhenReady || active.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        diagnosticsHandler.removeCallbacks(publishDiagnostics)
        mediaSession?.let { session ->
            removeSession(session)
            runCatching { session.player.release() }
            runCatching { session.release() }
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }

    /** Decoder preference/fallback and optional sample-level audio offset. */
    private class PlaybackRenderersFactory(
        context: Context,
        hardwareDecoder: Boolean,
        fallback: Boolean,
        private val audioOffsetMs: Long,
        floatOutput: Boolean,
    ) : DefaultRenderersFactory(context) {
        init {
            // ON indexes extension renderers after the core MediaCodec renderers: system
            // decoders stay preferred and the ffmpeg fallback is used only when no system
            // decoder supports the input format. This can only decode formats that the ffmpeg
            // build actually includes (audio via FfmpegAudioRenderer; an optional experimental
            // video renderer in this Media3 build), never an unsupported codec. Extensions must
            // be present in the APK to have any effect; none are assumed here.
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            setMediaCodecSelector(
                if (hardwareDecoder) MediaCodecSelector.DEFAULT else MediaCodecSelector.PREFER_SOFTWARE,
            )
            setEnableDecoderFallback(fallback)
            setEnableAudioFloatOutput(floatOutput)
        }

        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioOutputPlaybackParameters: Boolean,
        ): AudioSink {
            if (audioOffsetMs == 0L) {
                return checkNotNull(
                    super.buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParameters)
                        ?: DefaultAudioSink.Builder(context)
                            .setEnableFloatOutput(enableFloatOutput)
                            .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParameters)
                            .build(),
                )
            }
            return checkNotNull(
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParameters)
                    .setAudioProcessors(arrayOf<AudioProcessor>(AudioDelayProcessor(audioOffsetMs)))
                    .build(),
            )
        }
    }

    /**
     * Positive offset inserts a short silence at the start of each decoded audio stream;
     * negative offset drops the same amount of leading audio. Eligibility and frame size come
     * from the format supplied by Media3, so no codec support is assumed.
     */
    private class AudioDelayProcessor(private val offsetMs: Long) : BaseAudioProcessor() {
        private var totalFrames = 0
        private var framesRemaining = 0
        private var direction = 0

        override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
            val frames = (offsetMs * inputAudioFormat.sampleRate / 1_000L)
                .coerceIn(-MAX_FRAMES.toLong(), MAX_FRAMES.toLong())
                .toInt()
            totalFrames = abs(frames)
            framesRemaining = totalFrames
            direction = when {
                frames > 0 -> 1
                frames < 0 -> -1
                else -> 0
            }
            return inputAudioFormat
        }

        @Suppress("DEPRECATION")
        override fun isActive(): Boolean = direction != 0 || super.isActive()

        override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
            framesRemaining = totalFrames
        }

        override fun queueInput(inputBuffer: ByteBuffer) {
            val bytesPerFrame = inputAudioFormat.bytesPerFrame
            if (direction == 0 || framesRemaining == 0 || bytesPerFrame <= 0) {
                passThrough(inputBuffer)
                return
            }
            if (direction > 0) {
                val silenceFrames = min(framesRemaining, MAX_FRAMES_PER_CALL)
                val silenceBytes = silenceFrames * bytesPerFrame
                val output = replaceOutputBuffer(silenceBytes + inputBuffer.remaining())
                output.put(ByteArray(silenceBytes))
                output.put(inputBuffer)
                output.flip()
                framesRemaining -= silenceFrames
            } else {
                val skipBytes = min(framesRemaining.toLong() * bytesPerFrame, inputBuffer.remaining().toLong()).toInt()
                inputBuffer.position(inputBuffer.position() + skipBytes)
                framesRemaining -= skipBytes / bytesPerFrame
                passThrough(inputBuffer)
            }
        }

        override fun getDurationAfterProcessorApplied(durationUs: Long): Long =
            if (direction > 0 && durationUs != C.TIME_UNSET && inputAudioFormat.sampleRate > 0) {
                durationUs + totalFrames * 1_000_000L / inputAudioFormat.sampleRate
            } else {
                durationUs
            }

        override fun onReset() {
            direction = 0
            totalFrames = 0
            framesRemaining = 0
        }

        private fun passThrough(inputBuffer: ByteBuffer) {
            if (!inputBuffer.hasRemaining()) return
            val output = replaceOutputBuffer(inputBuffer.remaining())
            output.put(inputBuffer)
            output.flip()
        }

        private companion object {
            const val MAX_FRAMES = 5_000 * 48 // 5 s at 48 kHz
            const val MAX_FRAMES_PER_CALL = 2_048
        }
    }
}
