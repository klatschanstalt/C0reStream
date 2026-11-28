package pw.c0re.stream.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import pw.c0re.stream.data.M3UParser.RawChannel
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Explicit limits for Xtream endpoints. Paging is not part of the player_api contract. */
data class XtreamLimits(
    val maxJsonRecords: Int = 100_000,
    val maxResponseBytes: Long = 32L * 1024 * 1024,
    val maxPages: Int = 1,
    val paginationSupported: Boolean = false,
)

data class XtreamCategoryResult(
    val categories: Map<String, String>,
    val stats: ImportStats,
    val paginationDetected: Boolean,
    val notes: List<String>,
)

/** Xtream live import with explicit counters, notes and limit reporting. */
data class XtreamResult(
    val channels: List<RawChannel>,
    val stats: ImportStats,
    val categories: Map<String, String>,
    val limits: XtreamLimits,
    val paginationDetected: Boolean,
    val notes: List<String>,
)

/**
 * Xtream Codes API client. Credentials are kept out of URLs persisted by the app: the
 * synthetic playlist URL is encrypted by the database layer and never written to logs.
 */
class XtreamApi(
    private val httpClient: OkHttpClient,
    private val limits: XtreamLimits = XtreamLimits(),
) {

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

    // Callers surface exception messages in the UI and AppLog. Transport/JSON failures can
    // embed the request URL or a provider body echoing credentials, so scrub them at the source.
    private val urlInMessage = Regex("""(?:https?|xtream)://\S+""", RegexOption.IGNORE_CASE)
    private val userInfoInMessage = Regex("""[A-Za-z0-9._%+-]+:[^@/\s]+@[A-Za-z0-9.-]+""")
    private val credentialInMessage = Regex(
        """(?i)(username|user_name|login|password|passwd|pwd|token|secret|api_key|apikey|authorization|auth|signature|sig|jwt)=([^&\s]+)""",
    )

    private fun redact(message: String?): String =
        message?.takeIf { it.isNotBlank() }
            ?.replace(urlInMessage, "[url]")
            ?.replace(userInfoInMessage, "[redacted]")
            ?.replace(credentialInMessage, "\$1=[redacted]")
            ?: ""

    private fun safeFailure(error: Exception): Exception {
        if (error is CancellationException) return error
        return IOException(redact(error.message).ifBlank { error.javaClass.simpleName })
            .apply { initCause(error) }
    }

    private fun parseJsonObject(value: String): JSONObject = try {
        JSONObject(value)
    } catch (error: Exception) {
        throw safeFailure(error)
    }

    private fun parseJsonArrayRoot(value: String): JSONArray = try {
        JSONArray(value)
    } catch (error: Exception) {
        throw safeFailure(error)
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
            try {
                client().newCall(Request.Builder().url(url).build()).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    response.readText(limits.maxResponseBytes)
                }
            } catch (error: Exception) {
                throw safeFailure(error)
            }
        }

    /** Legacy entry point; use [fetchLiveChannelsWithReport] for stats and limit reporting. */
    suspend fun fetchLiveChannels(creds: Credentials): List<RawChannel> =
        fetchLiveChannelsWithReport(creds).channels

    suspend fun fetchLiveChannelsWithReport(creds: Credentials): XtreamResult {
        val categoryResult = try {
            fetchCategoriesWithReport(creds)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val rejection = ImportRejection(
                ImportRejectionCode.XTREAM_CATEGORIES_UNAVAILABLE.code,
                0,
                error.javaClass.simpleName,
            )
            XtreamCategoryResult(
                categories = emptyMap(),
                stats = ImportStats().withRejection(rejection),
                paginationDetected = false,
                notes = listOf("Live categories unavailable; channels kept without group labels."),
            )
        }

        val payload = parseJsonArray(fetchJson(creds, "get_live_streams"))
        val notes = ArrayList<String>()
        var stats = ImportStats(received = payload.array.length(), limits = ImportLimits())
        if (payload.array.length() > limits.maxJsonRecords) {
            stats = stats.withRejection(
                ImportRejection(
                    ImportRejectionCode.XTREAM_RECORD_LIMIT.code,
                    0,
                    "limit=${limits.maxJsonRecords}",
                ),
            ).copy(truncated = true)
            notes += "Only the first ${limits.maxJsonRecords} of ${payload.array.length()} streams were imported."
        }
        if (payload.paginationDetected) {
            stats = stats.withRejection(
                ImportRejection(ImportRejectionCode.XTREAM_PAGINATION_UNSUPPORTED.code, 0, "first page only"),
                incrementRejected = false,
            )
            notes += "Provider response advertises paging; player_api.php paging is not supported by this client."
        }
        val count = minOf(payload.array.length(), limits.maxJsonRecords)
        val result = ArrayList<RawChannel>(count)
        for (index in 0 until count) {
            val item = payload.array.optJSONObject(index)
            if (item == null) {
                stats = stats.withRejection(ImportRejection(ImportRejectionCode.XTREAM_INVALID_RECORD.code, index))
                continue
            }
            val streamId = item.optString("stream_id").takeIf { it.matches(Regex("[0-9]{1,20}")) }
            if (streamId == null) {
                stats = stats.withRejection(ImportRejection(ImportRejectionCode.XTREAM_INVALID_RECORD.code, index))
                continue
            }
            val name = (item.text("name") ?: "").ifBlank { item.text("stream_display_name") ?: "" }
                .ifBlank { "Unknown" }.take(512)
            val container = item.text("container_extension")?.takeIf { it.matches(Regex("[a-zA-Z0-9]{1,10}")) }
                ?: "ts"
            val logo = item.text("stream_icon")?.takeIf { NetworkPolicy.parse(it) != null }
            val group = item.text("category_id")?.let { categoryResult.categories[it] }
                ?: item.text("category_name")?.take(512)
            val url = runCatching { creds.streamUrl("live", streamId, container) }.getOrNull()
            if (url == null) {
                stats = stats.withRejection(ImportRejection(ImportRejectionCode.XTREAM_INVALID_RECORD.code, index))
                continue
            }
            result += RawChannel(
                name = name,
                logo = logo,
                group = group,
                tvgId = item.text("epg_channel_id")?.take(256),
                url = url,
                tvgName = item.text("tvg_name")?.take(256),
                channelNumber = firstText(item, "channel_num", "channel_number", "chno", "num", "number")
                    ?.takeIf { it.matches(Regex("[0-9]{1,6}(?:\\.[0-9]{1,3})?")) }?.take(32),
                quality = firstText(item, "quality", "resolution", "height")?.take(32),
            )
        }
        stats = stats.copy(
            accepted = result.size,
            rejected = stats.rejected + categoryResult.stats.rejected,
            rejections = (stats.rejections + categoryResult.stats.rejections)
                .take(stats.limits.maxRejectionDetails),
            truncated = stats.truncated || categoryResult.stats.truncated,
        )
        notes.addAll(categoryResult.notes)
        return XtreamResult(
            channels = result,
            stats = stats,
            categories = categoryResult.categories,
            limits = limits,
            paginationDetected = payload.paginationDetected || categoryResult.paginationDetected,
            notes = notes,
        )
    }

    suspend fun fetchCategories(creds: Credentials): Map<String, String> =
        fetchCategoriesWithReport(creds).categories

    suspend fun fetchCategoriesWithReport(creds: Credentials): XtreamCategoryResult {
        val payload = parseJsonArray(fetchJson(creds, "get_live_categories"))
        var stats = ImportStats(received = payload.array.length(), limits = ImportLimits())
        val notes = ArrayList<String>()
        if (payload.array.length() > limits.maxJsonRecords) {
            stats = stats.withRejection(
                ImportRejection(
                    ImportRejectionCode.XTREAM_RECORD_LIMIT.code,
                    0,
                    "limit=${limits.maxJsonRecords}",
                ),
            ).copy(truncated = true)
            notes += "Only the first ${limits.maxJsonRecords} of ${payload.array.length()} categories were read."
        }
        if (payload.paginationDetected) {
            stats = stats.withRejection(
                ImportRejection(ImportRejectionCode.XTREAM_PAGINATION_UNSUPPORTED.code, 0, "first page only"),
                incrementRejected = false,
            )
            notes += "Category response advertises paging; only the first page was read."
        }
        val result = LinkedHashMap<String, String>()
        val count = minOf(payload.array.length(), limits.maxJsonRecords)
        for (index in 0 until count) {
            val item = payload.array.optJSONObject(index) ?: continue
            val id = item.text("category_id")
            val name = item.text("category_name")
            if (id != null && name != null && id.isNotBlank() && name.isNotBlank()) {
                result[id] = name.take(512)
            }
        }
        return XtreamCategoryResult(result, stats.copy(accepted = result.size), payload.paginationDetected, notes)
    }

    suspend fun fetchSeriesInfo(creds: Credentials, seriesId: Int): JSONObject =
        parseJsonObject(fetchJson(creds, "get_series_info", mapOf("series_id" to seriesId.toString())))

    suspend fun fetchVodStreams(creds: Credentials): JSONArray =
        parseJsonArrayRoot(fetchJson(creds, "get_vod_streams"))

    suspend fun fetchSeries(creds: Credentials): JSONArray =
        parseJsonArrayRoot(fetchJson(creds, "get_series"))

    private data class JsonPayload(val array: JSONArray, val paginationDetected: Boolean)

    /** Some providers wrap arrays in an object with paging hints; never pretend that is complete. */
    private fun parseJsonArray(json: String): JsonPayload {
        val trimmed = json.trim()
        if (trimmed.startsWith("[")) return JsonPayload(parseJsonArrayRoot(trimmed), false)
        val obj = parseJsonObject(trimmed)
        val array = obj.optJSONArray("data") ?: JSONArray()
        val paged = obj.has("page") || obj.has("per_page") || obj.has("total") ||
            obj.has("total_count") || obj.has("has_more") || obj.has("next_page") ||
            obj.has("next")
        return JsonPayload(array, paged)
    }

    private fun JSONObject.text(key: String): String? = optString(key, "")
        .takeIf { it.isNotBlank() && it != "null" }

    private fun firstText(item: JSONObject, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> item.text(key) }
}
