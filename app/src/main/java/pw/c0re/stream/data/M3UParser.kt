package pw.c0re.stream.data

import pw.c0re.stream.data.model.Channel

// M3U/M3U8-Parser (Extended M3U mit #EXTINF-Attributen)
object M3UParser {

    data class RawChannel(val name: String, val logo: String?, val group: String?, val tvgId: String?, val url: String, val catchupSource: String? = null)

    fun parse(text: String): List<RawChannel> {
        val result = mutableListOf<RawChannel>()
        var pending: Map<String, String>? = null
        var pendingName: String? = null
        val attrRegex = Regex("([a-zA-Z0-9-]+)=\"([^\"]*)\"")

        for (line in text.lineSequence()) {
            val l = line.trim()
            when {
                l.startsWith("#EXTINF:") -> {
                    val rest = l.substringAfter("#EXTINF:")
                    // Name steht am Ende der EXTINF-Zeile, nach dem letzten Komma
                    val attrsPart = rest.substringBeforeLast(",")
                    pendingName = if (rest.contains(",")) rest.substringAfterLast(",").trim() else null
                    pending = attrRegex.findAll(attrsPart).associate { it.groupValues[1] to it.groupValues[2] }
                }
                l.isNotEmpty() && !l.startsWith("#") -> {
                    val attrs = pending
                    if (attrs != null) {
                        val name = pendingName?.ifBlank { null } ?: attrs["tvg-name"] ?: "Unbekannt"
                        result += RawChannel(
                            name = name,
                            logo = attrs["tvg-logo"],
                            group = attrs["group-title"],
                            tvgId = attrs["tvg-id"],
                            url = l,
                            catchupSource = attrs["catchup-source"],
                        )
                        pending = null
                        pendingName = null
                    }
                }
            }
        }
        return result
    }

    fun toChannels(playlistId: Long, raw: List<RawChannel>): List<Channel> =
        raw.map { r ->
            Channel(
                playlistId = playlistId,
                name = r.name,
                logoUrl = r.logo,
                group = r.group,
                url = r.url,
                tvgId = r.tvgId,
                catchupSource = r.catchupSource,
            )
        }
}
