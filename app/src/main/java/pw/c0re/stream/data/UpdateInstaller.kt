package pw.c0re.stream.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.SigningInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Downloads a release APK through the narrowly scoped GitHub release-asset profile and checks
 * integrity, identity and signing certificate before handing it to the system installer.
 * Installation is never silent: [install] only opens the system confirmation UI.
 *
 * Failures are kept distinct so a blocked download is not reported as a bad checksum, a bad
 * certificate or a failed install handover.
 */
object UpdateInstaller {
    internal const val MAX_APK_BYTES = 250L * 1024 * 1024
    private const val UPDATE_DIR = "updates"

    sealed interface Result {
        data class Ready(val file: File) : Result

        sealed interface Failure : Result {
            val detail: String
        }

        /** Network, HTTP, redirect or storage problem before an APK was available. */
        data class DownloadFailure(override val detail: String) : Failure

        /** Truncated or checksum-mismatched bytes, invalid archive, wrong package or version. */
        data class IntegrityFailure(override val detail: String) : Failure

        /** The APK is not signed by the certificate of the installed app. */
        data class SignatureFailure(override val detail: String) : Failure
    }

    internal sealed interface DownloadResult {
        data class Downloaded(val file: File) : DownloadResult
        data class Failed(val failure: Result.Failure) : DownloadResult
    }

    sealed interface InstallResult {
        /** The APK was passed to PackageInstaller; the system confirmation is still pending. */
        data object HandedOff : InstallResult

        /** "Install unknown apps" is off; nothing was handed to PackageInstaller. */
        data object PermissionRequired : InstallResult

        data class Failed(val detail: String) : InstallResult
    }

    suspend fun downloadAndVerify(
        context: Context,
        info: UpdateChecker.UpdateInfo,
    ): Result = withContext(Dispatchers.IO) {
        val manifestFailure = validateManifest(info)
        if (manifestFailure != null) return@withContext manifestFailure

        val url = NetworkPolicy.requireUrl(info.apkUrl.orEmpty())
        val directory = File(context.cacheDir, UPDATE_DIR).apply { mkdirs() }
        val target = File(directory, targetName(info))

        // Reuse a previously downloaded file only after the complete verification chain has
        // run again. A tampered or stale cache must never skip a check.
        if (target.isFile) {
            when (val cached = verifyApk(context, target, info)) {
                is Result.Ready -> return@withContext cached
                else -> target.delete()
            }
        }

        when (val downloaded = downloadApk(directory, target, url)) {
            is DownloadResult.Failed -> downloaded.failure
            is DownloadResult.Downloaded -> {
                val verified = verifyApk(context, downloaded.file, info)
                if (verified is Result.Ready) removeStaleApks(directory, target)
                verified
            }
        }
    }

    /** Manifest gate kept separate from the transport so it can be checked without a device. */
    internal fun validateManifest(info: UpdateChecker.UpdateInfo): Result.Failure? {
        val url = info.apkUrl ?: return Result.DownloadFailure("No APK URL")
        if (url.isBlank()) return Result.DownloadFailure("No APK URL")
        // Transport problems are download failures; a missing checksum is an integrity failure.
        // Check transport first so a bad URL is never mislabelled as an integrity problem.
        if (!NetworkPolicy.isHttps(url)) return Result.DownloadFailure("APK URL must use HTTPS")
        val checksum = info.sha256 ?: return Result.IntegrityFailure("No checksum")
        if (checksum.isBlank()) return Result.IntegrityFailure("No checksum")
        return null
    }

