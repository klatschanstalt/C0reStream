package pw.c0re.stream.data

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Hermetic reproduction of the GitHub release redirect chain:
 * `github.com/releases/latest/download/...` -> `github.com/releases/download/vX/...` ->
 * `release-assets.githubusercontent.com/github-production-release-asset/...?sig=...&jwt=...`.
 * Responses are supplied by an interceptor, so no network or TLS bypass is involved.
 */
class ReleaseAssetRedirectTest {

    private val latest = "https://github.com/klatschanstalt/C0reStream/releases/latest/download/C0reStream.apk"
    private val versioned = "https://github.com/klatschanstalt/C0reStream/releases/download/v1.7.1/C0reStream.apk"
    private val asset =
        "https://release-assets.githubusercontent.com/github-production-release-asset/123/asset" +
            "?sig=abc&jwt=def&se=2026-11-29T00%3A00%3A00Z&sp=r&sv=2024-11-04&sr=b"

    /** Serves [locations] as redirects, then a final 200, and records every attempted request. */
    private class FakeChain(
        private val locations: List<String>,
        private val base: OkHttpClient = NetworkPolicy.releaseAssetClient(),
    ) {
        val requests = ArrayList<Request>()

        fun client(): OkHttpClient = base.newBuilder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            val location = locations.getOrNull(requests.size - 1)
            val response = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .body("ok".toResponseBody())
            if (location == null) {
                response.code(200).message("OK")
            } else {
                response.code(302).message("Found").header("Location", location)
            }
            response.build()
        }.build()
    }

    private fun execute(locations: List<String>, start: String = latest, base: OkHttpClient = NetworkPolicy.releaseAssetClient()) =
        FakeChain(locations, base).client().newCall(Request.Builder().url(start).build()).execute()

    @Test
    fun `follows the two hop github release asset chain within the hop limit`() {
        val chain = FakeChain(listOf(versioned, asset))
        chain.client().newCall(Request.Builder().url(latest).build()).execute().use { response ->
            assertEquals(200, response.code)
            assertEquals(asset, response.request.url.toString())
        }
        assertEquals(3, chain.requests.size)
        assertEquals(latest, chain.requests[0].url.toString())
        assertEquals(versioned, chain.requests[1].url.toString())
        assertEquals(asset, chain.requests[2].url.toString())
    }

    @Test
    fun `allows the documented azure and github asset parameter set`() {
        val names = listOf(
            "sig", "se", "sp", "sv", "sr", "spr", "skoid", "sktid", "skt", "ske", "sks", "skv",
            "rscd", "rsct", "response-content-disposition", "response-content-type", "jwt",
        )
        val allowed = "https://release-assets.githubusercontent.com/github-production-release-asset/1?" +
            names.joinToString("&") { "$it=abc" }
        execute(listOf(allowed)).use { response -> assertEquals(200, response.code) }
    }

    @Test
    fun `rejects credential parameters on the asset hop`() {
        listOf("password=secret", "username=alice", "user=alice", "login=alice", "token=abc", "access_token=abc")
            .forEach { extra ->
                val failure = runCatching {
                    execute(listOf(versioned, "$asset&$extra")).use { }
                }.exceptionOrNull()
                assertTrue("$extra must be rejected", failure is IOException)
                assertTrue(failure!!.message!!.contains("blocked"))
            }
    }

    @Test
    fun `rejects https downgrade on any release hop`() {
        listOf(versioned.replace("https://", "http://"), asset.replace("https://", "http://"))
            .forEach { target ->
                val failure = runCatching {
                    execute(listOf(target)).use { }
                }.exceptionOrNull()
                assertTrue("$target must be rejected", failure is IOException)
                assertTrue(failure!!.message!!.contains("HTTPS", ignoreCase = true))
            }
    }

    @Test
    fun `rejects unknown hosts and wrong asset paths`() {
        listOf(
            "https://evil.example/github-production-release-asset/1?sig=abc",
            "https://release-assets.githubusercontent.com/other?sig=abc",
            "https://raw.githubusercontent.com/klatschanstalt/C0reStream/main/update.json",
            "https://github.com.evil.example/klatschanstalt/C0reStream/releases/download/v1/C0reStream.apk",
        ).forEach { target ->
            val failure = runCatching {
                execute(listOf(target)).use { }
            }.exceptionOrNull()
            assertTrue("$target must be rejected", failure is IOException)
            assertTrue(failure!!.message!!.contains("blocked"))
        }
    }

    @Test
    fun `limits the release redirect chain to three hops`() {
        val hops = (1..4).map {
            "https://github.com/klatschanstalt/C0reStream/releases/download/v$it/C0reStream.apk"
        }
        val failure = runCatching {
            execute(hops).use { }
        }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertTrue(failure!!.message!!.contains("Too many redirects"))
    }

    @Test
    fun `rejects original requests carrying credentials`() {
        val withQuery = runCatching {
            execute(listOf(versioned, asset), start = "$latest?username=alice").use { }
        }.exceptionOrNull()
        assertTrue(withQuery is IOException)

        val withHeader = runCatching {
            FakeChain(listOf(versioned, asset)).client().newCall(
                Request.Builder().url(latest).header("Authorization", "Bearer secret").build(),
            ).execute().use { }
        }.exceptionOrNull()
        assertTrue(withHeader is IOException)
    }

    @Test
    fun `general client does not inherit the release asset exception`() {
        val failure = runCatching {
            execute(listOf(versioned, asset), base = NetworkPolicy.client()).use { }
        }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertTrue(failure!!.message!!.contains("Credential-bearing"))
    }

    /**
     * Backward compatibility for installed v1.7.0/v1.7.1 clients: their strict general profile
     * accepts a direct HTTPS URL with no cross-origin, credential-bearing hop. The published
     * update manifest therefore points at a credential-free mirror of the exact signed APK.
     */
    @Test
    fun `strict general profile accepts a credential free direct download`() {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody("apk-bytes"))
            val url = server.url("/downloads/v1.7.2/C0reStream.apk")
            NetworkPolicy.client().newCall(Request.Builder().url(url).build()).execute().use { response ->
                assertEquals(200, response.code)
                assertEquals("apk-bytes", response.body.string())
            }
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }
}
