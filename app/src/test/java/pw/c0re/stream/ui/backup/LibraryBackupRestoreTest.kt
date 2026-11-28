package pw.c0re.stream.ui.backup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import pw.c0re.stream.data.AppSettings

/** Pure JVM coverage: a deliberately failing settings store, without Room or Android context. */
class LibraryBackupRestoreTest {

    private val password = "correct horse battery".toCharArray()

    private class FakeSettings(
        private var fail: Boolean = false,
    ) : SettingsBackupPort {
        var restoreCalls = 0
            private set
        var restored: JSONObject? = null
            private set

        override suspend fun snapshot(): AppSettings = AppSettings()
        override fun exportJson(value: AppSettings): JSONObject = JSONObject()
        override suspend fun restoreJson(value: JSONObject?) {
            restoreCalls++
            if (fail) throw IllegalStateException("DataStore unavailable")
            restored = value
        }

        fun failNextRestore() { fail = true }
        fun allowNextRestore() { fail = false }
    }

    private fun envelope(version: Int = 2, withSettings: Boolean = true): ByteArray {
        val root = JSONObject().put("version", version)
        if (withSettings) {
            root.put("settings", JSONObject().put("themeMode", "LIGHT").put("minBufferSec", 12))
        }
        return BackupCrypto.encrypt(root.toString().toByteArray(Charsets.UTF_8), password)
    }

    @Test
    fun `failing settings restore is reported as failed and safe to retry`() = runTest {
        val settings = FakeSettings().apply { failNextRestore() }
        val bytes = envelope()

        val failed = LibraryBackup.restoreSettingsOnly(settings, bytes, password)

        assertFalse(failed.settingsRestored)
        assertEquals(RestorePartStatus.FAILED, failed.settingsStatus)
        assertEquals(RestorePartStatus.NOT_APPLICABLE, failed.libraryStatus)
        assertEquals(RestoreOutcome.FAILED, failed.outcome)
        assertTrue(failed.settingsRetryable)
        assertNotNull(failed.settingsFailure)
        assertNull(settings.restored)

        settings.allowNextRestore()
        val retried = LibraryBackup.restoreSettingsOnly(settings, bytes, password)

        assertTrue(retried.settingsRestored)
        assertEquals(RestorePartStatus.RESTORED, retried.settingsStatus)
        assertEquals(RestoreOutcome.SUCCESS, retried.outcome)
        assertFalse(retried.settingsRetryable)
        assertEquals("LIGHT", settings.restored?.optString("themeMode"))
        assertEquals(2, settings.restoreCalls)
    }

    @Test
    fun `settings absent from backup are not reported as a failure`() = runTest {
        val settings = FakeSettings()
        val legacy = LibraryBackup.restoreSettingsOnly(settings, envelope(version = 1, withSettings = false), password)
        val missing = LibraryBackup.restoreSettingsOnly(settings, envelope(version = 2, withSettings = false), password)

        for (result in listOf(legacy, missing)) {
            assertEquals(RestorePartStatus.NOT_APPLICABLE, result.settingsStatus)
            assertEquals(RestoreOutcome.SUCCESS, result.outcome)
            assertFalse(result.settingsRetryable)
        }
        assertEquals(0, settings.restoreCalls)
    }

    @Test
    fun `cancellation propagates instead of being reported as success`() {
        val cancelled = object : SettingsBackupPort {
            override suspend fun snapshot(): AppSettings = AppSettings()
            override fun exportJson(value: AppSettings): JSONObject = JSONObject()
            override suspend fun restoreJson(value: JSONObject?) = throw CancellationException("stopped")
        }
        val bytes = envelope()

        assertThrows(CancellationException::class.java) {
            runTest { LibraryBackup.restoreSettingsOnly(cancelled, bytes, password) }
        }
    }

    @Test
    fun `outcome separates library and settings status`() {
        assertEquals(
            RestoreOutcome.SUCCESS,
            RestoreResult.classify(RestorePartStatus.RESTORED, RestorePartStatus.RESTORED),
        )
        assertEquals(
            RestoreOutcome.SUCCESS,
            RestoreResult.classify(RestorePartStatus.RESTORED, RestorePartStatus.NOT_APPLICABLE),
        )
        assertEquals(
            RestoreOutcome.PARTIAL,
            RestoreResult.classify(RestorePartStatus.RESTORED, RestorePartStatus.FAILED),
        )
        assertEquals(
            RestoreOutcome.FAILED,
            RestoreResult.classify(RestorePartStatus.FAILED, RestorePartStatus.NOT_APPLICABLE),
        )
        assertEquals(
            RestoreOutcome.CANCELLED,
            RestoreResult.classify(RestorePartStatus.CANCELLED, RestorePartStatus.NOT_APPLICABLE),
        )

        // Legacy construction stays source-compatible but no longer lies about settings.
        val legacyCall = RestoreResult(playlists = 1, channels = 2, settingsRestored = false)
        assertEquals(RestorePartStatus.RESTORED, legacyCall.libraryStatus)
        assertEquals(RestoreOutcome.SUCCESS, legacyCall.outcome)
    }
}
