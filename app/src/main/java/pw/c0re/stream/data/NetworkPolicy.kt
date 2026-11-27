package pw.c0re.stream.data

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Central URL and transport policy.
 *
 * HTTP stays supported for user-provided IPTV sources (binding owner decision), but every
 * URL must be a well-formed HTTP(S) URL without embedded credentials. Redirects are allowed
 * only within the same transport class: an HTTPS request can never be redirected to HTTP.
 *
 * Two classes of client profiles exist:
 *  - bounded request/response profiles ([apiClient], [importClient], [epgClient],
 *    [downloadClient] and the compatibility [client]) keep an absolute 90 s call deadline;
 *  - continuous stream profiles ([playbackClient], [recordingClient]) deliberately disable
 *    the absolute call deadline so a long-lived live stream is never cut off after 90 s.
 *    They still have connect (20 s) and read (30 s) timeouts, so stalled hops remain bounded.
 *
 * All profiles share one connection pool and dispatcher. Clients are cached per profile and
 * effective source settings, so repeated calls do not allocate a fresh OkHttpClient each time.
 */
object NetworkPolicy {
    private const val maxUrlLength = 16_384
    private const val maxUserAgentLength = 512
    private const val boundedCallTimeoutMs = 90_000L
    private const val streamConnectTimeoutMs = 20_000L
    private const val streamReadTimeoutMs = 30_000L
    private const val maxRedirects = 5
    private const val maxCachedClients = 24

    /** Shared across every profile so switching profile never strands idle connections. */
    internal val sharedConnectionPool = ConnectionPool(5, 5, TimeUnit.MINUTES)
    internal val sharedDispatcher = Dispatcher()

    private enum class Profile {
        API, IMPORT, EPG, DOWNLOAD, PLAYBACK, RECORDING,
    }

