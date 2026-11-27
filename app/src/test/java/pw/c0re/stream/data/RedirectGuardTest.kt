package pw.c0re.stream.data

import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

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
}
