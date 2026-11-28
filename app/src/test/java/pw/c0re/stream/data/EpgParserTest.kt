package pw.c0re.stream.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader
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

    @Test
    fun `classifies valid explicit missing and corrupt offsets separately`() {
        assertEquals(XmltvTimeStatus.EXPLICIT_UTC, EpgParser.parseXmltvTimeDetailed("20260101120000Z").status)
        assertEquals(XmltvTimeStatus.VALID_OFFSET, EpgParser.parseXmltvTimeDetailed("20260101120000 +0100").status)
        assertEquals(XmltvTimeStatus.VALID_OFFSET, EpgParser.parseXmltvTimeDetailed("20260101120000 -05:30").status)
        assertEquals(XmltvTimeStatus.MISSING_OFFSET, EpgParser.parseXmltvTimeDetailed("20260101120000").status)
        assertEquals(XmltvTimeStatus.INVALID_OFFSET_SYNTAX, EpgParser.parseXmltvTimeDetailed("20260101120000 +23:99").status)
        assertEquals(XmltvTimeStatus.INVALID_OFFSET_SYNTAX, EpgParser.parseXmltvTimeDetailed("20260101120000 +2400").status)
        assertEquals(XmltvTimeStatus.UNSUPPORTED_OFFSET, EpgParser.parseXmltvTimeDetailed("20260101120000 +1900").status)
        assertEquals(XmltvTimeStatus.INVALID_DATE, EpgParser.parseXmltvTimeDetailed("20261301120000 +0000").status)

        // A corrupt offset must never be reinterpreted as a plausible UTC timestamp.
        assertEquals(0L, EpgParser.parseXmltvTime("20260101120000 +23:99"))
        assertEquals(0L, EpgParser.parseXmltvTime("20260101120000 +2400"))
        assertEquals(0L, EpgParser.parseXmltvTime("20260101120000 +1900"))
        assertEquals(0L, EpgParser.parseXmltvTime("20260101120000 +01"))
        assertFalse(EpgParser.parseXmltvTimeDetailed("20260101120000 +2400").accepted)
    }

    @Test
    fun `honours real world offset boundaries`() {
        assertEquals(
            Instant.parse("2026-01-01T08:00:00Z").toEpochMilli(),
            EpgParser.parseXmltvTime("20260101220000 +14:00"),
        )
        assertEquals(
            Instant.parse("2026-01-02T00:00:00Z").toEpochMilli(),
            EpgParser.parseXmltvTime("20260101120000 -12:00"),
        )
        assertEquals(XmltvTimeStatus.VALID_OFFSET, EpgParser.parseXmltvTimeDetailed("20260101220000 +1400").status)
        assertEquals(XmltvTimeStatus.UNSUPPORTED_OFFSET, EpgParser.parseXmltvTimeDetailed("20260101220000 +1800").status)
        assertEquals(XmltvTimeStatus.UNSUPPORTED_OFFSET, EpgParser.parseXmltvTimeDetailed("20260101120000 -1800").status)
    }

    @Test
    fun `skips corrupt entries without aborting the rest of the import`() {
        val xml = """
            <tv>
              <programme channel="c" start="20260101120000 +2400" stop="20260101130000 Z"><title>Bad offset</title></programme>
              <programme channel="c" start="20260101130000 Z" stop="20260101140000 Z"><title>Good</title></programme>
              <programme channel="c" start="20260101140000 +23:99"><title>Bad minutes</title></programme>
            </tv>
        """.trimIndent()
        val result = EpgParser.parseWithDiagnostics(StringReader(xml))

        assertEquals(listOf("Good"), result.entries.getValue("c").map { it.title })
        assertEquals(2, result.diagnostics.skippedProgrammes)
        assertTrue(result.diagnostics.timeStatuses.getValue(XmltvTimeStatus.INVALID_OFFSET_SYNTAX) >= 2)
        assertEquals(0, result.diagnostics.invalidStops)
        assertEquals(listOf("Good"), EpgParser.parse(xml).getValue("c").map { it.title })
    }

    @Test
    fun `keeps missing offsets accepted but traceable`() {
        val xml = """
            <tv><programme channel="c" start="20260101120000"><title>Local time</title></programme></tv>
        """.trimIndent()
        val result = EpgParser.parseWithDiagnostics(StringReader(xml))
        val entry = result.entries.getValue("c").single()
        assertEquals(Instant.parse("2026-01-01T12:00:00Z").toEpochMilli(), entry.start)
        assertEquals(1, result.diagnostics.timeStatuses.getValue(XmltvTimeStatus.MISSING_OFFSET))
    }

    @Test
    fun `invalid stop is filled from the next programme instead of becoming a valid time`() {
        val xml = """
            <tv>
              <programme channel="c" start="20260101120000 Z" stop="20260101139999 Z"><title>A</title></programme>
              <programme channel="c" start="20260101130000 Z"><title>B</title></programme>
            </tv>
        """.trimIndent()
        val result = EpgParser.parseWithDiagnostics(StringReader(xml))
        val entries = result.entries.getValue("c")

        assertEquals(1, result.diagnostics.invalidStops)
        assertEquals(entries[0].start + 60 * 60_000L, entries[0].stop)
    }

    @Test
    fun `excessive corrupt timestamps abort the import in a bounded way`() {
        val xml = buildString {
            append("<tv>")
            repeat(1_001) {
                append("<programme channel=\"c\" start=\"bad\"><title>X</title></programme>")
            }
            append("</tv>")
        }
        assertThrows(IllegalStateException::class.java) { EpgParser.parse(xml) }
    }
}
