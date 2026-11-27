package pw.c0re.stream.data

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import pw.c0re.stream.data.model.EpgEntry
import java.io.Reader
import java.io.StringReader
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/**
 * Hardened XMLTV parser. DTDs are rejected, entity expansion is bounded, text and record
 * counts are limited, and missing stop times are filled from the next programme.
 */
object EpgParser {
    private const val maxProgrammes = 250_000
    private const val maxProgrammesPerChannel = 20_000
    private const val maxTitle = 8_192
    private const val maxDescription = 65_536
    private const val maxDepth = 64

    private val timeFormat = DateTimeFormatter
        .ofPattern("uuuuMMddHHmmss", Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT)
    private val timePattern = Regex("([0-9]{14})(?:\\s*([+-][0-9]{2}:?[0-9]{2}|Z))?")

    fun parse(text: String): Map<String, List<EpgEntry>> = parse(StringReader(text))

    fun parse(reader: Reader): Map<String, List<EpgEntry>> {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        // Do not process document declarations or external entities. Some platform parsers do
        // not expose the feature; the DOCDECL event check below is the second line of defence.
        runCatching { parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false) }
        parser.setInput(reader)

        val map = LinkedHashMap<String, MutableList<EpgEntry>>()
        var channelId: String? = null
        var start = 0L
        var stop = 0L
        val title = StringBuilder()
        val description = StringBuilder()
        var inProgramme = false
        var inTitle = false
        var inDescription = false
        var depth = 0
        var count = 0

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.DOCDECL -> error("DTD not allowed")
                XmlPullParser.START_TAG -> {
                    check(++depth <= maxDepth) { "XML nesting too deep" }
                    when (parser.name) {
                        "programme" -> {
                            channelId = parser.getAttributeValue(null, "channel")?.take(256)
                            inProgramme = true
                            start = parseXmltvTime(parser.getAttributeValue(null, "start").orEmpty())
                            stop = parseXmltvTime(parser.getAttributeValue(null, "stop").orEmpty())
                            title.setLength(0)
                            description.setLength(0)
                        }
                        "title" -> if (inProgramme) inTitle = true
                        "desc" -> if (inProgramme) inDescription = true
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> {
                    val text = parser.text
                    if (text != null) {
                        if (inTitle) {
                            check(title.length + text.length <= maxTitle) { "EPG title too long" }
                            title.append(text)
                        }
                        if (inDescription) {
                            check(description.length + text.length <= maxDescription) { "EPG description too long" }
                            description.append(text)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    depth = (depth - 1).coerceAtLeast(0)
                    when (parser.name) {
                        "title" -> inTitle = false
                        "desc" -> inDescription = false
                        "programme" -> {
                            val id = channelId
                            if (inProgramme && !id.isNullOrBlank() && start > 0) {
                                check(++count <= maxProgrammes) { "Too many EPG entries" }
                                val entries = map.getOrPut(id) { mutableListOf() }
                                check(entries.size < maxProgrammesPerChannel) { "Too many EPG entries for channel" }
                                entries += EpgEntry(
                                    start = start,
                                    stop = if (stop > start) stop else 0L,
                                    title = title.toString().trim().take(maxTitle).ifBlank { "Unknown" },
                                    description = description.toString().trim().take(maxDescription).ifBlank { null },
                                )
                            }
                            inProgramme = false
                            channelId = null
                        }
                    }
                }
            }
            event = parser.nextToken()
        }
        return map.mapValues { (_, entries) -> normalize(entries) }
    }

    private fun normalize(entries: List<EpgEntry>): List<EpgEntry> {
        val sorted = entries.sortedBy { it.start }.distinctBy { it.start }
        return sorted.mapIndexed { index, entry ->
            val nextStart = sorted.getOrNull(index + 1)?.start
            if (entry.stop > entry.start) entry
            else entry.copy(stop = nextStart?.takeIf { it > entry.start } ?: (entry.start + 30 * 60_000L))
        }
    }

    fun parseXmltvTime(value: String): Long {
        val match = timePattern.matchEntire(value.trim()) ?: return 0L
        val offset = when (val raw = match.groupValues[2]) {
            "", "Z" -> ZoneOffset.UTC
            else -> runCatching {
                if (raw.contains(':')) ZoneOffset.of(raw)
                else ZoneOffset.of(raw.substring(0, 3) + ":" + raw.substring(3))
            }.getOrDefault(ZoneOffset.UTC)
        }
        return runCatching {
            LocalDateTime.parse(match.groupValues[1], timeFormat).toInstant(offset).toEpochMilli()
        }.getOrDefault(0L)
    }
}
