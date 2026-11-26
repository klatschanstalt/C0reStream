package tv.ham3l.app.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tv.ham3l.app.data.db.AppDatabase
import tv.ham3l.app.data.model.EpgEntry

/**
 * EPG-Cache pro Playlist mit 30-Minuten-TTL.
 * Liefert fuer einen Kanal (tvg-id) das aktuell laufende Programm.
 */
class EpgStore(private val db: AppDatabase, private val repo: PlaylistRepository) {

    private val cache = mutableMapOf<Long, Pair<Long, Map<String, List<EpgEntry>>>>()
    private val ttlMs = 30 * 60 * 1000L
    private val mutex = Mutex()

    suspend fun nowFor(playlistId: Long, tvgId: String?): EpgEntry? {
        if (tvgId.isNullOrBlank()) return null
        val now = System.currentTimeMillis()
        val epgMap = mutex.withLock {
            val cached = cache[playlistId]
            if (cached != null && now - cached.first < ttlMs) {
                cached.second
            } else {
                val loaded = repo.loadEpgNow(playlistId)
                if (loaded.isEmpty()) {
                    null
                } else {
                    cache[playlistId] = now to loaded
                    loaded
                }
            }
        } ?: return null
        return epgMap[tvgId]?.firstOrNull { entry -> entry.start <= now && now < entry.stop }
            ?: epgMap[tvgId]?.firstOrNull { entry -> entry.start > now }
    }

    // Fuer EPG-Guide: alle Eintraege eines Kanals
    suspend fun dayFor(playlistId: Long, tvgId: String?): List<EpgEntry> {
        nowFor(playlistId, tvgId) // stellt sicher, dass der Cache warm ist
        if (tvgId.isNullOrBlank()) return emptyList()
        val now = System.currentTimeMillis()
        val cached = mutex.withLock { cache[playlistId]?.second } ?: return emptyList()
        return cached[tvgId].orEmpty()
    }

    fun clear(playlistId: Long) { cache.remove(playlistId) }
}
