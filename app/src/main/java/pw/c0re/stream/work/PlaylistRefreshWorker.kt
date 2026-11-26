package pw.c0re.stream.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import pw.c0re.stream.C0reStreamApp
import pw.c0re.stream.data.AppLog
import pw.c0re.stream.data.PlaylistRepository
import java.util.concurrent.TimeUnit

/**
 * Automatischer Playlist-Refresh im Hintergrund (12 h).
 * Xtream-Playlists werden ausgelassen, da deren Login-Refresh bewusst manuell bleibt.
 */
class PlaylistRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = (applicationContext as C0reStreamApp).database
        val repo = PlaylistRepository(db)
        var ok = 0
        var failed = 0
        db.playlistDao().all().first().forEach { pl ->
            val url = pl.url ?: return@forEach
            if (url.startsWith("xtream://")) return@forEach
            repo.fetchAndStore(pl.name, url, pl.epgUrl, pl.userAgent, pl.useProxy, pl.proxyHost, pl.proxyPort, pl.id)
                .onSuccess { ok++ }
                .onFailure { failed++; AppLog.log("Refresh", "Playlist-Aktualisierung fehlgeschlagen") }
        }
        AppLog.log("Refresh", "$ok aktualisiert, $failed fehlgeschlagen")
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "ham3l_playlist_refresh",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PlaylistRefreshWorker>(12, TimeUnit.HOURS).build(),
            )
        }
    }
}
