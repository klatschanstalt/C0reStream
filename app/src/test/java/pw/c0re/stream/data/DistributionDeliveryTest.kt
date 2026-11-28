package pw.c0re.stream.data

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pw.c0re.stream.data.resources.ResourceCatalog
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Locale

/**
 * Hermetic security/regression checks for own-server HTTPS distribution (v1.7.0/v1.7.1 strict
 * profile, v1.7.2 release-asset profile), update metadata and the published signed manifest.
 *
 * The JVM test classpath has no okhttp-tls, so no TLS key is embedded here. The strict profile's
 * guard still sees a genuine HTTPS target URL; only the last hop to the local MockWebServer is
 * rewritten to http://127.0.0.1 after the guard has run. TLS itself is verified in production by
 * OkHttp's default trust manager and by the signing-certificate pin in UpdateInstaller.
 */
class DistributionDeliveryTest {

    private val apkPath = "/distribution/releases/v1.7.2/C0reStream.apk"
    private val ownApkUrl = "https://stream.c0re.pw$apkPath"
    private val publishedSha256 = "0a46693033151ccb0dbe8d55da18f375d0065ff2c484fb45faf6bb1f689c512e"

    private fun ownServerUrl(server: MockWebServer): HttpUrl =
        server.url(apkPath).newBuilder().scheme("https").build()

    /** Strict bounded profile (v1.7.0/v1.7.1 download path), with a local wire bridge only. */
    private fun strictClient(vararg servers: MockWebServer): OkHttpClient {
        val localPorts = servers.map { it.port }.toSet()
        return NetworkPolicy.downloadClient().newBuilder()
            .addInterceptor { chain ->
                val url = chain.request().url
                if (url.isHttps && (url.host == "localhost" || url.host == "127.0.0.1") &&
                    url.port in localPorts
                ) {
                    val local = url.newBuilder().scheme("http").build()
                    chain.proceed(chain.request().newBuilder().url(local).build())
                } else {
                    chain.proceed(chain.request())
                }
            }
            .build()
    }

