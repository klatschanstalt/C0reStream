package pw.c0re.stream.data

import okhttp3.Response
import okio.Buffer
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

private const val defaultMaxBytes = 32L * 1024 * 1024
private const val maxDecompressedBytes = 64L * 1024 * 1024

/** Reads a response body with a hard size limit. Never logs URLs or credentials. */
internal fun Response.readBytes(maxBytes: Long = defaultMaxBytes): ByteArray {
    check(isSuccessful) { "HTTP $code" }
    val responseBody = body
    val declared = responseBody.contentLength()
    check(declared <= maxBytes) { "Response too large" }
    val source = responseBody.source()
    val buffer = Buffer()
    while (true) {
        val remaining = maxBytes + 1 - buffer.size
        check(remaining > 0) { "Response too large" }
        val count = source.read(buffer, minOf(8192L, remaining))
        if (count == -1L) break
        check(buffer.size <= maxBytes) { "Response too large" }
    }
    return buffer.readByteArray()
}

/** Decodes optional gzip payloads (XMLTV .gz files are often served without Content-Encoding). */
internal fun decodeText(bytes: ByteArray, charsetName: String? = null): String {
    val uncompressed = if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
        gunzip(bytes, maxDecompressedBytes)
    } else {
        bytes
    }
    val charset = runCatching { charsetOf(charsetName) }.getOrDefault(Charsets.UTF_8)
    return uncompressed.toString(charset)
}

private fun charsetOf(name: String?): java.nio.charset.Charset =
    if (name.isNullOrBlank()) Charsets.UTF_8 else java.nio.charset.Charset.forName(name)

private fun gunzip(bytes: ByteArray, maxBytes: Long): ByteArray {
    GZIPInputStream(bytes.inputStream()).use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            check(out.size().toLong() + count <= maxBytes) { "Decompressed response too large" }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }
}

internal fun Response.readText(maxBytes: Long = defaultMaxBytes): String {
    val contentType = body.contentType()
    return decodeText(readBytes(maxBytes), contentType?.charset(Charsets.UTF_8)?.name())
}

/** Backwards-compatible alias used by metadata/update endpoints. */
internal fun Response.readMetadata(maxBytes: Long = defaultMaxBytes): String = readText(maxBytes)

/** Inflates a gzip payload that was passed as raw text; bounded by [maxBytes]. */
internal fun decodeGzipText(value: String, maxBytes: Long = maxDecompressedBytes): String =
    decodeGzipBytes(ByteArray(value.length) { value[it].code.toByte() }, maxBytes)

/** Inflates gzip bytes with an explicit decompressed-byte ceiling. */
internal fun decodeGzipBytes(bytes: ByteArray, maxBytes: Long = maxDecompressedBytes): String =
    gunzip(bytes, maxBytes).toString(Charsets.UTF_8)
