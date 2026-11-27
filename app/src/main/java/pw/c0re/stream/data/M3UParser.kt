package pw.c0re.stream.data

import pw.c0re.stream.data.model.Channel
import java.util.Locale

/**
 * Extended M3U/M3U8 parser with bounded input, quoted or unquoted attributes and
 * catch-up metadata. Only HTTP(S) stream URLs are accepted.
 */
object M3UParser {
    private const val maxTextLength = 32 * 1024 * 1024
    private const val maxLines = 200_000
    private const val maxChannels = 100_000
    private const val maxLineLength = 64 * 1024
    private const val unknownName = "Unknown"

    data class RawChannel(
        val name: String,
        val logo: String?,
        val group: String?,
        val tvgId: String?,
        val url: String,
        val catchupSource: String? = null,
    )

    fun parse(text: String): List<RawChannel> {
        require(text.length <= maxTextLength) { "Playlist too large" }
        val result = ArrayList<RawChannel>()
        var pending: Map<String, String>? = null
        var pendingName: String? = null
        var pendingGroup: String? = null
        var lineCount = 0

        for (rawLine in text.lineSequence()) {
            check(++lineCount <= maxLines) { "Too many playlist lines" }
            check(rawLine.length <= maxLineLength) { "Playlist line too long" }
            val line = rawLine.trim().removePrefix("\uFEFF")
            when {
                line.startsWith("#EXTINF:") -> {
                    val rest = line.substringAfter("#EXTINF:")
                    val (attributes, name) = splitExtInf(rest)
                    pending = parseAttributes(attributes)
                    pendingName = name.ifBlank { null }
                }
                line.startsWith("#EXTGRP:") -> {
                    pendingGroup = line.substringAfter("#EXTGRP:").trim().ifBlank { null }
                }
                line.startsWith("#") -> Unit
                line.isNotEmpty() -> {
                    val attributes = pending
                    if (attributes != null && NetworkPolicy.parse(line) != null) {
                        check(result.size < maxChannels) { "Playlist too large" }
                        val name = pendingName
                            ?: attributes["tvg-name"]?.takeIf { it.isNotBlank() }
                            ?: unknownName
                        val logo = attributes["tvg-logo"]?.takeIf { NetworkPolicy.parse(it) != null }
                        val catchup = attributes["catchup-source"]
                            ?.takeIf { NetworkPolicy.isTemplateUrl(it) || NetworkPolicy.parse(it) != null }
                        result += RawChannel(
                            name = name.trim().take(512),
                            logo = logo,
                            group = (attributes["group-title"] ?: pendingGroup)?.trim()?.take(512),
                            tvgId = attributes["tvg-id"]?.trim()?.take(256),
                            url = line,
                            catchupSource = catchup,
                        )
                    }
                    pending = null
                    pendingName = null
                    pendingGroup = null
                }
            }
        }
        return result
    }

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

    private fun parseAttributes(value: String): Map<String, String> {
        val attributes = LinkedHashMap<String, String>()
        var index = 0
        while (index < value.length) {
            while (index < value.length && value[index].isWhitespace()) index++
            val keyStart = index
            while (index < value.length && (value[index].isLetterOrDigit() || value[index] == '-' || value[index] == '_')) index++
            if (index == keyStart) {
                index++
                continue
            }
            val key = value.substring(keyStart, index).lowercase(Locale.ROOT)
            while (index < value.length && value[index].isWhitespace()) index++
            if (index < value.length && value[index] == '=') {
                index++
                while (index < value.length && value[index].isWhitespace()) index++
                val parsed = if (index < value.length && value[index] == '"') {
                    index++
                    val start = index
                    while (index < value.length && value[index] != '"') index++
                    val text = value.substring(start, index)
                    if (index < value.length) index++
                    text
                } else {
                    val start = index
                    while (index < value.length && !value[index].isWhitespace()) index++
                    value.substring(start, index)
                }
                attributes[key] = parsed.take(maxLineLength)
            }
        }
        return attributes
    }

    fun toChannels(playlistId: Long, raw: List<RawChannel>): List<Channel> =
        raw.map { channel ->
            Channel(
                playlistId = playlistId,
                name = channel.name,
                logoUrl = channel.logo,
                group = channel.group,
                url = channel.url,
                tvgId = channel.tvgId,
                catchupSource = channel.catchupSource,
            )
        }
}
