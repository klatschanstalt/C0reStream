package pw.c0re.stream.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds provider order and parsed channel metadata. Existing rows keep their IDs and order 0. */
internal val schemaV6Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE channels ADD COLUMN orderIndex INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE channels ADD COLUMN tvgName TEXT")
        db.execSQL("ALTER TABLE channels ADD COLUMN channelNumber TEXT")
        db.execSQL("ALTER TABLE channels ADD COLUMN quality TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_channels_playlistId_orderIndex ON channels (playlistId, orderIndex)")
    }
}
