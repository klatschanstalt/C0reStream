package pw.c0re.stream.data

import java.security.MessageDigest
import java.util.Locale

/** Stable channel identity and deduplication helpers. */
object ChannelKey {
    private const val maxKeyLength = 48

    fun normalizeUrl(raw: String): String? {
        val url = NetworkPolicy.parse(raw) ?: return null
        val scheme = url.scheme.lowercase(Locale.ROOT)
        val defaultPort = (scheme == "http" && url.port == 80) || (scheme == "https" && url.port == 443)
        val port = if (defaultPort) "" else ":${url.port}"
        val path = url.encodedPath.trimEnd('/').ifEmpty { "/" }
        val query = url.encodedQuery?.let { "?$it" }.orEmpty()
        return "$scheme://${url.host.lowercase(Locale.ROOT)}$port$path$query"
    }

    /**
     * The key survives display-name changes and URL token refreshes when a tvg-id is present.
     * Without a tvg-id the normalised stream URL is the identity, so renamed channels keep
     * their favourites and history. It is scoped per playlist by the caller.
     */
    fun stableKey(url: String, tvgId: String?): String {
        val id = tvgId?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
        val base = id?.let { "id:$it" } ?: "url:${normalizeUrl(url) ?: url}"
        return sha256(base).take(maxKeyLength)
    }

    fun deduplicateByKey(url: String, tvgId: String?): String = stableKey(url, tvgId)

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
