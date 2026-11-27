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
 * [parse] is deliberately HTTP-only and remains the gate for API/import/EPG/download calls.
 * Playback sources are validated separately by [parseStreamUrl], which additionally accepts
 * rtsp://, rtspu:// and udp:// URLs; it never weakens the HTTP redirect guard.
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
     * A validated playback source. HTTP(S) entries are parsed with [parse], so all existing HTTP
     * semantics (including host canonicalisation) are preserved. RTSP/RTP/UDP entries are parsed
     * structurally because OkHttp's URL parser only understands HTTP(S).
     */
    data class StreamUrl(
        val raw: String,
        val scheme: String,
        val host: String?,
        val port: Int?,
        val path: String?,
        val query: String?,
    ) {
        val isHttp: Boolean get() = scheme == "http" || scheme == "https"
        val isRtsp: Boolean get() = scheme == "rtsp" || scheme == "rtspu"
        val isUdp: Boolean get() = scheme == "udp"

        /** Fragment-free canonical form used for stable channel keys. */
        val canonical: String
            get() {
                val defaultPort = when (scheme) {
                    "http" -> 80
                    "https" -> 443
                    "rtsp", "rtspu" -> 554
                    else -> null
                }
                val effectivePort = port?.takeIf { it != defaultPort }
                val authority = buildString {
                    append(scheme).append("://")
                    host?.let { append(it.lowercase(Locale.ROOT)) }
                    effectivePort?.let { append(':').append(it) }
                }
                val normalizedPath = path.orEmpty().substringBefore("?").trimEnd('/').ifEmpty { "/" }
                return authority + normalizedPath + (query?.let { "?$it" }.orEmpty())
            }
    }

    /**
     * Validates a URL used only as a playback source. Accepts http, https, rtsp, rtspu and udp;
     * rejects embedded credentials, control characters, whitespace, backslashes, overlong URLs
     * and unsafe schemes. HTTP(S) still goes through [parse], so API/import/EPG/download calls
     * and the per-hop redirect guard keep their HTTP-only policy.
     */
    fun parseStreamUrl(value: String): StreamUrl? {
        if (value.length !in 7..maxUrlLength) return null
        if (value.any { it.isISOControl() || it.isWhitespace() || it == '\\' }) return null
        val separator = value.indexOf("://")
        if (separator <= 0) return null
        val scheme = value.substring(0, separator).lowercase(Locale.ROOT)
        when (scheme) {
            "http", "https" -> {
                val url = parse(value) ?: return null
                return StreamUrl(value, scheme, url.host, url.port, url.encodedPath, url.encodedQuery)
            }
            "rtsp", "rtspu", "udp" -> return parseCustomStreamUrl(value, scheme)
            else -> return null
        }
    }

    /** True when [value] is an RTSP (including rtspu) playback source. */
    fun isRtsp(value: String): Boolean = parseStreamUrl(value)?.isRtsp == true

    /** True when [value] is a raw UDP playback source. */
    fun isUdp(value: String): Boolean = parseStreamUrl(value)?.isUdp == true

    /** Only HTTP(S) sources may be handed to the OkHttp-based RecordingManager. */
    fun isRecordableStream(value: String): Boolean = parseStreamUrl(value)?.isHttp == true

    private fun parseCustomStreamUrl(value: String, scheme: String): StreamUrl? {
        val rest = value.substring(scheme.length + 3)
        val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }
            .let { if (it < 0) rest.length else it }
        var authority = rest.substring(0, authorityEnd)
        val tail = rest.substring(authorityEnd)
        // VLC-style udp://@group:port denotes a bare multicast group, not a credential.
        if (scheme == "udp" && authority.startsWith("@")) authority = authority.substring(1)
        if (authority.any { it == '@' }) return null
        val (host, port) = splitStreamAuthority(authority, scheme) ?: return null
        val path = tail.substringBefore("?").substringBefore("#").takeIf { it.isNotEmpty() }
        val query = tail.indexOf('?').takeIf { it >= 0 }?.let { index ->
            tail.substring(index + 1).substringBefore("#").takeIf { it.isNotEmpty() }
        }
        return StreamUrl(value, scheme, host, port, path, query)
    }

    private fun splitStreamAuthority(authority: String, scheme: String): Pair<String?, Int?>? {
        if (authority.length > 512) return null
        val host: String
        val portText: String?
        if (authority.startsWith("[")) {
            val close = authority.indexOf(']')
            if (close < 0) return null
            host = authority.substring(0, close + 1)
            val remainder = authority.substring(close + 1)
            when {
                remainder.isEmpty() -> portText = null
                remainder.startsWith(":") -> portText = remainder.substring(1)
                else -> return null
            }
        } else {
            val firstColon = authority.indexOf(':')
            if (firstColon < 0) {
                host = authority
                portText = null
            } else {
                // Unbracketed IPv6 is ambiguous; require [::1] form.
                if (authority.indexOf(':', firstColon + 1) >= 0) return null
                host = authority.substring(0, firstColon)
                portText = authority.substring(firstColon + 1)
            }
        }
        val port = portText?.let { text ->
            text.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        }
        if (host.isEmpty()) {
            // udp://:5000 means "listen on port"; RTSP always needs a server.
            if (scheme != "udp" || port == null) return null
            return null to port
        }
        if (!isValidStreamHost(host)) return null
        return host to port
    }

    private fun isValidStreamHost(host: String): Boolean {
        if (host.isBlank() || host.length > 253) return false
        val inner = if (host.startsWith("[") && host.endsWith("]")) {
            host.substring(1, host.length - 1)
        } else {
            host
        }
        if (inner.isEmpty()) return false
        return inner.all { c ->
            c.isLetterOrDigit() || c == '.' || c == '-' || c == '_' || c == ':' || c == '%'
        }
    }

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
