package pw.c0re.stream.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import pw.c0re.stream.data.M3UParser.RawChannel
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Xtream Codes API client. Credentials are kept out of URLs persisted by the app: the
 * synthetic playlist URL is encrypted by the database layer and never written to logs.
 */
class XtreamApi(private val httpClient: OkHttpClient) {

    data class Credentials(val host: String, val username: String, val password: String) {
        fun apiUrl(): HttpUrl {
            val base = NetworkPolicy.requireUrl(host).newBuilder().query(null).fragment(null).build()
            return if (base.encodedPath.endsWith("/player_api.php")) base
            else base.newBuilder().addPathSegment("player_api.php").build()
        }

        fun baseUrl(): String = apiUrl().toString()

        fun streamUrl(kind: String, id: String, container: String): String {
            require(kind in setOf("live", "movie", "series"))
            require(id.matches(Regex("[0-9]{1,20}"))) { "Invalid stream id" }
            require(container.matches(Regex("[a-zA-Z0-9]{1,10}"))) { "Invalid container" }
            val root = NetworkPolicy.requireUrl(host).newBuilder()
                .query(null)
                .fragment(null)
                .encodedPath("/")
                .build()
            return root.newBuilder()
                .addPathSegment(kind)
                .addPathSegment(username)
                .addPathSegment(password)
                .addPathSegment("$id.$container")
                .build()
                .toString()
        }

        override fun toString(): String = "[protected credentials]"
    }

    companion object {
        private const val maxJsonRecords = 100_000

        /** Synthetic, encrypted-at-rest playlist identity: xtream://user:pass@host */
        fun parseSyntheticUrl(url: String): Credentials? {
            if (!url.startsWith("xtream://")) return null
            val rest = url.removePrefix("xtream://")
            val at = rest.lastIndexOf('@')
            if (at <= 0 || at == rest.lastIndex) return null
            val userinfo = rest.substring(0, at)
            val host = rest.substring(at + 1)
            val colon = userinfo.indexOf(':')
            if (colon <= 0) return null
            val user = decode(userinfo.substring(0, colon))
            val pass = decode(userinfo.substring(colon + 1))
            if (host.isBlank() || user.isBlank()) return null
            if (NetworkPolicy.parse(host) == null) return null
            return Credentials(host, user, pass)
        }

        fun syntheticUrl(creds: Credentials): String =
            "xtream://" + encode(creds.username) + ":" + encode(creds.password) + "@" + creds.host

        private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
        private fun decode(value: String): String = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault("")
    }

    private fun client(): OkHttpClient = httpClient.newBuilder()
        // NetworkPolicy.redirectGuard validates every hop; automatic redirects must stay off.
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    private suspend fun fetchJson(creds: Credentials, action: String, extra: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val url = creds.apiUrl().newBuilder()
                .addQueryParameter("username", creds.username)
                .addQueryParameter("password", creds.password)
                .addQueryParameter("action", action)
                .apply { extra.forEach { (key, value) -> addQueryParameter(key, value) } }
                .build()
            client().newCall(Request.Builder().url(url).build()).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                response.readText()
            }
        }

    suspend fun fetchLiveChannels(creds: Credentials): List<RawChannel> {
        val categories = try {
            fetchCategories(creds)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLog.log("Xtream", error.message ?: "?")
            emptyMap()
        }
        val root = JSONArray(fetchJson(creds, "get_live_streams"))
        check(root.length() <= maxJsonRecords) { "Too many streams" }
        val result = ArrayList<RawChannel>(root.length())
        for (index in 0 until root.length()) {
            val item = root.optJSONObject(index) ?: continue
            val streamId = item.optString("stream_id").takeIf { it.matches(Regex("[0-9]{1,20}")) } ?: continue
            val name = item.optString("name").ifBlank { item.optString("stream_display_name") }
                .ifBlank { "Unknown" }.take(512)
            val container = item.optString("container_extension", "ts")
                .takeIf { it.matches(Regex("[a-zA-Z0-9]{1,10}")) } ?: "ts"
            val logo = item.optString("stream_icon").takeIf { NetworkPolicy.parse(it) != null }
            val group = categories[item.optString("category_id")]?.take(512)
            val url = runCatching { creds.streamUrl("live", streamId, container) }.getOrNull() ?: continue
            result += RawChannel(
                name = name,
                logo = logo,
                group = group,
                tvgId = item.optString("epg_channel_id").takeIf { it.isNotBlank() }?.take(256),
                url = url,
            )
        }
        return result
    }

    suspend fun fetchCategories(creds: Credentials): Map<String, String> {
        val root = JSONArray(fetchJson(creds, "get_live_categories"))
        check(root.length() <= maxJsonRecords)
        val result = LinkedHashMap<String, String>()
        for (index in 0 until root.length()) {
            val item = root.optJSONObject(index) ?: continue
            val id = item.optString("category_id")
            val name = item.optString("category_name")
            if (id.isNotBlank() && name.isNotBlank()) result[id] = name.take(512)
        }
        return result
    }

    suspend fun fetchSeriesInfo(creds: Credentials, seriesId: Int): JSONObject =
        JSONObject(fetchJson(creds, "get_series_info", mapOf("series_id" to seriesId.toString())))

    suspend fun fetchVodStreams(creds: Credentials): JSONArray =
        JSONArray(fetchJson(creds, "get_vod_streams"))

    suspend fun fetchSeries(creds: Credentials): JSONArray =
        JSONArray(fetchJson(creds, "get_series"))
}
