package pw.c0re.stream.data.resources

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import pw.c0re.stream.data.AppLog
import pw.c0re.stream.data.NetworkPolicy
import pw.c0re.stream.data.readBytes
import java.io.ByteArrayInputStream
import java.io.File
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Loads non-executable resources from public GitHub Release assets. The manifest is signed with
 * the same key that signs the APK, so no separate secret or executable module is needed.
 * Failures keep the previously activated version available offline.
 */
object ResourceManager {
    private const val manifestUrl =
        "https://github.com/klatschanstalt/C0reStream/releases/latest/download/resources.json"
    private const val signatureUrl =
        "https://github.com/klatschanstalt/C0reStream/releases/latest/download/resources.sig"
    private const val directoryName = "resources"

    suspend fun refresh(context: Context): Boolean = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, directoryName).apply { mkdirs() }
        val publicKey = ownPublicKey(context) ?: return@withContext false
        val fingerprint = ResourceCatalog.fingerprint(publicKey)
        try {
            val client = NetworkPolicy.client()
            val manifestBytes = client.newCall(Request.Builder().url(NetworkPolicy.requireUrl(manifestUrl)).build())
                .execute().use { it.readBytes(256 * 1024) }
            val signatureHex = client.newCall(Request.Builder().url(NetworkPolicy.requireUrl(signatureUrl)).build())
                .execute().use { response -> response.readBytes(8 * 1024).toString(Charsets.US_ASCII).trim() }
            val manifest = ResourceCatalog.parse(manifestBytes)
            if (!ResourceCatalog.verifySignedBytes(manifestBytes, signatureHex, publicKey, fingerprint)) {
                AppLog.log("Resources", "Manifest signature rejected")
                return@withContext false
            }
            val current = ResourceCatalog.activeVersion(directory) ?: 0L
            if (manifest.configVersion <= current) return@withContext true
            for (entry in manifest.entries) {
                when (val result = ResourceCatalog.download(manifest.baseUrl, entry, directory)) {
                    is DownloadResult.Ready, DownloadResult.NotModified -> Unit
                    is DownloadResult.Failure -> {
                        AppLog.log("Resources", "Download failed: ${result.reason}")
                        return@withContext false
                    }
                }
            }
            if (!ResourceCatalog.activate(directory, manifest.configVersion)) return@withContext false
            ResourceCatalog.cleanup(directory)
            true
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLog.log("Resources", error.message ?: "refresh failed")
            false
        }
    }

    fun rollback(context: Context): Boolean =
        ResourceCatalog.rollback(File(context.filesDir, directoryName)) != null

    private fun ownPublicKey(context: Context): java.security.PublicKey? {
        val info = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }.getOrNull() ?: return null
        val signer = info.signingInfo?.apkContentsSigners?.firstOrNull() ?: return null
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(signer.toByteArray())) as? X509Certificate
        return certificate?.publicKey
    }

}
