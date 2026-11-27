package pw.c0re.stream.data.resources

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

/** One resource described by a signed [ResourceManifest]. All fields are untrusted until parsed. */
data class ResourceEntry(
    val key: String,
    val path: String,
    val version: Long,
    val sha256: String,
    val bytes: Long,
    val encoding: String? = null,
    val mime: String? = null,
)

/** Describes one configuration version. [schemaVersion] must be 1; [baseUrl] must be HTTPS. */
data class ResourceManifest(
    val schemaVersion: Int = 1,
    val configVersion: Long,
    val baseUrl: String,
    val entries: List<ResourceEntry>,
)

/**
 * Signed metadata wrapper. [signatureHex] signs the canonical bytes of [manifest];
 * [signerFingerprint] is the lowercase SHA-256 of the signer's public key
 * (the key carried by the publisher's signing certificate).
 */
data class SignedResourceEnvelope(
    val manifest: ResourceManifest,
    val signatureHex: String,
    val signerFingerprint: String,
)

/** Outcome of [ResourceCatalog.download]. Reasons are coarse on purpose: no URLs are logged. */
sealed interface DownloadResult {
    data class Ready(val file: File, val version: Long) : DownloadResult
    data object NotModified : DownloadResult
    data class Failure(val reason: Reason) : DownloadResult

    enum class Reason {
        INVALID_INPUT,
        UNSAFE_PATH,
        UNSUPPORTED_ENCODING,
        HTTP,
        RANGE_UNSUPPORTED,
        SIZE_EXCEEDED,
        TRUNCATED,
        CHECKSUM_MISMATCH,
        IO,
    }
}

/**
 * Self-contained, JVM-testable resource manifest and download subsystem.
 *
 * Layout under the supplied directory: `v<version>/<entry.path>` plus per-file
 * `<path>.sha256` and `<path>.etag` sidecars, in-progress `<path>.part` files and an
 * `ACTIVE` marker containing the active version number. A version is "fully verified"
 * when it contains at least one file whose SHA-256 matches its sidecar, no part/temp
 * files and no oversized files.
 *
 * Only HTTPS is accepted. No function logs URLs, headers or credentials; failures are
 * reported as typed reasons instead.
 */
object ResourceCatalog {
    const val SCHEMA_VERSION = 1
    const val MAX_RESOURCE_BYTES = 64L * 1024 * 1024

    private const val MAX_MANIFEST_BYTES = 1024 * 1024
    private const val MAX_ENTRIES = 1000
    private const val MAX_FIELD = 256
    private const val MAX_MIME = 128
    private const val MAX_ENCODING = 32
    private const val MAX_BASE_URL = 2048
    private const val MAX_ETAG = 256
    private const val MAX_SIGNATURE_HEX = 1024
    private const val ACTIVE_FILE = "ACTIVE"
    private val HEX64 = Regex("[0-9a-fA-F]{64}")
    private val VERSION_DIR = Regex("v[1-9][0-9]{0,17}")
    private val SAFE_PATH = Regex("[A-Za-z0-9._/-]+")
    private val CONTENT_RANGE = Regex("^bytes\\s+(\\d+)-")

    // ---------------------------------------------------------------- parsing

    /** Strictly parses a manifest JSON document, rejecting all disallowed values. */
    fun parse(bytes: ByteArray): ResourceManifest {
        require(bytes.isNotEmpty() && bytes.size <= MAX_MANIFEST_BYTES) { "Manifest size out of range" }
        val root = try {
            JSONObject(String(bytes, StandardCharsets.UTF_8))
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid manifest JSON", error)
        }
        val manifest = ResourceManifest(
            schemaVersion = longField(root, "schemaVersion", 0).toInt(),
            configVersion = longField(root, "configVersion", 1),
            baseUrl = stringField(root, "baseUrl", MAX_BASE_URL),
            entries = ArrayList<ResourceEntry>().also { list ->
                val array = root.opt("entries") as? JSONArray
                    ?: throw IllegalArgumentException("Missing entries")
                require(array.length() <= MAX_ENTRIES) { "Too many entries" }
                for (index in 0 until array.length()) {
                    val item = array.opt(index) as? JSONObject
                        ?: throw IllegalArgumentException("Invalid entry")
                    list += ResourceEntry(
                        key = stringField(item, "key", MAX_FIELD),
                        path = stringField(item, "path", MAX_FIELD),
                        version = longField(item, "version", 1),
                        sha256 = stringField(item, "sha256", 64).lowercase(Locale.ROOT),
                        bytes = longField(item, "bytes", 0),
                        encoding = optionalStringField(item, "encoding", MAX_ENCODING)
                            ?.lowercase(Locale.ROOT),
                        mime = optionalStringField(item, "mime", MAX_MIME),
                    )
                }
            },
        )
        validateManifest(manifest)
        return manifest
    }

