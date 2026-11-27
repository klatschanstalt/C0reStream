package pw.c0re.stream.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
