package pw.c0re.stream.ui.backup

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import pw.c0re.stream.data.AppLog
import pw.c0re.stream.data.ChannelKey
import pw.c0re.stream.data.NetworkPolicy
import pw.c0re.stream.data.SettingsRepository
import pw.c0re.stream.data.XtreamApi
import pw.c0re.stream.data.db.AppDatabase
import pw.c0re.stream.data.db.ChannelEntity
import pw.c0re.stream.data.db.FavoriteEntity
import pw.c0re.stream.data.db.HistoryEntity
import pw.c0re.stream.data.db.PlaylistEntity
import pw.c0re.stream.data.db.ProtectedUrl
import pw.c0re.stream.data.db.ProgressEntity
import pw.c0re.stream.data.db.ReminderEntity
import pw.c0re.stream.data.db.WatchlistEntity

data class RestoreResult(
    val playlists: Int,
    val channels: Int,
    val settingsRestored: Boolean,
)

/**
 * Portable, authenticated library backup (format version 2). Includes playlists, channels,
 * favorites, history, playback progress, watchlist, reminders and app settings. Restore is
 * idempotent: identical playlists/channels are merged instead of duplicated.
 */
object LibraryBackup {
    private const val version = 2
    private const val maxRecords = 200_000

    private fun JSONObject.optional(key: String): String? =
        if (isNull(key)) null else getString(key)

    private fun JSONObject.putNullable(key: String, value: String?) = put(key, value ?: JSONObject.NULL)

    private fun JSONArray.objects(): List<JSONObject> {
        require(length() <= maxRecords) { "Too many backup records" }
        return List(length()) { getJSONObject(it) }
    }

    suspend fun export(
        db: AppDatabase,
        settings: SettingsRepository,
        password: CharArray,
    ): ByteArray = withContext(Dispatchers.IO) {
        val snapshot = settings.snapshot()
        val root = db.withTransaction {
            val playlists = JSONArray()
            val channels = JSONArray()
            val favorites = JSONArray()
            val history = JSONArray()
            for (playlist in db.playlistDao().allOnce()) {
                playlists.put(
                    JSONObject().put("id", playlist.id).put("name", playlist.name)
                        .putNullable("url", playlist.url)
                        .putNullable("epg", playlist.epgUrl)
                        .putNullable("agent", playlist.userAgent)
                        .put("proxy", playlist.useProxy)
                        .putNullable("host", playlist.proxyHost)
                        .put("port", playlist.proxyPort ?: JSONObject.NULL)
                        .put("autoRefresh", playlist.autoRefresh)
                        .put("refreshHours", playlist.refreshHours)
                        .put("updated", playlist.lastUpdated),
                )
                for (channel in db.channelDao().snapshot(playlist.id)) {
                    channels.put(
                        JSONObject().put("id", channel.id).put("playlist", playlist.id)
                            .put("name", channel.name).put("url", channel.url)
                            .putNullable("logo", channel.logoUrl)
                            .putNullable("group", channel.group)
                            .putNullable("tvg", channel.tvgId)
                            .putNullable("catchup", channel.catchupSource)
                            .put("stableKey", channel.stableKey),
                    )
                }
            }
            for (favorite in db.favoriteDao().allOnce()) {
                favorites.put(JSONObject().put("channel", favorite.channelId))
            }
            for (entry in db.historyDao().snapshot()) {
                history.put(JSONObject().put("channel", entry.channelId).put("time", entry.watchedAt))
            }
            val progress = JSONArray()
            for (entry in db.progressDao().allOnce()) {
                progress.put(
                    JSONObject().put("playlist", entry.playlistId).put("key", entry.mediaKey)
                        .put("title", entry.title).put("url", entry.url)
                        .put("position", entry.positionMs).put("duration", entry.durationMs)
                        .put("kind", entry.kind).put("updated", entry.updatedAt),
                )
            }
            val watchlist = JSONArray()
            for (entry in db.watchlistDao().allOnce()) {
                watchlist.put(
                    JSONObject().put("playlist", entry.playlistId).put("key", entry.mediaKey)
                        .put("title", entry.title).putNullable("poster", entry.posterUrl)
                        .putNullable("url", entry.url).put("kind", entry.kind).put("added", entry.addedAt),
                )
            }
            val reminders = JSONArray()
            for (entry in db.reminderDao().allOnce()) {
                reminders.put(
                    JSONObject().put("channel", entry.channelId).put("channelName", entry.channelName)
                        .putNullable("tvg", entry.tvgId).put("title", entry.title)
                        .put("start", entry.start).put("stop", entry.stop).put("created", entry.createdAt),
                )
            }
            JSONObject().put("version", version)
                .put("settings", settings.exportJson(snapshot))
                .put("playlists", playlists)
                .put("channels", channels)
                .put("favorites", favorites)
                .put("history", history)
                .put("progress", progress)
                .put("watchlist", watchlist)
                .put("reminders", reminders)
        }
        val payload = root.toString().toByteArray(Charsets.UTF_8)
        try {
            BackupCrypto.encrypt(payload, password)
        } finally {
            payload.fill(0)
        }
    }

