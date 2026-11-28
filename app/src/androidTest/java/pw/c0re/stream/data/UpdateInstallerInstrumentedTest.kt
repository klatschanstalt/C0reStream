package pw.c0re.stream.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * Device-side coverage of the handover boundary only. Actual installation is a system-UI action
 * and cannot be forced in a hermetic test; the PackageInstaller result is reported by
 * UpdateInstallReceiver as a separate, terminal stage.
 */
@RunWith(AndroidJUnit4::class)
class UpdateInstallerInstrumentedTest {

    @Test
    fun installPermissionCheckMirrorsPackageManager() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(
            context.packageManager.canRequestPackageInstalls(),
            UpdateInstaller.canInstall(context),
        )
    }

    @Test
    fun cachedApkIsReverifiedBeforeReuse() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bytes = "not a valid apk".toByteArray()
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val info = UpdateChecker.UpdateInfo(
            latestVersion = "1.7.1",
            notes = null,
            apkUrl = "https://example.invalid/C0reStream.apk",
            sha256 = sha,
        )
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(directory, UpdateInstaller.targetName(info)).apply { writeBytes(bytes) }
        try {
            // A matching checksum alone must not be enough: the archive check still rejects it,
            // and no network request is needed to reach that verdict.
            val result = UpdateInstaller.downloadAndVerify(context, info)
            assertTrue(result is UpdateInstaller.Result.IntegrityFailure)
            assertFalse(file.exists())
        } finally {
            file.delete()
        }
    }

    @Test
    fun missingApkFailsBeforeAnyInstallerHandover() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val missing = File(context.cacheDir, "c0re-missing-update.apk")
        missing.delete()
        val result = UpdateInstaller.install(context, missing)
        assertTrue(result is UpdateInstaller.InstallResult.Failed)
        assertTrue(!File(context.cacheDir, "c0re-missing-update.apk").exists())
    }
}
