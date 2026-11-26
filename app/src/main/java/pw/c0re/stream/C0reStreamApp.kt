package pw.c0re.stream

import android.app.Application
import pw.c0re.stream.data.db.AppDatabase

class C0reStreamApp : Application() {
    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.build(this)
        pw.c0re.stream.work.PlaylistRefreshWorker.schedule(this)
    }
}