    /** Access-ordered, bounded cache; evicted clients are simply dropped, the pool is shared. */
    private val clients = object : LinkedHashMap<String, OkHttpClient>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, OkHttpClient>?): Boolean =
            size > maxCachedClients
    }
    private val clientLock = Any()

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

    private fun sameOrigin(a: HttpUrl, b: HttpUrl): Boolean =
        a.scheme == b.scheme && a.host == b.host && a.port == b.port

    private val sensitiveHeaderNames = setOf(
        "authorization", "proxy-authorization", "proxy-authenticate", "cookie", "set-cookie",
        "x-api-key", "api-key", "x-auth-token", "x-access-token", "x-csrf-token",
        "x-secret", "x-session-token", "x-amz-security-token",
    )

    /** Credential-like headers, including custom "X-..." ones, must never cross an origin. */
    private fun isSensitiveHeader(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        if (lower in sensitiveHeaderNames) return true
        return listOf("authorization", "token", "secret", "cookie", "credential", "api-key", "apikey", "signature")
            .any { lower.contains(it) }
    }

    private val sensitiveQueryNames = setOf(
        "token", "access_token", "accesstoken", "auth", "authorization", "password", "passwd",
        "pwd", "secret", "signature", "sig", "api_key", "apikey", "credential", "session",
    )

    private fun hasSensitiveQuery(url: HttpUrl): Boolean =
        url.queryParameterNames.any { it.lowercase(Locale.ROOT) in sensitiveQueryNames }

    /**
     * Resolves one redirect hop. The original transport class is enforced on every hop, so an
     * HTTPS -> HTTP -> HTTPS chain cannot replay credentials over plaintext in the middle.
     * A hop that changes scheme/host/port while carrying credentials (headers or URL query)
     * is rejected; callers must close the current response in that case.
     */
    internal fun redirectTarget(current: HttpUrl, location: String, originalHttps: Boolean): HttpUrl {
        val next = current.resolve(location) ?: throw IOException("Invalid redirect URL")
        parse(next.toString()) ?: throw IOException("Invalid redirect URL")
        if (originalHttps && !next.isHttps) throw IOException("HTTPS downgrade blocked")
        if (!sameOrigin(current, next) && (hasSensitiveQuery(current) || hasSensitiveQuery(next))) {
            throw IOException("Credential-bearing cross-origin redirect blocked")
        }
        return next
    }

    /**
     * OkHttp's automatic redirect handling runs below application interceptors, so it cannot
     * see intermediate hops. Follow redirects manually to enforce the policy per hop. Exposed
     * internally so JVM tests can exercise cleanup of invalid targets deterministically.
     */
    internal val redirectGuardInterceptor: Interceptor = Interceptor { chain ->
        var request = chain.request()
        val originalHttps = request.url.isHttps
        var redirects = 0
        var finalResponse: Response? = null
        while (finalResponse == null) {
            val response = chain.proceed(request)
            val location = if (response.isRedirect) response.header("Location") else null
            if (location == null) {
                finalResponse = response
            } else {
                try {
                    val next = redirectTarget(request.url, location, originalHttps)
                    if (!sameOrigin(request.url, next) && request.headers.names().any(::isSensitiveHeader)) {
                        throw IOException("Credential-bearing cross-origin redirect blocked")
                    }
                    if (++redirects > maxRedirects) throw IOException("Too many redirects")
                    request = request.newBuilder().url(next).build()
                } finally {
                    // Every abandoned hop releases its connection, including invalid targets.
                    closeQuietly(response)
                }
            }
        }
        finalResponse
    }

    private fun profileClient(
        profile: Profile,
        userAgent: String?,
        useProxy: Boolean,
        proxyHost: String?,
        proxyPort: Int?,
    ): OkHttpClient {
        val effectiveUserAgent = userAgent(userAgent)
        val effectiveProxy = if (useProxy) proxy(proxyHost, proxyPort) else null
        val proxyAddress = (effectiveProxy?.address() as? InetSocketAddress)
            ?.let { "${it.hostString}:${it.port}" }
        val key = "${profile.name}|${effectiveUserAgent.orEmpty()}|${proxyAddress.orEmpty()}"
        synchronized(clientLock) {
            clients[key]?.let { return it }
            val builder = OkHttpClient.Builder()
                .connectionPool(sharedConnectionPool)
                .dispatcher(sharedDispatcher)
                .connectTimeout(streamConnectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(streamReadTimeoutMs, TimeUnit.MILLISECONDS)
                // Redirects are resolved by redirectGuardInterceptor so every hop is validated.
                .followRedirects(false)
                .followSslRedirects(false)
                .addInterceptor(redirectGuardInterceptor)
            // Streaming profiles disable the absolute deadline; bounded profiles keep it.
            builder.callTimeout(
                if (profile == Profile.PLAYBACK || profile == Profile.RECORDING) {
                    0L
                } else {
                    boundedCallTimeoutMs
                },
                TimeUnit.MILLISECONDS,
            )
            effectiveUserAgent?.let { value ->
                builder.addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("User-Agent", value).build())
                }
            }
            effectiveProxy?.let(builder::proxy)
            val client = builder.build()
            clients[key] = client
            return client
        }
    }

    /** General/API profile. Kept for compatibility; identical to [apiClient]. */
    fun client(
        userAgent: String? = null,
        useProxy: Boolean = false,
        proxyHost: String? = null,
        proxyPort: Int? = null,
    ): OkHttpClient = apiClient(userAgent, useProxy, proxyHost, proxyPort)

    fun apiClient(
        userAgent: String? = null,
        useProxy: Boolean = false,
        proxyHost: String? = null,
        proxyPort: Int? = null,
    ): OkHttpClient = profileClient(Profile.API, userAgent, useProxy, proxyHost, proxyPort)

    fun importClient(
        userAgent: String? = null,
        useProxy: Boolean = false,
        proxyHost: String? = null,
        proxyPort: Int? = null,
    ): OkHttpClient = profileClient(Profile.IMPORT, userAgent, useProxy, proxyHost, proxyPort)

    fun epgClient(
        userAgent: String? = null,
        useProxy: Boolean = false,
        proxyHost: String? = null,
        proxyPort: Int? = null,
    ): OkHttpClient = profileClient(Profile.EPG, userAgent, useProxy, proxyHost, proxyPort)

    /** Continuous live/VOD HTTP data source. No absolute call timeout. */
    fun playbackClient(
        userAgent: String? = null,
        useProxy: Boolean = false,
        proxyHost: String? = null,
        proxyPort: Int? = null,
    ): OkHttpClient = profileClient(Profile.PLAYBACK, userAgent, useProxy, proxyHost, proxyPort)

    /** Continuous local recording of a plain stream. No absolute call timeout. */
    fun recordingClient(
        userAgent: String? = null,
        useProxy: Boolean = false,
        proxyHost: String? = null,
        proxyPort: Int? = null,
    ): OkHttpClient = profileClient(Profile.RECORDING, userAgent, useProxy, proxyHost, proxyPort)

    /** Update/package downloads. Bounded call timeout. */
    fun downloadClient(
        userAgent: String? = null,
        useProxy: Boolean = false,
        proxyHost: String? = null,
        proxyPort: Int? = null,
    ): OkHttpClient = profileClient(Profile.DOWNLOAD, userAgent, useProxy, proxyHost, proxyPort)

    internal fun closeQuietly(response: Response) {
        runCatching { response.close() }
    }
}
