package pw.c0re.stream.work

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.CoroutineWorker
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import pw.c0re.stream.BuildConfig
import pw.c0re.stream.C0reStreamApp
import pw.c0re.stream.MainActivity
import pw.c0re.stream.R
import pw.c0re.stream.data.AppLog
import pw.c0re.stream.data.UpdateChecker
import pw.c0re.stream.data.resources.ResourceManager
import java.util.concurrent.TimeUnit

/** Background update check. Installation always stays user-confirmed. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ResourceManager.refresh(applicationContext)
        val info = runCatching { UpdateChecker.check(BuildConfig.VERSION_NAME) }.getOrNull()
        if (info == null || info.apkUrl == null || info.sha256 == null) return Result.success()
        val intent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, C0reStreamApp.UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.update_available_title))
            .setContentText(
                applicationContext.getString(R.string.update_available_text, info.latestVersion),
            )
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { NotificationManagerCompat.from(applicationContext).notify(UPDATE_ID, notification) }
                .onFailure { AppLog.log("Update", it.message ?: "notification failed") }
        }
        return Result.success()
    }

    companion object {
        private const val workName = "c0restream_update_check"
        /** Shared with the install receiver so a successful install clears the stale notice. */
        const val UPDATE_ID = 4404

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(workName, ExistingPeriodicWorkPolicy.KEEP, request)
            val immediate = OneTimeWorkRequestBuilder<UpdateWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork("${workName}_now", ExistingWorkPolicy.KEEP, immediate)
        }
    }
}