    @Test
    fun `strict profile accepts a direct HTTPS own-server url with zero redirects and exact body`() {
        val body = "C0reStream APK bytes\nsecond line\n"
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody(body))
            strictClient(server).newCall(Request.Builder().url(ownServerUrl(server)).build())
                .execute().use { response ->
                    assertEquals(200, response.code)
                    assertEquals(body, response.body.string())
                }
            // Zero redirects: exactly one request, no intermediate 3xx hop.
            assertEquals(1, server.requestCount)
            val recorded = server.takeRequest()
            assertEquals(apkPath, recorded.path)
            assertNull(recorded.getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `strict profile still blocks an HTTPS to HTTP downgrade redirect`() {
        val first = MockWebServer()
        val second = MockWebServer()
        try {
            val plaintext = second.url("/plain.apk").newBuilder().scheme("http").build().toString()
            first.enqueue(
                MockResponse().setResponseCode(302).setHeader("Location", plaintext),
            )
            second.enqueue(MockResponse().setBody("plaintext apk"))
            val failure = runCatching {
                strictClient(first, second)
                    .newCall(Request.Builder().url(ownServerUrl(first)).build())
                    .execute().use { }
            }.exceptionOrNull()
            assertTrue(failure is IOException)
            assertTrue(failure!!.message!!.contains("HTTPS downgrade"))
            assertEquals("no plaintext hop may be performed", 0, second.requestCount)
        } finally {
            first.shutdown()
            second.shutdown()
        }
    }

    @Test
    fun `strict profile blocks cross-origin redirects carrying sig jwt username or password`() {
        listOf("sig", "jwt", "username", "password").forEach { name ->
            val first = MockWebServer()
            val second = MockWebServer()
            try {
                val credentialTarget = second.url("/final.apk").newBuilder().scheme("https")
                    .query("$name=SECRET").build().toString()
                first.enqueue(
                    MockResponse().setResponseCode(302).setHeader("Location", credentialTarget),
                )
                second.enqueue(MockResponse().setBody("credential target"))
                val failure = runCatching {
                    strictClient(first, second)
                        .newCall(Request.Builder().url(ownServerUrl(first)).build())
                        .execute().use { }
                }.exceptionOrNull()
                assertTrue("$name must be blocked", failure is IOException)
                assertTrue("$name must be blocked", failure!!.message!!.contains("Credential-bearing"))
                assertEquals("$name must not reach the target", 0, second.requestCount)
            } finally {
                first.shutdown()
                second.shutdown()
            }
        }
    }

    /**
     * Documents the generation gap: v1.7.2's release-asset profile only accepts github.com
     * release URLs. An apkUrl on the own server is therefore rejected by v1.7.2 clients.
     */
    @Test
    fun `release asset profile rejects the published own-server apk url`() {
        val server = MockWebServer()
        try {
            val client = NetworkPolicy.releaseAssetClient().newBuilder()
                .addInterceptor { chain ->
                    val url = chain.request().url
                    if (url.isHttps && (url.host == "localhost" || url.host == "127.0.0.1") &&
                        url.port == server.port
                    ) {
                        val local = url.newBuilder().scheme("http").build()
                        chain.proceed(chain.request().newBuilder().url(local).build())
                    } else {
                        chain.proceed(chain.request())
                    }
                }
                .build()
            server.enqueue(MockResponse().setBody("apk"))
            val failure = runCatching {
                client.newCall(Request.Builder().url(ownServerUrl(server)).build()).execute().use { }
            }.exceptionOrNull()
            assertTrue(failure is IOException)
            assertTrue(failure!!.message!!.contains("Release asset request blocked"))
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `update json validation requires HTTPS and a 64 hex checksum`() {
        val valid = validateUpdateJson(ownApkUrl, publishedSha256)
        assertNotNull(valid)
        assertEquals(publishedSha256, valid!!.sha256)
        assertEquals(ownApkUrl, valid.apkUrl)

        assertNull(validateUpdateJson(ownApkUrl.replace("https://", "http://"), publishedSha256))
        assertNull(validateUpdateJson(ownApkUrl, "abc"))
        assertNull(validateUpdateJson(ownApkUrl, "z".repeat(64)))
        assertNull(validateUpdateJson("https://user:secret@stream.c0re.pw$apkPath", publishedSha256))

        val upper = validateUpdateJson(ownApkUrl, publishedSha256.uppercase(Locale.ROOT))
        assertNotNull(upper)
        assertEquals(publishedSha256, upper!!.sha256)
    }

    /** Spiegelt die Feldvalidierung aus UpdateChecker.check plus UpdateInstaller.validateManifest. */
    private fun validateUpdateJson(url: String, checksum: String): UpdateChecker.UpdateInfo? {
        val json = JSONObject()
            .put("latestVersion", "1.7.2")
            .put("apkUrl", url)
            .put("sha256", checksum)
        val apk = json.optString("apkUrl").trim().takeIf { it.isNotBlank() }
            ?.takeIf { NetworkPolicy.isHttps(it) }
        val sha = json.optString("sha256").trim().lowercase(Locale.ROOT)
            .takeIf { it.matches(Regex("[0-9a-f]{64}")) }
        val info = UpdateChecker.UpdateInfo("1.7.2", null, apk, sha)
        return if (UpdateInstaller.validateManifest(info) == null) info else null
    }

    @Test
    fun `version comparison is numeric and a downgrade is never offered`() {
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.9"))
        assertTrue(UpdateChecker.isNewer("1.11.0", "1.10.9"))
        assertFalse(UpdateChecker.isNewer("1.9.9", "1.10.0"))
        assertFalse(UpdateChecker.isNewer("1.10.0", "1.11.0"))
        assertFalse(UpdateChecker.isNewer("1.7.1", "1.7.2"))
        assertFalse(UpdateChecker.isNewer("1.7.2", "1.7.2"))
        val offered = listOf("1.7.1", "1.7.2", "1.10.0", "1.11.0")
            .filter { UpdateChecker.isNewer(it, "1.7.2") }
        assertEquals(listOf("1.10.0", "1.11.0"), offered)
    }

    @Test
    fun `published resource manifest verifies over exact bytes with production certificate`() {
        val bytes = fixture("resources.json")
        val signatureHex = String(fixture("resources.sig.hex"), StandardCharsets.UTF_8).trim()
        val certificate = publishedCertificate()
        val publicKey = certificate.publicKey
        // SPKI-Pin (ResourceManager) und Zertifikats-Fingerprint (apksigner) sind verschieden.
        assertEquals(
            "4b6a3c36cb9ab8f297d976f81a411d232476dfd1400392b4d72d303db08993c1",
            ResourceCatalog.fingerprint(publicKey),
        )
        assertEquals(
            "65fb26b0f617583228ea8813080fa7e2f8f6421022d8d279f928c726ea6e8543",
            ResourceCatalog.fingerprint(certificate),
        )
        assertTrue(ResourceCatalog.verifySignedBytes(bytes, signatureHex, publicKey, ResourceCatalog.fingerprint(publicKey)))

        // Unabhängig von ResourceCatalog: direkte SHA256withRSA-Prüfung über dieselben Bytes.
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(publicKey)
        verifier.update(bytes)
        assertTrue(verifier.verify(decodeHex(signatureHex)!!))

        // Inhaltliche Änderung bei gültigem JSON muss die Signatur ungültig machen.
        val changed = String(bytes, StandardCharsets.UTF_8)
            .replaceFirst("\"configVersion\": 1", "\"configVersion\": 2")
            .toByteArray(StandardCharsets.UTF_8)
        assertFalse(
            ResourceCatalog.verifySignedBytes(
                changed, signatureHex, publicKey, ResourceCatalog.fingerprint(publicKey),
            ),
        )
        assertFalse(
            ResourceCatalog.verifySignedBytes(
                bytes, signatureHex, publicKey, "0".repeat(64),
            ),
        )
    }

    @Test
    fun `distribution urls with sig jwt or userinfo never reach the local log`() {
        val count = AppLog.lines.size
        AppLog.log(
            "Distribution",
            "GET https://stream.c0re.pw$apkPath?sig=SECRET&jwt=TOKEN",
        )
        val first = AppLog.lines.last()
        assertFalse(first.contains("stream.c0re.pw"))
        assertFalse(first.contains("sig="))
        assertFalse(first.contains("jwt="))
        assertFalse(first.contains("SECRET"))

        AppLog.log("Distribution", "user:password@stream.c0re.pw rejected")
        val second = AppLog.lines.last()
        assertFalse(second.contains("password@"))
        assertTrue(AppLog.lines.size >= count + 2)
    }

    private fun fixture(name: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("distribution/$name")?.use { it.readBytes() }
            ?: error("Missing test fixture distribution/$name")

    private fun publishedCertificate(): X509Certificate {
        val pem = String(fixture("cert.pem"), StandardCharsets.UTF_8)
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(pem.byteInputStream()) as X509Certificate
    }

    private fun decodeHex(value: String): ByteArray? {
        if (value.isEmpty() || value.length % 2 != 0 ||
            value.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }
        ) return null
        return value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
