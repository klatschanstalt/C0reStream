package pw.c0re.stream.data

import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pw.c0re.stream.data.db.AppDatabase
import pw.c0re.stream.data.db.EpgProgramEntity
import pw.c0re.stream.data.model.EpgEntry

/**
 * Persistent EPG cache with a 30-minute freshness window. Network failures never delete
 * existing guide data.
 */
class EpgStore(private val db: AppDatabase, private val repo: PlaylistRepository) {
    private val mutex = Mutex()
    private val ttlMs = 30 * 60 * 1000L
    private val retentionMs = 7 * 24 * 60 * 60 * 1000L

    suspend fun ensureFresh(playlistId: Long, force: Boolean = false): Boolean = mutex.withLock {
        val playlist = db.playlistDao().byId(playlistId) ?: return@withLock false
        val now = System.currentTimeMillis()
        if (!force && playlist.epgUpdatedAt > 0 && now - playlist.epgUpdatedAt < ttlMs) {
            return@withLock true
        }
        val parsed = try {
            repo.loadEpg(playlistId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLog.log("EPG", error.message ?: "?")
            emptyMap()
        }
        if (parsed.isEmpty()) return@withLock false
        db.withTransaction {
            db.epgDao().deleteForPlaylist(playlistId)
            val rows = ArrayList<EpgProgramEntity>()
            for ((tvgId, entries) in parsed) {
                if (tvgId.isBlank()) continue
                for (entry in entries) {
                    if (entry.start <= 0 || entry.stop <= entry.start) continue
                    rows += EpgProgramEntity(
                        playlistId = playlistId,
                        tvgId = tvgId.take(256),
                        start = entry.start,
                        stop = entry.stop,
                        title = entry.title.take(8192),
                        description = entry.description?.take(65536),
                    )
                }
            }
            rows.chunked(1000).forEach { chunk -> db.epgDao().insertAll(chunk) }
            db.playlistDao().setEpgUpdated(playlistId, now)
            db.epgDao().deleteOld(now - retentionMs)
        }
        true
    }

    suspend fun nowFor(playlistId: Long, tvgId: String?): EpgEntry? {
        if (tvgId.isNullOrBlank()) return null
        try {
            ensureFresh(playlistId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLog.log("EPG", error.message ?: "?")
        }
        val now = System.currentTimeMillis()
        return db.epgDao().current(playlistId, tvgId, now)?.toModel()
            ?: db.epgDao().next(playlistId, tvgId, now)?.toModel()
    }

    suspend fun guideFor(playlistId: Long, tvgId: String?): List<EpgEntry> {
        if (tvgId.isNullOrBlank()) return emptyList()
        try {
            ensureFresh(playlistId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLog.log("EPG", error.message ?: "?")
        }
        val now = System.currentTimeMillis()
        val entries = db.epgDao().range(playlistId, tvgId, now - 6 * 60 * 60 * 1000L, now + 48 * 60 * 60 * 1000L)
        return if (entries.isEmpty()) {
            db.epgDao().byChannel(playlistId, tvgId).map { it.toModel() }
        } else {
            entries.map { it.toModel() }
        }
    }

    suspend fun refresh(playlistId: Long): Boolean = ensureFresh(playlistId, force = true)

    suspend fun clear(playlistId: Long) {
        db.epgDao().deleteForPlaylist(playlistId)
        db.playlistDao().setEpgUpdated(playlistId, 0)
    }

    private fun EpgProgramEntity.toModel() = EpgEntry(
        start = start,
        stop = stop,
        title = title,
        description = description,
    )
}
