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

/** Outcome of an XMLTV start/stop timestamp. Corrupt values are never treated as UTC. */
enum class XmltvTimeStatus {
    /** An explicit numeric offset, e.g. +0100 or -05:30. */
    VALID_OFFSET,

    /** Explicit XMLTV "Z" (UTC). */
    EXPLICIT_UTC,

    /** No offset in the source: valid XMLTV local time, interpreted as UTC for compatibility. */
    MISSING_OFFSET,

    /** A suffix is present but is not a well-formed XMLTV offset, e.g. +23:99 or +2400. */
    INVALID_OFFSET_SYNTAX,

    /** Well-formed offset that cannot be used (outside the supported +/-14:00 range). */
    UNSUPPORTED_OFFSET,

    /** Malformed date/time or an impossible calendar date. */
    INVALID_DATE,
}

data class XmltvTimeParse(
    val epochMillis: Long,
    val status: XmltvTimeStatus,
) {
    val accepted: Boolean
        get() = status == XmltvTimeStatus.VALID_OFFSET ||
            status == XmltvTimeStatus.EXPLICIT_UTC ||
            status == XmltvTimeStatus.MISSING_OFFSET
}

/** Bounded, value-free counters for one import; no URLs or titles are logged. */
data class EpgParseDiagnostics(
    val skippedProgrammes: Int = 0,
    val invalidStops: Int = 0,
    val timeStatuses: Map<XmltvTimeStatus, Int> = emptyMap(),
)

data class EpgParseResult(
    val entries: Map<String, List<EpgEntry>>,
    val diagnostics: EpgParseDiagnostics,
)

/**
 * Hardened XMLTV parser. DTDs are rejected, entity expansion is bounded, text and record
 * counts are limited, and missing stop times are filled from the next programme. Individual
 * programmes with corrupt timestamps are skipped; only an excessive number of corrupt values
 * aborts the import so a damaged feed cannot quietly produce seemingly valid EPG times.
 */
object EpgParser {
    private const val maxProgrammes = 250_000
    private const val maxProgrammesPerChannel = 20_000
    private const val maxInvalidTimestamps = 1_000
    private const val maxTitle = 8_192
    private const val maxDescription = 65_536
    private const val maxDepth = 64
    private const val maxOffsetMinutes = 14 * 60

    private val timeFormat = DateTimeFormatter
        .ofPattern("uuuuMMddHHmmss", Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT)

    // Hours 00..23 and minutes 00..59. Values such as +2400 or +23:99 fail here rather than
    // being silently reinterpreted as a different (or zero) offset.
    private val offsetSyntax = Regex("[+-](?:[01][0-9]|2[0-3]):?[0-5][0-9]")

    fun parse(text: String): Map<String, List<EpgEntry>> = parse(StringReader(text))

    fun parse(reader: Reader): Map<String, List<EpgEntry>> {
        val result = parseWithDiagnostics(reader)
        val corrupt = result.diagnostics.timeStatuses
            .filterKeys { it == XmltvTimeStatus.INVALID_OFFSET_SYNTAX ||
                it == XmltvTimeStatus.UNSUPPORTED_OFFSET || it == XmltvTimeStatus.INVALID_DATE }
            .values.sum()
        if (corrupt > 0 || result.diagnostics.skippedProgrammes > 0 || result.diagnostics.invalidStops > 0) {
            AppLog.log(
                "EPG",
                "Skipped ${result.diagnostics.skippedProgrammes} programmes (missing channel or corrupt timestamp); " +
                    "$corrupt invalid offsets or dates; ${result.diagnostics.invalidStops} invalid stop times",
            )
        }
        return result.entries
    }

    /** Diagnostic variant used by tests and callers that want to surface bad feed entries. */
    fun parseWithDiagnostics(reader: Reader): EpgParseResult {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        // Do not process document declarations or external entities. Some platform parsers do
        // not expose the feature; the DOCDECL event check below is the second line of defence.
        runCatching { parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false) }
        parser.setInput(reader)

        val map = LinkedHashMap<String, MutableList<EpgEntry>>()
        val timeStatuses = LinkedHashMap<XmltvTimeStatus, Int>()
        var invalidTimestamps = 0
        var skippedProgrammes = 0
        var invalidStops = 0
        var channelId: String? = null
        var start = 0L
        var stop = 0L
        var stopInvalid = false
        val title = StringBuilder()
        val description = StringBuilder()
        var inProgramme = false
        var inTitle = false
        var inDescription = false
        var depth = 0
        var count = 0

