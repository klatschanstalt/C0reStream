package pw.c0re.stream.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun `group from EXTGRP is used when group-title is missing`() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-name="Sport One",Sport One
            #EXTGRP:Sports
            http://stream.example/sport.ts
        """.trimIndent()
        val channel = M3UParser.parse(text).single()
        assertEquals("Sports", channel.group)
        assertEquals("Sport One", channel.name)
    }

    @Test
    fun `rejects non http stream urls`() {
        val text = """
            #EXTM3U
            #EXTINF:-1,Local file
            file:///sdcard/movie.mp4
            #EXTINF:-1,Valid
            https://stream.example/live.ts
        """.trimIndent()
        val channels = M3UParser.parse(text)
        assertEquals(1, channels.size)
        assertEquals("Valid", channels.single().name)
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
        assertEquals("Unknown", channels[1].name)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `oversized playlist is rejected`() {
        val text = "x".repeat(32 * 1024 * 1024 + 1)
        M3UParser.parse(text)
    }

    @Test
    fun `handles windows line endings and byte order mark`() {
        val text = "\uFEFF#EXTM3U\r\n#EXTINF:-1,One\r\nhttps://stream.example/1.ts\r\n"
        val channels = M3UParser.parse(text)
        assertEquals(1, channels.size)
        assertTrue(channels.single().name == "One")
    }
}
