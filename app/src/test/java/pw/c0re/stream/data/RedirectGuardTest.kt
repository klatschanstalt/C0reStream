package pw.c0re.stream.data

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class RedirectGuardTest {

    @Test
    fun `follows bounded redirect chains`() {
        val first = MockWebServer()
        val second = MockWebServer()
        try {
            first.enqueue(
                MockResponse().setResponseCode(302).setHeader("Location", second.url("/final").toString()),
            )
            second.enqueue(MockResponse().setBody("ok"))
            NetworkPolicy.client().newCall(Request.Builder().url(first.url("/start")).build())
                .execute().use { response ->
                    assertEquals(200, response.code)
                    assertEquals("ok", response.body.string())
                    assertEquals("/final", response.request.url.encodedPath)
                }
        } finally {
            first.shutdown()
            second.shutdown()
        }
    }

    @Test
    fun `blocks redirect loops`() {
        val server = MockWebServer()
        try {
            repeat(10) {
                server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/again"))
            }
            val failure = runCatching {
                NetworkPolicy.client().newCall(Request.Builder().url(server.url("/start")).build())
                    .execute().use { }
            }.exceptionOrNull()
            assertTrue(failure is IOException)
            assertTrue(failure!!.message!!.contains("Too many redirects"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `blocks credential bearing cross host redirect`() {
        val first = MockWebServer()
        val second = MockWebServer()
        try {
            val crossHost = second.url("/final").newBuilder().host("127.0.0.1").build().toString()
            first.enqueue(
                MockResponse().setResponseCode(302).setHeader("Location", crossHost),
            )
            second.enqueue(MockResponse().setBody("ok"))
            val failure = runCatching {
                NetworkPolicy.client().newCall(
                    Request.Builder().url(first.url("/start")).header("Authorization", "Bearer secret").build(),
                ).execute().use { }
            }.exceptionOrNull()
            assertTrue(failure is IOException)
            assertTrue(failure!!.message!!.contains("Credential-bearing"))
        } finally {
            first.shutdown()
            second.shutdown()
        }
    }

    @Test
    fun `blocks credential headers on port change only`() {
        val first = MockWebServer()
        val second = MockWebServer()
        try {
            // Same host, different port: a distinct origin even though the hostname matches.
            val crossPort = second.url("/final").newBuilder().host(first.url("/").host).build().toString()
            first.enqueue(
                MockResponse().setResponseCode(302).setHeader("Location", crossPort),
            )
            second.enqueue(MockResponse().setBody("ok"))
            val failure = runCatching {
                NetworkPolicy.client().newCall(
                    Request.Builder().url(first.url("/start")).header("Authorization", "Bearer secret").build(),
                ).execute().use { }
            }.exceptionOrNull()
            assertTrue(failure is IOException)
            assertTrue(failure!!.message!!.contains("Credential-bearing"))

            // A same-origin hop with the very same header stays legal.
            first.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/final"))
            first.enqueue(MockResponse().setBody("ok"))
            NetworkPolicy.client().newCall(
                Request.Builder().url(first.url("/again")).header("Authorization", "Bearer secret").build(),
            ).execute().use { response ->
                assertEquals(200, response.code)
                assertEquals("ok", response.body.string())
            }
        } finally {
            first.shutdown()
            second.shutdown()
        }
    }

    @Test
    fun `blocks cookie proxy auth and custom sensitive headers cross origin`() {
        listOf(
            "Cookie" to "session=secret",
            "Proxy-Authorization" to "Basic c2VjcmV0",
            "X-Api-Key" to "secret",
            "X-Access-Token" to "secret",
        ).forEach { (header, value) ->
            val first = MockWebServer()
            val second = MockWebServer()
            try {
                val crossHost = second.url("/final").newBuilder().host("127.0.0.1").build().toString()
                first.enqueue(MockResponse().setResponseCode(302).setHeader("Location", crossHost))
                second.enqueue(MockResponse().setBody("ok"))
                val failure = runCatching {
                    NetworkPolicy.playbackClient().newCall(
                        Request.Builder().url(first.url("/start")).header(header, value).build(),
                    ).execute().use { }
                }.exceptionOrNull()
                assertTrue("$header must not cross an origin", failure is IOException)
                assertTrue(failure!!.message!!.contains("Credential-bearing"))
            } finally {
                first.shutdown()
                second.shutdown()
            }
        }
    }

    @Test
    fun `blocks credential query redirects across scheme changes`() {
        val http = NetworkPolicy.requireUrl("http://example.com/player_api.php?username=alice&password=secret")
        val failure = runCatching {
            NetworkPolicy.redirectTarget(
                http,
                "https://example.com/player_api.php?username=alice&password=secret",
                originalHttps = false,
            )
        }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertTrue(failure!!.message!!.contains("Credential-bearing"))
    }

    @Test
    fun `closes the response when a redirect target is invalid`() {
        val closed = AtomicBoolean(false)
        val guardClient = OkHttpClient.Builder()
            .addInterceptor(NetworkPolicy.redirectGuardInterceptor)
            .addInterceptor { chain ->
                val source = Buffer().writeUtf8("unread redirect body")
                val tracked = object : ResponseBody() {
                    override fun contentType(): okhttp3.MediaType? = null
                    override fun contentLength(): Long = -1L
                    override fun source(): okio.BufferedSource = source
                    override fun close() {
                        closed.set(true)
                        source.close()
                    }
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(302)
                    .message("Found")
                    .header("Location", "http://user:secret@example.com/invalid")
                    .body(tracked)
                    .build()
            }
            .build()
        val failure = runCatching {
            guardClient.newCall(Request.Builder().url("http://localhost/start").build())
                .execute().use { }
        }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertTrue("invalid redirect hop must release its response body", closed.get())
    }
}
