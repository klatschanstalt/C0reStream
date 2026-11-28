package pw.c0re.stream.data.resources

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest

class ResourceCatalogTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `canonical bytes have fixed order and sorted entries`() {
        val first = entry("a", "a.txt")
        val second = entry("b", "b.txt", sha = sha256("other".toByteArray(StandardCharsets.UTF_8)))
        val manifest = ResourceManifest(
            configVersion = 7,
            baseUrl = "https://example.test/assets",
            entries = listOf(first, second),
        )
        val bytes = ResourceCatalog.serialize(manifest)
        val reversed = ResourceCatalog.serialize(manifest.copy(entries = listOf(second, first)))
        assertArrayEquals(bytes, reversed)

        val text = String(bytes, StandardCharsets.UTF_8)
        assertTrue(text.startsWith("{\"schemaVersion\":1,\"configVersion\":7,\"baseUrl\":"))
        assertTrue(text.indexOf("\"key\":\"a\"") < text.indexOf("\"key\":\"b\""))
        assertTrue(text.indexOf("\"path\"") < text.indexOf("\"version\""))
        assertEquals(manifest, ResourceCatalog.parse(bytes))
    }

    @Test
    fun `parse rejects unsupported schema, bad urls, bad hashes and duplicate keys`() {
        assertThrows(IllegalArgumentException::class.java) {
            ResourceCatalog.parse(manifestJson(schema = 2))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ResourceCatalog.parse(manifestJson(config = 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ResourceCatalog.parse(manifestJson(base = "http://example.test/assets"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ResourceCatalog.parse(manifestJson(base = "https://user:pass@example.test/assets"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ResourceCatalog.parse(manifestJson(entries = "[" + badHashEntry() + "]"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ResourceCatalog.parse(manifestJson(entries = "[" + entryJson("a") + "," + entryJson("a") + "]"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ResourceCatalog.parse(manifestJson(entries = "[" + entryJson("x".repeat(257)) + "]"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ResourceCatalog.parse(manifestJson(entries = "[" + entryJson("a", path = "../secret") + "]"))
        }

        val many = JSONArray()
        repeat(1_001) { many.put(JSONObject(entryJson("k$it", path = "f$it.txt"))) }
        assertThrows(IllegalArgumentException::class.java) {
            ResourceCatalog.parse(
                JSONObject().put("schemaVersion", 1).put("configVersion", 1)
                    .put("baseUrl", "https://example.test/assets").put("entries", many)
                    .toString().toByteArray(StandardCharsets.UTF_8),
            )
        }
    }

    @Test
    fun `signature verification rejects tampering and wrong keys`() {
        val keys = keys()
        val bytes = ResourceCatalog.serialize(singleManifest())
        val signature = ResourceCatalog.sign(bytes, keys.private)
        val fingerprint = ResourceCatalog.fingerprint(keys.public)

        assertTrue(ResourceCatalog.verify(bytes, signature, keys.public))
        assertTrue(ResourceCatalog.verify(bytes, signature, keys.public, fingerprint))
        assertFalse(ResourceCatalog.verify(bytes, signature, keys.public, "0".repeat(64)))
        assertFalse(ResourceCatalog.verify(bytes, "zz", keys.public))
        assertFalse(ResourceCatalog.verify(bytes, signature, keys().public))

        val tampered = bytes.copyOf()
        tampered[tampered.lastIndex] = (tampered[tampered.lastIndex].toInt() xor 1).toByte()
        assertFalse(ResourceCatalog.verify(tampered, signature, keys.public))
    }

    @Test
    fun `published manifest bytes verify without canonical re-serialization`() {
        val keys = keys()
        val published = manifestJson()
        val signature = ResourceCatalog.sign(published, keys.private)
        val fingerprint = ResourceCatalog.fingerprint(keys.public)

        assertTrue(ResourceCatalog.verifySignedBytes(published, signature, keys.public, fingerprint))
        assertFalse(ResourceCatalog.verifySignedBytes(published, signature, keys().public, fingerprint))
        val tampered = published.copyOf()
        tampered[tampered.lastIndex] = (tampered[tampered.lastIndex].toInt() xor 1).toByte()
        assertFalse(ResourceCatalog.verifySignedBytes(tampered, signature, keys.public, fingerprint))

        // The published file format differs from the canonical form; verifying the latter would
        // reject every release manifest.
        assertFalse(
            ResourceCatalog.verifySignedBytes(
                ResourceCatalog.serialize(ResourceCatalog.parse(published)),
                signature,
                keys.public,
                fingerprint,
            ),
        )
    }

    @Test
    fun `envelope round trip verifies canonical manifest bytes`() {
        val keys = keys()
        val manifest = singleManifest()
        val fingerprint = ResourceCatalog.fingerprint(keys.public)
        val envelope = SignedResourceEnvelope(
            manifest,
            ResourceCatalog.sign(ResourceCatalog.serialize(manifest), keys.private),
            fingerprint,
        )
        val parsed = ResourceCatalog.parseEnvelope(ResourceCatalog.serializeEnvelope(envelope))
        assertEquals(manifest, parsed.manifest)
        assertTrue(ResourceCatalog.verifyEnvelope(parsed, keys.public, fingerprint))
        assertFalse(ResourceCatalog.verifyEnvelope(parsed, keys().public, fingerprint))
        assertFalse(ResourceCatalog.verifyEnvelope(parsed, keys.public, "1".repeat(64)))
    }

    @Test
    fun `download validation fails closed without network access`() = runBlocking {
        val dir = temp.root
        val valid = entry()
        assertEquals(
            DownloadResult.Failure(DownloadResult.Reason.INVALID_INPUT),
            ResourceCatalog.download("http://example.test/assets", valid, dir),
        )
        assertEquals(
            DownloadResult.Failure(DownloadResult.Reason.UNSAFE_PATH),
            ResourceCatalog.download("https://example.test/assets", valid.copy(path = "../secret"), dir),
        )
        assertEquals(
            DownloadResult.Failure(DownloadResult.Reason.SIZE_EXCEEDED),
            ResourceCatalog.download(
                "https://example.test/assets",
                valid.copy(bytes = ResourceCatalog.MAX_RESOURCE_BYTES + 1),
                dir,
            ),
        )
        assertEquals(
            DownloadResult.Failure(DownloadResult.Reason.UNSUPPORTED_ENCODING),
            ResourceCatalog.download("https://example.test/assets", valid.copy(encoding = "bz2"), dir),
        )
    }

    @Test
    fun `activate writes marker atomically only for verified versions`() {
        val root = temp.root
        verifiedVersion(root, 3)
        File(root, "ACTIVE.tmp").writeText("stale")
        assertTrue(ResourceCatalog.activate(root, 3))
        assertEquals("3", File(root, "ACTIVE").readText(StandardCharsets.UTF_8).trim())
        assertFalse(File(root, "ACTIVE.tmp").exists())

        val unverified = File(root, "v4")
        unverified.mkdirs()
        File(unverified, "x.bin").writeText("x")
        assertFalse(ResourceCatalog.activate(root, 4))
        assertEquals(3L, ResourceCatalog.activeVersion(root)!!)
    }

    @Test
    fun `latest available ignores unverified versions and rollback is bounded`() {
        val root = temp.root
        verifiedVersion(root, 1)
        verifiedVersion(root, 2)
        val unverified = File(root, "v3")
        unverified.mkdirs()
        File(unverified, "x.bin").writeText("x")

        assertEquals(2L, ResourceCatalog.latestAvailable(root)!!)
        assertTrue(ResourceCatalog.activate(root, 2))
        assertEquals(1L, ResourceCatalog.rollback(root)!!)
        assertEquals(1L, ResourceCatalog.activeVersion(root)!!)
        assertNull(ResourceCatalog.rollback(root))
    }

    @Test
    fun `cleanup protects active, removes stale versions and temp files`() {
        val root = temp.root
        verifiedVersion(root, 1)
        verifiedVersion(root, 2)
        val stale = File(root, "v3")
        stale.mkdirs()
        File(stale, "x.bin").writeText("x")
        assertTrue(ResourceCatalog.activate(root, 2))

        val active = File(root, "v2")
        File(active, "junk.part").writeText("partial")
        File(active, "gone.bin.sha256").writeText("0".repeat(64))
        val oversized = File(active, "huge.bin")
        RandomAccessFile(oversized, "rw").use { it.setLength(ResourceCatalog.MAX_RESOURCE_BYTES + 1) }
        File(root, "stray.tmp").writeText("x")

        ResourceCatalog.cleanup(root, 0)

        assertFalse(File(root, "v1").exists())
        assertFalse(stale.exists())
        assertTrue(active.isDirectory)
        assertTrue(File(active, "data.bin").isFile)
        assertFalse(File(active, "junk.part").exists())
        assertFalse(File(active, "gone.bin.sha256").exists())
        assertFalse(oversized.exists())
        assertFalse(File(root, "stray.tmp").exists())
        assertEquals(2L, ResourceCatalog.activeVersion(root)!!)
    }

    @Test
    fun `signed empty manifest activates without any resource files`() {
        val dir = temp.root
        val keys = keys()
        val bytes = manifestJson(config = 5, entries = "[]")
        val signature = ResourceCatalog.sign(bytes, keys.private)
        val fingerprint = ResourceCatalog.fingerprint(keys.public)

        assertTrue(ResourceCatalog.verifySignedBytes(bytes, signature, keys.public, fingerprint))
        val manifest = ResourceCatalog.parse(bytes)
        assertTrue(manifest.entries.isEmpty())

        // The non-empty activation path must fail for an empty manifest: it requires resources.
        assertFalse(ResourceCatalog.activate(dir, 5))
        assertNull(ResourceCatalog.activeVersion(dir))

        assertTrue(ResourceCatalog.activateManifest(dir, manifest))
        assertEquals(5L, ResourceCatalog.activeVersion(dir)!!)
        assertEquals(5L, ResourceCatalog.latestAvailable(dir)!!)
        assertTrue(File(File(dir, "v5"), "EMPTY").isFile)
    }

    @Test
    fun `non-empty manifest activates only with fully staged resources`() {
        val dir = temp.root
        val manifest = singleManifest()

        assertFalse(ResourceCatalog.activateManifest(dir, manifest))
        assertNull(ResourceCatalog.activeVersion(dir))

        stageEntry(dir, manifest.entries[0], "hello")
        assertTrue(ResourceCatalog.activateManifest(dir, manifest))
        assertEquals(1L, ResourceCatalog.activeVersion(dir)!!)
        assertEquals(1L, ResourceCatalog.latestAvailable(dir)!!)
    }

    @Test
    fun `invalid signature never reaches activation`() {
        val dir = temp.root
        val keys = keys()
        val bytes = manifestJson(config = 6, entries = "[]")
        val wrongSignature = ResourceCatalog.sign(bytes, keys().private)
        val fingerprint = ResourceCatalog.fingerprint(keys.public)

        assertFalse(ResourceCatalog.verifySignedBytes(bytes, wrongSignature, keys.public, fingerprint))
        // ResourceManager gates activation on verification; mirror that order here.
        if (ResourceCatalog.verifySignedBytes(bytes, wrongSignature, keys.public, fingerprint)) {
            ResourceCatalog.activateManifest(dir, ResourceCatalog.parse(bytes))
        }
        assertNull(ResourceCatalog.activeVersion(dir))
        assertNull(ResourceCatalog.latestAvailable(dir))

        val nonEmptyBytes = ResourceCatalog.serialize(singleManifest())
        val wrongNonEmpty = ResourceCatalog.sign(nonEmptyBytes, keys().private)
        assertFalse(ResourceCatalog.verifySignedBytes(nonEmptyBytes, wrongNonEmpty, keys.public, fingerprint))
        if (ResourceCatalog.verifySignedBytes(nonEmptyBytes, wrongNonEmpty, keys.public, fingerprint)) {
            ResourceCatalog.activateManifest(dir, ResourceCatalog.parse(nonEmptyBytes))
        }
        assertNull(ResourceCatalog.activeVersion(dir))
    }

    @Test
    fun `missing expected resource and corrupt resource fail closed`() {
        val dir = temp.root
        val manifest = singleManifest()
        val versionDir = File(dir, "v1")
        versionDir.mkdirs()

        // A valid unrelated file is not the resource the signed manifest requires.
        File(versionDir, "other.txt").writeText("hello")
        File(versionDir, "other.txt.sha256").writeText(sha256("hello".toByteArray(StandardCharsets.UTF_8)))
        assertFalse(ResourceCatalog.activateManifest(dir, manifest))
        assertNull(ResourceCatalog.activeVersion(dir))

        // The expected file exists but its checksum sidecar is corrupt.
        File(versionDir, "a.txt").writeText("hello")
        File(versionDir, "a.txt.sha256").writeText("0".repeat(64))
        assertFalse(ResourceCatalog.activateManifest(dir, manifest))
        assertNull(ResourceCatalog.activeVersion(dir))
    }

    @Test
    fun `repeated update is idempotent and never downgrades`() {
        val dir = temp.root
        val newer = ResourceCatalog.parse(manifestJson(config = 2, entries = "[]"))

        assertTrue(ResourceCatalog.activateManifest(dir, newer))
        assertTrue(ResourceCatalog.activateManifest(dir, newer))
        assertEquals(2L, ResourceCatalog.activeVersion(dir)!!)

        val older = ResourceCatalog.parse(manifestJson(config = 1, entries = "[]"))
        assertTrue(ResourceCatalog.activateManifest(dir, older))
        assertEquals(2L, ResourceCatalog.activeVersion(dir)!!)
    }

    @Test
    fun `rollback restores verified and intentional empty versions`() {
        val dir = temp.root
        verifiedVersion(dir, 1)
        val empty = ResourceCatalog.parse(manifestJson(config = 2, entries = "[]"))

        assertTrue(ResourceCatalog.activateManifest(dir, empty))
        assertEquals(2L, ResourceCatalog.activeVersion(dir)!!)
        assertEquals(1L, ResourceCatalog.rollback(dir)!!)
        assertEquals(1L, ResourceCatalog.activeVersion(dir)!!)

        assertTrue(ResourceCatalog.activateEmpty(dir, 2))
        assertEquals(2L, ResourceCatalog.activeVersion(dir)!!)
        assertEquals(1L, ResourceCatalog.rollback(dir)!!)
        assertEquals(1L, ResourceCatalog.activeVersion(dir)!!)
    }

    @Test
    fun `empty activation rejects injected files and cleanup preserves a valid marker`() {
        val dir = temp.root
        val versionDir = File(dir, "v4")
        versionDir.mkdirs()
        File(versionDir, "stray.bin").writeText("x")
        assertFalse(ResourceCatalog.activateEmpty(dir, 4))
        assertNull(ResourceCatalog.activeVersion(dir))

        assertTrue(File(versionDir, "stray.bin").delete())
        assertTrue(ResourceCatalog.activateEmpty(dir, 4))
        assertEquals(4L, ResourceCatalog.activeVersion(dir)!!)

        ResourceCatalog.cleanup(dir)
        assertEquals(4L, ResourceCatalog.latestAvailable(dir)!!)
        assertTrue(File(versionDir, "EMPTY").isFile)

        File(versionDir, "EMPTY").writeText("tampered")
        assertNull(ResourceCatalog.latestAvailable(dir))
        assertNull(ResourceCatalog.rollback(dir))
    }

    private fun stageEntry(root: File, entry: ResourceEntry, content: String) {
        val dir = File(root, "v${entry.version}").apply { mkdirs() }
        File(dir, entry.path).writeText(content)
        File(dir, "${entry.path}.sha256").writeText(sha256(content.toByteArray(StandardCharsets.UTF_8)))
    }

    private fun verifiedVersion(root: File, version: Long) {
        val dir = File(root, "v$version")
        dir.mkdirs()
        val content = "content-$version"
        File(dir, "data.bin").writeText(content)
        File(dir, "data.bin.sha256").writeText(sha256(content.toByteArray(StandardCharsets.UTF_8)))
    }

    private fun singleManifest(): ResourceManifest = ResourceManifest(
        configVersion = 1,
        baseUrl = "https://example.test/assets",
        entries = listOf(entry()),
    )

    private fun entry(
        key: String = "a",
        path: String = "a.txt",
        version: Long = 1,
        sha: String = sha256("hello".toByteArray(StandardCharsets.UTF_8)),
        bytes: Long = 5L,
    ): ResourceEntry = ResourceEntry(key, path, version, sha, bytes)

    private fun keys(): KeyPair = KeyPairGenerator.getInstance("RSA").run {
        initialize(2048)
        generateKeyPair()
    }

    private fun manifestJson(
        schema: Int = 1,
        config: Long = 1,
        base: String = "https://example.test/assets",
        entries: String = "[" + entryJson("a") + "]",
    ): ByteArray = JSONObject()
        .put("schemaVersion", schema)
        .put("configVersion", config)
        .put("baseUrl", base)
        .put("entries", JSONArray(entries))
        .toString().toByteArray(StandardCharsets.UTF_8)

    private fun entryJson(key: String, path: String = "a.txt"): String = JSONObject()
        .put("key", key)
        .put("path", path)
        .put("version", 1)
        .put("sha256", sha256("hello".toByteArray(StandardCharsets.UTF_8)))
        .put("bytes", 5)
        .toString()

    private fun badHashEntry(): String = JSONObject(entryJson("a"))
        .put("sha256", "abc")
        .toString()

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return buildString(digest.size * 2) {
            for (byte in digest) {
                val value = byte.toInt() and 0xff
                append("0123456789abcdef"[value shr 4])
                append("0123456789abcdef"[value and 0x0f])
            }
        }
    }
}
