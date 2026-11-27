package pw.c0re.stream.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelKeyTest {

    @Test
    fun `stable key prefers tvg id over url`() {
        val a = ChannelKey.stableKey("https://a.example/live/1.ts", "Channel.One")
        val b = ChannelKey.stableKey("https://b.example/live/2.ts", "channel.one")
        assertEquals(a, b)
    }

    @Test
    fun `stable key ignores display name when tvg id is present`() {
        // Display names are not part of the identity input at all.
        val a = ChannelKey.stableKey("https://a.example/1.ts", "news")
        val b = ChannelKey.stableKey("https://a.example/2.ts", "NEWS")
        assertEquals(a, b)
    }

    @Test
    fun `stable key follows url when tvg id is missing`() {
        val a = ChannelKey.stableKey("https://example.com/live/1.ts", null)
        val b = ChannelKey.stableKey("https://example.com/live/2.ts", null)
        assertNotEquals(a, b)
    }

    @Test
    fun `stable key is scoped and bounded`() {
        val key = ChannelKey.stableKey("https://example.com/live/1.ts", "id")
        assertEquals(48, key.length)
        assertTrue(key.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `url normalization removes default ports and fragments`() {
        assertEquals(
            "https://example.com/live/1",
            ChannelKey.normalizeUrl("HTTPS://Example.COM:443/live/1/#fragment"),
        )
        assertEquals(
            "http://example.com:8080/live",
            ChannelKey.normalizeUrl("http://example.com:8080/live/"),
        )
    }

    @Test
    fun `normalization rejects non http urls`() {
        assertNull(ChannelKey.normalizeUrl("file:///etc/passwd"))
        assertNull(ChannelKey.normalizeUrl("not a url"))
    }
}
