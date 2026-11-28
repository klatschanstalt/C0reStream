package pw.c0re.stream.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import pw.c0re.stream.C0reStreamApp
import pw.c0re.stream.data.ChannelKey
import pw.c0re.stream.data.db.ChannelEntity
import pw.c0re.stream.data.db.PlaylistEntity
import pw.c0re.stream.data.db.HistoryEntity
import pw.c0re.stream.data.db.ProgressEntity
import pw.c0re.stream.data.player.PlaybackPreferences

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as C0reStreamApp).database

    /** Playback settings store; video mode is scoped per channel, decoder/buffering globally. */
    val playbackPrefs = PlaybackPreferences(app)

    /** Stable, URL-free key for per-channel playback overrides. */
    fun preferenceKey(channel: ChannelEntity?): String? = channel?.let {
        val stable = ChannelKey.stableKey(it.url, it.tvgId)
        PlaybackPreferences.channelKey("live", it.id) + "_" + stable.take(24)
    }

    fun channelFor(id: Long): Flow<ChannelEntity?> = flow {
        emit(if (id <= 0) null else db.channelDao().byId(id))
    }

    /** Channels of the same playlist, for D-pad/remote channel zapping. */
    fun siblingsOf(channel: ChannelEntity?): Flow<List<ChannelEntity>> = flow {
        emit(if (channel == null) emptyList() else db.channelDao().byPlaylist(channel.playlistId).first())
    }

    fun logWatch(channel: ChannelEntity) = viewModelScope.launch {
        db.historyDao().deleteByChannel(channel.id)
        db.historyDao().add(
            HistoryEntity(
                channelId = channel.id,
                name = channel.name,
                logoUrl = channel.logoUrl,
                url = channel.url,
                watchedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Source-specific network settings for playback/recording of the current playlist. */
    suspend fun sourceFor(playlistId: Long): PlaylistEntity? =
        if (playlistId <= 0L) null else db.playlistDao().byId(playlistId)

    suspend fun progressFor(mediaKey: String): ProgressEntity? =
        if (mediaKey.isBlank()) null else db.progressDao().byKey(mediaKey)

    fun saveProgress(
        mediaKey: String?,
        playlistId: Long,
        title: String,
        url: String?,
        positionMs: Long,
        durationMs: Long,
        kind: String,
    ) {
        if (mediaKey.isNullOrBlank() || url.isNullOrBlank() || kind == "live") return
        if (durationMs <= 0 || positionMs <= 0) return
        viewModelScope.launch {
            if (positionMs < 30_000 || positionMs >= durationMs - 10_000) {
                db.progressDao().delete(mediaKey)
            } else {
                db.progressDao().upsert(
                    ProgressEntity(
                        playlistId = playlistId,
                        mediaKey = mediaKey,
                        title = title,
                        url = url,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        kind = kind,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }
}

/** Identity of one delayed playback action. All fields are checked before the action runs. */
internal data class PlaybackGeneration(
    val media: Long,
    val error: Long,
    val request: Long,
    val medium: String,
    val player: Any,
)

/**
 * Binds delayed player work (error retry, progress persistence, sleep timer) to a unique media
 * generation, player instance, error generation and playback request. A stale action is dropped
 * instead of preparing, pausing or writing progress for whichever medium is active by the time
 * its delay expires. All methods are called from the main/Compose thread.
 */
internal class PlaybackRetryCoordinator(
    private val scope: CoroutineScope,
    private val maxAttempts: Int = 3,
    private val delayForAttempt: (Int) -> Long = { attempt -> 1_500L * attempt },
) {
    private var mediaGeneration = 0L
    private var errorGeneration = 0L
    private var requestGeneration = 0L
    private var activeMedium: String? = null
    private var activePlayer: Any? = null
    private var retryJob: Job? = null
    private var attempts = 0

    val generation: Long get() = mediaGeneration

    fun isActive(medium: String, player: Any): Boolean =
        activeMedium == medium && activePlayer === player

    fun generationFor(player: Any): PlaybackGeneration? {
        val medium = activeMedium ?: return null
        if (activePlayer !== player) return null
        return PlaybackGeneration(mediaGeneration, errorGeneration, requestGeneration, medium, player)
    }

    fun onMediaChanged(medium: String, player: Any) {
        retryJob?.cancel()
        mediaGeneration++
        errorGeneration++
        requestGeneration++
        attempts = 0
        activeMedium = medium
        activePlayer = player
    }

    fun onPlayerChanged(player: Any?) {
        if (activePlayer === player) return
        retryJob?.cancel()
        activePlayer = player
        errorGeneration++
        attempts = 0
    }

    fun onStoppedOrReleased() {
        retryJob?.cancel()
        mediaGeneration++
        errorGeneration++
        requestGeneration++
        attempts = 0
        activeMedium = null
        activePlayer = null
    }

    /** Immediately supersedes pending work, e.g. when the user zaps to another channel. */
    fun invalidatePending() {
        retryJob?.cancel()
        errorGeneration++
    }

    fun onPlaybackRequest() {
        requestGeneration++
    }

    fun onReady() {
        retryJob?.cancel()
        errorGeneration++
        attempts = 0
    }

    /** Schedules one bounded retry. Returns false when the medium is gone or the limit is hit. */
    fun scheduleRetry(player: Any, retry: () -> Unit): Boolean {
        retryJob?.cancel()
        errorGeneration++
        val generation = generationFor(player) ?: return false
        if (attempts >= maxAttempts) return false
        val attempt = ++attempts
        retryJob = scope.launch {
            delay(delayForAttempt(attempt))
            if (!isRetryCurrent(generation)) return@launch
            onPlaybackRequest()
            retry()
        }
        return true
    }

    /** Immediate, user-triggered retry: still verifies the medium and player identity. */
    fun retryNow(player: Any, retry: () -> Unit): Boolean {
        retryJob?.cancel()
        errorGeneration++
        if (generationFor(player) == null) return false
        attempts = 0
        onPlaybackRequest()
        retry()
        return true
    }

    /** Medium/player identity only: progress and sleep timers survive recoverable errors. */
    fun isMediumCurrent(generation: PlaybackGeneration): Boolean =
        mediaGeneration == generation.media &&
            activeMedium == generation.medium &&
            activePlayer === generation.player

    /** Full guard: no retry may run for a superseded error or playback request. */
    fun isRetryCurrent(generation: PlaybackGeneration): Boolean =
        isMediumCurrent(generation) &&
            requestGeneration == generation.request &&
            errorGeneration == generation.error &&
            retryJob?.isActive == true
}
