package pw.c0re.stream

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import pw.c0re.stream.data.AppSettings
import pw.c0re.stream.data.SettingsRepository
import pw.c0re.stream.data.db.AppDatabase
import pw.c0re.stream.data.resources.ResourceManager
import pw.c0re.stream.work.PlaylistRefreshWorker
import pw.c0re.stream.work.ReminderScheduler
import pw.c0re.stream.work.UpdateWorker

class C0reStreamApp : Application() {
    lateinit var database: AppDatabase
        private set
    lateinit var settings: SettingsRepository
        private set
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val cachedSettings = MutableStateFlow(AppSettings())

    override fun onCreate() {
        super.onCreate()
        SettingsRepository.migrateLegacyPreferences(this)
        database = AppDatabase.build(this)
        settings = SettingsRepository(this)
        createNotificationChannels()
        appScope.launch {
            settings.settings.collect { cachedSettings.value = it }
        }
        appScope.launch {
            val current = settings.snapshot()
            PlaylistRefreshWorker.schedule(this@C0reStreamApp, current.refreshHours, current.autoRefresh)
            ReminderScheduler.reschedule(this@C0reStreamApp, database)
            UpdateWorker.schedule(this@C0reStreamApp)
            ResourceManager.refresh(this@C0reStreamApp)
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        val reminders = NotificationChannel(
            REMINDER_CHANNEL_ID,
            getString(R.string.reminder_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.reminder_channel)
        }
        manager.createNotificationChannel(reminders)
        val updates = NotificationChannel(
            UPDATE_CHANNEL_ID,
            getString(R.string.update_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(updates)
    }

    companion object {
        const val REMINDER_CHANNEL_ID = "c0restream_reminders"
        const val UPDATE_CHANNEL_ID = "c0restream_updates"
    }
}
