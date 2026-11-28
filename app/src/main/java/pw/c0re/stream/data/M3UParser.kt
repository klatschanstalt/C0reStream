package pw.c0re.stream.data

import java.util.Locale

/** Machine-readable reason codes exposed to callers instead of silent truncation/omission. */
enum class ImportRejectionCode(val code: String) {
    PLAYLIST_TOO_LARGE("PLAYLIST_TOO_LARGE"),
    TOO_MANY_LINES("TOO_MANY_LINES"),
    LINE_TOO_LONG("LINE_TOO_LONG"),
    TOO_MANY_CHANNELS("TOO_MANY_CHANNELS"),
    INVALID_STREAM_URL("INVALID_STREAM_URL"),
    GZIP_DECODE_FAILED("GZIP_DECODE_FAILED"),
    GZIP_TOO_LARGE("GZIP_TOO_LARGE"),
    SHRINK_GUARD("SHRINK_GUARD"),
    DUPLICATE_KEY_COLLISION("DUPLICATE_KEY_COLLISION"),
    XTREAM_RECORD_LIMIT("XTREAM_RECORD_LIMIT"),
    XTREAM_PAGINATION_UNSUPPORTED("XTREAM_PAGINATION_UNSUPPORTED"),
    XTREAM_CATEGORIES_UNAVAILABLE("XTREAM_CATEGORIES_UNAVAILABLE"),
    XTREAM_INVALID_RECORD("XTREAM_INVALID_RECORD"),
}

/** One bounded rejection entry. [detail] never contains a URL or credential. */
data class ImportRejection(
    val code: String,
    val line: Int = 0,
    val detail: String? = null,
)

/** Explicit, caller-overridable import limits. */
data class ImportLimits(
    val maxTextLength: Int = 32 * 1024 * 1024,
    val maxLines: Int = 200_000,
    val maxChannels: Int = 100_000,
    val maxLineLength: Int = 64 * 1024,
    val maxRejectionDetails: Int = 1_000,
    val maxDecompressedBytes: Long = 64L * 1024 * 1024,
)

/**
 * Counts for one import. [received] counts candidate channel entries, [accepted] entries kept,
 * [merged] exact duplicates folded into an existing entry, [rejected] entries dropped with a
 * reason. [truncated] is set whenever a security limit stopped the import early, so nothing is
 * silently capped.
 */
data class ImportStats(
    val received: Int = 0,
    val accepted: Int = 0,
    val merged: Int = 0,
    val rejected: Int = 0,
    val rejections: List<ImportRejection> = emptyList(),
    val limits: ImportLimits = ImportLimits(),
    val truncated: Boolean = false,
    val gzipDecoded: Boolean = false,
    val rejectionDetailsTruncated: Boolean = false,
    val collisions: Int = 0,
    val preservedIds: Int = 0,
) {
    val rejectionCounts: Map<String, Int> get() = rejections.groupingBy { it.code }.eachCount()

    fun rejectedCount(code: ImportRejectionCode): Int = rejectionCounts[code.code] ?: 0

    fun withRejection(rejection: ImportRejection, incrementRejected: Boolean = true): ImportStats = copy(
        rejected = rejected + if (incrementRejected) 1 else 0,
        rejections = if (rejections.size < limits.maxRejectionDetails) rejections + rejection else rejections,
        rejectionDetailsTruncated = rejectionDetailsTruncated ||
            rejections.size >= limits.maxRejectionDetails,
    )

    fun plus(other: ImportStats): ImportStats = ImportStats(
        received = received + other.received,
        accepted = accepted + other.accepted,
        merged = merged + other.merged,
        rejected = rejected + other.rejected,
        rejections = (rejections + other.rejections).take(limits.maxRejectionDetails),
        limits = limits,
        truncated = truncated || other.truncated,
        gzipDecoded = gzipDecoded || other.gzipDecoded,
        rejectionDetailsTruncated = rejectionDetailsTruncated || other.rejectionDetailsTruncated,
        collisions = collisions + other.collisions,
        preservedIds = preservedIds + other.preservedIds,
    )
}

