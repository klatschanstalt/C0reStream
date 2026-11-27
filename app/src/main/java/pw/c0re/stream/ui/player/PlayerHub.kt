package pw.c0re.stream.ui.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import pw.c0re.stream.data.player.PlaybackDiagnostics
import pw.c0re.stream.service.PlaybackService

/**
 * App-wide playback state. The MediaController is intentionally kept alive when the full
 * player screen is closed so background playback and the mini-player keep working.
 */
object PlayerHub {
    data class PendingPlayback(
        val url: String,
        val title: String?,
        val mediaKey: String?,
        val kind: String,
        val playlistId: Long,
    )

    var controller by mutableStateOf<MediaController?>(null)
    var title by mutableStateOf<String?>(null)
    var isPlaying by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var channelId by mutableLongStateOf(0L)
    var streamUrl by mutableStateOf<String?>(null)
    var mediaKey by mutableStateOf<String?>(null)
    var kind by mutableStateOf("live")
    var playlistId by mutableLongStateOf(0L)
    var pending by mutableStateOf<PendingPlayback?>(null)

    /** Latest service-side telemetry, delivered through MediaSession extras (no URLs). */
    var diagnostics by mutableStateOf(PlaybackDiagnostics())

    /** Current item metadata, exposed for the diagnostics overlay and player UI. */
    var mediaMetadata by mutableStateOf(MediaMetadata.EMPTY)

    /** Current decoded video size, including pixel aspect ratio. */
    var videoSize by mutableStateOf(VideoSize.UNKNOWN)

    private var future: ListenableFuture<MediaController>? = null
    private var playerListener: Player.Listener? = null
    private var controllerListener: MediaController.Listener? = null

    @androidx.annotation.OptIn(UnstableApi::class)
    fun connect(context: Context) {
        if (controller != null || future != null) return
        val appContext = context.applicationContext
        val controllerListenerImpl = object : MediaController.Listener {
            override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
                if (this@PlayerHub.controller === controller) {
                    diagnostics = PlaybackDiagnostics.fromBundle(extras)
                }
            }

            override fun onDisconnected(controller: MediaController) {
                if (this@PlayerHub.controller !== controller) return
                detachPlayerListener()
                this@PlayerHub.controller = null
                isPlaying = false
            }
        }.also { controllerListener = it }

        val pendingFuture = MediaController.Builder(
            appContext,
            SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java)),
        )
            .setListener(controllerListenerImpl)
            .buildAsync()
            .also { future = it }
        pendingFuture.addListener({
            if (pendingFuture.isCancelled || future !== pendingFuture) return@addListener
            runCatching { pendingFuture.get() }.onSuccess { mediaController ->
                if (future !== pendingFuture) {
                    runCatching { mediaController.release() }
                    return@onSuccess
                }
                attachPlayerListener(mediaController)
                diagnostics = PlaybackDiagnostics.fromBundle(mediaController.sessionExtras)
                mediaMetadata = mediaController.mediaMetadata
                videoSize = mediaController.videoSize
                controller = mediaController
                error = null
            }.onFailure {
                future = null
                controllerListener = null
                error = "Playback service unavailable"
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    private fun attachPlayerListener(mediaController: MediaController) {
        if (playerListener != null) return
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                PlayerHub.error = error.errorCodeName
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) PlayerHub.error = null
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                this@PlayerHub.mediaMetadata = mediaMetadata
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                this@PlayerHub.videoSize = videoSize
            }
        }
        playerListener = listener
        mediaController.addListener(listener)
    }

    private fun detachPlayerListener() {
        val active = controller
        val listener = playerListener
        if (active != null && listener != null) runCatching { active.removeListener(listener) }
        playerListener = null
    }

    fun prepare(url: String, title: String?, mediaKey: String?, kind: String, playlistId: Long) {
        pending = PendingPlayback(url, title, mediaKey, kind, playlistId)
    }

    fun consumePending() {
        pending = null
    }

    fun attach(
        title: String?,
        channelId: Long,
        streamUrl: String?,
        mediaKey: String?,
        kind: String,
        playlistId: Long,
    ) {
        this.title = title
        this.channelId = channelId
        this.streamUrl = streamUrl
        this.mediaKey = mediaKey
        this.kind = kind
        this.playlistId = playlistId
    }

    fun close() {
        val current = controller
        detachPlayerListener()
        current?.let { mediaController ->
            runCatching { mediaController.stop() }
            runCatching { mediaController.release() }
        }
        controllerListener = null
        controller = null
        future?.cancel(false)
        future = null
        title = null
        isPlaying = false
        error = null
        channelId = 0L
        streamUrl = null
        mediaKey = null
        kind = "live"
        playlistId = 0L
        pending = null
        diagnostics = PlaybackDiagnostics()
        mediaMetadata = MediaMetadata.EMPTY
        videoSize = VideoSize.UNKNOWN
    }
}
