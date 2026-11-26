package pw.c0re.stream.data

/**
 * Catch-up/Timeshift-URL-Bau nach den gaengigen M3U-Platzhaltern
 * (catchup="1" catchup-source="..."), wie iptvnator sie unterstuetzt:
 * ${start}, ${end}, ${duration} sowie {utc}, {lutc}, {duration}.
 */
object Catchup {

    fun build(source: String, startMs: Long, stopMs: Long, nowMs: Long): String? {
        val start = startMs / 1000
        val end = stopMs / 1000
        val duration = (stopMs - startMs) / 1000
        val out = source
            .replace("\${start}", start.toString())
            .replace("\${end}", end.toString())
            .replace("\${duration}", duration.toString())
            .replace("{utc}", start.toString())
            .replace("{lutc}", (nowMs / 1000).toString())
            .replace("{duration}", duration.toString())
        return out.takeIf { it != source }
    }
}
