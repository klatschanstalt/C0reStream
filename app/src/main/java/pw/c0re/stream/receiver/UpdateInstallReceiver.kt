package pw.c0re.stream.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageInstaller
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import pw.c0re.stream.C0reStreamApp
import pw.c0re.stream.R

/** Receives PackageInstaller results and reports failures without logging installer messages. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val manager = NotificationManagerCompat.from(context)
        if (status == PackageInstaller.STATUS_SUCCESS) {
            manager.cancel(UPDATE_NOTIFICATION_ID)
            return
        }
        val notification = NotificationCompat.Builder(context, C0reStreamApp.UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.update_install_failed_title))
            .setContentText(context.getString(R.string.update_install_failed_text))
            .setOnlyAlertOnce(true)
            .build()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            runCatching { manager.notify(UPDATE_NOTIFICATION_ID, notification) }
        }
    }

    companion object {
        const val UPDATE_NOTIFICATION_ID = 4403
    }
}
