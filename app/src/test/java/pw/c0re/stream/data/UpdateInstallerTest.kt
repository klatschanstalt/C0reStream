package pw.c0re.stream.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Hermetic coverage of the first two stages: "download succeeded" (a complete, bounded file was
 * published) and "checksum matched" (pure verification). PackageArchive/SigningInfo checks and
 * PackageInstaller handover need an Android device and are covered separately.
 */
class UpdateInstallerTest {

    private fun info(
        url: String? = "https://example.test/C0reStream.apk",
        sha: String? = "",
        version: String = "1.7.1",
    ) = UpdateChecker.UpdateInfo(
        latestVersion = version,
        notes = null,
        apkUrl = url,
        sha256 = sha,
    )

    private fun tempDir(): File = File.createTempFile("update-test-", "").apply {
        delete()
        mkdirs()
    }

    /** A body that claims more bytes than it actually carries, without a socket EOF. */
    private fun declaredLengthClient(declared: Long, actual: String): OkHttpClient {
        val source = Buffer().writeUtf8(actual)
        return OkHttpClient.Builder().addInterceptor { chain ->
            val body = object : ResponseBody() {
                override fun contentType(): MediaType? = null
                override fun contentLength(): Long = declared
                override fun source(): BufferedSource = source
            }
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body)
                .build()
        }.build()
    }

    @Test
    fun `complete download is published and http errors are download failures`() = runBlocking {
        val server = MockWebServer()
        val directory = tempDir()
        val target = File(directory, "C0reStream-1.7.1.apk")
        try {
            server.enqueue(MockResponse().setBody("apk-bytes"))
            val downloaded = UpdateInstaller.downloadApk(
                directory,
                target,
                server.url("/C0reStream.apk"),
                OkHttpClient(),
            )
            assertTrue(downloaded is UpdateInstaller.DownloadResult.Downloaded)
            assertEquals("apk-bytes", target.readText())
            assertTrue(directory.listFiles().orEmpty().none { it.name.endsWith(".part") })

            server.enqueue(MockResponse().setResponseCode(404))
            val failed = UpdateInstaller.downloadApk(
                directory,
                File(directory, "other.apk"),
                server.url("/missing"),
                OkHttpClient(),
            )
            assertTrue(failed is UpdateInstaller.DownloadResult.Failed)
            val failure = (failed as UpdateInstaller.DownloadResult.Failed).failure
            assertTrue(failure is UpdateInstaller.Result.DownloadFailure)
            assertEquals("HTTP 404", failure.detail)
        } finally {
            server.shutdown()
            directory.deleteRecursively()
        }
    }

    @Test
    fun `blocked redirect is a download failure not a checksum or signature failure`() = runBlocking {
        val directory = tempDir()
        try {
            val client = OkHttpClient.Builder().addInterceptor {
                throw IOException("Credential-bearing cross-origin redirect blocked")
            }.build()
            val result = UpdateInstaller.downloadApk(
                directory,
                File(directory, "target.apk"),
                "https://github.test/release.apk".toHttpUrl(),
                client,
            )
            assertTrue(result is UpdateInstaller.DownloadResult.Failed)
            val failure = (result as UpdateInstaller.DownloadResult.Failed).failure
            assertTrue(failure is UpdateInstaller.Result.DownloadFailure)
            assertTrue(failure.detail.contains("Credential-bearing"))
            assertFalse(failure is UpdateInstaller.Result.IntegrityFailure)
            assertFalse(failure is UpdateInstaller.Result.SignatureFailure)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `truncated body is an integrity failure`() = runBlocking {
        val directory = tempDir()
        val target = File(directory, "target.apk")
        try {
            val result = UpdateInstaller.downloadApk(
                directory,
                target,
                "https://example.test/apk".toHttpUrl(),
                declaredLengthClient(1_024L, "short"),
            )
            assertTrue(result is UpdateInstaller.DownloadResult.Failed)
            val failure = (result as UpdateInstaller.DownloadResult.Failed).failure
            assertTrue(failure is UpdateInstaller.Result.IntegrityFailure)
            assertEquals("Incomplete download", failure.detail)
            assertFalse(target.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `oversized declared apk is rejected before it is stored`() = runBlocking {
        val directory = tempDir()
        val target = File(directory, "target.apk")
        try {
            val result = UpdateInstaller.downloadApk(
                directory,
                target,
                "https://example.test/apk".toHttpUrl(),
                declaredLengthClient(UpdateInstaller.MAX_APK_BYTES + 1, "tiny"),
            )
            assertTrue(result is UpdateInstaller.DownloadResult.Failed)
            val failure = (result as UpdateInstaller.DownloadResult.Failed).failure
            assertTrue(failure is UpdateInstaller.Result.DownloadFailure)
            assertEquals("APK too large", failure.detail)
            assertFalse(target.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `cancellation is propagated instead of reported as a verification failure`() {
        val directory = tempDir()
        val failure = runCatching {
            runBlocking {
                val client = OkHttpClient.Builder().addInterceptor {
                    throw CancellationException("cancelled")
                }.build()
                UpdateInstaller.downloadApk(
                    directory,
                    File(directory, "target.apk"),
                    "https://example.test/apk".toHttpUrl(),
                    client,
                )
            }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertTrue(directory.listFiles().orEmpty().none { it.name.endsWith(".part") })
        directory.deleteRecursively()
    }

    @Test
    fun `checksum comparison accepts case and rejects changed bytes`() {
        val directory = tempDir()
        try {
            val bytes = "verified apk".toByteArray()
            val file = File(directory, "apk").apply { writeBytes(bytes) }
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            assertNull(UpdateInstaller.verifyChecksum(file, digest.uppercase()))
            val mismatch = UpdateInstaller.verifyChecksum(file, "0".repeat(64))
            val integrity = mismatch
            assertNotNull(integrity)
            assertEquals("Checksum mismatch", integrity?.detail)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `manifest validation separates missing checksum from bad transport`() {
        assertTrue(UpdateInstaller.validateManifest(info(url = null)) is UpdateInstaller.Result.DownloadFailure)
        assertTrue(
            UpdateInstaller.validateManifest(info(url = "http://example.test/apk")) is
                UpdateInstaller.Result.DownloadFailure,
        )
        assertTrue(
            UpdateInstaller.validateManifest(info(sha = null)) is
                UpdateInstaller.Result.IntegrityFailure,
        )
        assertNull(UpdateInstaller.validateManifest(info(sha = "a".repeat(64))))
    }

    @Test
    fun `cache name is version and checksum bound and cannot escape the directory`() {
        val name = UpdateInstaller.targetName(
            info(url = "https://example.test/apk", sha = "abcdef1234567890", version = "1.7.1/../../evil"),
        )
        assertFalse(name.contains('/'))
        assertFalse(name.contains('\\'))
        assertTrue(name.startsWith("C0reStream-1.7.1"))
        assertTrue(name.contains("-abcdef123456.apk"))
        assertFalse(UpdateInstaller.targetName(info(sha = "abc")) ==
            UpdateInstaller.targetName(info(sha = "def")))
    }

    @Test
    fun `replaceFile swaps an existing cached apk atomically enough for cache use`() {
        val directory = tempDir()
        try {
            val temp = File(directory, "new.part").apply { writeText("new") }
            val target = File(directory, "target.apk").apply { writeText("old") }
            assertTrue(UpdateInstaller.replaceFile(temp, target))
            assertEquals("new", target.readText())
            assertFalse(temp.exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
