package pw.c0re.stream.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name") fun all(): Flow<List<PlaylistEntity>>
    @Query("SELECT * FROM playlists ORDER BY name") suspend fun allOnce(): List<PlaylistEntity>
    @Query("SELECT * FROM playlists WHERE id = :id") suspend fun byId(id: Long): PlaylistEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(playlist: PlaylistEntity): Long
    @Query("DELETE FROM playlists WHERE id = :id") suspend fun delete(id: Long)
    @Query("UPDATE playlists SET channelCount = :count, lastUpdated = :timestamp WHERE id = :id")
    suspend fun updateMeta(id: Long, count: Int, timestamp: Long)
    @Query("UPDATE playlists SET epgUpdatedAt = :timestamp WHERE id = :id")
    suspend fun setEpgUpdated(id: Long, timestamp: Long)
    @Query("UPDATE playlists SET autoRefresh = :enabled, refreshHours = :hours WHERE id = :id")
    suspend fun setRefreshPolicy(id: Long, enabled: Boolean, hours: Int)
}

@Dao
interface ChannelDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertOne(channel: ChannelEntity): Long
    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY name")
    fun byPlaylist(playlistId: Long): Flow<List<ChannelEntity>>
    @Query("SELECT * FROM channels WHERE name LIKE '%' || :query || '%' ORDER BY name LIMIT 500")
    fun searchAll(query: String): Flow<List<ChannelEntity>>
    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND name LIKE '%' || :query || '%' ORDER BY name LIMIT 5000")
    fun search(playlistId: Long, query: String): Flow<List<ChannelEntity>>
    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND `group` = :group ORDER BY name LIMIT 5000")
    fun byGroup(playlistId: Long, group: String): Flow<List<ChannelEntity>>
    @Query("SELECT DISTINCT `group` FROM channels WHERE playlistId = :playlistId AND `group` IS NOT NULL ORDER BY `group`")
    fun groups(playlistId: Long): Flow<List<String>>
    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY id")
    suspend fun snapshot(playlistId: Long): List<ChannelEntity>
    @Query("SELECT * FROM channels WHERE id = :id") suspend fun byId(id: Long): ChannelEntity?
    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND stableKey = :stableKey LIMIT 1")
    suspend fun byStableKey(playlistId: Long, stableKey: String): ChannelEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(channels: List<ChannelEntity>)
    @Query("DELETE FROM channels WHERE playlistId = :playlistId") suspend fun deleteForPlaylist(playlistId: Long)
    @Query("DELETE FROM channels WHERE id = :id") suspend fun deleteById(id: Long)
    @Query("DELETE FROM channels WHERE id IN (:ids)") suspend fun deleteByIds(ids: List<Long>)
    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId") suspend fun countForPlaylist(playlistId: Long): Int
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY name") fun all(): Flow<List<FavoriteEntity>>
    @Query("SELECT * FROM favorites ORDER BY name") suspend fun allOnce(): List<FavoriteEntity>
    @Query("SELECT COUNT(*) FROM favorites WHERE channelId = :channelId") fun countFor(channelId: Long): Flow<Int>
    @Query("SELECT COUNT(*) FROM favorites WHERE channelId = :channelId") suspend fun countForOnce(channelId: Long): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun add(favorite: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE channelId = :channelId") suspend fun remove(channelId: Long)
    @Query("DELETE FROM favorites WHERE channelId IN (SELECT id FROM channels WHERE playlistId = :playlistId)")
    suspend fun removeForPlaylist(playlistId: Long)
    @Query("DELETE FROM favorites WHERE channelId NOT IN (SELECT id FROM channels)")
    suspend fun removeOrphans()
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY id") suspend fun snapshot(): List<HistoryEntity>
    @Query("SELECT * FROM history ORDER BY watchedAt DESC LIMIT 50") fun recent(): Flow<List<HistoryEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun add(entry: HistoryEntity)
    @Query("DELETE FROM history") suspend fun clear()
    @Query("DELETE FROM history WHERE channelId = :channelId") suspend fun deleteByChannel(channelId: Long)
    @Query("DELETE FROM history WHERE channelId IN (SELECT id FROM channels WHERE playlistId = :playlistId)")
    suspend fun removeForPlaylist(playlistId: Long)
    @Query("DELETE FROM history WHERE channelId NOT IN (SELECT id FROM channels)")
    suspend fun removeOrphans()
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress WHERE mediaKey = :mediaKey LIMIT 1") suspend fun byKey(mediaKey: String): ProgressEntity?
    @Query("SELECT * FROM progress ORDER BY updatedAt DESC LIMIT :limit") fun recent(limit: Int = 50): Flow<List<ProgressEntity>>
    @Query("SELECT * FROM progress ORDER BY updatedAt") suspend fun allOnce(): List<ProgressEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entry: ProgressEntity)
    @Query("DELETE FROM progress WHERE mediaKey = :mediaKey") suspend fun delete(mediaKey: String)
    @Query("DELETE FROM progress WHERE playlistId = :playlistId") suspend fun deleteForPlaylist(playlistId: Long)
    @Query("DELETE FROM progress WHERE playlistId = :playlistId AND mediaKey = :mediaKey")
    suspend fun deleteForPlaylistAndKey(playlistId: Long, mediaKey: String)
}

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC") fun all(): Flow<List<WatchlistEntity>>
    @Query("SELECT * FROM watchlist ORDER BY addedAt") suspend fun allOnce(): List<WatchlistEntity>
    @Query("SELECT * FROM watchlist WHERE mediaKey = :mediaKey LIMIT 1") suspend fun byKey(mediaKey: String): WatchlistEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entry: WatchlistEntity)
    @Query("DELETE FROM watchlist WHERE mediaKey = :mediaKey") suspend fun remove(mediaKey: String)
    @Query("DELETE FROM watchlist WHERE playlistId = :playlistId") suspend fun deleteForPlaylist(playlistId: Long)
}

