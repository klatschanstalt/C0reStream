package pw.c0re.stream.data.profiles

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Storage for local profiles. PIN verification state is intentionally not persisted here. */
@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY name")
    fun all(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles ORDER BY name")
    suspend fun allOnce(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun byId(id: Long): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity): Long

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int
}