    /**
     * Serializes to the canonical byte representation used for signatures: fixed field
     * order, entries sorted by key, lowercase hex, UTF-8, no insignificant whitespace.
     */
    fun serialize(manifest: ResourceManifest): ByteArray {
        validateManifest(manifest)
        val out = StringBuilder()
        out.append("{\"schemaVersion\":").append(SCHEMA_VERSION)
        out.append(",\"configVersion\":").append(manifest.configVersion)
        out.append(",\"baseUrl\":")
        appendQuoted(out, manifest.baseUrl)
        out.append(",\"entries\":[")
        manifest.entries.sortedBy { it.key }.forEachIndexed { index, entry ->
            if (index > 0) out.append(',')
            out.append("{\"key\":")
            appendQuoted(out, entry.key)
            out.append(",\"path\":")
            appendQuoted(out, entry.path)
            out.append(",\"version\":").append(entry.version)
            out.append(",\"sha256\":")
            appendQuoted(out, entry.sha256.lowercase(Locale.ROOT))
            out.append(",\"bytes\":").append(entry.bytes)
            out.append(",\"encoding\":")
            if (entry.encoding == null) out.append("null") else appendQuoted(out, entry.encoding.lowercase(Locale.ROOT))
            out.append(",\"mime\":")
            if (entry.mime == null) out.append("null") else appendQuoted(out, entry.mime)
            out.append('}')
        }
        out.append("]}")
        return out.toString().toByteArray(StandardCharsets.UTF_8)
    }

    /** Parses `{"manifest": ..., "signature": "...", "signerFingerprint": "..."}`. */
    fun parseEnvelope(bytes: ByteArray): SignedResourceEnvelope {
        require(bytes.isNotEmpty() && bytes.size <= MAX_MANIFEST_BYTES) { "Envelope size out of range" }
        val root = try {
            JSONObject(String(bytes, StandardCharsets.UTF_8))
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid envelope JSON", error)
        }
        val manifestObject = root.opt("manifest") as? JSONObject
            ?: throw IllegalArgumentException("Missing manifest")
        val manifest = parse(manifestObject.toString().toByteArray(StandardCharsets.UTF_8))
        val signature = stringField(root, "signature", MAX_SIGNATURE_HEX)
        val fingerprint = stringField(root, "signerFingerprint", 64)
        requireHex(signature, null)
        requireHex(fingerprint, 32)
        return SignedResourceEnvelope(manifest, signature.lowercase(Locale.ROOT), fingerprint.lowercase(Locale.ROOT))
    }

    /** Serializes an envelope deterministically around [serialize]. */
    fun serializeEnvelope(envelope: SignedResourceEnvelope): ByteArray {
        requireHex(envelope.signatureHex, null)
        requireHex(envelope.signerFingerprint, 32)
        val out = StringBuilder()
        out.append("{\"manifest\":").append(String(serialize(envelope.manifest), StandardCharsets.UTF_8))
        out.append(",\"signature\":")
        appendQuoted(out, envelope.signatureHex.lowercase(Locale.ROOT))
        out.append(",\"signerFingerprint\":")
        appendQuoted(out, envelope.signerFingerprint.lowercase(Locale.ROOT))
        out.append('}')
        return out.toString().toByteArray(StandardCharsets.UTF_8)
    }

    // ------------------------------------------------------------ signatures

    /** RSASSA-PKCS1-v1_5/SHA256 signature; returns lowercase hex. */
    fun sign(manifestBytes: ByteArray, privateKey: PrivateKey): String = try {
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(privateKey)
        signer.update(manifestBytes)
        hex(signer.sign())
    } catch (error: GeneralSecurityException) {
        throw IllegalArgumentException("Cannot sign manifest", error)
    }

