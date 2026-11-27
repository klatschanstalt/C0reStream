package pw.c0re.stream.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pw.c0re.stream.data.db.AppDatabase
import pw.c0re.stream.data.db.PlaylistEntity

@RunWith(AndroidJUnit4::class)
class PlaylistRepositoryInstrumentedTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: PlaylistRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = PlaylistRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `partial refresh keeps previous channels and does not lose favorites`() = runTest {
        val id = db.playlistDao().upsert(PlaylistEntity(name = "P", url = "https://example.com/a.m3u"))
        val initial = (1..10).map { index ->
            M3UParser.RawChannel(
                name = "Channel $index",
                logo = null,
                group = null,
                tvgId = "id-$index",
                url = "https://example.com/$index.ts",
            )
        }
        repo.storeChannels("P", "https://example.com/a.m3u", null, null, false, null, null, initial, id)
        assertEquals(10, db.channelDao().countForPlaylist(id))
        val channelId = db.channelDao().snapshot(id).first().id
        db.favoriteDao().add(
            pw.c0re.stream.data.db.FavoriteEntity(
                channelId = channelId,
                name = "Channel 1",
                logoUrl = null,
                url = "https://example.com/1.ts",
            ),
        )

        val truncated = initial.take(2)
        val failure = runCatching {
            repo.storeChannels("P", "https://example.com/a.m3u", null, null, false, null, null, truncated, id)
        }.exceptionOrNull()
        assertTrue("Expected shrink guard", failure != null)
        assertEquals(10, db.channelDao().countForPlaylist(id))
        assertEquals(1, db.favoriteDao().allOnce().size)
    }

    @Test
    fun `legitimate small refresh still merges stable ids`() = runTest {
        val id = db.playlistDao().upsert(PlaylistEntity(name = "P", url = "https://example.com/a.m3u"))
        val initial = (1..10).map { index ->
            M3UParser.RawChannel(
                name = "Channel $index",
                logo = null,
                group = null,
                tvgId = "id-$index",
                url = "https://example.com/$index.ts",
            )
        }
        repo.storeChannels("P", "https://example.com/a.m3u", null, null, false, null, null, initial, id)
        val before = db.channelDao().snapshot(id).associateBy { it.tvgId }
        val renamed = initial.take(8).map { it.copy(name = it.name + " New") }
        repo.storeChannels("P", "https://example.com/a.m3u", null, null, false, null, null, renamed, id)
        val after = db.channelDao().snapshot(id).associateBy { it.tvgId }
        assertEquals(8, after.size)
        assertEquals(before["id-1"]?.id, after["id-1"]?.id)
        assertTrue(after.getValue("id-1").name.endsWith(" New"))
    }

    @Test
    fun `removing channels also removes their reminders`() = runTest {
        val id = db.playlistDao().upsert(PlaylistEntity(name = "P", url = "https://example.com/a.m3u"))
        val initial = (1..6).map { index ->
            M3UParser.RawChannel(
                name = "Channel $index",
                logo = null,
                group = null,
                tvgId = "id-$index",
                url = "https://example.com/$index.ts",
            )
        }
        repo.storeChannels("P", "https://example.com/a.m3u", null, null, false, null, null, initial, id)
        val channelId = db.channelDao().snapshot(id).first { it.tvgId == "id-1" }.id
        db.reminderDao().upsert(
            pw.c0re.stream.data.db.ReminderEntity(
                channelId = channelId,
                channelName = "Channel 1",
                tvgId = "id-1",
                title = "Show",
                start = 1L,
                stop = 2L,
                createdAt = 0L,
            ),
        )
        val kept = initial.filter { it.tvgId != "id-1" }
        repo.storeChannels("P", "https://example.com/a.m3u", null, null, false, null, null, kept, id)
        assertEquals(0, db.reminderDao().allOnce().size)
        assertEquals(5, db.channelDao().countForPlaylist(id))
    }
}
