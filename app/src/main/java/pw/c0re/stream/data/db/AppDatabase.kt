package pw.c0re.stream.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import java.io.File

@Database(
    entities = [
        PlaylistEntity::class,
        ChannelEntity::class,
        FavoriteEntity::class,
        HistoryEntity::class,
        ProgressEntity::class,
        WatchlistEntity::class,
        EpgProgramEntity::class,
        ReminderEntity::class,
        pw.c0re.stream.data.profiles.ProfileEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(ProtectedUrlConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun channelDao(): ChannelDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun progressDao(): ProgressDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun epgDao(): EpgDao
    abstract fun reminderDao(): ReminderDao
    abstract fun profileDao(): pw.c0re.stream.data.profiles.ProfileDao

    companion object {
        private const val databaseName = "c0restream.db"
        private const val legacyDatabaseName = "ham3l.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, resolveDatabaseName(context))
                .addMigrations(encryptUrlsMigration, schemaV4Migration, schemaV5Migration, schemaV6Migration)
                .build()

        /**
         * Rebranding must not orphan an existing library. Rename the legacy database and its
         * WAL/SHM sidecars once; if the rename fails, keep using the legacy name rather than
         * silently opening an empty database.
         */
        private fun resolveDatabaseName(context: Context): String {
            val target = context.getDatabasePath(databaseName)
            val legacy = context.getDatabasePath(legacyDatabaseName)
            if (!legacy.exists() || target.exists()) return databaseName
            target.parentFile?.mkdirs()
            val moved = legacy.renameTo(target)
            if (moved) {
                listOf("-wal", "-shm", "-journal").forEach { suffix ->
                    val old = File(legacy.path + suffix)
                    if (old.exists()) old.renameTo(File(target.path + suffix))
                }
                return databaseName
            }
            return legacyDatabaseName
        }
    }
}
