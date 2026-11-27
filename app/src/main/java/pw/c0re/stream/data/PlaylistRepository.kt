package pw.c0re.stream.data

import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import pw.c0re.stream.data.db.AppDatabase
import pw.c0re.stream.data.db.ChannelEntity
import pw.c0re.stream.data.db.PlaylistEntity
import pw.c0re.stream.data.db.ProtectedUrl
import pw.c0re.stream.data.model.EpgEntry

class PlaylistRepository(private val db: AppDatabase) {

    suspend fun fetchAndStore(
        displayName: String,
        url: String,
        epgUrl: String?,
        userAgent: String?,
        useProxy: Boolean,
        proxyHost: String?,
        proxyPort: Int?,
        existingId: Long? = null,
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val client = NetworkPolicy.client(userAgent, useProxy, proxyHost, proxyPort)
            val body = client.newCall(Request.Builder().url(NetworkPolicy.requireUrl(url)).build())
                .execute()
                .use { response -> response.readText() }
            val channels = M3UParser.parse(body)
            check(channels.isNotEmpty()) { "No channels found" }
            storeChannels(displayName, url, epgUrl, userAgent, useProxy, proxyHost, proxyPort, channels, existingId)
        }.onFailure { if (it is CancellationException) throw it }
    }

    suspend fun refresh(playlist: PlaylistEntity): Result<Long> {
        val url = playlist.url ?: return Result.failure(IllegalArgumentException("Local playlist"))
        return if (url.startsWith("xtream://")) {
            runCatching {
                val credentials = requireNotNull(XtreamApi.parseSyntheticUrl(url)) { "Invalid Xtream credentials" }
                val client = NetworkPolicy.client(
                    playlist.userAgent, playlist.useProxy, playlist.proxyHost, playlist.proxyPort,
                )
                val raw = XtreamApi(client).fetchLiveChannels(credentials)
                check(raw.isNotEmpty()) { "No channels found" }
                storeChannels(
                    playlist.name, url, playlist.epgUrl, playlist.userAgent, playlist.useProxy,
                    playlist.proxyHost, playlist.proxyPort, raw, playlist.id,
                )
            }.onFailure { if (it is CancellationException) throw it }
        } else {
            fetchAndStore(
                playlist.name, url, playlist.epgUrl, playlist.userAgent, playlist.useProxy,
                playlist.proxyHost, playlist.proxyPort, playlist.id,
            )
        }
    }

    suspend fun storeChannels(
        name: String,
        url: String?,
        epg: String?,
        userAgent: String?,
        useProxy: Boolean,
        proxyHost: String?,
        proxyPort: Int?,
        raw: List<M3UParser.RawChannel>,
        existingId: Long? = null,
    ): Long = db.withTransaction {
        check(raw.isNotEmpty()) { "No channels found" }
        val playlistId = if (existingId != null) {
            val existing = requireNotNull(db.playlistDao().byId(existingId)) { "Playlist missing" }
            db.playlistDao().upsert(
                existing.copy(
                    name = name,
                    protectedUrl = url?.let { ProtectedUrl(it) },
                    protectedEpgUrl = epg?.let { ProtectedUrl(it) },
                    userAgent = NetworkPolicy.userAgent(userAgent),
                    useProxy = useProxy,
                    proxyHost = proxyHost,
                    proxyPort = proxyPort,
                ),
            )
            existingId
        } else {
            db.playlistDao().upsert(
                PlaylistEntity(
                    name = name,
                    url = url,
                    epgUrl = epg,
                    userAgent = NetworkPolicy.userAgent(userAgent),
                    useProxy = useProxy,
                    proxyHost = proxyHost,
                    proxyPort = proxyPort,
                ),
            )
        }

        // Stable keys deduplicate the import and preserve IDs across URL/token refreshes.
        val deduplicated = LinkedHashMap<String, M3UParser.RawChannel>()
        for (channel in raw) {
            val key = ChannelKey.stableKey(channel.url, channel.tvgId)
            deduplicated.putIfAbsent(key, channel)
        }

        val old = db.channelDao().snapshot(playlistId)
        if (existingId != null && old.size > 4 && deduplicated.size < old.size / 2) {
            error("Playlist shrank unexpectedly; keeping previous channels")
        }
        val oldByKey = old.filter { it.stableKey.isNotBlank() }.associateBy { it.stableKey }
        val oldByUrl = old.groupBy { it.url }.mapValues { ArrayDeque(it.value) }
        val channels = deduplicated.map { (key, channel) ->
            val existing = oldByKey[key] ?: oldByUrl[channel.url]?.removeFirstOrNull()
            ChannelEntity(
                id = existing?.id ?: 0L,
                playlistId = playlistId,
                name = channel.name,
                logoUrl = channel.logo,
                group = channel.group,
                url = channel.url,
                tvgId = channel.tvgId,
                catchupSource = channel.catchupSource,
                stableKey = key,
            )
        }
        val retainedIds = channels.mapNotNull { it.id.takeIf { id -> id > 0 } }.toSet()
        val removedIds = old.filter { it.id !in retainedIds }.map { it.id }
        if (removedIds.isNotEmpty()) {
            db.channelDao().deleteByIds(removedIds)
            db.reminderDao().deleteForChannels(removedIds)
        }
        db.channelDao().insertAll(channels)
        db.favoriteDao().removeOrphans()
        db.historyDao().removeOrphans()
        db.playlistDao().updateMeta(playlistId, channels.size, System.currentTimeMillis())
        playlistId
    }

    suspend fun deletePlaylist(playlistId: Long) = db.withTransaction {
        db.favoriteDao().removeForPlaylist(playlistId)
        db.historyDao().removeForPlaylist(playlistId)
        db.progressDao().deleteForPlaylist(playlistId)
        db.watchlistDao().deleteForPlaylist(playlistId)
        db.reminderDao().deleteForPlaylist(playlistId)
        db.epgDao().deleteForPlaylist(playlistId)
        db.channelDao().deleteForPlaylist(playlistId)
        db.playlistDao().delete(playlistId)
    }

    suspend fun loadEpg(playlistId: Long): Map<String, List<EpgEntry>> = withContext(Dispatchers.IO) {
        val playlist = db.playlistDao().byId(playlistId) ?: return@withContext emptyMap()
        val epgUrl = playlist.epgUrl ?: return@withContext emptyMap()
        try {
            val client = NetworkPolicy.client(
                playlist.userAgent, playlist.useProxy, playlist.proxyHost, playlist.proxyPort,
            )
            val text = client.newCall(Request.Builder().url(NetworkPolicy.requireUrl(epgUrl)).build())
                .execute()
                .use { response -> response.readText(48L * 1024 * 1024) }
            EpgParser.parse(text)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLog.log("EPG", error.message ?: "?")
            emptyMap()
        }
    }

    suspend fun setRefreshPolicy(playlistId: Long, enabled: Boolean, hours: Int) =
        db.playlistDao().setRefreshPolicy(playlistId, enabled, hours.coerceIn(1, 168))
}
