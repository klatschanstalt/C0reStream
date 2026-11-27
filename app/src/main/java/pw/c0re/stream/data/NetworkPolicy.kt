package pw.c0re.stream.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Central URL and transport policy.
 *
 * HTTP stays supported for user-provided IPTV sources (binding owner decision), but every
 * URL must be a well-formed HTTP(S) URL without embedded credentials. Redirects are allowed
 * only within the same transport class: an HTTPS request can never be redirected to HTTP.
 */
object NetworkPolicy {
    private const val maxUrlLength = 16_384
    private const val maxUserAgentLength = 512

    fun parse(value: String): HttpUrl? {
        if (value.length !in 8..maxUrlLength) return null
        if (value.any { it.isISOControl() || it == '\\' || it == ' ' }) return null
        val url = value.toHttpUrlOrNull() ?: return null
        if (url.scheme != "http" && url.scheme != "https") return null
        if (url.host.isBlank()) return null
        if (url.encodedUsername.isNotEmpty() || url.encodedPassword.isNotEmpty()) return null
        return url
    }

    fun requireUrl(value: String): HttpUrl = requireNotNull(parse(value)) { "Invalid network URL" }

    fun isHttps(value: String): Boolean = parse(value)?.isHttps == true

    /**
     * Catch-up sources are URL templates containing ${start}, {utc}, {duration} and similar
     * placeholders. They are validated structurally before substitution and the final URL is
     * validated again by [Catchup.build].
     */
    fun isTemplateUrl(value: String): Boolean {
        if (value.length !in 8..maxUrlLength) return false
        if (value.any { it.isISOControl() || it == '\\' || it == ' ' }) return false
        val lower = value.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        val authority = value.substringAfter("://").substringBefore("/")
        if (authority.isBlank() || authority.contains("@")) return false
        return true
    }

    fun userAgent(value: String?): String? = value
        ?.takeIf { it.isNotBlank() }
        ?.takeIf { it.length <= maxUserAgentLength && it.none { c -> c.isISOControl() } }

    fun proxy(host: String?, port: Int?): Proxy? {
        if (host.isNullOrBlank() || port == null || port !in 1..65535) return null
        if (host.length > 255 || host.any { it.isISOControl() || it == '/' || it == '@' }) return null
        return Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))
    }

    /** Rejects HTTPS-to-HTTP redirects even if a caller enables redirect following. */
    private val downgradeGuard = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        if (request.url.isHttps && !response.request.url.isHttps) {
            response.close()
            throw IOException("HTTPS downgrade blocked")
        }
        response
    }

    fun client(
        userAgent: String? = null,
        useProxy: Boolean = false,
        proxyHost: String? = null,
        proxyPort: Int? = null,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .followRedirects(true)
            // HTTP -> HTTPS upgrades are useful; the interceptor below blocks the reverse.
            .followSslRedirects(true)
            .addInterceptor(downgradeGuard)
        userAgent(userAgent)?.let { value ->
            builder.addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", value).build())
            }
        }
        if (useProxy) proxy(proxyHost, proxyPort)?.let(builder::proxy)
        return builder.build()
    }

    internal fun closeQuietly(response: Response) {
        runCatching { response.close() }
    }
}
