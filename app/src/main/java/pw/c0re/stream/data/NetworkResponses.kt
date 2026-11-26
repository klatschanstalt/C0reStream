package pw.c0re.stream.data

import okhttp3.Response
import okio.Buffer

/** Bound decoded HTTP metadata before parsing; never include URLs or credentials in errors. */
internal fun Response.readMetadata(maxBytes: Long = 32L * 1024 * 1024): String {
    check(isSuccessful) { "HTTP $code" }
    val responseBody = body
    check(responseBody.contentLength() <= maxBytes) { "Response too large" }
    val source = responseBody.source()
    val buffer = Buffer()
    while (true) {
        val remaining = maxBytes + 1 - buffer.size
        check(remaining > 0) { "Response too large" }
        val count = source.read(buffer, minOf(8192L, remaining))
        if (count == -1L) break
        check(buffer.size <= maxBytes) { "Response too large" }
    }
    return buffer.readString(responseBody.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
}
