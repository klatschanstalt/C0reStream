package tv.ham3l.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.ham3l.app.data.M3UParser.RawChannel
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Xtream-Codes-API-Anbindung (player_api.php), wie iptvnator sie unterstuetzt.
 * Liest live_streams und baut daraus RawChannel-Objekte, die in den
 * normalen M3U-Speicherpfad laufen. Nutzt org.json (Android-Bordmittel).
 */
class XtreamApi(private val httpClient: OkHttpClient) {

    data class Credentials(val host: String, val username: String, val password: String) {
        fun baseUrl(): String = host.removeSuffix("/") + "/player_api.php"
    }

    companion object {
        /**
         * Synthetische Playlist-URL fuer Xtream-Server (ohne Schema-Migration):
         * "xtream://<user>:<pass>@<host>"
         */
        fun parseSyntheticUrl(url: String): Credentials? {
            if (!url.startsWith("xtream://")) return null
            val rest = url.removePrefix("xtream://")
            val userinfo = rest.substringBefore("@")
            val host = rest.substringAfter("@")
            val user = userinfo.substringBefore(":")
            val pass = userinfo.substringAfter(":", missingDelimiterValue = "")
            if (host.isBlank() || user.isBlank()) return null
            return Credentials(host, user, pass)
        }

        fun syntheticUrl(creds: Credentials): String =
            "xtream://" + creds.username + ":" + creds.password + "@" + creds.host
    }

    suspend fun fetchLiveChannels(creds: Credentials): List<RawChannel> =
        withContext(Dispatchers.IO) {
            val url = creds.baseUrl() + "?username=" + java.net.URLEncoder.encode(creds.username, "UTF-8") +
                "&password=" + java.net.URLEncoder.encode(creds.password, "UTF-8") + "&action=get_live_streams"
            val body = httpClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                check(resp.isSuccessful) { "HTTP " + resp.code }
                resp.readMetadata()
            }
            val root = JSONArray(body)
            (0 until root.length()).map { i ->
                val o: JSONObject = root.getJSONObject(i)
                val streamId = o.optString("stream_id")
                val container = o.optString("container_extension", "ts")
                RawChannel(
                    name = o.optString("name", "Unbekannt"),
                    logo = o.optString("stream_icon").takeIf { it.isNotBlank() },
                    group = null,
                    tvgId = o.optString("epg_channel_id").takeIf { it.isNotBlank() },
                    url = creds.host.removeSuffix("/") + "/live/" + creds.username + "/" + creds.password + "/" + streamId + "." + container,
                )
            }
        }
}