    /**
     * Streams the APK into a temporary file and only publishes it after a complete, bounded read.
     * [client] is injectable for hermetic tests; production uses the release-asset profile whose
     * redirect guard permits GitHub's signed asset hop without allowing arbitrary redirects.
     */
    internal suspend fun downloadApk(
        directory: File,
        target: File,
        url: HttpUrl,
        client: OkHttpClient = NetworkPolicy.releaseAssetClient(),
    ): DownloadResult = withContext(Dispatchers.IO) {
        val temp = try {
            File.createTempFile("c0re-update-", ".part", directory)
        } catch (error: Exception) {
            return@withContext DownloadResult.Failed(Result.DownloadFailure(describe(error)))
        }
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext DownloadResult.Failed(Result.DownloadFailure("HTTP ${response.code}"))
                }
                val body = response.body
                val declared = body.contentLength()
                if (declared !in -1..MAX_APK_BYTES) {
                    return@withContext DownloadResult.Failed(Result.DownloadFailure("APK too large"))
                }
                var total = 0L
                body.byteStream().use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_APK_BYTES) {
                                return@withContext DownloadResult.Failed(Result.DownloadFailure("APK too large"))
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                if (declared >= 0 && total != declared) {
                    return@withContext DownloadResult.Failed(Result.IntegrityFailure("Incomplete download"))
                }
                if (total == 0L) {
                    return@withContext DownloadResult.Failed(Result.IntegrityFailure("Empty download"))
                }
            }
            if (!replaceFile(temp, target)) {
                return@withContext DownloadResult.Failed(Result.DownloadFailure("Cannot store APK"))
            }
            DownloadResult.Downloaded(target)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            DownloadResult.Failed(Result.DownloadFailure(describe(error)))
        } finally {
            temp.delete()
        }
    }

    internal fun replaceFile(temp: File, target: File): Boolean {
        if (target.exists() && !target.delete()) return false
        if (temp.renameTo(target)) return true
        if (target.exists()) target.delete()
        return temp.renameTo(target)
    }

    /** Version- and checksum-bound file name; a manifest cannot escape the cache directory. */
    internal fun targetName(info: UpdateChecker.UpdateInfo): String {
        val version = info.latestVersion.take(32)
            .filter { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }
            .ifEmpty { "unknown" }
        val checksum = info.sha256.orEmpty().take(12).filter(Char::isLetterOrDigit)
        val suffix = if (checksum.isEmpty()) "" else "-$checksum"
        return "C0reStream-$version$suffix.apk"
    }

    internal fun verifyChecksum(file: File, expected: String): Result.IntegrityFailure? {
        val actual = sha256(file)
        return if (actual.equals(expected, ignoreCase = true)) null
        else Result.IntegrityFailure("Checksum mismatch")
    }

    private fun verifyApk(
        context: Context,
        file: File,
        info: UpdateChecker.UpdateInfo,
    ): Result {
        val expected = info.sha256 ?: return Result.IntegrityFailure("No checksum")
        verifyChecksum(file, expected)?.let { return it }
        val archive = packageArchive(context, file)
            ?: run { file.delete(); return Result.IntegrityFailure("Invalid APK") }
        if (archive.packageName != context.packageName) {
            file.delete()
            return Result.IntegrityFailure("Package name mismatch")
        }
        if (archive.longVersionCode <= currentVersionCode(context)) {
            file.delete()
            return Result.IntegrityFailure("APK is not newer")
        }
        if (!sameSigner(context, archive)) {
            file.delete()
            return Result.SignatureFailure("Signing certificate mismatch")
        }
        return Result.Ready(file)
    }

    /** Mirrors the platform permission checked before PackageInstaller handover. */
    fun canInstall(context: Context): Boolean = runCatching {
        context.packageManager.canRequestPackageInstalls()
    }.getOrDefault(false)

    /**
     * Commits the already verified APK to Android's PackageInstaller. The system shows its own
     * confirmation UI; no silent installation is attempted. Handover success is not installation
     * success -- the result is reported by [pw.c0re.stream.receiver.UpdateInstallReceiver].
     */
    fun install(context: Context, file: File): InstallResult {
        if (!file.isFile) return InstallResult.Failed("APK missing")
        if (!canInstall(context)) return InstallResult.PermissionRequired

        val installer = context.packageManager.packageInstaller
        var sessionId: Int? = null
        return try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                .apply { setAppPackageName(context.packageName) }
            val id = installer.createSession(params)
            sessionId = id
            val callback = PendingIntent.getBroadcast(
                context,
                id,
                Intent(context, pw.c0re.stream.receiver.UpdateInstallReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val session = installer.openSession(id)
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
            InstallResult.HandedOff
        } catch (error: Exception) {
            sessionId?.let { id -> runCatching { installer.abandonSession(id) } }
            InstallResult.Failed(describe(error))
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
        return digest.digest().joinToString("") { String.format(Locale.ROOT, "%02x", it) }
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
        val installedHashes = signerHashes(installed.signingInfo)
        val archiveHashes = signerHashes(archive.signingInfo)
        return installedHashes == archiveHashes && installedHashes.isNotEmpty()
    }

    /**
     * v1 APKs expose their signer through [SigningInfo.getApkContentsSigners], v2-only APKs only
     * through the certificate history. Both must match exactly; no key rotation is inferred.
     */
    private fun signerHashes(info: SigningInfo?): Set<String> {
        if (info == null) return emptySet()
        return (info.apkContentsSigners.asSequence() + info.signingCertificateHistory.asSequence())
            .map { sha256(it.toByteArray()) }
            .toSet()
    }

    private fun removeStaleApks(directory: File, target: File) {
        directory.listFiles()
            ?.filter { it != target && (it.name.endsWith(".apk") || it.name.endsWith(".part")) }
            ?.forEach { it.delete() }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { String.format(Locale.ROOT, "%02x", it) }

    private fun describe(error: Throwable): String =
        error.message?.take(256) ?: error.javaClass.simpleName
}
