package pw.c0re.stream.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object NetworkPolicy {
    fun parse(value: String): HttpUrl? {
        if (value.length > 16384 || value.any { it.isISOControl() || it == '\\' }) return null
        val url = value.toHttpUrlOrNull() ?: return null
        if (url.encodedUsername.isNotEmpty() || url.encodedPassword.isNotEmpty()) return null
        return url
    }
    fun requireUrl(value: String): HttpUrl = requireNotNull(parse(value)) { "Invalid network URL" }
}