    /**
     * Verifies an RSASSA-PKCS1-v1_5/SHA256 signature over exactly [manifestBytes].
     * When [expectedFingerprint] is provided it is compared in constant time against the
     * SHA-256 of the supplied public key's DER SubjectPublicKeyInfo. Returns false on any
     * malformed input; never throws for bad signatures.
     */
    fun verify(
        manifestBytes: ByteArray,
        signatureHex: String,
        publicKey: PublicKey,
        expectedFingerprint: String? = null,
    ): Boolean {
        val signature = decodeHex(signatureHex) ?: return false
        if (signature.isEmpty() || signature.size > MAX_SIGNATURE_HEX / 2) return false
        if (expectedFingerprint != null) {
            if (expectedFingerprint.length > 64) return false
            val expected = decodeHex(expectedFingerprint) ?: return false
            val encoded = publicKey.encoded ?: return false
            val actual = decodeHex(sha256(encoded)) ?: return false
            if (expected.size != 32 || !MessageDigest.isEqual(expected, actual)) return false
        }
        return try {
            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(publicKey)
            verifier.update(manifestBytes)
            verifier.verify(signature)
        } catch (error: GeneralSecurityException) {
            false
        } catch (error: IllegalArgumentException) {
            false
        }
    }

    /** Verifies an envelope: pins its declared fingerprint, then checks its signature. */
    fun verifyEnvelope(
        envelope: SignedResourceEnvelope,
        publicKey: PublicKey,
        expectedFingerprint: String? = null,
    ): Boolean {
        val declared = decodeHex(envelope.signerFingerprint) ?: return false
        if (declared.size != 32) return false
        if (expectedFingerprint != null) {
            if (expectedFingerprint.length > 64) return false
            val expected = decodeHex(expectedFingerprint) ?: return false
            if (!MessageDigest.isEqual(declared, expected)) return false
        }
        return try {
            verify(serialize(envelope.manifest), envelope.signatureHex, publicKey, expectedFingerprint)
        } catch (error: IllegalArgumentException) {
            false
        }
    }

    /** SHA-256 fingerprint (lowercase hex) of a public key's SubjectPublicKeyInfo. */
    fun fingerprint(publicKey: PublicKey): String =
        publicKey.encoded?.let { sha256(it) } ?: throw IllegalArgumentException("Unencodable public key")

    /** SHA-256 fingerprint of a signing certificate's DER encoding. */
    fun fingerprint(certificate: X509Certificate): String = sha256(certificate.encoded)

    // -------------------------------------------------------------- download

    /**
     * Downloads one entry into `v<version>/<entry.path>`. Supports `If-None-Match`/ETag,
     * bounded resumable Range requests with a `.part` file, gzip/deflate decoding,
     * SHA-256 verification and an atomic rename into place. [onProgress] receives
     * (bytes written including any resumed prefix, declared total). Range resume is
     * disabled for gzip/deflate because compressed byte offsets are not the stored ones.
     * Cancellation and all failures clean up the `.part` file; cancellation propagates.
     */
    suspend fun download(
        baseUrl: String,
        entry: ResourceEntry,
        destinationDir: File,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): DownloadResult = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val url = parseHttpsUrl(baseUrl) ?: return@withContext DownloadResult.Failure(DownloadResult.Reason.INVALID_INPUT)
        validateEntry(entry)?.let { return@withContext DownloadResult.Failure(it) }
        if (entry.bytes > MAX_RESOURCE_BYTES) {
            return@withContext DownloadResult.Failure(DownloadResult.Reason.SIZE_EXCEEDED)
        }
        val versionDir = File(destinationDir, "v${entry.version}")
        val target = safeResolve(versionDir, entry.path)
            ?: return@withContext DownloadResult.Failure(DownloadResult.Reason.UNSAFE_PATH)
        if (!versionDir.isDirectory && !versionDir.mkdirs()) {
            return@withContext DownloadResult.Failure(DownloadResult.Reason.IO)
        }
        val part = File(target.parentFile, "${target.name}.part")
        val etagFile = File(target.parentFile, "${target.name}.etag")
        val checksumFile = File(target.parentFile, "${target.name}.sha256")

