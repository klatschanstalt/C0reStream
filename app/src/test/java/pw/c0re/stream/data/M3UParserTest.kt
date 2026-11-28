package pw.c0re.stream.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class M3UParserTest {

    @Test
    fun `parses extended attributes and quoted names`() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-id="news.de" tvg-logo="https://img.example/news.png" group-title="News, DE",Tagesschau
            https://stream.example/news/index.m3u8
        """.trimIndent()
        val channels = M3UParser.parse(text)
        assertEquals(1, channels.size)
        val channel = channels.first()
        assertEquals("Tagesschau", channel.name)
        assertEquals("news.de", channel.tvgId)
        assertEquals("News, DE", channel.group)
        assertEquals("https://img.example/news.png", channel.logo)
        assertEquals("https://stream.example/news/index.m3u8", channel.url)
    }

    @Test
    fun `group from EXTGRP is used when group-title is missing and does not leak`() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-name="Sport One",Sport One
            #EXTGRP:Sports
            http://stream.example/sport.ts
            #EXTINF:-1 group-title="News",News One
            http://stream.example/news.ts
            #EXTINF:-1,No group
            http://stream.example/other.ts
        """.trimIndent()
        val channels = M3UParser.parse(text)
        assertEquals("Sports", channels[0].group)
        assertEquals("Sport One", channels[0].name)
        assertEquals("News", channels[1].group)
        assertNull(channels[2].group)
    }

    @Test
    fun `rejects non http stream urls with a reason code`() {
        val text = """
            #EXTM3U
            #EXTINF:-1,Local file
            file:///sdcard/movie.mp4
            #EXTINF:-1,Valid
            https://stream.example/live.ts
        """.trimIndent()
        val parsed = M3UParser.parseWithStats(text)
        assertEquals(1, parsed.channels.size)
        assertEquals("Valid", parsed.channels.single().name)
        assertEquals(2, parsed.stats.received)
        assertEquals(1, parsed.stats.rejected)
        assertEquals(1, parsed.stats.rejectedCount(ImportRejectionCode.INVALID_STREAM_URL))
        assertEquals("INVALID_STREAM_URL", parsed.stats.rejections.single().code)
    }

    @Test
    fun `accepts rtsp rtspu and udp channels with full metadata`() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-id="cam1" tvg-name="Camera One" tvg-logo="https://img.example/cam.png" group-title="Cams",Camera 1
            rtsp://192.168.1.10:554/cam1
            #EXTINF:-1 tvg-id="cam2" group-title="Cams",Camera 2
            rtspu://media.example/live/cam2
            #EXTINF:-1 tvg-id="cam3" group-title="Cams",Camera 3
            udp://@239.255.1.1:5000
        """.trimIndent()
        val parsed = M3UParser.parseWithStats(text)
        assertEquals(3, parsed.channels.size)
        assertEquals(3, parsed.stats.received)
        assertEquals(3, parsed.stats.accepted)
        assertEquals(0, parsed.stats.rejected)
        assertFalse(parsed.stats.truncated)
        assertEquals("rtsp://192.168.1.10:554/cam1", parsed.channels[0].url)
        assertEquals("cam1", parsed.channels[0].tvgId)
        assertEquals("Camera One", parsed.channels[0].tvgName)
        assertEquals("Cams", parsed.channels[0].group)
        assertEquals("https://img.example/cam.png", parsed.channels[0].logo)
        assertEquals("rtspu://media.example/live/cam2", parsed.channels[1].url)
        assertEquals("udp://@239.255.1.1:5000", parsed.channels[2].url)
    }

    @Test
    fun `rejects credentialed and unsafe stream lines but keeps valid rtsp`() {
        val text = """
            #EXTM3U
            rtsp://user:pass@example.com/live
            file:///tmp/movie.mp4
            rtsps://example.com/live
            rtsp://example.com/live
        """.trimIndent()
        val parsed = M3UParser.parseWithStats(text)
        assertEquals(1, parsed.channels.size)
        assertEquals("rtsp://example.com/live", parsed.channels.single().url)
        assertEquals(4, parsed.stats.received)
        assertEquals(3, parsed.stats.rejectedCount(ImportRejectionCode.INVALID_STREAM_URL))
    }

    @Test
    fun `large playlist with rtsp entries is parsed completely without truncation`() {
        val count = 50_000
        val text = buildString(count * 80) {
            append("#EXTM3U\n")
            for (index in 1..count) {
                append("#EXTINF:-1 tvg-id=\"rtsp-$index\" group-title=\"Cameras\",Camera $index\n")
                append("rtsp://camera.example/$index/live\n")
            }
        }
        val result = M3UParser.parseWithStats(text)
        assertEquals(count, result.channels.size)
        assertEquals(count, result.stats.received)
        assertEquals(count, result.stats.accepted)
        assertEquals(0, result.stats.rejected)
        assertFalse(result.stats.truncated)
        assertEquals("rtsp://camera.example/$count/live", result.channels.last().url)
        assertEquals("Camera $count", result.channels.last().name)
        assertEquals("Cameras", result.channels.first().group)
    }

    @Test
    fun `keeps catchup templates and validates logos`() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-logo="javascript:alert(1)" catchup-source="https://cdn.example/timeshift?start={utc}&end={utcend}",Channel
            https://stream.example/live.ts
        """.trimIndent()
        val channel = M3UParser.parse(text).single()
        assertNull(channel.logo)
        assertEquals("https://cdn.example/timeshift?start={utc}&end={utcend}", channel.catchupSource)
    }

    @Test
    fun `missing name falls back to tvg name then placeholder`() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-name="Fallback", 
            https://stream.example/a.ts
            #EXTINF:-1,
            https://stream.example/b.ts
        """.trimIndent()
        val channels = M3UParser.parse(text)
        assertEquals("Fallback", channels[0].name)
        assertEquals("Fallback", channels[0].tvgName)
        assertEquals("Unknown", channels[1].name)
    }

    @Test
    fun `large playlist is parsed completely without silent truncation`() {
        val count = 50_000
        val text = buildString(count * 80) {
            append("#EXTM3U\n")
            for (index in 1..count) {
                append("#EXTINF:-1 tvg-id=\"id-$index\" group-title=\"News\",Channel $index\n")
                append("https://example.com/$index.ts\n")
            }
        }
        val result = M3UParser.parseWithStats(text)
        assertEquals(count, result.channels.size)
        assertEquals(count, result.stats.received)
        assertEquals(count, result.stats.accepted)
        assertEquals(0, result.stats.merged)
        assertFalse(result.stats.truncated)
        assertEquals("News", result.channels.first().group)
        assertEquals("Channel $count", result.channels.last().name)
    }

    @Test
    fun `oversized playlist is reported instead of silently truncated`() {
        val text = "x".repeat(1_000)
        val result = M3UParser.parseWithStats(text, ImportLimits(maxTextLength = 100))
        assertEquals(0, result.channels.size)
        assertTrue(result.stats.truncated)
        assertEquals(1, result.stats.rejectedCount(ImportRejectionCode.PLAYLIST_TOO_LARGE))
        assertTrue(result.stats.rejectionCounts.containsKey("PLAYLIST_TOO_LARGE"))
    }

    @Test
    fun `handles windows line endings and byte order mark`() {
        val text = "\uFEFF#EXTM3U\r\n#EXTINF:-1,One\r\nhttps://stream.example/1.ts\r\n"
        val channels = M3UParser.parse(text)
        assertEquals(1, channels.size)
        assertEquals("One", channels.single().name)
    }

    @Test
    fun `keeps 5000 entries in provider order with their groups`() {
        val lines = StringBuilder("\uFEFF#EXTM3U\r\n")
        repeat(5_000) { index ->
            lines.append("#EXTINF:$index tvg-id=\"id-$index\" group-title=\"Group ${index % 7}\",Channel $index\r\n")
            lines.append("https://stream.example/$index.ts\r\n")
        }
        val parsed = M3UParser.parseWithStats(lines.toString())
        assertEquals(5_000, parsed.channels.size)
        assertEquals(5_000, parsed.stats.received)
        assertEquals(5_000, parsed.stats.accepted)
        assertEquals(0, parsed.stats.rejected)
        assertFalse(parsed.stats.truncated)
        parsed.channels.forEachIndexed { index, channel ->
            assertEquals("Channel $index", channel.name)
            assertEquals("Group ${index % 7}", channel.group)
            assertEquals("id-$index", channel.tvgId)
        }
    }

    @Test
    fun `duplicate url with different channel names and tvg ids is kept`() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-id="one" group-title="News",News HD
            https://stream.example/shared.ts
            #EXTINF:-1 tvg-id="two" group-title="Sport",Sport HD
            https://stream.example/shared.ts
            #EXTINF:-1 group-title="News",News SD
            https://stream.example/shared.ts
        """.trimIndent()
        val channels = M3UParser.parse(text)
        assertEquals(3, channels.size)
        assertEquals(listOf("News HD", "Sport HD", "News SD"), channels.map { it.name })
        assertEquals(listOf("News", "Sport", "News"), channels.map { it.group })
    }

    @Test
    fun `channel numbers come from tvg chno channel id number and leading extinf value`() {
        val text = """
            #EXTM3U
            #EXTINF:7 tvg-chno="1.1" tvg-id="a",First
            https://stream.example/a.ts
            #EXTINF:8 channel-id=22 tvg-id="b",Second
            https://stream.example/b.ts
            #EXTINF:9 "number"="33" tvg-id="c",Third
            https://stream.example/c.ts
            #EXTINF:44 tvg-id="d",Fourth
            https://stream.example/d.ts
        """.trimIndent()
        val channels = M3UParser.parse(text)
        assertEquals("1.1", channels[0].channelNumber)
        assertEquals("22", channels[1].channelNumber)
        assertEquals("33", channels[2].channelNumber)
        assertEquals("44", channels[3].channelNumber)
    }

    @Test
    fun `unquoted attributes and quality are parsed`() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-id=unq tvg-name=Stable group-title=News quality=HD,Display
            https://stream.example/unquoted.ts
        """.trimIndent()
        val channel = M3UParser.parse(text).single()
        assertEquals("unq", channel.tvgId)
        assertEquals("Stable", channel.tvgName)
        assertEquals("News", channel.group)
        assertEquals("HD", channel.quality)
    }

    @Test
    fun `gzip payload passed as raw text is inflated`() {
        val plain = "#EXTM3U\r\n#EXTINF:-1,Gzip Channel\r\nhttps://stream.example/gzip.ts\r\n"
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(plain.toByteArray(Charsets.UTF_8)) }
        val rawText = out.toByteArray().toString(Charsets.ISO_8859_1)
        val parsed = M3UParser.parseWithStats(rawText)
        assertTrue(parsed.stats.gzipDecoded)
        assertEquals(1, parsed.channels.size)
        assertEquals("Gzip Channel", parsed.channels.single().name)
    }

    @Test
    fun `gzip bytes with byte order mark are decoded through the bounded decoder`() {
        val plain = "\uFEFF#EXTM3U\n#EXTINF:-1,BOM Gzip\nhttps://stream.example/bom.ts\n"
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(plain.toByteArray(Charsets.UTF_8)) }
        val parsed = M3UParser.parseWithStats(out.toByteArray())
        assertEquals(1, parsed.channels.size)
        assertEquals("BOM Gzip", parsed.channels.single().name)
    }

    @Test
    fun `channel limit is reported`() {
        val text = (1..5).joinToString("\n") { "#EXTINF:-1,Channel $it\nhttps://stream.example/$it.ts" }
        val parsed = M3UParser.parseWithStats(text, ImportLimits(maxChannels = 2))
        assertEquals(2, parsed.channels.size)
        assertTrue(parsed.stats.truncated)
        assertEquals(1, parsed.stats.rejectedCount(ImportRejectionCode.TOO_MANY_CHANNELS))
        assertEquals("TOO_MANY_CHANNELS", parsed.stats.rejectionCounts.keys.first())
    }

    @Test
    fun `overlong lines are rejected and reported without stopping the import`() {
        val longUrl = "https://stream.example/" + "a".repeat(64) + ".ts"
        val text = "https://a.co/2\n$longUrl\nhttps://a.co/1\n"
        val parsed = M3UParser.parseWithStats(text, ImportLimits(maxLineLength = 20))
        assertEquals(2, parsed.channels.size)
        assertEquals(1, parsed.stats.rejectedCount(ImportRejectionCode.LINE_TOO_LONG))
        assertFalse(parsed.stats.truncated)
    }

    @Test
    fun `line limit is reported`() {
        val text = (1..20).joinToString("\n") { "https://stream.example/$it.ts" }
        val parsed = M3UParser.parseWithStats(text, ImportLimits(maxLines = 3))
        assertTrue(parsed.stats.truncated)
        assertEquals(1, parsed.stats.rejectedCount(ImportRejectionCode.TOO_MANY_LINES))
    }

    @Test
    fun `content sniff accepts m3u m3u8 bom and gzip without trusting provider mime`() {
        val m3u = "#EXTM3U\n#EXTINF:-1,One\nhttps://stream.example/1.ts\n".toByteArray(Charsets.UTF_8)
        val m3u8 = "\uFEFF#EXTM3U\r\n#EXTINF:-1,One\r\nhttps://stream.example/1.m3u8\r\n"
            .toByteArray(Charsets.UTF_8)
        val plain = "#EXTINF:-1,One\nhttps://stream.example/1.ts\n".toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(m3u) }
        assertTrue(M3UParser.looksLikePlaylist(m3u))
        assertTrue(M3UParser.looksLikePlaylist(m3u8))
        assertTrue(M3UParser.looksLikePlaylist(plain))
        assertTrue("gzip payload must not be rejected on a generic MIME type", M3UParser.looksLikePlaylist(out.toByteArray()))
    }

    @Test
    fun `content sniff rejects empty binary and unstructured files`() {
        assertFalse(M3UParser.looksLikePlaylist(ByteArray(0)))
        assertFalse(M3UParser.looksLikePlaylist("just some random text".toByteArray(Charsets.UTF_8)))
        assertFalse(
            M3UParser.looksLikePlaylist(
                byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4e.toByte(), 0x47.toByte(), 0, 0, 0, 0x0d.toByte()),
            ),
        )
        assertFalse(M3UParser.looksLikePlaylist("#EXTM3U\n# no entries\n".toByteArray(Charsets.UTF_8)))
    }
}
