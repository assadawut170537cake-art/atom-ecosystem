package online.assadawut.atom

import android.app.Application
import online.assadawut.atom.database.ATOMDatabase
import online.assadawut.atom.sync.DriveSyncManager

class ATOMApplication : Application() {
    lateinit var database: ATOMDatabase
        private set
    lateinit var syncManager: DriveSyncManager
        private set

    override fun onCreate() {
        super.onCreate()
        database = ATOMDatabase.create(this)
        syncManager = DriveSyncManager(
            context = this,
            database = database
        )
    }
}