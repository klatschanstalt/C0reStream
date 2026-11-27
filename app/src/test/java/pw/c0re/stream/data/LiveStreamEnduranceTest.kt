package pw.c0re.stream.data

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Opt-in endurance check for the P0 streaming timeout. Run locally with
 * `-Dc0re.longstream=true`; CI skips it so normal builds stay fast.
 */
class LiveStreamEnduranceTest {

    @Test
    fun `continuous live stream is not cut off by an absolute call timeout`() {
        assumeTrue(
            "Set -Dc0re.longstream=true to run the ~100s endurance test",
            System.getProperty("c0re.longstream") == "true",
        )
        val totalBytes = 100_000
        val body = Buffer().write(ByteArray(totalBytes))
        val server = MockWebServer()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(body)
                    .throttleBody(1_000, 1, TimeUnit.SECONDS),
            )
            val started = System.currentTimeMillis()
            NetworkPolicy.playbackClient()
                .newCall(okhttp3.Request.Builder().url(server.url("/live.ts")).build())
                .execute().use { response ->
                    assertEquals(200, response.code)
                    assertEquals(totalBytes, response.body.bytes().size)
                }
            val elapsedSeconds = (System.currentTimeMillis() - started) / 1_000
            assert(elapsedSeconds >= 90) { "Stream ended after only ${elapsedSeconds}s" }
        } finally {
            server.shutdown()
        }
    }
}
