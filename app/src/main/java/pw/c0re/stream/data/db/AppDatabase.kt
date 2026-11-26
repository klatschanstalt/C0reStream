package pw.c0re.stream.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [PlaylistEntity::class, ChannelEntity::class, FavoriteEntity::class, HistoryEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(ProtectedUrlConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun channelDao(): ChannelDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "ham3l.db")
                .addMigrations(encryptUrlsMigration)
                .build()
    }
}
