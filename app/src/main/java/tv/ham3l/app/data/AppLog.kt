package tv.ham3l.app.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lokales Fehler-/Ereignisprotokoll (Ring-Puffer, 100 Eintraege),
 * in den Einstellungen einsehbar. Keine Netzwerk-Leaks, bleibt auf dem Geraet.
 */
object AppLog {
    private const val MAX = 100
    private val buffer = ArrayDeque<String>()

    val lines: List<String>
        get() = synchronized(buffer) { buffer.toList() }

    fun log(tag: String, message: String) = synchronized(buffer) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        buffer.addLast("$ts [$tag] $message")
        if (buffer.size > MAX) buffer.removeFirst()
    }
}
