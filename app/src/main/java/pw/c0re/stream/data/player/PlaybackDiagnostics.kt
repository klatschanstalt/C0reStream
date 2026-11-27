package pw.c0re.stream.data.player

import android.os.Bundle

/**
 * Snapshot of publicly safe playback telemetry. Values come from Media3 player state and
 * analytics events only: no stream URL, header, token or credential is included.
 *
 * The optional [colorInfo] and [videoSizeInfo] fields carry Format.colorInfo and VideoSize
 * details when the service publishes them; empty values are safe for older session extras.
 * [audioMime], [videoMime], [audioDecoder], [audioSampleRate], [audioChannelCount] and
 * [videoFrameRate] are additive fields with zero/empty defaults, so an older controller that
 * only reads the original keys is unaffected.
 */
data class PlaybackDiagnostics(
    val fps: Float = 0f,
    val bitrateKbps: Long = 0L,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val pixelWidthHeightRatio: Float = 1f,
    val codec: String = "",
    val decoder: String = "",
    val bufferMs: Long = 0L,
    val droppedFrames: Long = 0L,
    val audioFormat: String = "",
    /** HDR/color description derived from Format.colorInfo, e.g. "BT.2020 PQ HDR". */
    val colorInfo: String = "",
    /** Decoded-frame detail derived from VideoSize, e.g. "DAR 1.78:1". */
    val videoSizeInfo: String = "",
    /** Sample MIME type reported by the active audio format, e.g. "audio/mpeg". */
    val audioMime: String = "",
    /** Sample MIME type reported by the active video format, e.g. "video/avc". */
    val videoMime: String = "",
    /** Audio decoder name reported by onAudioDecoderInitialized, including HW/SW hint. */
    val audioDecoder: String = "",
    /** Sample rate in Hz of the active audio format; 0 when unreported. */
    val audioSampleRate: Int = 0,
    /** Channel count of the active audio format; 0 when unreported. */
    val audioChannelCount: Int = 0,
    /** Frame rate declared by the active video format; 0 when unreported. */
    val videoFrameRate: Float = 0f,
) {
    /** Display aspect ratio from decoded size and pixel aspect ratio; 0 when unknown. */
    val displayAspectRatio: Float
        get() = if (videoWidth > 0 && videoHeight > 0 && pixelWidthHeightRatio > 0f) {
            videoWidth * pixelWidthHeightRatio / videoHeight
        } else {
            0f
        }

    /** Serialised into MediaSession extras so the UI can read service-side analytics. */
    fun toBundle(): Bundle = Bundle().apply {
        toKeyValueMap().forEach { (key, value) ->
            when (value) {
                is Float -> putFloat(key, value)
                is Long -> putLong(key, value)
                is Int -> putInt(key, value)
                is String -> putString(key, value)
            }
        }
    }

    /**
     * Pure Bundle payload: the same bounded key/value pairs used by [toBundle] and
     * [fromBundle]. Keeping this layer free of Android framework calls lets JVM tests verify
     * the full round trip and bounds without a Robolectric runtime.
     */
    internal fun toKeyValueMap(): Map<String, Any> = linkedMapOf(
        KEY_FPS to finiteFloat(fps, MIN_FPS, MAX_FPS),
        KEY_BITRATE to bitrateKbps.coerceIn(0L, MAX_BITRATE),
        KEY_WIDTH to videoWidth.coerceIn(0, MAX_DIMENSION),
        KEY_HEIGHT to videoHeight.coerceIn(0, MAX_DIMENSION),
        KEY_PAR to finiteFloat(pixelWidthHeightRatio, MIN_PAR, MAX_PAR, DEFAULT_PAR),
        KEY_CODEC to capText(codec),
        KEY_DECODER to capText(decoder),
        KEY_BUFFER to bufferMs.coerceIn(0L, MAX_BUFFER),
        KEY_DROPPED to droppedFrames.coerceIn(0L, Long.MAX_VALUE / 4),
        KEY_AUDIO to capText(audioFormat),
        KEY_COLOR to capText(colorInfo),
        KEY_VIDEO_SIZE to capText(videoSizeInfo),
        KEY_AUDIO_MIME to capText(audioMime),
        KEY_VIDEO_MIME to capText(videoMime),
        KEY_AUDIO_DECODER to capText(audioDecoder),
        KEY_AUDIO_SAMPLE_RATE to audioSampleRate.coerceIn(0, MAX_SAMPLE_RATE),
        KEY_AUDIO_CHANNELS to audioChannelCount.coerceIn(0, MAX_CHANNELS),
        KEY_VIDEO_FRAME_RATE to finiteFloat(videoFrameRate, MIN_FPS, MAX_FPS),
    )

    companion object {
        private const val KEY_FPS = "fps"
        private const val KEY_BITRATE = "bitrate_kbps"
        private const val KEY_WIDTH = "video_width"
        private const val KEY_HEIGHT = "video_height"
        private const val KEY_PAR = "pixel_aspect_ratio"
        private const val KEY_CODEC = "codec"
        private const val KEY_DECODER = "decoder"
        private const val KEY_BUFFER = "buffer_ms"
        private const val KEY_DROPPED = "dropped_frames"
        private const val KEY_AUDIO = "audio_format"
        private const val KEY_COLOR = "color_info"
        private const val KEY_VIDEO_SIZE = "video_size_info"
        private const val KEY_AUDIO_MIME = "audio_mime"
        private const val KEY_VIDEO_MIME = "video_mime"
        private const val KEY_AUDIO_DECODER = "audio_decoder"
        private const val KEY_AUDIO_SAMPLE_RATE = "audio_sample_rate"
        private const val KEY_AUDIO_CHANNELS = "audio_channels"
        private const val KEY_VIDEO_FRAME_RATE = "video_frame_rate"
        internal const val MAX_TEXT = 160
        private const val MIN_FPS = 0f
        private const val MAX_FPS = 240f
        private const val MIN_PAR = 0.1f
        private const val MAX_PAR = 10f
        private const val DEFAULT_PAR = 1f
        private const val MAX_BITRATE = 1_000_000L
        private const val MAX_DIMENSION = 16_384
        private const val MAX_BUFFER = 3_600_000L
        private const val MAX_SAMPLE_RATE = 384_000
        private const val MAX_CHANNELS = 32

        /** Decodes the same bounded payload from untyped values (Bundle or test map). */
        internal fun fromKeyValueMap(values: Map<String, Any?>): PlaybackDiagnostics {
            fun text(key: String): String = capText(values[key] as? String ?: "")
            fun number(key: String): Number? = values[key] as? Number
            fun float(key: String, default: Float, min: Float, max: Float): Float {
                val raw = number(key)?.toFloat() ?: return default
                return if (raw.isFinite()) raw.coerceIn(min, max) else default
            }
            fun int(key: String, min: Int, max: Int): Int =
                (number(key)?.toInt() ?: min).coerceIn(min, max)
            fun long(key: String, max: Long): Long =
                (number(key)?.toLong() ?: 0L).coerceIn(0L, max)
            return PlaybackDiagnostics(
                fps = float(KEY_FPS, MIN_FPS, MIN_FPS, MAX_FPS),
                bitrateKbps = long(KEY_BITRATE, MAX_BITRATE),
                videoWidth = int(KEY_WIDTH, 0, MAX_DIMENSION),
                videoHeight = int(KEY_HEIGHT, 0, MAX_DIMENSION),
                pixelWidthHeightRatio = float(KEY_PAR, DEFAULT_PAR, MIN_PAR, MAX_PAR),
                codec = text(KEY_CODEC),
                decoder = text(KEY_DECODER),
                bufferMs = long(KEY_BUFFER, MAX_BUFFER),
                droppedFrames = long(KEY_DROPPED, Long.MAX_VALUE / 4),
                audioFormat = text(KEY_AUDIO),
                colorInfo = text(KEY_COLOR),
                videoSizeInfo = text(KEY_VIDEO_SIZE),
                audioMime = text(KEY_AUDIO_MIME),
                videoMime = text(KEY_VIDEO_MIME),
                audioDecoder = text(KEY_AUDIO_DECODER),
                audioSampleRate = int(KEY_AUDIO_SAMPLE_RATE, 0, MAX_SAMPLE_RATE),
                audioChannelCount = int(KEY_AUDIO_CHANNELS, 0, MAX_CHANNELS),
                videoFrameRate = float(KEY_VIDEO_FRAME_RATE, MIN_FPS, MIN_FPS, MAX_FPS),
            )
        }

        fun fromBundle(bundle: Bundle?): PlaybackDiagnostics {
            if (bundle == null) return PlaybackDiagnostics()
            fun read(key: String): Any? = runCatching {
                when (key) {
                    KEY_FPS, KEY_PAR, KEY_VIDEO_FRAME_RATE -> bundle.getFloat(key)
                    KEY_BITRATE, KEY_BUFFER, KEY_DROPPED -> bundle.getLong(key)
                    KEY_WIDTH, KEY_HEIGHT, KEY_AUDIO_SAMPLE_RATE, KEY_AUDIO_CHANNELS -> bundle.getInt(key)
                    else -> bundle.getString(key)
                }
            }.getOrNull()
            return fromKeyValueMap(
                ALL_KEYS.associateWith(::read),
            )
        }

        private val ALL_KEYS = listOf(
            KEY_FPS, KEY_BITRATE, KEY_WIDTH, KEY_HEIGHT, KEY_PAR, KEY_CODEC, KEY_DECODER,
            KEY_BUFFER, KEY_DROPPED, KEY_AUDIO, KEY_COLOR, KEY_VIDEO_SIZE, KEY_AUDIO_MIME,
            KEY_VIDEO_MIME, KEY_AUDIO_DECODER, KEY_AUDIO_SAMPLE_RATE, KEY_AUDIO_CHANNELS,
            KEY_VIDEO_FRAME_RATE,
        )
    }
}