/** Parser result with the ordered channels and explicit import accounting. */
data class M3uParseResult(
    val channels: List<M3UParser.RawChannel>,
    val stats: ImportStats,
)

/**
 * Extended M3U/M3U8 parser with bounded input, quoted or unquoted attributes, catch-up metadata,
 * BOM/CRLF handling and gzip payload support. HTTP(S), rtsp://, rtspu:// and udp:// stream URLs
 * are accepted; logos and catch-up sources remain HTTP(S)-only.
 *
 * Provider order is never changed and stream variants are never merged here; duplicates are
 * accounted for by [PlaylistRepository.planImport] so a merge is always visible in [ImportStats].
 */
object M3UParser {
    private const val unknownName = "Unknown"
    private val leadingNumber = Regex("^(-?\\d+(?:\\.\\d+)?)(?=\\s|$)")

    data class RawChannel(
        val name: String,
        val logo: String?,
        val group: String?,
        val tvgId: String?,
        val url: String,
        val catchupSource: String? = null,
        /** Stable provider identity; preferred over the display name for channel keys. */
        val tvgName: String? = null,
        /** tvg-chno / channel-id / number / #EXTINF leading number, as provided. */
        val channelNumber: String? = null,
        /** Optional quality hint used to keep HD/SD variants apart. */
        val quality: String? = null,
    )

    /** Backwards-compatible entry point; use [parseWithStats] to see limits and rejections. */
    fun parse(text: String): List<RawChannel> = parseWithStats(text).channels

    /**
     * Content-based format check for locally picked files. Document providers frequently report
     * `application/octet-stream`, the all-files wildcard type or another generic MIME type for
     * valid M3U/M3U8 files, so
     * the provider's type is never trusted. Gzip payloads are accepted here and fully validated by
     * [parseWithStats]; empty, binary or unstructured input is rejected before anything is stored.
     */
    fun looksLikePlaylist(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) return true
        // A NUL byte in the header is a reliable indicator of binary (e.g. image) content.
        val head = bytes.copyOf(minOf(bytes.size, 4_096))
        if (head.any { it == 0.toByte() }) return false
        val text = stripBom(String(head, Charsets.UTF_8))
        val firstEntry = text.lineSequence()
            .map { stripBom(it).trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
            ?: return false
        return NetworkPolicy.parseStreamUrl(firstEntry) != null
    }

