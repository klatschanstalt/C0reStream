package pw.c0re.stream.data

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URLDecoder
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
 *    [downloadClient], [releaseAssetClient] and the compatibility [client]) keep an absolute
 *    90 s call deadline;
 *  - continuous stream profiles ([playbackClient], [recordingClient]) deliberately disable
 *    the absolute call deadline so a long-lived live stream is never cut off after 90 s.
 *    They still have connect (20 s) and read (30 s) timeouts, so stalled hops remain bounded.
 *
 * [releaseAssetClient] is a narrowly scoped opt-in profile for GitHub release artifacts only;
 * every other profile keeps the strict "credentials never cross an origin" rule.
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
    private const val maxReleaseAssetRedirects = 3
    private const val maxCachedClients = 24

    private const val githubReleaseHost = "github.com"
    private const val githubReleaseWwwHost = "www.github.com"
    private const val releaseAssetHost = "release-assets.githubusercontent.com"
    private const val githubReleasePathPrefix = "/klatschanstalt/C0reStream/releases/"
    private const val releaseAssetPathPrefix = "/github-production-release-asset/"

    /** Shared across every profile so switching profile never strands idle connections. */
    internal val sharedConnectionPool = ConnectionPool(5, 5, TimeUnit.MINUTES)
    internal val sharedDispatcher = Dispatcher()

    private enum class Profile {
        API, IMPORT, EPG, DOWNLOAD, PLAYBACK, RECORDING, RELEASE_ASSET,
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
        val lower = value.lowercase(Locale.ROOT)
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

    private val sensitiveHeaderMarkers = listOf(
        "authorization", "token", "secret", "cookie", "credential", "api-key", "apikey", "signature",
        "username", "password", "passwd", "login",
    )

    /** Credential-like headers, including custom "X-..." ones, must never cross an origin. */
    private fun isSensitiveHeader(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        if (lower in sensitiveHeaderNames) return true
        return sensitiveHeaderMarkers.any { lower.contains(it) }
    }

    /**
     * Single central credential-name set shared by every redirect guard. Xtream-style APIs
     * identify a subscriber by `username`/`user`/`user_name`/`login`, so those names are just as
     * sensitive as `password` or `token`. Names are compared case-insensitively and after
     * percent-decoding; [HttpUrl.queryParameterNames] already reports every distinct name, so
     * repeated parameters are covered as well.
     */
    private val sensitiveQueryNames = setOf(
        "token", "access_token", "accesstoken", "auth", "authorization", "password", "passwd",
        "pwd", "secret", "signature", "sig", "jwt", "api_key", "apikey", "credential", "session",
        "username", "user", "user_name", "user-name", "login", "login_name", "logon", "uname",
    )

    private fun normalizedParameterName(name: String): String {
        val decoded = runCatching { URLDecoder.decode(name, "UTF-8") }.getOrDefault(name)
        return decoded.lowercase(Locale.ROOT).trim()
    }

    private fun hasSensitiveQuery(url: HttpUrl): Boolean =
        url.queryParameterNames.any { normalizedParameterName(it) in sensitiveQueryNames }

    /**
     * Xtream stream URLs carry the subscriber name and password as path segments:
     * `/{live|movie|series}/{user}/{password}/{id}.{container}`. Such URLs are credentials too,
     * even though their secrets are not in the query string.
     */
    private val credentialPathPattern =
        Regex("^/(?:live|movie|series)/[^/]+/[^/]+/[0-9]{1,20}\\.[A-Za-z0-9]{1,10}$")

    private fun hasSensitivePath(url: HttpUrl): Boolean = credentialPathPattern.matches(url.encodedPath)

    private fun hasSensitiveUrl(url: HttpUrl): Boolean = hasSensitiveQuery(url) || hasSensitivePath(url)

    /**
     * Resolves one redirect hop. The original transport class is enforced on every hop, so an
     * HTTPS -> HTTP -> HTTPS chain cannot replay credentials over plaintext in the middle.
     * A hop that changes scheme/host/port while carrying credentials (headers, URL query or
     * credential-bearing path segments) is rejected; callers must close the current response.
     */
    internal fun redirectTarget(current: HttpUrl, location: String, originalHttps: Boolean): HttpUrl {
        val next = current.resolve(location) ?: throw IOException("Invalid redirect URL")
        parse(next.toString()) ?: throw IOException("Invalid redirect URL")
        if (originalHttps && !next.isHttps) throw IOException("HTTPS downgrade blocked")
        if (!sameOrigin(current, next) && (hasSensitiveUrl(current) || hasSensitiveUrl(next))) {
            throw IOException("Credential-bearing cross-origin redirect blocked")
        }
        return next
    }

    /**
     * Only the GitHub/Azure parameters signed into a release-asset URL may appear on the asset
     * host. Anything else that looks like a credential (password, username, token, ...) fails
     * closed; TLS verification is never relaxed.
     */
    private val releaseAssetQueryNames = setOf(
        "sig", "se", "sp", "sv", "sr", "spr", "skoid", "sktid", "skt", "ske", "sks", "skv",
        "rscd", "rsct", "response-content-disposition", "response-content-type", "jwt",
    )

    private fun isGithubReleaseUrl(url: HttpUrl): Boolean =
        (url.host == githubReleaseHost || url.host == githubReleaseWwwHost) &&
            url.encodedPath.startsWith(githubReleasePathPrefix)

    private fun isReleaseAssetUrl(url: HttpUrl): Boolean =
        url.host == releaseAssetHost && url.encodedPath.startsWith(releaseAssetPathPrefix)

    private fun hasOnlyReleaseAssetQueries(url: HttpUrl): Boolean =
        url.queryParameterNames.all { normalizedParameterName(it) in releaseAssetQueryNames }

    /** Validates one hop of the narrowly scoped GitHub release-artifact redirect chain. */
    internal fun releaseAssetRedirectTarget(current: HttpUrl, location: String): HttpUrl {
        val next = current.resolve(location) ?: throw IOException("Invalid redirect URL")
        parse(next.toString()) ?: throw IOException("Invalid redirect URL")
        if (current.isHttps && !next.isHttps) throw IOException("HTTPS downgrade blocked")
        if (!next.isHttps) throw IOException("HTTPS required for release assets")
        val allowed = when {
            isReleaseAssetUrl(next) ->
                isGithubReleaseUrl(current) && hasOnlyReleaseAssetQueries(next)
            isGithubReleaseUrl(current) && isGithubReleaseUrl(next) ->
                !hasSensitiveQuery(next)
            else -> false
        }
        if (!allowed) throw IOException("Release asset redirect target blocked")
        return next
    }

    /** Release-artifact requests must be plain GitHub URLs with no credentials attached. */
    private fun isReleaseAssetRequest(request: Request): Boolean =
        request.url.isHttps && isGithubReleaseUrl(request.url) &&
            !hasSensitiveQuery(request.url) &&
            request.headers.names().none(::isSensitiveHeader)

    /**
     * Purpose-bound redirect guard for GitHub release artifacts. Enforces HTTPS on every hop,
     * a three-hop ceiling, and an explicit github.com -> release-assets.githubusercontent.com
     * chain. General clients keep the strict [redirectGuardInterceptor].
     */
    internal val releaseAssetRedirectGuardInterceptor: Interceptor = Interceptor { chain ->
        var request = chain.request()
        if (!isReleaseAssetRequest(request)) throw IOException("Release asset request blocked by policy")
        var redirects = 0
        var finalResponse: Response? = null
        while (finalResponse == null) {
            val response = chain.proceed(request)
            val location = if (response.isRedirect) response.header("Location") else null
            if (location == null) {
                finalResponse = response
            } else {
                try {
                    val next = releaseAssetRedirectTarget(request.url, location)
                    if (++redirects > maxReleaseAssetRedirects) throw IOException("Too many redirects")
                    val builder = request.newBuilder().url(next)
                    if (!sameOrigin(request.url, next)) {
                        // Defensive: never carry a credential header to the asset origin.
                        request.headers.names().filter(::isSensitiveHeader).forEach(builder::removeHeader)
                    }
                    request = builder.build()
                } finally {
                    closeQuietly(response)
                }
            }
        }
        finalResponse
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
        redirectGuard: Interceptor = redirectGuardInterceptor,
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
                .addInterceptor(redirectGuard)
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
            if (profile == Profile.RELEASE_ASSET) {
                // Application interceptors may add headers after the redirect guard has run.
                // The network interceptor sees the actual wire request and fails closed.
                builder.addNetworkInterceptor { chain ->
                    if (chain.request().headers.names().any(::isSensitiveHeader)) {
                        throw IOException("Credential header blocked on release asset request")
                    }
                    chain.proceed(chain.request())
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

    /**
     * Opt-in profile for GitHub release artifacts (APK and signed resource files) only.
     *
     * The original request must be HTTPS on `github.com`/`www.github.com` under
     * `/klatschanstalt/C0reStream/releases/` and must carry no sensitive query parameter or
     * header. Redirects stay HTTPS, are limited to three hops, and may only move within that
     * GitHub release path or from it to
     * `release-assets.githubusercontent.com/github-production-release-asset/` with the known
     * GitHub/Azure SAS parameters. Every other origin and every other profile stays strict;
     * TLS/certificate verification is unchanged.
     */
    fun releaseAssetClient(
        userAgent: String? = null,
        useProxy: Boolean = false,
        proxyHost: String? = null,
        proxyPort: Int? = null,
    ): OkHttpClient = profileClient(
        Profile.RELEASE_ASSET,
        userAgent,
        useProxy,
        proxyHost,
        proxyPort,
        releaseAssetRedirectGuardInterceptor,
    )

    internal fun closeQuietly(response: Response) {
        runCatching { response.close() }
    }
}
