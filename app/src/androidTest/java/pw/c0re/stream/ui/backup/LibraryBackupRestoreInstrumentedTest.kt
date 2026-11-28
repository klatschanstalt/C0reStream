package pw.c0re.stream.ui.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pw.c0re.stream.data.AppSettings
import pw.c0re.stream.data.db.AppDatabase
import pw.c0re.stream.data.db.ChannelEntity
import pw.c0re.stream.data.db.PlaylistEntity

@RunWith(AndroidJUnit4::class)
class LibraryBackupRestoreInstrumentedTest {

    private val password = "correct horse battery".toCharArray()
    private lateinit var sourceDb: AppDatabase
    private lateinit var targetDb: AppDatabase

    private class FakeSettings(private val fail: Boolean = false) : SettingsBackupPort {
        var restoreCalls = 0
            private set
        var restored: JSONObject? = null
            private set

        override suspend fun snapshot(): AppSettings = AppSettings()
        override fun exportJson(value: AppSettings): JSONObject = JSONObject().put("themeMode", "LIGHT")
        override suspend fun restoreJson(value: JSONObject?) {
            restoreCalls++
            if (fail) throw IllegalStateException("DataStore unavailable")
            restored = value
        }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        sourceDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        targetDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        sourceDb.close()
        targetDb.close()
    }

    @Test
    fun `failing settings restore leaves library intact and settings-only retry closes the gap`() = runTest {
        val playlistId = sourceDb.playlistDao().upsert(
            PlaylistEntity(name = "Restored", url = "https://example.com/list.m3u"),
        )
        sourceDb.channelDao().insertOne(
            ChannelEntity(
                playlistId = playlistId,
                name = "Channel",
                logoUrl = null,
                group = null,
                url = "https://example.com/live.ts",
                tvgId = null,
                stableKey = "stable-key",
            ),
        )
        val envelope = LibraryBackup.export(sourceDb, FakeSettings(), password)

        val failing = FakeSettings(fail = true)
        val partial = LibraryBackup.restore(targetDb, failing, envelope, password)

        assertEquals(RestorePartStatus.RESTORED, partial.libraryStatus)
        assertEquals(RestorePartStatus.FAILED, partial.settingsStatus)
        assertEquals(RestoreOutcome.PARTIAL, partial.outcome)
        assertFalse(partial.settingsRestored)
        assertTrue(partial.settingsRetryable)
        assertEquals("IllegalStateException", partial.settingsFailure)
        assertEquals(1, failing.restoreCalls)
        val restoredPlaylist = targetDb.playlistDao().allOnce().single()
        assertEquals("Restored", restoredPlaylist.name)
        assertEquals(1, targetDb.channelDao().snapshot(restoredPlaylist.id).size)

        val retrySettings = FakeSettings()
        val retry = LibraryBackup.restoreSettingsOnly(retrySettings, envelope, password)

        assertEquals(RestorePartStatus.NOT_APPLICABLE, retry.libraryStatus)
        assertEquals(RestorePartStatus.RESTORED, retry.settingsStatus)
        assertEquals(RestoreOutcome.SUCCESS, retry.outcome)
        assertEquals("LIGHT", retrySettings.restored?.optString("themeMode"))
        assertEquals(1, targetDb.playlistDao().allOnce().size)
        assertEquals(1, targetDb.channelDao().snapshot(restoredPlaylist.id).size)
    }

    @Test
    fun `cancelled restore is classified as cancelled`() = runTest {
        val playlistId = sourceDb.playlistDao().upsert(
            PlaylistEntity(name = "Cancelled", url = "https://example.com/list.m3u"),
        )
        sourceDb.channelDao().insertOne(
            ChannelEntity(
                playlistId = playlistId,
                name = "Channel",
                logoUrl = null,
                group = null,
                url = "https://example.com/live.ts",
                tvgId = null,
                stableKey = "stable-key",
            ),
        )
        val envelope = LibraryBackup.export(sourceDb, FakeSettings(), password)
        val cancelledSettings = object : SettingsBackupPort {
            override suspend fun snapshot(): AppSettings = AppSettings()
            override fun exportJson(value: AppSettings): JSONObject = JSONObject()
            override suspend fun restoreJson(value: JSONObject?) = throw CancellationException("stopped")
        }

        val result = LibraryBackup.restoreCatching(targetDb, cancelledSettings, envelope, password)

        assertEquals(RestoreOutcome.CANCELLED, result.outcome)
        assertEquals(RestorePartStatus.CANCELLED, result.libraryStatus)
        assertEquals(RestorePartStatus.CANCELLED, result.settingsStatus)
        assertFalse(result.settingsRetryable)
    }
}