/** Stable, credential-free classification of decoder initialisation failures. */
enum class DecoderFailureClass(val token: String) {
    NONE(""),
    AUDIO_INIT_FAILED("AUDIO_DECODER_INIT_FAILED"),
    VIDEO_INIT_FAILED("VIDEO_DECODER_INIT_FAILED"),
    INIT_LIMIT_REACHED("DECODER_INIT_LIMIT_REACHED"),
    ;

    /**
     * Appends the classification to an existing diagnostics text field (video [PlaybackDiagnostics.decoder],
     * audio [PlaybackDiagnostics.audioDecoder] / [PlaybackDiagnostics.audioFormat]). No new Bundle key
     * or user-facing string resource is introduced; the cap keeps session extras bounded.
     */
    fun decorate(value: String): String {
        if (token.isEmpty()) return value.take(PlaybackDiagnostics.MAX_TEXT)
        val room = PlaybackDiagnostics.MAX_TEXT - token.length - 3
        val base = value.take(room.coerceAtLeast(0)).trim()
        return if (base.isEmpty()) token else "$base | $token"
    }
}

/**
 * Pure per-session policy for repeated decoder initialisation failures.
 *
 * Media3 already tries the finite decoder list internally. This policy never starts a retry:
 * it saturates the counter and marks a terminal class so repeated failures cannot produce an
 * unbounded sequence of diagnostics updates or application-level fallbacks.
 */
object DecoderFailurePolicy {
    const val MAX_INIT_FAILURES_PER_SESSION = 3

    /** Saturating increment; once the cap is reached the count never grows again. */
    fun nextCount(current: Int): Int =
        (current.coerceAtLeast(0) + 1).coerceAtMost(MAX_INIT_FAILURES_PER_SESSION)

    fun classify(failureCount: Int, isInitFailure: Boolean, isAudioRenderer: Boolean): DecoderFailureClass =
        when {
            failureCount <= 0 || !isInitFailure -> DecoderFailureClass.NONE
            failureCount >= MAX_INIT_FAILURES_PER_SESSION -> DecoderFailureClass.INIT_LIMIT_REACHED
            isAudioRenderer -> DecoderFailureClass.AUDIO_INIT_FAILED
            else -> DecoderFailureClass.VIDEO_INIT_FAILED
        }
}

private fun finiteFloat(value: Float, min: Float, max: Float, fallback: Float = min): Float =
    if (value.isFinite()) value.coerceIn(min, max) else fallback

private fun capText(value: String): String = value
    .map { if (it.isISOControl()) ' ' else it }
    .joinToString("")
    .take(PlaybackDiagnostics.MAX_TEXT)
    .trim()
