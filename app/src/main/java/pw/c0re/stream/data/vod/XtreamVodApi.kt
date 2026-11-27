package pw.c0re.stream.data.vod

import org.json.JSONArray
import org.json.JSONObject
import pw.c0re.stream.data.NetworkPolicy
import pw.c0re.stream.data.XtreamApi

/** VOD movies and series over the Xtream Codes API. */
class XtreamVodApi(private val api: XtreamApi) {

    data class VodItem(
        val id: Int,
        val name: String,
        val poster: String?,
        val rating: String?,
        val container: String = "mp4",
    )

    data class Episode(
        val id: Int,
        val season: Int,
        val episodeNum: Int,
        val title: String,
        val container: String,
    )

    suspend fun movies(creds: XtreamApi.Credentials): List<VodItem> =
        parseItems(api.fetchVodStreams(creds), "stream_id", withContainer = true)

    suspend fun series(creds: XtreamApi.Credentials): List<VodItem> =
        parseItems(api.fetchSeries(creds), "series_id", withContainer = false)

    private fun parseItems(root: JSONArray, idField: String, withContainer: Boolean): List<VodItem> {
        check(root.length() <= 100_000) { "Too many VOD records" }
        val result = ArrayList<VodItem>(root.length())
        for (index in 0 until root.length()) {
            val item = root.optJSONObject(index) ?: continue
            val id = item.optInt(idField)
            if (id <= 0) continue
            val container = if (withContainer) {
                item.optString("container_extension", "mp4")
                    .takeIf { it.matches(Regex("[a-zA-Z0-9]{1,10}")) } ?: "mp4"
            } else {
                "mp4"
            }
            result += VodItem(
                id = id,
                name = item.optString("name").ifBlank { "Unknown" }.take(512),
                poster = (item.optString("stream_icon").takeIf { it.isNotBlank() }
                    ?: item.optString("cover").takeIf { it.isNotBlank() })
                    ?.takeIf { NetworkPolicy.parse(it) != null },
                rating = item.optString("rating").takeIf { it.isNotBlank() && it != "0" }?.take(32),
                container = container,
            )
        }
        return result.sortedBy { it.name.lowercase() }
    }

    suspend fun seriesInfo(creds: XtreamApi.Credentials, seriesId: Int): Map<Int, List<Episode>> {
        val root = api.fetchSeriesInfo(creds, seriesId)
        val episodes = root.optJSONObject("episodes") ?: return emptyMap()
        val result = sortedMapOf<Int, List<Episode>>()
        val keys = episodes.keys()
        var total = 0
        while (keys.hasNext()) {
            val seasonKey = keys.next()
            val array = episodes.optJSONArray(seasonKey) ?: continue
            check(array.length() <= 10_000) { "Too many episodes" }
            total += array.length()
            check(total <= 100_000) { "Too many episodes" }
            val season = seasonKey.toIntOrNull() ?: 1
            val parsed = ArrayList<Episode>(array.length())
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optInt("id")
                if (id <= 0) continue
                parsed += Episode(
                    id = id,
                    season = season,
                    episodeNum = item.optInt("episode_num"),
                    title = item.optString("title").ifBlank { "Episode" }.take(512),
                    container = item.optString("container_extension", "mp4")
                        .takeIf { it.matches(Regex("[a-zA-Z0-9]{1,10}")) } ?: "mp4",
                )
            }
            result[season] = parsed.sortedBy { it.episodeNum }
        }
        return result
    }

    fun movieUrl(creds: XtreamApi.Credentials, id: Int, container: String = "mp4"): String =
        creds.streamUrl("movie", id.toString(), container)

    fun episodeUrl(creds: XtreamApi.Credentials, id: Int, container: String): String =
        creds.streamUrl("series", id.toString(), container)

    fun mediaKey(kind: String, playlistId: Long, id: Int): String = "$kind:$playlistId:$id"
}