        val expectedHash = entry.sha256.lowercase(Locale.ROOT)
        val targetValid = target.isFile && target.length() == entry.bytes && sha256(target) == expectedHash
        val etag = if (targetValid) readEtag(etagFile) else null
        if (targetValid && etag == null) return@withContext DownloadResult.Ready(target, entry.version)

        var resumeFrom = 0L
        if (part.isFile && isIdentityEncoding(entry.encoding)) {
            resumeFrom = part.length().coerceAtMost(entry.bytes)
            if (resumeFrom >= entry.bytes) {
                runCatching { part.delete() }
                resumeFrom = 0
            }
        } else {
            runCatching { part.delete() }
        }
        if (!responseUrlHasSafePath(url, entry.path)) {
            return@withContext DownloadResult.Failure(DownloadResult.Reason.UNSAFE_PATH)
        }

        val builder = Request.Builder()
            .url(url.newBuilder().addPathSegments(entry.path).build())
            .header("Accept-Encoding", "identity")
        if (resumeFrom > 0) builder.header("Range", "bytes=$resumeFrom-")
        if (etag != null) builder.header("If-None-Match", etag)

        var success = false
        try {
            httpsClient.newCall(builder.build()).execute().use { response ->
                if (response.code == 304) {
                    runCatching { part.delete() }
                    return@withContext if (targetValid) {
                        DownloadResult.NotModified
                    } else {
                        DownloadResult.Failure(DownloadResult.Reason.HTTP)
                    }
                }
                if (response.code != 200 && response.code != 206) {
                    return@withContext DownloadResult.Failure(DownloadResult.Reason.HTTP)
                }
                if (response.code == 206 && resumeFrom == 0L) {
                    return@withContext DownloadResult.Failure(DownloadResult.Reason.RANGE_UNSUPPORTED)
                }
                if (response.code == 206) {
                    val start = CONTENT_RANGE.find(response.header("Content-Range") ?: "")
                        ?.groupValues?.get(1)?.toLongOrNull()
                    if (start != resumeFrom) {
                        return@withContext DownloadResult.Failure(DownloadResult.Reason.RANGE_UNSUPPORTED)
                    }
                }
                val body = response.body
                if (response.code == 200 && resumeFrom > 0) {
                    resumeFrom = 0
                }
                val remaining = entry.bytes - resumeFrom
                if (remaining > 0 && isIdentityEncoding(entry.encoding)) {
                    val declared = body.contentLength()
                    if (declared > remaining) {
                        return@withContext DownloadResult.Failure(DownloadResult.Reason.SIZE_EXCEEDED)
                    }
                }

                val digest = MessageDigest.getInstance("SHA-256")
                if (resumeFrom > 0) digestFile(part, digest)
                var written = resumeFrom
                var lastReport = -32L * 1024L
                onProgress(written, entry.bytes)

                FileOutputStream(part, resumeFrom > 0).use { output ->
                    decode(body.byteStream(), entry.encoding).use { input ->
                        val buffer = ByteArray(8 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read.toLong() > entry.bytes - written) {
                                return@withContext DownloadResult.Failure(DownloadResult.Reason.SIZE_EXCEEDED)
                            }
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            written += read.toLong()
                            if (written - lastReport >= 32 * 1024 || written == entry.bytes) {
                                onProgress(written, entry.bytes)
                                lastReport = written
                            }
                        }
                    }
                }
                if (written != entry.bytes) {
                    return@withContext DownloadResult.Failure(DownloadResult.Reason.TRUNCATED)
                }
                val actual = digest.digest()
                val expected = decodeHex(expectedHash)!!
                if (!MessageDigest.isEqual(actual, expected)) {
                    return@withContext DownloadResult.Failure(DownloadResult.Reason.CHECKSUM_MISMATCH)
                }

                moveAtomic(part, target)
                writeAtomic(checksumFile, hex(actual).toByteArray(StandardCharsets.UTF_8))
                val responseEtag = response.header("ETag")?.trim()?.takeIf {
                    it.isNotEmpty() && it.length <= MAX_ETAG && it.none { c -> c.isISOControl() }
                }
                if (responseEtag == null) runCatching { etagFile.delete() }
                else writeAtomic(etagFile, responseEtag.toByteArray(StandardCharsets.UTF_8))
                success = true
                return@withContext DownloadResult.Ready(target, entry.version)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            return@withContext DownloadResult.Failure(DownloadResult.Reason.IO)
        } finally {
            if (!success) runCatching { part.delete() }
        }
    }

    // ------------------------------------------------ activation and upkeep

    /** Atomically marks a fully verified version active. Returns false if it is not usable. */
    fun activate(dir: File, version: Long): Boolean {
        if (version <= 0 || !isVerified(dir, version)) return false
        return try {
            writeAtomic(File(dir, ACTIVE_FILE), version.toString().toByteArray(StandardCharsets.UTF_8))
            true
        } catch (error: IOException) {
            false
        }
    }

    /** Active version from the marker, or null when absent/corrupt. */
    fun activeVersion(dir: File): Long? = try {
        File(dir, ACTIVE_FILE).readText(StandardCharsets.UTF_8).trim().toLongOrNull()?.takeIf { it > 0 }
    } catch (error: IOException) {
        null
    }

    /** Highest fully verified version available offline, or null. */
    fun latestAvailable(dir: File): Long? = versionDirs(dir).filter { isVerified(dir, it) }.maxOrNull()

    /** Switches ACTIVE to the highest verified version below the current one, if any. */
    fun rollback(dir: File): Long? {
        val active = activeVersion(dir) ?: return null
        val previous = versionDirs(dir).filter { it < active && isVerified(dir, it) }.maxOrNull() ?: return null
        return if (activate(dir, previous)) previous else null
    }

    /**
     * Deletes unverified/temp/oversized files and stale versions, keeping at most
     * [keepVersions] recent verified versions plus the ACTIVE version. No-op if [dir]
     * is not a directory; never deletes the ACTIVE marker.
     */
    fun cleanup(dir: File, keepVersions: Int = 2) {
        if (!dir.isDirectory) return
        val active = activeVersion(dir)
        val keep = HashSet<Long>()
        if (active != null) keep += active
        versionDirs(dir).filter { isVerified(dir, it) }.sortedDescending()
            .take(maxOf(0, keepVersions)).forEach { keep += it }
        dir.listFiles()?.forEach { file ->
            when {
                file.isDirectory && VERSION_DIR.matches(file.name) -> {
                    val version = file.name.drop(1).toLongOrNull()
                    if (version == null || version !in keep) deleteRecursively(file) else cleanVersionDir(file)
                }
                file.isDirectory -> deleteRecursively(file)
                file.isFile && file.name != ACTIVE_FILE -> runCatching { file.delete() }
            }
        }
    }

    private fun isVerified(dir: File, version: Long): Boolean {
        val versionDir = File(dir, "v$version")
        if (!versionDir.isDirectory) return false
        var resources = 0
        val files = versionDir.walkTopDown().filter { it.isFile }.toList()
        for (file in files) {
            val name = file.name
            if (name.endsWith(".sha256") || name.endsWith(".etag")) continue
            if (name.endsWith(".part") || name.endsWith(".tmp")) return false
            if (file.length() > MAX_RESOURCE_BYTES) return false
            val sidecar = File(file.parentFile, "$name.sha256")
            val expected = try {
                sidecar.readText(StandardCharsets.UTF_8).trim().lowercase(Locale.ROOT)
            } catch (error: IOException) {
                return false
            }
            if (!HEX64.matches(expected) || sha256(file) != expected) return false
            resources++
        }
        return resources > 0
    }

    private fun versionDirs(dir: File): List<Long> = dir.listFiles { file ->
        file.isDirectory && VERSION_DIR.matches(file.name)
    }?.mapNotNull { it.name.drop(1).toLongOrNull() }.orEmpty()

    private fun cleanVersionDir(versionDir: File) {
        versionDir.walkTopDown().filter { it.isFile }.forEach { file ->
            val name = file.name
            when {
                name.endsWith(".part") || name.endsWith(".tmp") -> runCatching { file.delete() }
                name.endsWith(".sha256") || name.endsWith(".etag") -> {
                    val base = File(file.parentFile, name.substringBeforeLast('.'))
                    if (!base.isFile) runCatching { file.delete() }
                }
                file.length() > MAX_RESOURCE_BYTES -> deleteWithSidecars(file)
                else -> {
                    val sidecar = File(file.parentFile, "$name.sha256")
                    val expected = try {
                        sidecar.readText(StandardCharsets.UTF_8).trim().lowercase(Locale.ROOT)
                    } catch (error: IOException) {
                        ""
                    }
                    if (!HEX64.matches(expected) || sha256(file) != expected) deleteWithSidecars(file)
                }
            }
        }
    }

    private fun deleteWithSidecars(file: File) {
        runCatching { file.delete() }
        runCatching { File(file.parentFile, "${file.name}.sha256").delete() }
        runCatching { File(file.parentFile, "${file.name}.etag").delete() }
    }

    private fun deleteRecursively(file: File) {
        runCatching { file.walkBottomUp().forEach { runCatching { it.delete() } } }
    }

    // ------------------------------------------------------------- internals

    private fun validateManifest(manifest: ResourceManifest) {
        require(manifest.schemaVersion == SCHEMA_VERSION) { "Unsupported schemaVersion" }
        require(manifest.configVersion > 0) { "Invalid configVersion" }
        parseHttpsUrl(manifest.baseUrl) ?: throw IllegalArgumentException("Invalid baseUrl")
        require(manifest.entries.size <= MAX_ENTRIES) { "Too many entries" }
        val keys = HashSet<String>()
        manifest.entries.forEach { entry ->
            require(keys.add(entry.key)) { "Duplicate entry key" }
            validateEntry(entry)?.let { throw IllegalArgumentException(it.name) }
        }
    }

    /** Returns a download failure reason for an invalid entry, or null when valid. */
    private fun validateEntry(entry: ResourceEntry): DownloadResult.Reason? {
        if (entry.key.isEmpty() || entry.key.length > MAX_FIELD || entry.key.any { it.isISOControl() }) {
            return DownloadResult.Reason.INVALID_INPUT
        }
        if (!isSafePath(entry.path)) return DownloadResult.Reason.UNSAFE_PATH
        if (entry.version <= 0 || entry.bytes < 0) return DownloadResult.Reason.INVALID_INPUT
        if (!HEX64.matches(entry.sha256)) return DownloadResult.Reason.INVALID_INPUT
        if (!supportedEncoding(entry.encoding)) return DownloadResult.Reason.UNSUPPORTED_ENCODING
        if (entry.mime != null && (entry.mime.length > MAX_MIME || entry.mime.any { it.isISOControl() })) {
            return DownloadResult.Reason.INVALID_INPUT
        }
        return null
    }

    private fun isSafePath(path: String): Boolean {
        if (path.isEmpty() || path.length > MAX_FIELD || !SAFE_PATH.matches(path)) return false
        val segments = path.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) return false
        return listOf(".part", ".tmp", ".sha256", ".etag").none { path.endsWith(it) }
    }

    private fun supportedEncoding(encoding: String?): Boolean =
        encoding == null || encoding in setOf("identity", "gzip", "deflate")

    private fun isIdentityEncoding(encoding: String?): Boolean = encoding == null || encoding == "identity"

    private fun decode(input: InputStream, encoding: String?): InputStream = when (encoding) {
        "gzip" -> GZIPInputStream(input)
        "deflate" -> InflaterInputStream(input)
        else -> input
    }

    private fun parseHttpsUrl(value: String): HttpUrl? {
        if (value.length !in 8..MAX_BASE_URL) return null
        if (value.any { it.isISOControl() || it == '\\' || it == ' ' }) return null
        val url = value.toHttpUrlOrNull() ?: return null
        if (!url.isHttps || url.host.isEmpty()) return null
        if (url.encodedUsername.isNotEmpty() || url.encodedPassword.isNotEmpty()) return null
        if (url.query != null || url.fragment != null) return null
        return url
    }

    private fun responseUrlHasSafePath(base: HttpUrl, path: String): Boolean =
        base.newBuilder().addPathSegments(path).build().let { it.isHttps && it.host == base.host }

    private fun safeResolve(dir: File, path: String): File? {
        val root = dir.canonicalFile.toPath()
        val resolved = File(dir, path).canonicalFile.toPath()
        return resolved.takeIf { it.startsWith(root) }?.toFile()
    }

    private val httpsClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor(httpsOnlyRedirects())
            .build()
    }

    private fun httpsOnlyRedirects(): Interceptor = Interceptor { chain ->
        var request = chain.request()
        var hops = 0
        var response = chain.proceed(request)
        while (response.isRedirect) {
            val location = response.header("Location") ?: break
            val next = request.url.resolve(location) ?: throw IOException("Invalid redirect")
            response.close()
            if (parseHttpsUrl(next.toString()) == null || next.host != request.url.host) {
                throw IOException("Insecure redirect blocked")
            }
            if (++hops > 5) throw IOException("Too many redirects")
            request = request.newBuilder().url(next).build()
            response = chain.proceed(request)
        }
        response
    }

    private fun readEtag(file: File): String? = try {
        file.readText(StandardCharsets.UTF_8).trim().takeIf {
            it.isNotEmpty() && it.length <= MAX_ETAG && it.none { c -> c.isISOControl() }
        }
    } catch (error: IOException) {
        null
    }

    private fun digestFile(file: File, digest: MessageDigest) {
        file.inputStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
    }

    private fun sha256(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digestFile(file, digest)
        return hex(digest.digest())
    }

    private fun writeAtomic(file: File, bytes: ByteArray) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        try {
            Files.write(temp.toPath(), bytes)
            moveAtomic(temp, file)
        } finally {
            runCatching { temp.delete() }
        }
    }

    private fun moveAtomic(from: File, to: File) {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (error: AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun stringField(obj: JSONObject, name: String, max: Int): String {
        val value = obj.opt(name) ?: throw IllegalArgumentException("Missing $name")
        require(value is String && value.isNotEmpty() && value.length <= max) { "Invalid $name" }
        return value
    }

    private fun optionalStringField(obj: JSONObject, name: String, max: Int): String? {
        val value = obj.opt(name) ?: return null
        if (value === JSONObject.NULL) return null
        require(value is String && value.length <= max) { "Invalid $name" }
        return value.takeIf { it.isNotEmpty() }
    }

    private fun longField(obj: JSONObject, name: String, min: Long): Long {
        val value = obj.opt(name) ?: throw IllegalArgumentException("Missing $name")
        require((value is Int || value is Long) && (value as Number).toLong() >= min) { "Invalid $name" }
        return (value as Number).toLong()
    }

    private fun requireHex(value: String, exactBytes: Int?): ByteArray {
        require(value.length <= MAX_SIGNATURE_HEX && value.length % 2 == 0) { "Invalid hex field" }
        val bytes = decodeHex(value) ?: throw IllegalArgumentException("Invalid hex field")
        if (exactBytes != null) require(bytes.size == exactBytes) { "Invalid hex field length" }
        return bytes
    }

    private fun decodeHex(value: String): ByteArray? {
        if (value.length % 2 != 0) return null
        val out = ByteArray(value.length / 2)
        for (index in out.indices) {
            val high = hexNibble(value[index * 2])
            val low = hexNibble(value[index * 2 + 1])
            if (high < 0 || low < 0) return null
            out[index] = ((high shl 4) or low).toByte()
        }
        return out
    }

    private fun hexNibble(char: Char): Int = when (char) {
        in '0'..'9' -> char - '0'
        in 'a'..'f' -> char - 'a' + 10
        in 'A'..'F' -> char - 'A' + 10
        else -> -1
    }

    private fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        val digits = "0123456789abcdef"
        for (byte in bytes) {
            val value = byte.toInt() and 0xff
            append(digits[value shr 4])
            append(digits[value and 0x0f])
        }
    }

    private fun appendQuoted(out: StringBuilder, value: String) {
        out.append('"')
        for (char in value) when {
            char == '"' -> out.append("\\\"")
            char == '\\' -> out.append("\\\\")
            char == '\n' -> out.append("\\n")
            char == '\r' -> out.append("\\r")
            char == '\t' -> out.append("\\t")
            char == '\b' -> out.append("\\b")
            char == '\u000C' -> out.append("\\f")
            char.code < 0x20 -> {
                val digits = Integer.toHexString(char.code)
                out.append("\\u")
                repeat(4 - digits.length) { out.append('0') }
                out.append(digits)
            }
            else -> out.append(char)
        }
        out.append('"')
    }
}
