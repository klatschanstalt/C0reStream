package pw.c0re.stream.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import pw.c0re.stream.C0reStreamApp
import pw.c0re.stream.R
import pw.c0re.stream.work.UpdateWorker

/**
 * Receives PackageInstaller results. A successful
 * [pw.c0re.stream.data.UpdateInstaller.install] handover is not an installed update; only
 * STATUS_SUCCESS here means the APK was actually installed. Installer
 * messages are never logged or shown verbatim, only a category-specific, localized text.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        // User confirmation is handled by the system UI; no terminal result to report yet.
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) return

        val manager = NotificationManagerCompat.from(context)
        if (status == PackageInstaller.STATUS_SUCCESS) {
            manager.cancel(UpdateWorker.UPDATE_ID)
            notify(context, manager, R.string.update_installed_title, R.string.update_installed_text)
            return
        }

        val textRes = when (status) {
            PackageInstaller.STATUS_FAILURE_BLOCKED -> R.string.update_install_failed_blocked
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> R.string.update_install_failed_incompatible
            PackageInstaller.STATUS_FAILURE_STORAGE -> R.string.update_install_failed_storage
            PackageInstaller.STATUS_FAILURE_INVALID -> R.string.update_install_failed_invalid
            PackageInstaller.STATUS_FAILURE_ABORTED -> R.string.update_install_failed_aborted
            PackageInstaller.STATUS_FAILURE_CONFLICT -> R.string.update_install_failed_conflict
            else -> R.string.update_install_failed_unknown
        }
        notify(context, manager, R.string.update_install_failed_title, textRes)
    }

    private fun notify(context: Context, manager: NotificationManagerCompat, titleRes: Int, textRes: Int) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = NotificationCompat.Builder(context, C0reStreamApp.UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(titleRes))
            .setContentText(context.getString(textRes))
            .setOnlyAlertOnce(true)
            .build()
        runCatching { manager.notify(UPDATE_NOTIFICATION_ID, notification) }
    }

    companion object {
        const val UPDATE_NOTIFICATION_ID = 4403
    }
}
