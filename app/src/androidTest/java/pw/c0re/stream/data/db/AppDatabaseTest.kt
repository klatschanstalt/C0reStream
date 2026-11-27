package pw.c0re.stream.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun playlistRoundTripKeepsUrls() = runTest {
        val id = db.playlistDao().upsert(
            PlaylistEntity(
                name = "Test",
                url = "https://example.com/playlist.m3u",
                epgUrl = "https://example.com/epg.xml.gz",
            ),
        )
        val stored = db.playlistDao().byId(id)
        assertNotNull(stored)
        assertEquals("https://example.com/playlist.m3u", stored!!.url)
        assertEquals("https://example.com/epg.xml.gz", stored.epgUrl)
        assertEquals(id, stored.id)
    }

    @Test
    fun channelSearchAndGroups() = runTest {
        val playlistId = db.playlistDao().upsert(PlaylistEntity(name = "P", url = "https://example.com/a.m3u"))
        db.channelDao().insertAll(
            listOf(
                ChannelEntity(
                    playlistId = playlistId,
                    name = "Alpha News",
                    logoUrl = null,
                    group = "News",
                    url = "https://example.com/1.ts",
                    tvgId = "alpha",
                    stableKey = "k1",
                ),
                ChannelEntity(
                    playlistId = playlistId,
                    name = "Beta Sport",
                    logoUrl = null,
                    group = "Sport",
                    url = "https://example.com/2.ts",
                    tvgId = "beta",
                    stableKey = "k2",
                ),
            ),
        )
        assertEquals(2, db.channelDao().countForPlaylist(playlistId))
        assertEquals(listOf("News", "Sport"), db.channelDao().groups(playlistId).first())
        assertEquals("Alpha News", db.channelDao().search(playlistId, "alpha").first().single().name)
        assertEquals(1, db.channelDao().byGroup(playlistId, "Sport").first().size)
        assertEquals("k2", db.channelDao().byStableKey(playlistId, "k2")?.stableKey)
    }

    @Test
    fun favoritesAreUniquePerChannel() = runTest {
        db.favoriteDao().add(FavoriteEntity(channelId = 5, name = "A", logoUrl = null, url = "https://e.com/a.ts"))
        db.favoriteDao().add(FavoriteEntity(channelId = 5, name = "A renamed", logoUrl = null, url = "https://e.com/a.ts"))
        assertEquals(1, db.favoriteDao().allOnce().size)
        assertEquals(1, db.favoriteDao().countForOnce(5))
        db.favoriteDao().remove(5)
        assertEquals(0, db.favoriteDao().countForOnce(5))
    }

    @Test
    fun epgCurrentAndNext() = runTest {
        val now = 1_000_000L
        db.epgDao().insertAll(
            listOf(
                EpgProgramEntity(playlistId = 1, tvgId = "c", start = now - 10_000, stop = now + 10_000, title = "Now", description = null),
                EpgProgramEntity(playlistId = 1, tvgId = "c", start = now + 20_000, stop = now + 30_000, title = "Next", description = null),
            ),
        )
        assertEquals("Now", db.epgDao().current(1, "c", now)?.title)
        assertEquals("Next", db.epgDao().next(1, "c", now)?.title)
        assertEquals(2, db.epgDao().countForPlaylist(1))
        // Retention removes programmes that ended before the cutoff, not the upcoming one.
        db.epgDao().deleteOld(now + 15_000)
        assertEquals(1, db.epgDao().countForPlaylist(1))
    }

    @Test
    fun progressUpsertReplacesByMediaKey() = runTest {
        db.progressDao().upsert(
            ProgressEntity(
                playlistId = 1,
                mediaKey = "movie:1:42",
                title = "Movie",
                url = "https://example.com/movie.mp4",
                positionMs = 1_000,
                durationMs = 10_000,
                kind = "movie",
                updatedAt = 1,
            ),
        )
        db.progressDao().upsert(
            ProgressEntity(
                playlistId = 1,
                mediaKey = "movie:1:42",
                title = "Movie",
                url = "https://example.com/movie.mp4",
                positionMs = 5_000,
                durationMs = 10_000,
                kind = "movie",
                updatedAt = 2,
            ),
        )
        val all = db.progressDao().allOnce()
        assertEquals(1, all.size)
        assertEquals(5_000L, all.single().positionMs)
        db.progressDao().deleteForPlaylistAndKey(1, "movie:1:42")
        assertNull(db.progressDao().byKey("movie:1:42"))
    }

    @Test
    fun remindersAreUniquePerChannelStart() = runTest {        val first = ReminderEntity(
            channelId = 7,
            channelName = "Seven",
            tvgId = "seven",
            title = "Show",
            start = 5_000,
            stop = 6_000,
            createdAt = 1,
        )
        db.reminderDao().upsert(first)
        db.reminderDao().upsert(first.copy(title = "Show updated"))
        val stored = db.reminderDao().allOnce()
        assertEquals(1, stored.size)
        assertEquals("Show updated", stored.single().title)
        assertEquals("Show updated", db.reminderDao().byChannelStart(7, 5_000)?.title)
    }

    @Test
    fun profilesRoundTrip() = runTest {
        val id = db.profileDao().upsert(
            pw.c0re.stream.data.profiles.ProfileEntity(
                name = "Kids",
                pinHash = "v1$120000$salt$key",
                createdAt = 1L,
            ),
        )
        val stored = db.profileDao().byId(id)
        assertEquals("Kids", stored?.name)
        assertEquals(1, db.profileDao().count())
        db.profileDao().delete(id)
        assertEquals(0, db.profileDao().count())
    }
}