@Dao
interface EpgDao {
    @Query("DELETE FROM epg_programs WHERE playlistId = :playlistId")
    suspend fun deleteForPlaylist(playlistId: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(entries: List<EpgProgramEntity>)
    @Query("SELECT * FROM epg_programs WHERE playlistId = :playlistId AND tvgId = :tvgId AND start <= :now AND stop > :now ORDER BY start LIMIT 1")
    suspend fun current(playlistId: Long, tvgId: String, now: Long): EpgProgramEntity?
    @Query("SELECT * FROM epg_programs WHERE playlistId = :playlistId AND tvgId = :tvgId AND start > :now ORDER BY start LIMIT 1")
    suspend fun next(playlistId: Long, tvgId: String, now: Long): EpgProgramEntity?
    @Query("SELECT * FROM epg_programs WHERE playlistId = :playlistId AND tvgId = :tvgId AND start >= :from AND start <= :to ORDER BY start")
    suspend fun range(playlistId: Long, tvgId: String, from: Long, to: Long): List<EpgProgramEntity>
    @Query("SELECT * FROM epg_programs WHERE playlistId = :playlistId AND tvgId = :tvgId ORDER BY start")
    suspend fun byChannel(playlistId: Long, tvgId: String): List<EpgProgramEntity>
    @Query("DELETE FROM epg_programs WHERE stop < :before")
    suspend fun deleteOld(before: Long)
    @Query("SELECT COUNT(*) FROM epg_programs WHERE playlistId = :playlistId")
    suspend fun countForPlaylist(playlistId: Long): Int
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY start") suspend fun allOnce(): List<ReminderEntity>
    @Query("SELECT * FROM reminders WHERE start > :now ORDER BY start") suspend fun upcoming(now: Long): List<ReminderEntity>
    @Query("SELECT * FROM reminders WHERE id = :id") suspend fun byId(id: Long): ReminderEntity?
    @Query("SELECT * FROM reminders WHERE channelId = :channelId AND start = :start LIMIT 1")
    suspend fun byChannelStart(channelId: Long, start: Long): ReminderEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(entry: ReminderEntity): Long
    @Query("DELETE FROM reminders WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM reminders WHERE channelId = :channelId AND start = :start")
    suspend fun deleteForChannelStart(channelId: Long, start: Long)
    @Query("DELETE FROM reminders WHERE channelId IN (SELECT id FROM channels WHERE playlistId = :playlistId)")
    suspend fun deleteForPlaylist(playlistId: Long)
    @Query("DELETE FROM reminders WHERE start < :before") suspend fun deleteOld(before: Long)
}
