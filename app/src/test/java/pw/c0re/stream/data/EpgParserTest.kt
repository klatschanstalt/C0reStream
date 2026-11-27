package pw.c0re.stream.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class EpgParserTest {

    @Test
    fun `parses programmes with timezone offsets`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <programme channel="news.de" start="20260101120000 +0100" stop="20260101130000 +0100">
                <title>Mittag</title>
                <desc>Nachrichten</desc>
              </programme>
            </tv>
        """.trimIndent()
        val map = EpgParser.parse(xml)
        val entry = map.getValue("news.de").single()
        assertEquals(Instant.parse("2026-01-01T11:00:00Z").toEpochMilli(), entry.start)
        assertEquals(Instant.parse("2026-01-01T12:00:00Z").toEpochMilli(), entry.stop)
        assertEquals("Mittag", entry.title)
        assertEquals("Nachrichten", entry.description)
    }

    @Test
    fun `fills missing stop from the next programme`() {
        val xml = """
            <tv>
              <programme channel="c" start="20260101120000 +0000"><title>A</title></programme>
              <programme channel="c" start="20260101130000 +0000"><title>B</title></programme>
            </tv>
        """.trimIndent()
        val entries = EpgParser.parse(xml).getValue("c")
        assertEquals(2, entries.size)
        assertEquals(entries[0].start + 60 * 60_000L, entries[0].stop)
        assertEquals(entries[1].start + 30 * 60_000L, entries[1].stop)
    }

    @Test
    fun `duplicate start times are collapsed and sorted`() {
        val xml = """
            <tv>
              <programme channel="c" start="20260101130000 +0000" stop="20260101140000 +0000"><title>Late</title></programme>
              <programme channel="c" start="20260101120000 +0000" stop="20260101130000 +0000"><title>Early</title></programme>
              <programme channel="c" start="20260101120000 +0000" stop="20260101130000 +0000"><title>Duplicate</title></programme>
            </tv>
        """.trimIndent()
        val entries = EpgParser.parse(xml).getValue("c")
        assertEquals(listOf("Early", "Late"), entries.map { it.title })
    }

    @Test
    fun `rejects DTD declarations`() {
        val xml = """
            <?xml version="1.0"?>
            <!DOCTYPE tv [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <tv><programme channel="c" start="20260101120000 +0000"><title>&xxe;</title></programme></tv>
        """.trimIndent()
        val failure = runCatching { EpgParser.parse(xml) }.exceptionOrNull()
        assertTrue("Expected DTD rejection", failure != null)
    }

    @Test
    fun `skips programmes without channel or start`() {
        val xml = """
            <tv>
              <programme start="20260101120000 +0000"><title>No channel</title></programme>
              <programme channel="c"><title>No start</title></programme>
            </tv>
        """.trimIndent()
        assertTrue(EpgParser.parse(xml).isEmpty())
    }

    @Test
    fun `parses xmltv time formats`() {
        assertEquals(0L, EpgParser.parseXmltvTime("not a date"))
        assertEquals(0L, EpgParser.parseXmltvTime("20261301120000 +0000"))
        assertEquals(
            Instant.parse("2026-01-01T12:00:00Z").toEpochMilli(),
            EpgParser.parseXmltvTime("20260101120000"),
        )
        assertEquals(
            Instant.parse("2026-01-01T11:30:00Z").toEpochMilli(),
            EpgParser.parseXmltvTime("20260101120000 +0030"),
        )
    }
}
