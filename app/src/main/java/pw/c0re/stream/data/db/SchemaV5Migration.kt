package pw.c0re.stream.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds local profiles with opaque PIN records. Existing libraries are untouched. */
internal val schemaV5Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `profiles` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`avatarRes` INTEGER, " +
                "`pinHash` TEXT, " +
                "`createdAt` INTEGER NOT NULL)"
        )
    }
}
