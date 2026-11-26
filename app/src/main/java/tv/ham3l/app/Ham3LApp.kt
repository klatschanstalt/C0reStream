package tv.ham3l.app

import android.app.Application
import tv.ham3l.app.data.db.AppDatabase

class Ham3LApp : Application() {
    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.build(this)
        tv.ham3l.app.work.PlaylistRefreshWorker.schedule(this)
    }
}
