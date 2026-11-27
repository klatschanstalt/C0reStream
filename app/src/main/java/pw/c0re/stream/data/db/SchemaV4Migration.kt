package pw.c0re.stream.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds stable channel identities, refresh policy, EPG persistence, progress, watchlist and
 * reminders. Existing rows are preserved; stable keys are populated on the next refresh.
 */
internal val schemaV4Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE playlists ADD COLUMN autoRefresh INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE playlists ADD COLUMN refreshHours INTEGER NOT NULL DEFAULT 12")
        db.execSQL("ALTER TABLE playlists ADD COLUMN epgUpdatedAt INTEGER NOT NULL DEFAULT 0")

        db.execSQL("ALTER TABLE channels ADD COLUMN stableKey TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_channels_playlistId_stableKey ON channels (playlistId, stableKey)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS progress (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "playlistId INTEGER NOT NULL, " +
                "mediaKey TEXT NOT NULL, " +
                "title TEXT NOT NULL, " +
                "url TEXT NOT NULL, " +
                "positionMs INTEGER NOT NULL, " +
                "durationMs INTEGER NOT NULL, " +
                "kind TEXT NOT NULL, " +
                "updatedAt INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_progress_mediaKey ON progress (mediaKey)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_progress_updatedAt ON progress (updatedAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_progress_playlistId ON progress (playlistId)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS watchlist (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "playlistId INTEGER NOT NULL, " +
                "mediaKey TEXT NOT NULL, " +
                "title TEXT NOT NULL, " +
                "posterUrl TEXT, " +
                "url TEXT, " +
                "kind TEXT NOT NULL, " +
                "addedAt INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_watchlist_mediaKey ON watchlist (mediaKey)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_watchlist_addedAt ON watchlist (addedAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_watchlist_playlistId ON watchlist (playlistId)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS epg_programs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "playlistId INTEGER NOT NULL, " +
                "tvgId TEXT NOT NULL, " +
                "start INTEGER NOT NULL, " +
                "stop INTEGER NOT NULL, " +
                "title TEXT NOT NULL, " +
                "description TEXT)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_epg_programs_playlistId_tvgId_start " +
                "ON epg_programs (playlistId, tvgId, start)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_epg_programs_playlistId ON epg_programs (playlistId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_epg_programs_stop ON epg_programs (stop)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS reminders (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "channelId INTEGER NOT NULL, " +
                "channelName TEXT NOT NULL, " +
                "tvgId TEXT, " +
                "title TEXT NOT NULL, " +
                "start INTEGER NOT NULL, " +
                "stop INTEGER NOT NULL, " +
                "createdAt INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_reminders_channelId_start " +
                "ON reminders (channelId, start)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_reminders_start ON reminders (start)")
    }
}
