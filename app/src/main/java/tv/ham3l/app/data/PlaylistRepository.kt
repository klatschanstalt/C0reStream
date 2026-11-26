package tv.ham3l.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.ham3l.app.data.db.AppDatabase
import tv.ham3l.app.data.db.ChannelEntity
import tv.ham3l.app.data.db.PlaylistEntity
import tv.ham3l.app.data.model.EpgEntry
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

class PlaylistRepository(private val db: AppDatabase) {

    private fun client(ua: String?, useProxy: Boolean, proxyHost: String?, proxyPort: Int?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
        if (!ua.isNullOrBlank()) {
            builder.addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", ua).build()) }
        }
        if (useProxy && !proxyHost.isNullOrBlank() && proxyPort != null) {
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyHost, proxyPort)))
        }
        return builder.build()
    }

    suspend fun fetchAndStore(displayName: String, url: String, epgUrl: String?, ua: String?, useProxy: Boolean, proxyHost: String?, proxyPort: Int?): Result<Long> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = client(ua, useProxy, proxyHost, proxyPort)
                    .newCall(Request.Builder().url(url).build()).execute().use { resp ->
                        check(resp.isSuccessful) { "HTTP " + resp.code }
                        resp.body!!.string()
                    }
                val channels = M3UParser.parse(body)
                check(channels.isNotEmpty()) { "Keine Kanaele in der Liste gefunden" }

                val playlistId = db.playlistDao().upsert(
                    PlaylistEntity(name = displayName, url = url, epgUrl = epgUrl, userAgent = ua, useProxy = useProxy, proxyHost = proxyHost, proxyPort = proxyPort, lastUpdated = System.currentTimeMillis()),
                )
                db.channelDao().deleteForPlaylist(playlistId)
                db.channelDao().insertAll(M3UParser.toChannels(playlistId, channels).map {
                    ChannelEntity(playlistId = it.playlistId, name = it.name, logoUrl = it.logoUrl, group = it.group, url = it.url, tvgId = it.tvgId, catchupSource = it.catchupSource)
                })
                db.playlistDao().updateMeta(playlistId, channels.size, System.currentTimeMillis())
                playlistId
            }
        }

    suspend fun loadEpgNow(playlistId: Long): Map<String, List<EpgEntry>> = withContext(Dispatchers.IO) {
        val pl = db.playlistDao().byId(playlistId) ?: return@withContext emptyMap()
        val epgUrl = pl.epgUrl ?: return@withContext emptyMap()
        runCatching {
            val text = client(pl.userAgent, pl.useProxy, pl.proxyHost, pl.proxyPort)
                .newCall(Request.Builder().url(epgUrl).build()).execute().use { it.body!!.string() }
            EpgParser.parse(text.reader()).mapValues { entry -> entry.value.sortedBy { e -> e.start } }
        }.getOrDefault(emptyMap())
    }
}
