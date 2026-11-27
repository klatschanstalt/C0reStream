package pw.c0re.stream.data

import okhttp3.HttpUrl
import java.security.MessageDigest
import java.util.Locale

/** Stable channel identity, variant separation and legacy-row matching helpers. */
object ChannelKey {
    const val KEY_LENGTH = 48

    /**
     * Query parameters that change per playback/session. They are ignored when deriving a
     * variant identity so a refreshed token does not look like a new stream variant.
     */
    private val volatileQueryNames = setOf(
        "token", "tokens", "key", "sig", "signature", "hash", "auth", "authorization",
        "session", "sid", "t", "ts", "time", "exp", "expires", "expire", "stamp", "nonce",
        "uuid", "jwt", "access_token", "tmp", "rnd", "rand", "cachebust", "cb", "v",
    )
    private val whitespace = Regex("\\s+")

    fun normalizeUrl(raw: String): String? {
        val url = NetworkPolicy.parse(raw)
        if (url != null) {
            val scheme = url.scheme.lowercase(Locale.ROOT)
            val defaultPort = (scheme == "http" && url.port == 80) || (scheme == "https" && url.port == 443)
            val port = if (defaultPort) "" else ":${url.port}"
            val path = url.encodedPath.trimEnd('/').ifEmpty { "/" }
            val query = url.encodedQuery?.let { "?$it" }.orEmpty()
            return "$scheme://${url.host.lowercase(Locale.ROOT)}$port$path$query"
        }
        // RTSP/UDP playback sources have no HttpUrl representation; keep their canonical form.
        return NetworkPolicy.parseStreamUrl(raw)?.takeUnless { it.isHttp }?.canonical
    }

    /**
     * Primary deterministic key (48 lowercase hex characters).
     *
     * - With a tvg-id the id is the identity; an optional [variantKey] keeps HD/SD or otherwise
     *   distinct streams sharing that id apart. Display-name renames never change the key.
     * - Without a tvg-id the normalised URL is combined with a stable identity built from
     *   tvg-name (preferred), name, group and channel number, so two different channels sharing
     *   one URL stay distinct.
     *
     * Calling with only (url, tvgId) yields the historical key shape, which keeps backups and
     * existing rows matchable; use [legacyKey] when matching rows written by older versions.
     */
    fun stableKey(
        url: String,
        tvgId: String?,
        tvgName: String? = null,
        name: String? = null,
        group: String? = null,
        channelNumber: String? = null,
        variantKey: String? = null,
    ): String {
        val id = canonical(tvgId)
        val normalized = canonicalVariantUrl(url) ?: normalizeUrl(url) ?: "raw:${canonical(url, 512) ?: ""}"
        val base = if (id != null) {
            buildString {
                append("id:").append(id)
                if (!variantKey.isNullOrBlank()) append("|v:").append(variantKey)
            }
        } else {
            val identity = identity(tvgName, name, group, channelNumber)
            buildString {
                append("url:").append(normalized)
                if (identity != null) append("|i:").append(identity)
            }
        }
        return sha256(base).take(KEY_LENGTH)
    }

    /**
     * Stable discriminator for variants of the same tvg-id. Volatile query values are removed;
     * a non-volatile quality parameter (e.g. `?quality=hd`) still separates variants.
     */
    fun variantKey(url: String, channelNumber: String? = null, quality: String? = null): String? {
        val canonicalUrl = canonicalVariantUrl(url) ?: return null
        val parts = ArrayList<String>()
        parts += "u:$canonicalUrl"
        canonical(channelNumber, 32)?.let { parts += "c:$it" }
        canonical(quality, 32)?.let { parts += "q:$it" }
        return sha256(parts.joinToString("|")).take(16)
    }

    /** Exact key formula used before variant/identity support; for matching old rows only. */
    fun legacyKey(url: String, tvgId: String?): String {
        val id = tvgId?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
        val base = id?.let { "id:$it" } ?: "url:${normalizeUrl(url) ?: url}"
        return sha256(base).take(KEY_LENGTH)
    }

    /** True when [candidate] is the legacy key of the channel described by the arguments. */
    fun isLegacyKey(candidate: String?, url: String, tvgId: String?): Boolean =
        !candidate.isNullOrBlank() && candidate == legacyKey(url, tvgId)

    /**
     * Resolves a deterministic key collision between two distinct variants without merging them.
     * Output is still 48 lowercase hex characters.
     */
    fun disambiguate(key: String, discriminator: String): String =
        sha256("$key|dup:${canonical(discriminator, 512) ?: ""}").take(KEY_LENGTH)

    private fun canonical(value: String?, limit: Int = 256): String? =
        value?.trim()?.lowercase(Locale.ROOT)?.replace(whitespace, " ")?.take(limit)
            ?.takeIf { it.isNotBlank() }

    private fun identity(tvgName: String?, name: String?, group: String?, channelNumber: String?): String? {
        val stableName = canonical(tvgName) ?: canonical(name) ?: return null
        val parts = ArrayList<String>()
        parts += "n:$stableName"
        canonical(group)?.let { parts += "g:$it" }
        canonical(channelNumber, 32)?.let { parts += "c:$it" }
        return parts.joinToString("|")
    }

    private fun canonicalVariantUrl(raw: String): String? {
        val url: HttpUrl = NetworkPolicy.parse(raw) ?: return canonicalStreamVariantUrl(raw)
        val result = url.newBuilder().query(null).fragment(null)
        val kept = url.queryParameterNames
            .filter { name -> name.lowercase(Locale.ROOT) !in volatileQueryNames }
            .sorted()
        for (name in kept) {
            for (value in url.queryParameterValues(name)) result.addQueryParameter(name, value)
        }
        return result.build().toString()
    }

    /** Query-token stripping for rtsp://, rtspu:// and udp:// variants. */
    private fun canonicalStreamVariantUrl(raw: String): String? {
        val stream = NetworkPolicy.parseStreamUrl(raw)?.takeUnless { it.isHttp } ?: return null
        val base = stream.canonical.substringBefore('?')
        val query = stream.query ?: return base
        val kept = query.split('&')
            .filter { part ->
                part.isNotEmpty() &&
                    part.substringBefore('=').lowercase(Locale.ROOT) !in volatileQueryNames
            }
            .sortedBy { it.substringBefore('=').lowercase(Locale.ROOT) }
        return if (kept.isEmpty()) base else "$base?${kept.joinToString("&")}"
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