    suspend fun restore(
        db: AppDatabase,
        settings: SettingsRepository,
        envelope: ByteArray,
        password: CharArray,
    ): RestoreResult = withContext(Dispatchers.IO) {
        val payload = BackupCrypto.decrypt(envelope, password)
        val root = try {
            JSONObject(payload.toString(Charsets.UTF_8))
        } finally {
            payload.fill(0)
        }
        val format = root.getInt("version")
        require(format in 1..version) { "Unsupported backup version" }
        val playlists = root.optJSONArray("playlists")?.objects().orEmpty()
        val channels = root.optJSONArray("channels")?.objects().orEmpty()
        val favorites = root.optJSONArray("favorites") ?: JSONArray()
        val history = root.optJSONArray("history")?.objects().orEmpty()
        val progress = root.optJSONArray("progress")?.objects().orEmpty()
        val watchlist = root.optJSONArray("watchlist")?.objects().orEmpty()
        val reminders = root.optJSONArray("reminders")?.objects().orEmpty()
        require(favorites.length() <= maxRecords)

        // Validate every URL and identity before opening the transaction.
        playlists.forEach { validatePlaylist(it) }
        channels.forEach { require(NetworkPolicy.parseStreamUrl(it.getString("url")) != null) }
        progress.forEach { require(NetworkPolicy.parseStreamUrl(it.getString("url")) != null) }
        watchlist.forEach { item ->
            item.optional("url")?.let { require(NetworkPolicy.parseStreamUrl(it) != null) }
            item.optional("poster")?.let { require(NetworkPolicy.parse(it) != null) }
        }
        require(playlists.map { it.getLong("id") }.toSet().size == playlists.size) { "Duplicate playlist IDs" }
        require(channels.map { it.getLong("id") }.toSet().size == channels.size) { "Duplicate channel IDs" }

        val restored = db.withTransaction {
            val playlistIds = mutableMapOf<Long, Long>()
            val channelEntities = mutableMapOf<Long, ChannelEntity>()
            val seenChannelKeys = HashSet<String>()
            val existingPlaylists = db.playlistDao().allOnce()
            val byIdentity = existingPlaylists.associateBy { "${it.name}\u0000${it.url.orEmpty()}" }

            for (item in playlists) {
                val name = item.getString("name").take(512)
                val url = item.optional("url")
                val epg = item.optional("epg")
                val identity = "$name\u0000${url.orEmpty()}"
                val existing = byIdentity[identity]
                val id = if (existing != null) {
                    db.playlistDao().upsert(
                        existing.copy(
                            name = name,
                            protectedUrl = url?.let { ProtectedUrl(it) },
                            protectedEpgUrl = epg?.let { ProtectedUrl(it) },
                            userAgent = item.optional("agent"),
                            useProxy = item.optBoolean("proxy"),
                            proxyHost = item.optional("host"),
                            proxyPort = if (item.isNull("port")) null else item.optInt("port"),
                            autoRefresh = item.optBoolean("autoRefresh", true),
                            refreshHours = item.optInt("refreshHours", 12).coerceIn(1, 168),
                        ),
                    )
                    existing.id
                } else {
                    db.playlistDao().upsert(
                        PlaylistEntity(
                            name = name,
                            url = url,
                            epgUrl = epg,
                            userAgent = item.optional("agent"),
                            useProxy = item.optBoolean("proxy"),
                            proxyHost = item.optional("host"),
                            proxyPort = if (item.isNull("port")) null else item.optInt("port"),
                            autoRefresh = item.optBoolean("autoRefresh", true),
                            refreshHours = item.optInt("refreshHours", 12).coerceIn(1, 168),
                        ),
                    )
                }
                playlistIds[item.getLong("id")] = id
            }

            for (item in channels) {
                val playlistId = requireNotNull(playlistIds[item.getLong("playlist")]) { "Unknown playlist" }
                val url = item.getString("url")
                val name = item.getString("name").take(512)
                val tvgId = item.optional("tvg")
                val stableKey = item.optional("stableKey")?.takeIf { it.isNotBlank() }
                    ?: ChannelKey.stableKey(url, tvgId)
                if (!seenChannelKeys.add("$playlistId\u0000$stableKey")) continue
                val existing = db.channelDao().byStableKey(playlistId, stableKey)
                val entity = ChannelEntity(
                    id = existing?.id ?: 0L,
                    playlistId = playlistId,
                    name = name,
                    logoUrl = item.optional("logo")?.takeIf { NetworkPolicy.parse(it) != null },
                    group = item.optional("group")?.take(512),
                    url = url,
                    tvgId = tvgId?.take(256),
                    catchupSource = item.optional("catchup")
                        ?.takeIf { NetworkPolicy.isTemplateUrl(it) || NetworkPolicy.parse(it) != null },
                    stableKey = stableKey,
                )
                val id = if (existing != null) {
                    db.channelDao().insertAll(listOf(entity))
                    entity.id
                } else {
                    db.channelDao().insertOne(entity)
                }
                channelEntities[item.getLong("id")] = entity.copy(id = id)
            }

            for (index in 0 until favorites.length()) {
                val channel = channelEntities[favorites.getJSONObject(index).getLong("channel")] ?: continue
                db.favoriteDao().add(
                    FavoriteEntity(channelId = channel.id, name = channel.name, logoUrl = channel.logoUrl, url = channel.url),
                )
            }
            for (item in history) {
                val channel = channelEntities[item.getLong("channel")] ?: continue
                // Idempotent per (channel, timestamp) so repeated restores keep all visits.
                val watchedAt = item.optLong("time", System.currentTimeMillis())
                db.historyDao().deleteByChannelAndTime(channel.id, watchedAt)
                db.historyDao().add(
                    HistoryEntity(
                        channelId = channel.id,
                        name = channel.name,
                        logoUrl = channel.logoUrl,
                        url = channel.url,
                        watchedAt = watchedAt,
                    ),
                )
            }
            for (item in progress) {
                val playlistId = playlistIds[item.optLong("playlist")] ?: continue
                db.progressDao().upsert(
                    ProgressEntity(
                        playlistId = playlistId,
                        mediaKey = item.getString("key").take(512),
                        title = item.getString("title").take(512),
                        url = item.getString("url"),
                        positionMs = item.optLong("position").coerceAtLeast(0),
                        durationMs = item.optLong("duration").coerceAtLeast(0),
                        kind = item.optString("kind", "vod").take(32),
                        updatedAt = item.optLong("updated", System.currentTimeMillis()),
                    ),
                )
            }
            for (item in watchlist) {
                val playlistId = playlistIds[item.optLong("playlist")] ?: continue
                db.watchlistDao().upsert(
                    WatchlistEntity(
                        playlistId = playlistId,
                        mediaKey = item.getString("key").take(512),
                        title = item.getString("title").take(512),
                        posterUrl = item.optional("poster"),
                        url = item.optional("url"),
                        kind = item.optString("kind", "movie").take(32),
                        addedAt = item.optLong("added", System.currentTimeMillis()),
                    ),
                )
            }
            for (item in reminders) {
                val channel = channelEntities[item.getLong("channel")] ?: continue
                db.reminderDao().upsert(
                    ReminderEntity(
                        channelId = channel.id,
                        channelName = channel.name.take(512),
                        tvgId = channel.tvgId,
                        title = item.getString("title").take(512),
                        start = item.getLong("start"),
                        stop = item.optLong("stop", 0),
                        createdAt = item.optLong("created", System.currentTimeMillis()),
                    ),
                )
            }
            val counts = channels.groupingBy { it.getLong("playlist") }.eachCount()
            for ((oldPlaylistId, newPlaylistId) in playlistIds) {
                db.playlistDao().updateMeta(newPlaylistId, counts[oldPlaylistId] ?: 0, System.currentTimeMillis())
            }
            RestoreResult(playlists.size, channels.size, format >= 2 && root.has("settings"))
        }
        try {
            settings.restoreJson(root.optJSONObject("settings"))
        } catch (error: Exception) {
            // Settings live in DataStore, outside the Room transaction; keep the library and
            // report the failure instead of silently reverting a successful restore.
            AppLog.log("Backup", "Settings restore failed: ${error.message ?: "?"}")
        }
        restored
    }

    private fun validatePlaylist(item: JSONObject) {
        val url = item.optional("url") ?: return
        if (url.startsWith("xtream://")) {
            val credentials = requireNotNull(XtreamApi.parseSyntheticUrl(url)) { "Invalid Xtream URL" }
            require(NetworkPolicy.parse(credentials.host) != null)
        } else {
            require(NetworkPolicy.parseStreamUrl(url) != null) { "Invalid playlist URL" }
        }
        item.optional("epg")?.let { require(NetworkPolicy.parse(it) != null) { "Invalid EPG URL" } }
    }
}
