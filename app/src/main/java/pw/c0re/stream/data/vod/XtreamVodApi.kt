package pw.c0re.stream.data.vod

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import pw.c0re.stream.data.XtreamApi
import pw.c0re.stream.data.readMetadata

/**
 * VOD-Filme und Serien ueber die Xtream-Codes-API.
 */
class XtreamVodApi(private val httpClient: OkHttpClient) {

    data class VodItem(val id: Int, val name: String, val poster: String?, val rating: String?)
    data class Episode(val id: Int, val season: Int, val episodeNum: Int, val title: String, val container: String)

    suspend fun movies(creds: XtreamApi.Credentials): List<VodItem> = fetchItems(creds, "get_vod_streams")
    suspend fun series(creds: XtreamApi.Credentials): List<VodItem> = fetchItems(creds, "get_series")

    private suspend fun fetchItems(creds: XtreamApi.Credentials, action: String): List<VodItem> =
        withContext(Dispatchers.IO) {
            val url = creds.baseUrl() + "?username=" + java.net.URLEncoder.encode(creds.username, "UTF-8") +
                "&password=" + java.net.URLEncoder.encode(creds.password, "UTF-8") + "&action=" + action
            val body = httpClient.newCall(Request.Builder().url(url).build()).execute().use { it.readMetadata() }
            val root = JSONArray(body)
            (0 until root.length()).map { i ->
                val o: JSONObject = root.getJSONObject(i)
                VodItem(
                    id = o.optInt(if (action == "get_series") "series_id" else "stream_id"),
                    name = o.optString("name", "Unbekannt"),
                    poster = o.optString("stream_icon").takeIf { it.isNotBlank() } ?: o.optString("cover").takeIf { it.isNotBlank() },
                    rating = o.optString("rating").takeIf { it.isNotBlank() && it != "0" },
                )
            }.sortedBy { it.name.lowercase() }
        }

    suspend fun seriesInfo(creds: XtreamApi.Credentials, seriesId: Int): Map<Int, List<Episode>> =
        withContext(Dispatchers.IO) {
            val url = creds.baseUrl() + "?username=" + java.net.URLEncoder.encode(creds.username, "UTF-8") +
                "&password=" + java.net.URLEncoder.encode(creds.password, "UTF-8") + "&action=get_series_info&series_id=" + seriesId
            val body = httpClient.newCall(Request.Builder().url(url).build()).execute().use { it.readMetadata() }
            val root = JSONObject(body).optJSONObject("episodes") ?: return@withContext emptyMap()
            val result = mutableMapOf<Int, List<Episode>>()
            root.keys().forEach { seasonKey ->
                val arr = root.optJSONArray(seasonKey) ?: return@forEach
                val eps = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Episode(
                        id = o.optInt("id"),
                        season = seasonKey.toIntOrNull() ?: 1,
                        episodeNum = o.optInt("episode_num"),
                        title = o.optString("title", "Episode"),
                        container = o.optString("container_extension", "mp4"),
                    )
                }
                result[seasonKey.toIntOrNull() ?: 1] = eps.sortedBy { it.episodeNum }
            }
            result.toSortedMap()
        }

    fun movieUrl(creds: XtreamApi.Credentials, id: Int, container: String = "mp4"): String =
        creds.host.removeSuffix("/") + "/movie/" + creds.username + "/" + creds.password + "/" + id + "." + container

    fun episodeUrl(creds: XtreamApi.Credentials, id: Int, container: String): String =
        creds.host.removeSuffix("/") + "/series/" + creds.username + "/" + creds.password + "/" + id + "." + container
}
