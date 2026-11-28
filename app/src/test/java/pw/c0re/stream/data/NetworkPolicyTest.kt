package pw.c0re.stream.data

import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class NetworkPolicyTest {

    @Test
    fun `accepts http and https urls`() {
        assertNotNull(NetworkPolicy.parse("https://example.com/live.ts"))
        assertNotNull(NetworkPolicy.parse("http://example.com:8080/live.ts"))
    }

    @Test
    fun `rejects unsupported schemes`() {
        assertNull(NetworkPolicy.parse("file:///etc/passwd"))
        assertNull(NetworkPolicy.parse("ftp://example.com/file"))
        assertNull(NetworkPolicy.parse("javascript:alert(1)"))
        assertNull(NetworkPolicy.parse("data:text/html,hello"))
    }

    @Test
    fun `rejects embedded credentials`() {
        assertNull(NetworkPolicy.parse("https://user:secret@example.com/live.ts"))
    }

    @Test
    fun `rejects control characters whitespace and backslashes`() {
        assertNull(NetworkPolicy.parse("https://example.com/a b.ts"))
        assertNull(NetworkPolicy.parse("https://example.com/a\\b.ts"))
        assertNull(NetworkPolicy.parse("https://example.com/\u0000.ts"))
    }

    @Test
    fun `rejects overlong urls`() {
        assertNull(NetworkPolicy.parse("https://example.com/" + "a".repeat(20_000)))
    }

    @Test
    fun `detects https`() {
        assertTrue(NetworkPolicy.isHttps("https://example.com"))
        assertFalse(NetworkPolicy.isHttps("http://example.com"))
        assertFalse(NetworkPolicy.isHttps("nonsense"))
    }

    @Test
    fun `validates catchup templates structurally`() {
        assertTrue(NetworkPolicy.isTemplateUrl("https://cdn.example/ts?start={utc}&end={utcend}"))
        assertTrue(NetworkPolicy.isTemplateUrl("http://cdn.example/ts?start={start}"))
        assertFalse(NetworkPolicy.isTemplateUrl("https://user:pass@cdn.example/{start}"))
        assertFalse(NetworkPolicy.isTemplateUrl("file:///tmp/{start}"))
        assertFalse(NetworkPolicy.isTemplateUrl("https://cdn.example/a b"))
    }

    @Test
    fun `sanitizes user agents`() {
        assertNull(NetworkPolicy.userAgent(null))
        assertNull(NetworkPolicy.userAgent("   "))
        assertNull(NetworkPolicy.userAgent("bad\nagent"))
        assertNull(NetworkPolicy.userAgent("a".repeat(513)))
        assertEquals("VLC/3.0", NetworkPolicy.userAgent("VLC/3.0"))
    }

    @Test
    fun `validates proxies`() {
        assertNull(NetworkPolicy.proxy("host", 0))
        assertNull(NetworkPolicy.proxy("host", 70_000))
        assertNull(NetworkPolicy.proxy("bad/host", 8080))
        assertNull(NetworkPolicy.proxy("user@host", 8080))
        assertNull(NetworkPolicy.proxy(null, 8080))
        assertNotNull(NetworkPolicy.proxy("127.0.0.1", 8080))
    }

    @Test
    fun `redirect guard blocks https downgrade on any hop`() {
        val https = NetworkPolicy.requireUrl("https://example.com/player_api.php")
        assertTrue(runCatching {
            NetworkPolicy.redirectTarget(https, "http://example.com/plain", originalHttps = true)
        }.exceptionOrNull() is IOException)
        assertEquals(
            "https://example.com/next",
            NetworkPolicy.redirectTarget(https, "/next", originalHttps = true).toString(),
        )
        assertEquals(
            "https://cdn.example/next",
            NetworkPolicy.redirectTarget(https, "https://cdn.example/next", originalHttps = true).toString(),
        )
    }

    @Test
    fun `http upgrade is allowed but credential urls are rejected`() {
        val http = NetworkPolicy.requireUrl("http://example.com/live")
        assertEquals(
            "https://example.com/live",
            NetworkPolicy.redirectTarget(http, "https://example.com/live", originalHttps = false).toString(),
        )
        assertTrue(runCatching {
            NetworkPolicy.redirectTarget(http, "https://user:secret@example.com/live", originalHttps = false)
        }.exceptionOrNull() is IOException)
        assertTrue(runCatching {
            NetworkPolicy.redirectTarget(http, "ftp://example.com/live", originalHttps = false)
        }.exceptionOrNull() is IOException)
    }

    /**
     * No unit test waits 90 s: the fix is asserted through [okhttp3.OkHttpClient.callTimeoutMillis].
     * A real >90 s live-stream verification needs a controlled local server that keeps serving
     * past the old deadline (or manual on-device verification) and is intentionally not a JVM test.
     */
    @Test
    fun `continuous profiles have no absolute call deadline`() {
        listOf(NetworkPolicy.playbackClient(), NetworkPolicy.recordingClient()).forEach { client ->
            assertEquals(0, client.callTimeoutMillis)
            assertEquals(20_000, client.connectTimeoutMillis)
            assertEquals(30_000, client.readTimeoutMillis)
            assertFalse(client.followRedirects)
        }
    }

    @Test
    fun `bounded profiles keep an absolute call deadline`() {
        listOf(
            NetworkPolicy.client(),
            NetworkPolicy.apiClient(),
            NetworkPolicy.importClient(),
            NetworkPolicy.epgClient(),
            NetworkPolicy.downloadClient(),
        ).forEach { client ->
            assertTrue(client.callTimeoutMillis > 0)
            assertEquals(20_000, client.connectTimeoutMillis)
            assertEquals(30_000, client.readTimeoutMillis)
        }
    }

    @Test
    fun `profiles share pool and dispatcher and are cached`() {
        assertSame(NetworkPolicy.sharedConnectionPool, NetworkPolicy.apiClient().connectionPool)
        assertSame(NetworkPolicy.sharedConnectionPool, NetworkPolicy.playbackClient().connectionPool)
        assertSame(NetworkPolicy.sharedDispatcher, NetworkPolicy.apiClient().dispatcher)
        assertSame(NetworkPolicy.sharedDispatcher, NetworkPolicy.recordingClient().dispatcher)

        // client() remains the API profile, but profiles are distinct instances.
        assertSame(NetworkPolicy.apiClient(), NetworkPolicy.client())
        assertNotSame(NetworkPolicy.apiClient(), NetworkPolicy.playbackClient())
        assertNotSame(NetworkPolicy.playbackClient(), NetworkPolicy.recordingClient())
        // Repeated calls with the same settings reuse one client.
        assertSame(NetworkPolicy.playbackClient("VLC/3.0"), NetworkPolicy.playbackClient("VLC/3.0"))
        assertNotSame(NetworkPolicy.playbackClient("VLC/3.0"), NetworkPolicy.playbackClient("VLC/4.0"))
        assertNotNull(NetworkPolicy.playbackClient(useProxy = true, proxyHost = "127.0.0.1", proxyPort = 8080).proxy)
    }

    @Test
    fun `playback profile applies user agent and preserves range and etag across hops`() {
        val first = MockWebServer()
        val second = MockWebServer()
        try {
            first.enqueue(
                MockResponse().setResponseCode(302).setHeader("Location", second.url("/final").toString()),
            )
            second.enqueue(MockResponse().setBody("ok"))
            val client = NetworkPolicy.playbackClient("VLC/3.0")
            val request = Request.Builder().url(first.url("/start"))
                .header("Range", "bytes=0-1023")
                .header("If-None-Match", "\"etag-1\"")
                .build()
            client.newCall(request).execute().use { response ->
                assertEquals(200, response.code)
                assertEquals("ok", response.body.string())
            }
            listOf(first.takeRequest(), second.takeRequest()).forEach { recorded ->
                assertEquals("VLC/3.0", recorded.getHeader("User-Agent"))
                assertEquals("bytes=0-1023", recorded.getHeader("Range"))
                assertEquals("\"etag-1\"", recorded.getHeader("If-None-Match"))
            }
        } finally {
            first.shutdown()
            second.shutdown()
        }
    }

    @Test
    fun `parseStreamUrl accepts http and https playback urls`() {
        val https = NetworkPolicy.parseStreamUrl("https://cdn.example/live/index.m3u8")!!
        assertTrue(https.isHttp)
        assertFalse(https.isRtsp)
        assertEquals("cdn.example", https.host)
        assertEquals(443, https.port)
        assertNotNull(NetworkPolicy.parseStreamUrl("http://example.com:8080/live.ts"))
    }

    @Test
    fun `parseStreamUrl accepts rtsp and rtspu with and without ports`() {
        val plain = NetworkPolicy.parseStreamUrl("rtsp://192.168.1.10/live/channel")!!
        assertEquals("rtsp", plain.scheme)
        assertTrue(plain.isRtsp)
        assertFalse(plain.isUdp)
        assertEquals("192.168.1.10", plain.host)
        assertNull(plain.port)

        val withPort = NetworkPolicy.parseStreamUrl("RTSPU://media.example:554/live?token=abc")!!
        assertEquals("rtspu", withPort.scheme)
        assertTrue(withPort.isRtsp)
        assertEquals("media.example", withPort.host)
        assertEquals(554, withPort.port)
        assertEquals("token=abc", withPort.query)
    }

    @Test
    fun `parseStreamUrl accepts udp multicast groups and listen forms`() {
        listOf(
            "udp://239.255.1.1:5000",
            "udp://@239.255.1.1:5000",
            "udp://239.255.1.1",
            "udp://:5000",
            "udp://@:5000",
            "udp://[ff02::1]:5000",
        ).forEach { url -> assertNotNull(NetworkPolicy.parseStreamUrl(url)) }

        val group = NetworkPolicy.parseStreamUrl("udp://@239.255.1.1:5000")!!
        assertEquals("udp", group.scheme)
        assertTrue(group.isUdp)
        assertFalse(group.isRtsp)
        assertEquals("239.255.1.1", group.host)
        assertEquals(5000, group.port)
        val listen = NetworkPolicy.parseStreamUrl("udp://:5000")!!
        assertNull(listen.host)
        assertEquals(5000, listen.port)
    }

    @Test
    fun `parseStreamUrl rejects credentials unsafe schemes and malformed authorities`() {
        assertNull(NetworkPolicy.parseStreamUrl("rtsp://user:secret@example.com/live"))
        assertNull(NetworkPolicy.parseStreamUrl("udp://user:pass@239.0.0.1:5000"))
        assertNull(NetworkPolicy.parseStreamUrl("rtsp://user@example.com/live"))
        assertNull(NetworkPolicy.parseStreamUrl("file:///etc/passwd"))
        assertNull(NetworkPolicy.parseStreamUrl("ftp://example.com/file"))
        assertNull(NetworkPolicy.parseStreamUrl("rtsps://example.com/live"))
        assertNull(NetworkPolicy.parseStreamUrl("rtsp://"))
        assertNull(NetworkPolicy.parseStreamUrl("rtsp://host:70000/live"))
        assertNull(NetworkPolicy.parseStreamUrl("rtsp://host/a b"))
        assertNull(NetworkPolicy.parseStreamUrl("rtsp://host/a\\b"))
        assertNull(NetworkPolicy.parseStreamUrl("rtsp://host/\u0000"))
        assertNull(NetworkPolicy.parseStreamUrl("rtsp://" + "a".repeat(20_000)))
    }

    @Test
    fun `playback helpers keep http policy intact`() {
        assertNull(NetworkPolicy.parse("rtsp://example.com/live"))
        assertNull(NetworkPolicy.parse("udp://239.0.0.1:5000"))
        assertFalse(NetworkPolicy.isRtsp("https://example.com/live"))
        assertFalse(NetworkPolicy.isUdp("https://example.com/live"))
        assertTrue(NetworkPolicy.isRtsp("rtspu://example.com/live"))
        assertTrue(NetworkPolicy.isUdp("udp://239.0.0.1:5000"))
        assertTrue(NetworkPolicy.isRecordableStream("https://example.com/live"))
        assertFalse(NetworkPolicy.isRecordableStream("rtsp://example.com/live"))
        assertFalse(NetworkPolicy.isRecordableStream("rtspu://example.com/live"))
        assertFalse(NetworkPolicy.isRecordableStream("udp://239.0.0.1:5000"))
    }

    @Test
    fun `canonical stream url drops default ports trailing slashes and fragments`() {
        assertEquals(
            "rtsp://host/live",
            NetworkPolicy.parseStreamUrl("RTSP://Host:554/live/#fragment")!!.canonical,
        )
        assertEquals(
            "udp://239.0.0.1:5000/",
            NetworkPolicy.parseStreamUrl("udp://@239.0.0.1:5000")!!.canonical,
        )
    }

    @Test
    fun `redirect guard blocks credential query parameters on cross origin hops`() {
        val withToken = NetworkPolicy.requireUrl("http://example.com:8080/live?token=secret")
        assertTrue(runCatching {
            NetworkPolicy.redirectTarget(withToken, "http://example.com:9090/live", originalHttps = false)
        }.exceptionOrNull() is IOException)
        assertTrue(runCatching {
            NetworkPolicy.redirectTarget(
                NetworkPolicy.requireUrl("http://example.com:8080/live"),
                "http://cdn.example/live?password=secret",
                originalHttps = false,
            )
        }.exceptionOrNull() is IOException)
        // Same-origin hops carrying the same query stay legal.
        assertEquals(
            "http://example.com:8080/next?token=secret",
            NetworkPolicy.redirectTarget(withToken, "/next?token=secret", originalHttps = false).toString(),
        )
    }

    @Test
    fun `credential name detection includes username variants and encoded names`() {
        listOf(
            "username", "user", "user_name", "USER", "UserName", "LOGIN",
            "user%6Eame", "%75sername", "user%5Fname", "log%69n",
        ).forEach { name ->
            val url = NetworkPolicy.requireUrl("http://example.com/live?$name=alice")
            assertTrue(
                "$name must be treated as sensitive",
                runCatching {
                    NetworkPolicy.redirectTarget(url, "http://cdn.example/live", originalHttps = false)
                }.exceptionOrNull() is IOException,
            )
        }
    }

    @Test
    fun `credential bearing path segments block cross origin redirects`() {
        val stream = NetworkPolicy.requireUrl("https://example.com/live/alice/secret/123.ts")
        assertTrue(runCatching {
            NetworkPolicy.redirectTarget(stream, "https://cdn.example/123.ts", originalHttps = true)
        }.exceptionOrNull() is IOException)
        // A plain stream path remains redirectable.
        assertEquals(
            "https://cdn.example/123.ts",
            NetworkPolicy.redirectTarget(
                NetworkPolicy.requireUrl("https://example.com/live/123.ts"),
                "https://cdn.example/123.ts",
                originalHttps = true,
            ).toString(),
        )
    }

    @Test
    fun `release asset profile is additive and bounded`() {
        val release = NetworkPolicy.releaseAssetClient()
        assertEquals(20_000, release.connectTimeoutMillis)
        assertEquals(30_000, release.readTimeoutMillis)
        assertTrue(release.callTimeoutMillis > 0)
        assertFalse(release.followRedirects)
        assertFalse(release.followSslRedirects)
        assertNotSame(NetworkPolicy.client(), release)
        assertNotSame(NetworkPolicy.downloadClient(), release)
        assertSame(release, NetworkPolicy.releaseAssetClient())
    }
}
