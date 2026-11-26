package tv.ham3l.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name") fun all(): Flow<List<PlaylistEntity>>
    @Query("SELECT * FROM playlists WHERE id = :id") suspend fun byId(id: Long): PlaylistEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(p: PlaylistEntity): Long
    @Query("DELETE FROM playlists WHERE id = :id") suspend fun delete(id: Long)
    @Query("UPDATE playlists SET channelCount = :count, lastUpdated = :ts WHERE id = :id") suspend fun updateMeta(id: Long, count: Int, ts: Long)
}

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY name") fun byPlaylist(playlistId: Long): Flow<List<ChannelEntity>>
    @Query("SELECT * FROM channels WHERE name LIKE '%' || :q || '%' ORDER BY name LIMIT 100") fun searchAll(q: String): Flow<List<ChannelEntity>>
    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND name LIKE '%' || :q || '%' ORDER BY name") fun search(playlistId: Long, q: String): Flow<List<ChannelEntity>>
    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND `group` = :group ORDER BY name") fun byGroup(playlistId: Long, group: String): Flow<List<ChannelEntity>>
    @Query("SELECT DISTINCT `group` FROM channels WHERE playlistId = :playlistId AND `group` IS NOT NULL ORDER BY `group`") fun groups(playlistId: Long): Flow<List<String>>
    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY id") suspend fun snapshot(playlistId: Long): List<ChannelEntity>
    @Query("DELETE FROM channels WHERE id = :id") suspend fun deleteById(id: Long)
    @Query("SELECT * FROM channels WHERE id = :id") suspend fun byId(id: Long): ChannelEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(channels: List<ChannelEntity>)
    @Query("DELETE FROM channels WHERE playlistId = :playlistId") suspend fun deleteForPlaylist(playlistId: Long)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY name") fun all(): Flow<List<FavoriteEntity>>
    @Query("SELECT COUNT(*) FROM favorites WHERE channelId = :channelId") fun countFor(channelId: Long): Flow<Int>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun add(f: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE channelId = :channelId") suspend fun remove(channelId: Long)
    @Query("DELETE FROM favorites WHERE channelId IN (SELECT id FROM channels WHERE playlistId = :playlistId)") suspend fun removeForPlaylist(playlistId: Long)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY watchedAt DESC LIMIT 50") fun recent(): Flow<List<HistoryEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun add(h: HistoryEntity)
    @Query("DELETE FROM history") suspend fun clear()
    @Query("DELETE FROM history WHERE channelId IN (SELECT id FROM channels WHERE playlistId = :playlistId)") suspend fun removeForPlaylist(playlistId: Long)
}
