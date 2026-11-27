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
 * M3U und Xtream werden aktualisiert; lokale Dateien bleiben unverändert.
 */
class PlaylistRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = (applicationContext as C0reStreamApp).database
        val repo = PlaylistRepository(db)
        var ok = 0
        var failed = 0
        db.playlistDao().all().first().forEach { pl ->
            val url = pl.url ?: return@forEach
            val result = if (url.startsWith("xtream://")) runCatching {
                val creds = requireNotNull(pw.c0re.stream.data.XtreamApi.parseSyntheticUrl(url))
                val raw = pw.c0re.stream.data.XtreamApi(okhttp3.OkHttpClient()).fetchLiveChannels(creds)
                repo.storeChannels(pl.name, url, pl.epgUrl, pl.userAgent, pl.useProxy, pl.proxyHost, pl.proxyPort, raw, pl.id)
            } else repo.fetchAndStore(pl.name, url, pl.epgUrl, pl.userAgent, pl.useProxy, pl.proxyHost, pl.proxyPort, pl.id)
            result
                .onSuccess { ok++ }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; failed++; AppLog.log("Refresh", "Playlist-Aktualisierung fehlgeschlagen") }
        }
        AppLog.log("Refresh", "$ok aktualisiert, $failed fehlgeschlagen")
        return if (failed > 0 && runAttemptCount < 2) Result.retry() else Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "ham3l_playlist_refresh",
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<PlaylistRefreshWorker>(12, TimeUnit.HOURS)
                    .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build()).build(),
            )
        }
    }
}
