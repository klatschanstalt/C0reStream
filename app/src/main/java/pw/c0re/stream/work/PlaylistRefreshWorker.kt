package pw.c0re.stream.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import pw.c0re.stream.C0reStreamApp
import pw.c0re.stream.data.AppLog
import pw.c0re.stream.data.PlaylistRepository
import java.util.concurrent.TimeUnit

/**
 * Automatic playlist refresh. Local-only playlists are skipped; Xtream and M3U sources are
 * refreshed transactionally with stable channel IDs.
 */
class PlaylistRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as C0reStreamApp
        val settings = try {
            app.settings.snapshot()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLog.log("Refresh", "Settings unavailable: ${error.message ?: "?"}")
            pw.c0re.stream.data.AppSettings()
        }
        if (!settings.autoRefresh) return Result.success()
        val repo = PlaylistRepository(app.database)
        var refreshed = 0
        var failed = 0
        for (playlist in app.database.playlistDao().allOnce()) {
            if (!playlist.autoRefresh || playlist.url == null) continue
            val result = repo.refresh(playlist)
            result.onSuccess { refreshed++ }.onFailure { error ->
                if (error is CancellationException) throw error
                failed++
                AppLog.log("Refresh", "Playlist refresh failed")
            }
        }
        AppLog.log("Refresh", "$refreshed refreshed, $failed failed")
        return if (failed > 0 && runAttemptCount < 2) Result.retry() else Result.success()
    }

    companion object {
        private const val workName = "c0restream_playlist_refresh"

        fun schedule(context: Context, hours: Int = 12, enabled: Boolean = true) {
            if (!enabled) {
                WorkManager.getInstance(context).cancelUniqueWork(workName)
                return
            }
            val intervalHours = hours.coerceIn(1, 168).toLong()
            val request = PeriodicWorkRequestBuilder<PlaylistRefreshWorker>(intervalHours, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
