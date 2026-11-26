package pw.c0re.stream.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Encrypt existing URL columns in place without changing IDs or table structure.
 * Must be registered together with the protected entity mapping, not independently.
 */
internal val encryptUrlsMigration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA secure_delete = ON")
        val columns = mapOf(
            "playlists" to listOf("url", "epgUrl"),
            "channels" to listOf("url", "logoUrl", "catchupSource"),
            "favorites" to listOf("url", "logoUrl"),
            "history" to listOf("url", "logoUrl")
        )
        for ((table, fields) in columns) {
            for (field in fields) {
                var lastId = 0L
                while (true) {
                    val batch = mutableListOf<Pair<Long, String>>()
                    db.query("SELECT id, `$field` FROM `$table` WHERE id > ? AND `$field` IS NOT NULL ORDER BY id LIMIT 128", arrayOf<Any>(lastId)).use { cursor ->
                        while (cursor.moveToNext()) batch.add(cursor.getLong(0) to cursor.getString(1))
                    }
                    if (batch.isEmpty()) break
                    for ((id, value) in batch) {
                        db.execSQL("UPDATE `$table` SET `$field` = ? WHERE id = ?", arrayOf<Any>(UrlCipher.encrypt(value), id))
                        lastId = id
                    }
                }
            }
        }
    }
}
