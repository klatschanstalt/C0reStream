package pw.c0re.stream.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local error/event log (ring buffer, 100 entries) shown in settings. It never leaves the
 * device, and URLs/credentials are redacted before they reach the buffer.
 */
object AppLog {
    private const val MAX = 100
    private val buffer = ArrayDeque<String>()
    private val httpUrl = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
    private val xtreamUrl = Regex("xtream://\\S+", RegexOption.IGNORE_CASE)
    private val credentialPair = Regex("(?i)(password|username|token|secret)=([^&\\s]+)")

    val lines: List<String>
        get() = synchronized(buffer) { buffer.toList() }

    fun log(tag: String, message: String) = synchronized(buffer) {
        val safe = message
            .replace(xtreamUrl, "[xtream]")
            .replace(httpUrl, "[url]")
            .replace(credentialPair, "$1=[redacted]")
            .take(1_024)
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        buffer.addLast("$timestamp [$tag] $safe")
        if (buffer.size > MAX) buffer.removeFirst()
    }
}
