package pw.c0re.stream.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Update-Check gegen eine JSON-Datei im eigenen Repo:
 *   update.json: { "latestVersion": "1.2.0", "notes": "..." }
 * URL in deinem Repo anpassen.
 */
object UpdateChecker {

    const val UPDATE_URL = "https://raw.githubusercontent.com/klatschanstalt/C0reStream/main/update.json"

    data class UpdateInfo(val latestVersion: String, val notes: String?)

    suspend fun check(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val body = OkHttpClient.Builder().followSslRedirects(false).callTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build().newCall(Request.Builder().url(UPDATE_URL).build())
                .execute().use { it.readMetadata(65536) }
            val o = JSONObject(body)
            val latest = o.optString("latestVersion")
            UpdateInfo(latest, o.optString("notes").takeIf { it.isNotBlank() })
                .takeIf { latest.isNotBlank() && latest != currentVersion }
        }.getOrNull()
    }
}
