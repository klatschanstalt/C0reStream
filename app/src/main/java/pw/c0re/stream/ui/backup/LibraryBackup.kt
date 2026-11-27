package pw.c0re.stream.ui.backup

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import pw.c0re.stream.data.NetworkPolicy
import pw.c0re.stream.data.XtreamApi
import pw.c0re.stream.data.db.*

/** Portable library backup. Restore merges with new IDs; existing data is not deleted. */
object LibraryBackup {
    private fun JSONObject.optional(key: String): String? =
        if (isNull(key)) null else getString(key)
    private fun JSONObject.putNullable(key: String, value: String?) = put(key, value ?: JSONObject.NULL)
    private fun JSONArray.objects(): List<JSONObject> {
        require(length() <= 100000) { "Too many backup records" }
        return List(length()) { getJSONObject(it) }
    }
    suspend fun export(db: AppDatabase, password: CharArray): ByteArray = withContext(Dispatchers.IO) {
        val root = db.withTransaction {
            val playlists = JSONArray()
            val channels = JSONArray()
            val favorites = JSONArray()
            val history = JSONArray()
            for (p in db.playlistDao().all().first()) {
                playlists.put(JSONObject().put("id", p.id).put("name", p.name)
                    .putNullable("url", p.url).putNullable("epg", p.epgUrl)
                    .putNullable("agent", p.userAgent).put("proxy", p.useProxy)
                    .putNullable("host", p.proxyHost).put("port", p.proxyPort ?: JSONObject.NULL)
                    .put("updated", p.lastUpdated))
                for (c in db.channelDao().snapshot(p.id)) {
                    channels.put(JSONObject().put("id", c.id).put("playlist", p.id).put("name", c.name)
                        .put("url", c.url).putNullable("logo", c.logoUrl).putNullable("group", c.group)
                        .putNullable("tvg", c.tvgId).putNullable("catchup", c.catchupSource))
                }
            }
            for (f in db.favoriteDao().all().first()) favorites.put(f.channelId)
            for (h in db.historyDao().snapshot()) history.put(JSONObject().put("channel", h.channelId).put("time", h.watchedAt))
            JSONObject().put("version", 1).put("playlists", playlists).put("channels", channels)
                .put("favorites", favorites).put("history", history)
        }
        val payload = root.toString().toByteArray(Charsets.UTF_8)
        try { BackupCrypto.encrypt(payload, password) } finally { payload.fill(0) }
    }

    suspend fun restore(db: AppDatabase, envelope: ByteArray, password: CharArray): Int = withContext(Dispatchers.IO) {
        val payload = BackupCrypto.decrypt(envelope, password)
        val root = try { JSONObject(payload.toString(Charsets.UTF_8)) } finally { payload.fill(0) }
        require(root.getInt("version") == 1)
        val playlists = root.getJSONArray("playlists").objects()
        val channels = root.getJSONArray("channels").objects()
        val favorites = root.getJSONArray("favorites")
        val history = root.getJSONArray("history").objects()
        require(favorites.length() <= 100000)
        require(playlists.map { it.getLong("id") }.toSet().size == playlists.size)
        require(channels.map { it.getLong("id") }.toSet().size == channels.size)
        db.withTransaction {
            val ids = mutableMapOf<Long, Long>()
            val channelIds = mutableMapOf<Long, ChannelEntity>()
            val counts = channels.groupingBy { it.getLong("playlist") }.eachCount()
            for (p in playlists) {
                val url = p.optional("url")
                if (url != null) {
                    if (url.startsWith("xtream://")) {
                        val credentials = requireNotNull(XtreamApi.parseSyntheticUrl(url))
                        NetworkPolicy.requireUrl(credentials.host)
                    } else NetworkPolicy.requireUrl(url)
                }
                val epg = p.optional("epg")
                epg?.let { NetworkPolicy.requireUrl(it) }
                val oldId = p.getLong("id")
                ids[oldId] = db.playlistDao().upsert(PlaylistEntity(name = p.getString("name"), url = url,
                    epgUrl = epg, userAgent = p.optional("agent"), useProxy = p.optBoolean("proxy"),
                    proxyHost = p.optional("host"), proxyPort = if (p.isNull("port")) null else p.getInt("port"),
                    channelCount = counts[oldId] ?: 0, lastUpdated = p.optLong("updated")))
            }
            for (c in channels) {
                val url = c.getString("url")
                NetworkPolicy.requireUrl(url)
                val playlist = requireNotNull(ids[c.getLong("playlist")])
                val entity = ChannelEntity(playlistId = playlist, name = c.getString("name"),
                    logoUrl = c.optional("logo")?.takeIf { NetworkPolicy.parse(it) != null },
                    group = c.optional("group"), url = url, tvgId = c.optional("tvg"), catchupSource = c.optional("catchup"))
                val id = db.channelDao().insertOne(entity)
                channelIds[c.getLong("id")] = entity.copy(id = id)
            }
            for (i in 0 until favorites.length()) {
                val c = channelIds[favorites.getLong(i)] ?: continue
                db.favoriteDao().add(FavoriteEntity(channelId = c.id, name = c.name, logoUrl = c.logoUrl, url = c.url))
            }
            for (h in history) {
                val c = channelIds[h.getLong("channel")] ?: continue
                db.historyDao().add(HistoryEntity(channelId = c.id, name = c.name, logoUrl = c.logoUrl, url = c.url, watchedAt = h.getLong("time")))
            }
            playlists.size
        }
    }
}
