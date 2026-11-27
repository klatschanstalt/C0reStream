package pw.c0re.stream.data

/**
 * Catch-up/timeshift URL construction for the common M3U placeholders:
 * ${start}, ${end}, ${duration}, {utc}, {lutc}, {start}, {end}, {utcend}, {duration}.
 * The substituted URL is validated again and must stay HTTP(S).
 */
object Catchup {
    fun build(source: String, startMs: Long, stopMs: Long, nowMs: Long): String? {
        if (startMs <= 0 || stopMs <= startMs) return null
        val start = startMs / 1000
        val end = stopMs / 1000
        val duration = (stopMs - startMs) / 1000
        val out = source
            .replace("\${start}", start.toString())
            .replace("\${end}", end.toString())
            .replace("\${duration}", duration.toString())
            .replace("{utc}", start.toString())
            .replace("{lutc}", (nowMs / 1000).toString())
            .replace("{utcend}", end.toString())
            .replace("{start}", start.toString())
            .replace("{end}", end.toString())
            .replace("{duration}", duration.toString())
        return out.takeIf { it != source && NetworkPolicy.parse(it) != null }
    }
}
