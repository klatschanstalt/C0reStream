package pw.c0re.stream.data

import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import pw.c0re.stream.data.db.AppDatabase
import pw.c0re.stream.data.db.ChannelEntity
import pw.c0re.stream.data.db.PlaylistEntity
import pw.c0re.stream.data.model.EpgEntry
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

class PlaylistRepository(private val db: AppDatabase) {

    private fun client(ua: String?, useProxy: Boolean, proxyHost: String?, proxyPort: Int?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .followSslRedirects(false)
        if (!ua.isNullOrBlank()) {
            builder.addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", ua).build()) }
        }
        if (useProxy && !proxyHost.isNullOrBlank() && proxyPort != null) {
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort)))
        }
        return builder.build()
    }

    suspend fun fetchAndStore(displayName: String, url: String, epgUrl: String?, ua: String?, useProxy: Boolean, proxyHost: String?, proxyPort: Int?, existingId: Long? = null): Result<Long> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = client(ua, useProxy, proxyHost, proxyPort)
                    .newCall(Request.Builder().url(NetworkPolicy.requireUrl(url)).build()).execute().use { resp ->
                        check(resp.isSuccessful) { "HTTP " + resp.code }
                        resp.readMetadata()
                    }
                val channels = M3UParser.parse(body)
                check(channels.isNotEmpty()) { "Keine Kanaele in der Liste gefunden" }

                storeChannels(displayName, url, epgUrl, ua, useProxy, proxyHost, proxyPort, channels, existingId)
            }.onFailure { if (it is CancellationException) throw it }
        }

    suspend fun storeChannels(name: String, url: String?, epg: String?, ua: String?, proxy: Boolean, host: String?, port: Int?, raw: List<M3UParser.RawChannel>, existingId: Long? = null): Long = db.withTransaction {
        check(raw.isNotEmpty()) { "Keine Kanaele gefunden" }
        val id = if (existingId != null) {
            check(db.playlistDao().byId(existingId) != null) { "Playlist nicht mehr vorhanden" }
            existingId
        } else db.playlistDao().upsert(PlaylistEntity(name = name, url = url, epgUrl = epg, userAgent = ua, useProxy = proxy, proxyHost = host, proxyPort = port))
        val old = db.channelDao().snapshot(id)
        val byUrl = old.groupBy { it.url }.mapValues { ArrayDeque(it.value) }
        val channels = raw.distinctBy { it.url }.map { r ->
            ChannelEntity(id = byUrl[r.url]?.removeFirstOrNull()?.id ?: 0L, playlistId = id, name = r.name, logoUrl = r.logo, group = r.group, url = r.url, tvgId = r.tvgId, catchupSource = r.catchupSource)
        }
        val retained = channels.map { it.id }.toHashSet()
        old.filter { it.id !in retained }.forEach { db.channelDao().deleteById(it.id) }
        db.channelDao().insertAll(channels)
        db.playlistDao().updateMeta(id, channels.size, System.currentTimeMillis())
        id
    }

    suspend fun loadEpgNow(playlistId: Long): Map<String, List<EpgEntry>> = withContext(Dispatchers.IO) {
        val pl = db.playlistDao().byId(playlistId) ?: return@withContext emptyMap()
        val epgUrl = pl.epgUrl ?: return@withContext emptyMap()
        runCatching {
            val text = client(pl.userAgent, pl.useProxy, pl.proxyHost, pl.proxyPort)
                .newCall(Request.Builder().url(NetworkPolicy.requireUrl(epgUrl)).build()).execute().use { it.readMetadata() }
            EpgParser.parse(text.reader()).mapValues { entry -> entry.value.sortedBy { e -> e.start } }
        }.getOrDefault(emptyMap())
    }
}
