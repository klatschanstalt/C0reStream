package pw.c0re.stream.ui.player

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
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

    private var future: ListenableFuture<MediaController>? = null
    private var listener: Player.Listener? = null

    @androidx.annotation.OptIn(UnstableApi::class)
    fun connect(context: Context) {
        if (controller != null || future != null) return
        val appContext = context.applicationContext
        val pendingFuture = future ?: MediaController.Builder(
            appContext,
            SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java)),
        ).buildAsync().also { future = it }
        pendingFuture.addListener({
            if (pendingFuture.isCancelled || future !== pendingFuture) return@addListener
            runCatching { pendingFuture.get() }.onSuccess { mediaController ->
                if (future !== pendingFuture) {
                    runCatching { mediaController.release() }
                    return@onSuccess
                }
                listener = object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        PlayerHub.error = error.errorCodeName
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) PlayerHub.error = null
                    }
                }.also(mediaController::addListener)
                controller = mediaController
                error = null
            }.onFailure {
                future = null
                error = "Playback service unavailable"
            }
        }, ContextCompat.getMainExecutor(appContext))
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
        current?.let { mediaController ->
            listener?.let(mediaController::removeListener)
            runCatching { mediaController.stop() }
            runCatching { mediaController.release() }
        }
        listener = null
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
    }
}