        fun recordTime(status: XmltvTimeStatus) {
            timeStatuses[status] = (timeStatuses[status] ?: 0) + 1
            if (status == XmltvTimeStatus.INVALID_OFFSET_SYNTAX ||
                status == XmltvTimeStatus.UNSUPPORTED_OFFSET ||
                status == XmltvTimeStatus.INVALID_DATE
            ) {
                check(++invalidTimestamps <= maxInvalidTimestamps) { "Too many invalid EPG timestamps" }
            }
        }

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
                            val startTime = parseXmltvTimeDetailed(parser.getAttributeValue(null, "start").orEmpty())
                            val rawStop = parser.getAttributeValue(null, "stop")
                            val stopTime = parseXmltvTimeDetailed(rawStop.orEmpty())
                            start = startTime.epochMillis
                            stop = stopTime.epochMillis
                            stopInvalid = !rawStop.isNullOrBlank() && !stopTime.accepted
                            recordTime(startTime.status)
                            if (!rawStop.isNullOrBlank()) recordTime(stopTime.status)
                            if (stopInvalid) check(++invalidStops <= maxInvalidTimestamps) {
                                "Too many invalid EPG timestamps"
                            }
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
                                    // An invalid stop is treated as missing, never as a valid time.
                                    stop = if (stop > start) stop else 0L,
                                    title = title.toString().trim().take(maxTitle).ifBlank { "Unknown" },
                                    description = description.toString().trim().take(maxDescription).ifBlank { null },
                                )
                            } else if (inProgramme) {
                                skippedProgrammes++
                            }
                            inProgramme = false
                            channelId = null
                        }
                    }
                }
            }
            event = parser.nextToken()
        }
        val entries = map.mapValues { (_, entries) -> normalize(entries) }
        return EpgParseResult(
            entries,
            EpgParseDiagnostics(skippedProgrammes, invalidStops, timeStatuses.toMap()),
        )
    }

    private fun normalize(entries: List<EpgEntry>): List<EpgEntry> {
        val sorted = entries.sortedBy { it.start }.distinctBy { it.start }
        return sorted.mapIndexed { index, entry ->
            val nextStart = sorted.getOrNull(index + 1)?.start
            if (entry.stop > entry.start) entry
            else entry.copy(stop = nextStart?.takeIf { it > entry.start } ?: (entry.start + 30 * 60_000L))
        }
    }

    fun parseXmltvTime(value: String): Long = parseXmltvTimeDetailed(value).epochMillis

    /**
     * Classifies an XMLTV timestamp before converting it. A missing offset is accepted as UTC
     * (the legacy local-as-UTC behaviour); a present but malformed or unsupported offset yields
     * no epoch value, so a corrupt entry cannot masquerade as a valid EPG time.
     */
    fun parseXmltvTimeDetailed(value: String): XmltvTimeParse {
        val trimmed = value.trim()
        val dateText = trimmed.take(14)
        if (dateText.length != 14 || !dateText.all { it in '0'..'9' }) {
            return XmltvTimeParse(0L, XmltvTimeStatus.INVALID_DATE)
        }
        val suffix = trimmed.substring(14).trim()
        val offset: ZoneOffset
        val status: XmltvTimeStatus
        when {
            suffix.isEmpty() -> {
                offset = ZoneOffset.UTC
                status = XmltvTimeStatus.MISSING_OFFSET
            }
            suffix == "Z" -> {
                offset = ZoneOffset.UTC
                status = XmltvTimeStatus.EXPLICIT_UTC
            }
            !offsetSyntax.matches(suffix) -> {
                return XmltvTimeParse(0L, XmltvTimeStatus.INVALID_OFFSET_SYNTAX)
            }
            else -> {
                val hours = suffix.substring(1, 3).toInt()
                val minutes = suffix.takeLast(2).toInt()
                val parsed = runCatching {
                    if (suffix.contains(':')) ZoneOffset.of(suffix)
                    else ZoneOffset.of(suffix.substring(0, 3) + ":" + suffix.substring(3))
                }.getOrNull()
                if (parsed == null || hours * 60 + minutes > maxOffsetMinutes) {
                    return XmltvTimeParse(0L, XmltvTimeStatus.UNSUPPORTED_OFFSET)
                }
                offset = parsed
                status = XmltvTimeStatus.VALID_OFFSET
            }
        }
        val epoch = runCatching {
            LocalDateTime.parse(dateText, timeFormat).toInstant(offset).toEpochMilli()
        }.getOrNull() ?: return XmltvTimeParse(0L, XmltvTimeStatus.INVALID_DATE)
        return XmltvTimeParse(epoch, status)
    }
}
