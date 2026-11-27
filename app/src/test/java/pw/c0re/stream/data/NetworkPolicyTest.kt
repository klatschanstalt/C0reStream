package pw.c0re.stream.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
