package pw.c0re.stream.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale

/**
 * Reads the signed-release manifest published with GitHub Releases. Only HTTPS metadata is
 * accepted. The APK itself is verified separately by [UpdateInstaller] before installation.
 */
object UpdateChecker {
    const val UPDATE_URL = "https://raw.githubusercontent.com/klatschanstalt/C0reStream/main/update.json"

    data class UpdateInfo(
        val latestVersion: String,
        val notes: String?,
        val apkUrl: String?,
        val sha256: String?,
    )

    suspend fun check(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val client = NetworkPolicy.client()
            val body = client.newCall(
                Request.Builder().url(NetworkPolicy.requireUrl(UPDATE_URL)).build(),
            ).execute().use { response -> response.readMetadata(64 * 1024) }
            val json = JSONObject(body)
            val latest = json.optString("latestVersion").trim()
            if (latest.isBlank() || !isNewer(latest, currentVersion)) return@withContext null
            val apk = json.optString("apkUrl").trim().takeIf { it.isNotBlank() }
                ?.takeIf { NetworkPolicy.isHttps(it) }
            val checksum = json.optString("sha256").trim().lowercase(Locale.ROOT)
                .takeIf { it.matches(Regex("[0-9a-f]{64}")) }
            UpdateInfo(
                latestVersion = latest.take(64),
                notes = localizedNotes(json).takeIf { it.isNotBlank() }?.take(8_192),
                apkUrl = apk,
                sha256 = checksum,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLog.log("Update", error.message ?: "?")
            null
        }
    }

    /**
     * Prefers release notes in the device language. The unlocalised `notes` field remains the
     * backwards-compatible fallback for older manifests.
     */
    fun localizedNotes(json: JSONObject): String {
        val language = Locale.getDefault().language.lowercase(Locale.ROOT)
        val localized = if (language in setOf("de", "en", "fr")) {
            json.optString("notes_$language").trim()
        } else {
            ""
        }
        return localized.ifBlank { json.optString("notes").trim() }
    }

    /** Compares dotted numeric versions; pre-release suffixes make a version older. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parse(candidate)
        val b = parse(current)
        if (a == null || b == null) return candidate != current
        for (index in 0 until maxOf(a.first.size, b.first.size)) {
            val left = a.first.getOrElse(index) { 0 }
            val right = b.first.getOrElse(index) { 0 }
            if (left != right) return left > right
        }
        return a.second.isEmpty() && b.second.isNotEmpty()
    }

    private fun parse(value: String): Pair<List<Int>, String>? {
        val match = Regex("^[vV]?([0-9]+(?:\\.[0-9]+){0,3})(?:-([0-9A-Za-z.-]+))?$")
            .matchEntire(value.trim()) ?: return null
        val numbers = match.groupValues[1].split('.').mapNotNull { it.toIntOrNull() }
        if (numbers.isEmpty()) return null
        return numbers to match.groupValues[2]
    }
}
