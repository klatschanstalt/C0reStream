package pw.c0re.stream.data

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import pw.c0re.stream.data.model.EpgEntry
import java.io.Reader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// Minimaler XMLTV (EPG)-Parser, wie die XMLTV-Unterstuetzung von iptvnator
object EpgParser {

    private fun parseXmltvTime(s: String): Long = try {
        val fmt = SimpleDateFormat("yyyyMMddHHmmss", Locale.ROOT).apply { isLenient = false }
        fmt.timeZone = if (s.length >= 20) TimeZone.getTimeZone("GMT" + s.substring(15, 18) + ":" + s.substring(18, 20)) else TimeZone.getTimeZone("UTC")
        fmt.parse(s.take(14))?.time ?: 0L
    } catch (_: Exception) { 0L }

    fun parse(reader: Reader): Map<String, MutableList<EpgEntry>> {
        val map = mutableMapOf<String, MutableList<EpgEntry>>()
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        // Haertung: keine DTD/DocDecl-Verarbeitung (Schutz vor Entity-Expansion/"Billion Laughs")
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        check(!parser.getFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL))
        parser.setInput(reader)

        var currentChannelId: String? = null
        var start = 0L; var stop = 0L
        val title = StringBuilder(); val desc = StringBuilder()
        var inProgramme = false; var inTitle = false; var inDesc = false

        fun currentDescOrNull(): String? = desc.toString().trim().ifBlank { null }

        var count = 0
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.DOCDECL -> error("DTD not allowed")
                XmlPullParser.START_TAG -> when (parser.name) {
                    "channel" -> currentChannelId = parser.getAttributeValue(null, "id")
                    "programme" -> {
                        currentChannelId = parser.getAttributeValue(null, "channel")
                        inProgramme = true
                        start = parseXmltvTime(parser.getAttributeValue(null, "start") ?: "")
                        stop = parseXmltvTime(parser.getAttributeValue(null, "stop") ?: "")
                        title.setLength(0); desc.setLength(0)
                    }
                    "title" -> if (inProgramme) inTitle = true
                    "desc" -> if (inProgramme) inDesc = true
                }
                XmlPullParser.TEXT -> {
                    if (inTitle) { check(title.length + parser.text.length <= 8192); title.append(parser.text) }
                    if (inDesc) { check(desc.length + parser.text.length <= 65536); desc.append(parser.text) }
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "title" -> inTitle = false
                    "desc" -> inDesc = false
                    "programme" -> {
                        currentChannelId?.takeIf { it.isNotBlank() && start > 0 && stop > start }?.let { id ->
                            check(++count <= 250000)
                            map.getOrPut(id) { mutableListOf() }
                                .add(EpgEntry(start, stop, title.toString().trim(), currentDescOrNull()))
                        }
                        inProgramme = false
                    }
                }
            }
            event = parser.nextToken()
        }
        return map
    }
}
