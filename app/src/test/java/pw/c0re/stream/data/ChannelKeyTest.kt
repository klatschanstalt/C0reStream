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

    @Test
    fun `same tvg id but different variant urls stay distinct`() {
        val variantA = ChannelKey.variantKey("https://example.com/live/news/hd.ts?token=aaa")
        val variantB = ChannelKey.variantKey("https://example.com/live/news/sd.ts?token=bbb")
        val a = ChannelKey.stableKey(
            "https://example.com/live/news/hd.ts?token=aaa", "news", variantKey = variantA,
        )
        val b = ChannelKey.stableKey(
            "https://example.com/live/news/sd.ts?token=bbb", "news", variantKey = variantB,
        )
        assertNotEquals(a, b)
        // A refreshed token does not create a new identity.
        val refreshed = ChannelKey.variantKey("https://example.com/live/news/hd.ts?token=zzz")
        assertEquals(
            a,
            ChannelKey.stableKey(
                "https://example.com/live/news/hd.ts?token=zzz", "news", variantKey = refreshed,
            ),
        )
    }

    @Test
    fun `same url with different channels stays distinct`() {
        val shared = "https://example.com/shared.ts"
        val news = ChannelKey.stableKey(shared, null, name = "News", group = "Information")
        val sport = ChannelKey.stableKey(shared, null, name = "Sport", group = "Sports")
        val otherId = ChannelKey.stableKey(shared, "sport-id", name = "Sport", group = "Sports")
        assertNotEquals(news, sport)
        assertNotEquals(news, otherId)
        assertNotEquals(sport, otherId)
    }

    @Test
    fun `tvg name keeps the id stable across display renames`() {
        val before = ChannelKey.stableKey(
            "https://example.com/1.ts?token=old", null, tvgName = "Stable.Name", name = "Old label",
        )
        val after = ChannelKey.stableKey(
            "https://example.com/1.ts?token=new", null, tvgName = "Stable.Name", name = "New label",
        )
        assertEquals(before, after)
    }

    @Test
    fun `group and channel number disambiguate channels sharing a url and name`() {
        val url = "https://example.com/same.ts"
        val one = ChannelKey.stableKey(url, null, name = "Channel", group = "News", channelNumber = "1")
        val two = ChannelKey.stableKey(url, null, name = "Channel", group = "News", channelNumber = "2")
        val three = ChannelKey.stableKey(url, null, name = "Channel", group = "Sport", channelNumber = "1")
        assertNotEquals(one, two)
        assertNotEquals(one, three)
        assertNotEquals(two, three)
    }

    @Test
    fun `legacy key stays available for old rows`() {
        val url = "https://example.com/live/1.ts"
        assertEquals(ChannelKey.legacyKey(url, "XYZ"), ChannelKey.stableKey(url, "XYZ"))
        assertEquals(
            ChannelKey.legacyKey(url, null),
            ChannelKey.stableKey(url, null),
        )
        assertTrue(ChannelKey.isLegacyKey(ChannelKey.legacyKey(url, "XYZ"), url, "XYZ"))
        assertNotEquals(ChannelKey.legacyKey(url, "XYZ"), ChannelKey.legacyKey(url, "ABC"))
        assertEquals(48, ChannelKey.legacyKey(url, "XYZ").length)
    }

    @Test
    fun `disambiguation keeps deterministic 48 hex keys`() {
        val key = ChannelKey.stableKey("https://example.com/a.ts", "id")
        val first = ChannelKey.disambiguate(key, "variant-a")
        assertEquals(first, ChannelKey.disambiguate(key, "variant-a"))
        assertNotEquals(first, ChannelKey.disambiguate(key, "variant-b"))
        assertEquals(48, first.length)
        assertTrue(first.all { it in '0'..'9' || it in 'a'..'f' })
    }
}
