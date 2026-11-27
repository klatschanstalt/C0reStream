package pw.c0re.stream.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import pw.c0re.stream.data.db.AppDatabase
import pw.c0re.stream.data.db.ReminderEntity
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private const val tag = "c0restream-reminder"

    fun schedule(context: Context, reminder: ReminderEntity) {
        if (reminder.id <= 0) return
        val now = System.currentTimeMillis()
        if (reminder.start <= now) return
        val delayMs = (reminder.start - now - 60_000L).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_REMINDER_ID to reminder.id))
            .addTag(tag)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "reminder-${reminder.id}",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(context: Context, reminderId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork("reminder-$reminderId")
    }

    suspend fun reschedule(context: Context, db: AppDatabase) {
        val now = System.currentTimeMillis()
        db.reminderDao().deleteOld(now - 7 * 24 * 60 * 60 * 1000L)
        db.reminderDao().upcoming(now).forEach { schedule(context, it) }
    }
}