    /** Parses text, including text that holds an undecoded gzip payload. */
    fun parseWithStats(text: String, limits: ImportLimits = ImportLimits()): M3uParseResult {
        val builder = StatsBuilder(limits)
        var content = stripBom(text)
        val gzipAsText = hasGzipMagic(content)
        // A payload decoded with UTF-8 can contain replacement characters; only attempt the
        // byte-level recovery when the first character still is the gzip magic byte.
        val gzipAsUtf8 = !gzipAsText && content.length >= 2 && content[0].code == 0x1f
        if (gzipAsText || gzipAsUtf8) {
            val decoded = runCatching {
                if (gzipAsText) {
                    decodeGzipText(content, limits.maxDecompressedBytes)
                } else {
                    decodeGzipBytes(content.toByteArray(Charsets.UTF_8), limits.maxDecompressedBytes)
                }
            }
            decoded.exceptionOrNull()?.let { error ->
                val code = if (error.message?.contains("Decompressed") == true) {
                    ImportRejectionCode.GZIP_TOO_LARGE
                } else {
                    ImportRejectionCode.GZIP_DECODE_FAILED
                }
                builder.reject(code, 0)
            }
            val value = decoded.getOrNull()
            if (value != null) {
                content = stripBom(value)
                builder.gzipDecoded = true
            }
        }
        if (content.length > limits.maxTextLength) {
            builder.reject(
                ImportRejectionCode.PLAYLIST_TOO_LARGE,
                0,
                "limit=${limits.maxTextLength}",
            )
            builder.truncated = true
            content = content.substring(0, limits.maxTextLength)
        }

        val result = ArrayList<RawChannel>()
        var pending: Pending? = null
        var pendingGroup: String? = null
        var lineNumber = 0

        for (rawLine in content.lineSequence()) {
            lineNumber++
            if (lineNumber > limits.maxLines) {
                builder.reject(ImportRejectionCode.TOO_MANY_LINES, lineNumber)
                builder.truncated = true
                break
            }
            val line = stripBom(rawLine).trim()
            if (line.length > limits.maxLineLength) {
                builder.reject(ImportRejectionCode.LINE_TOO_LONG, lineNumber)
                pending = null
                pendingGroup = null
                continue
            }
            when {
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val parsed = parseExtInf(line.substring(8))
                    pending = Pending(
                        attributes = parsed.first,
                        name = parsed.second,
                        channelNumber = parsed.third,
                        group = pendingGroup,
                    )
                    pendingGroup = null
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    val group = line.substring(8).trim().ifBlank { null }
                    if (pending != null) pending = pending.copy(group = group) else pendingGroup = group
                }
                line.startsWith("#") -> Unit
                line.isNotEmpty() -> {
                    builder.received++
                    if (NetworkPolicy.parseStreamUrl(line) == null) {
                        builder.reject(ImportRejectionCode.INVALID_STREAM_URL, lineNumber)
                        pending = null
                        pendingGroup = null
                        continue
                    }
                    if (result.size >= limits.maxChannels) {
                        builder.reject(ImportRejectionCode.TOO_MANY_CHANNELS, lineNumber)
                        builder.truncated = true
                        break
                    }
                    val attributes = pending?.attributes.orEmpty()
                    val name = (pending?.name ?: attributes["tvg-name"]?.trim()?.ifBlank { null })
                        ?: unknownName
                    val logo = attributes["tvg-logo"]?.takeIf { NetworkPolicy.parse(it) != null }
                    val catchup = attributes["catchup-source"]
                        ?.takeIf { NetworkPolicy.isTemplateUrl(it) || NetworkPolicy.parse(it) != null }
                    result += RawChannel(
                        name = name.trim().take(512),
                        logo = logo,
                        group = (attributes["group-title"]?.takeIf { it.isNotBlank() } ?: pending?.group)
                            ?.trim()?.take(512),
                        tvgId = attributes["tvg-id"]?.trim()?.take(256)?.ifBlank { null },
                        url = line,
                        catchupSource = catchup,
                        tvgName = attributes["tvg-name"]?.trim()?.take(256)?.ifBlank { null },
                        channelNumber = channelNumber(attributes, pending?.channelNumber),
                        quality = listOfNotNull(
                            attributes["quality"], attributes["resolution"], attributes["height"],
                        ).firstOrNull { it.isNotBlank() }?.trim()?.take(32),
                    )
                    builder.accepted = result.size
                    pending = null
                    pendingGroup = null
                }
            }
        }
        return M3uParseResult(result, builder.build())
    }

    /** Parses gzip/raw bytes with the shared, bounded response decoder. */
    fun parseWithStats(bytes: ByteArray, limits: ImportLimits = ImportLimits()): M3uParseResult =
        runCatching { decodeText(bytes) }.fold(
            onSuccess = { text -> parseWithStats(text, limits) },
            onFailure = { error ->
                val code = if (error.message?.contains("large", ignoreCase = true) == true) {
                    ImportRejectionCode.GZIP_TOO_LARGE
                } else {
                    ImportRejectionCode.GZIP_DECODE_FAILED
                }
                M3uParseResult(
                    channels = emptyList(),
                    stats = ImportStats(limits = limits).withRejection(
                        ImportRejection(code.code, 0, error.javaClass.simpleName),
                    ),
                )
            },
        )

    private data class Pending(
        val attributes: Map<String, String>,
        val name: String?,
        val channelNumber: String?,
        val group: String?,
    )

    private class StatsBuilder(private val limits: ImportLimits) {
        var received = 0
        var accepted = 0
        var rejected = 0
        var truncated = false
        var gzipDecoded = false
        val rejections = ArrayList<ImportRejection>()
        var detailsDropped = 0

        fun reject(code: ImportRejectionCode, line: Int, detail: String? = null) {
            rejected++
            if (rejections.size < limits.maxRejectionDetails) {
                rejections += ImportRejection(code.code, line, detail?.take(160))
            } else {
                detailsDropped++
            }
        }

        fun build() = ImportStats(
            received = received,
            accepted = accepted,
            rejected = rejected,
            rejections = rejections,
            limits = limits,
            truncated = truncated || detailsDropped > 0,
            gzipDecoded = gzipDecoded,
            rejectionDetailsTruncated = detailsDropped > 0,
        )
    }

    private fun stripBom(value: String): String {
        var result = value
        while (result.startsWith("\uFEFF")) result = result.substring(1)
        return result
    }

    private fun hasGzipMagic(value: String): Boolean =
        value.length >= 2 && value[0].code == 0x1f && value[1].code == 0x8b

    private fun channelNumber(attributes: Map<String, String>, leading: String?): String? =
        listOfNotNull(attributes["tvg-chno"], attributes["channel-id"], attributes["number"])
            .firstOrNull { it.isNotBlank() }
            ?.trim()?.take(32)?.ifBlank { null }
            ?: leading?.takeIf { it.toIntOrNull()?.let { value -> value > 0 } == true }?.take(32)

    /** Splits the attribute list from the display name at the first comma outside quotes. */
    private fun splitExtInf(value: String): Pair<String, String> {
        var inQuotes = false
        for (index in value.indices) {
            when (value[index]) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) return value.substring(0, index) to value.substring(index + 1).trim()
            }
        }
        return value to ""
    }

    /** Returns (attributes, display name, leading #EXTINF number). */
    private fun parseExtInf(value: String): Triple<Map<String, String>, String?, String?> {
        val (attributesPart, name) = splitExtInf(value)
        var rest = attributesPart.trimStart()
        val leading = leadingNumber.find(rest)?.value
        if (leading != null) rest = rest.substring(leading.length)
        return Triple(parseAttributes(rest), name.ifBlank { null }, leading)
    }

    /** Accepts quoted and unquoted keys and values, including a quoted "number" key. */
    private fun parseAttributes(value: String): Map<String, String> {
        val attributes = LinkedHashMap<String, String>()
        var index = 0
        while (index < value.length) {
            while (index < value.length && value[index].isWhitespace()) index++
            if (index >= value.length) break
            val key = if (value[index] == '"') {
                index++
                val start = index
                while (index < value.length && value[index] != '"') index++
                val result = value.substring(start, index)
                if (index < value.length) index++
                result
            } else {
                val start = index
                while (index < value.length &&
                    (value[index].isLetterOrDigit() || value[index] == '-' || value[index] == '_')
                ) index++
                if (index == start) {
                    index++
                    continue
                }
                value.substring(start, index)
            }
            if (key.isBlank()) continue
            while (index < value.length && value[index].isWhitespace()) index++
            if (index < value.length && value[index] == '=') {
                index++
                while (index < value.length && value[index].isWhitespace()) index++
                val parsed = if (index < value.length && value[index] == '"') {
                    index++
                    val text = StringBuilder()
                    while (index < value.length && value[index] != '"') {
                        if (value[index] == '\\' && index + 1 < value.length && value[index + 1] == '"') {
                            index++
                        }
                        text.append(value[index])
                        index++
                    }
                    if (index < value.length) index++
                    text.toString()
                } else {
                    val start = index
                    while (index < value.length && !value[index].isWhitespace()) index++
                    value.substring(start, index)
                }
                attributes[key.trim().lowercase(Locale.ROOT)] = parsed.take(64 * 1024)
            }
        }
        return attributes
    }
}
