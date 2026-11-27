package pw.c0re.stream.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Downloads a release APK over HTTPS and refuses to install it unless the SHA-256 checksum and
 * the signing certificate match the currently installed app. This prevents a compromised
 * update manifest from replacing the app with foreign code.
 */
object UpdateInstaller {
    private const val maxApkBytes = 250L * 1024 * 1024
    private const val updateDir = "updates"

    sealed interface Result {
        data class Ready(val file: File) : Result
        data class Failure(val reason: String) : Result
    }

    suspend fun downloadAndVerify(
        context: Context,
        info: UpdateChecker.UpdateInfo,
    ): Result = withContext(Dispatchers.IO) {
        val url = info.apkUrl ?: return@withContext Result.Failure("No APK URL")
        val expected = info.sha256 ?: return@withContext Result.Failure("No checksum")
        if (!NetworkPolicy.isHttps(url)) return@withContext Result.Failure("APK URL must use HTTPS")
        val directory = File(context.cacheDir, updateDir).apply { mkdirs() }
        val target = File(directory, "C0reStream-${info.latestVersion.take(32)}.apk")
        runCatching {
            NetworkPolicy.downloadClient().newCall(Request.Builder().url(NetworkPolicy.requireUrl(url)).build())
                .execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    val body = response.body
                    check(body.contentLength() in -1..maxApkBytes) { "APK too large" }
                    body.byteStream().use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var total = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                check(total <= maxApkBytes) { "APK too large" }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                }
        }.onFailure { target.delete(); return@withContext Result.Failure(it.message ?: "Download failed") }

        val digest = sha256(target)
        if (!digest.equals(expected, ignoreCase = true)) {
            target.delete()
            return@withContext Result.Failure("Checksum mismatch")
        }
        val archive = packageArchive(context, target)
            ?: run { target.delete(); return@withContext Result.Failure("Invalid APK") }
        if (archive.packageName != context.packageName) {
            target.delete()
            return@withContext Result.Failure("Package name mismatch")
        }
        if (archive.longVersionCode <= currentVersionCode(context)) {
            target.delete()
            return@withContext Result.Failure("APK is not newer")
        }
        if (!sameSigner(context, archive)) {
            target.delete()
            return@withContext Result.Failure("Signing certificate mismatch")
        }
        directory.listFiles()?.filter { it != target && it.name.endsWith(".apk") }?.forEach { it.delete() }
        Result.Ready(target)
    }

    /**
     * Commits the verified APK to Android's PackageInstaller. The system shows its own
     * confirmation UI; no silent installation is attempted.
     */
    fun install(context: Context, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(context.packageName) }
        val sessionId = installer.createSession(params)
        val callback = PendingIntent.getBroadcast(
            context,
            sessionId,
            Intent(context, pw.c0re.stream.receiver.UpdateInstallReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val session = installer.openSession(sessionId)
        try {
            file.inputStream().use { input ->
                session.openWrite("c0restream-update", 0L, file.length()).use { output ->
                    input.copyTo(output)
                }
            }
            session.commit(callback.intentSender)
        } finally {
            runCatching { session.close() }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Suppress("DEPRECATION")
    private fun packageArchive(context: Context, file: File): android.content.pm.PackageInfo? =
        context.packageManager.getPackageArchiveInfo(
            file.absolutePath,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )

    @Suppress("DEPRECATION")
    private fun currentVersionCode(context: Context): Long = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
    }.getOrDefault(0L)

    @Suppress("DEPRECATION")
    private fun sameSigner(context: Context, archive: android.content.pm.PackageInfo): Boolean {
        val installed = runCatching {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }.getOrNull() ?: return false
        val installedSigners = installed.signingInfo?.apkContentsSigners ?: return false
        val archiveSigners = archive.signingInfo?.apkContentsSigners ?: return false
        val installedHashes = installedSigners.map { sha256(it.toByteArray()) }.toSet()
        val archiveHashes = archiveSigners.map { sha256(it.toByteArray()) }.toSet()
        return installedHashes == archiveHashes && installedHashes.isNotEmpty()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { String.format(Locale.ROOT, "%02x", it) }
}
