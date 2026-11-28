package pw.c0re.stream.data

import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Hermetic regressions for the central credential detection used by every redirect guard.
 * A credential-bearing request must never be silently forwarded to another origin.
 */
class CredentialRedirectTest {

    /** Distinct origin: identical host, different port (as required by the origin definition). */
    private fun crossOriginLocation(first: MockWebServer, second: MockWebServer): String =
        second.url("/final").newBuilder().host(first.url("/").host).build().toString()

    private fun assertCrossOriginBlocked(query: String) {
        val first = MockWebServer()
        val second = MockWebServer()
        try {
            first.enqueue(
                MockResponse().setResponseCode(302).setHeader("Location", crossOriginLocation(first, second)),
            )
            second.enqueue(MockResponse().setBody("ok"))
            val start = first.url("/").newBuilder().encodedPath("/start").encodedQuery(query).build()
            val failure = runCatching {
                NetworkPolicy.client().newCall(Request.Builder().url(start).build()).execute().use { }
            }.exceptionOrNull()
            assertTrue("$query must not cross an origin", failure is IOException)
            assertTrue(failure!!.message!!.contains("Credential-bearing"))
            assertEquals("no request may reach the foreign origin", 0, second.requestCount)
        } finally {
            first.shutdown()
            second.shutdown()
        }
    }

    @Test
    fun `username user user_name and login parameters are blocked cross origin`() {
        listOf("username=alice", "user=alice", "user_name=alice", "login=alice").forEach(::assertCrossOriginBlocked)
    }

    @Test
    fun `uppercase and mixed case credential names are blocked cross origin`() {
        listOf(
            "USERNAME=alice", "UserName=alice", "UsEr=alice", "USER=alice",
            "USER_NAME=alice", "LOGIN=alice", "LogIn=alice",
        ).forEach(::assertCrossOriginBlocked)
    }

    @Test
    fun `percent encoded credential names are blocked cross origin`() {
        listOf(
            "user%6Eame=alice", "%75sername=alice", "user%5Fname=alice",
            "log%69n=alice", "PASS%57ORD=secret",
        ).forEach(::assertCrossOriginBlocked)
    }

    @Test
    fun `multiple and repeated credential parameters are blocked cross origin`() {
        listOf(
            "action=get_live_streams&username=alice&password=secret",
            "user=alice&USER=bob",
            "token=abc&login=alice",
            "username=alice&username=bob",
        ).forEach(::assertCrossOriginBlocked)
    }

    @Test
    fun `same origin redirect with credential query remains allowed`() {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/final?username=alice"))
            server.enqueue(MockResponse().setBody("ok"))
            val start = server.url("/").newBuilder().encodedPath("/start").encodedQuery("username=alice").build()
            NetworkPolicy.client().newCall(Request.Builder().url(start).build()).execute().use { response ->
                assertEquals(200, response.code)
                assertEquals("ok", response.body.string())
            }
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `https downgrade is blocked on any hop`() {
        val https = NetworkPolicy.requireUrl(
            "https://github.com/klatschanstalt/C0reStream/releases/latest/download/C0reStream.apk?username=alice",
        )
        val failure = runCatching {
            NetworkPolicy.redirectTarget(https, "http://github.com/plain", originalHttps = true)
        }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertTrue(failure!!.message!!.contains("downgrade", ignoreCase = true))
    }

    @Test
    fun `sensitive headers never follow a cross origin redirect`() {
        listOf(
            "Authorization" to "Bearer secret",
            "X-Username" to "alice",
            "X-Password" to "secret",
            "X-Auth-Token" to "abc",
        ).forEach { (name, value) ->
            val first = MockWebServer()
            val second = MockWebServer()
            try {
                first.enqueue(
                    MockResponse().setResponseCode(302).setHeader("Location", crossOriginLocation(first, second)),
                )
                second.enqueue(MockResponse().setBody("ok"))
                val failure = runCatching {
                    NetworkPolicy.client().newCall(
                        Request.Builder().url(first.url("/start")).header(name, value).build(),
                    ).execute().use { }
                }.exceptionOrNull()
                assertTrue("$name must not cross an origin", failure is IOException)
                assertEquals("$name must not reach the foreign origin", 0, second.requestCount)
                assertEquals(value, first.takeRequest().getHeader(name))
            } finally {
                first.shutdown()
                second.shutdown()
            }
        }
    }

    @Test
    fun `credential values are absent from redirect failure diagnostics`() {
        val first = MockWebServer()
        val second = MockWebServer()
        try {
            first.enqueue(
                MockResponse().setResponseCode(302).setHeader("Location", crossOriginLocation(first, second)),
            )
            second.enqueue(MockResponse().setBody("ok"))
            val failure = runCatching {
                NetworkPolicy.client().newCall(
                    Request.Builder()
                        .url(first.url("/").newBuilder().encodedPath("/start")
                            .encodedQuery("username=alice-supersecret&password=pw-supersecret").build())
                        .build(),
                ).execute().use { }
            }.exceptionOrNull()
            assertTrue(failure is IOException)
            assertFalse(failure!!.message!!.contains("alice-supersecret"))
            assertFalse(failure.message!!.contains("pw-supersecret"))
        } finally {
            first.shutdown()
            second.shutdown()
        }
    }

    @Test
    fun `general client still blocks the github release asset hop`() {
        val failure = runCatching {
            NetworkPolicy.redirectTarget(
                NetworkPolicy.requireUrl(
                    "https://github.com/klatschanstalt/C0reStream/releases/download/v1.7.1/C0reStream.apk",
                ),
                "https://release-assets.githubusercontent.com/github-production-release-asset/1?sig=abc",
                originalHttps = true,
            )
        }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertTrue(failure!!.message!!.contains("Credential-bearing"))
    }
}
