package online.assadawut.atom.sync

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import online.assadawut.atom.database.ATOMDatabase

class DriveSyncManager(private val context: Context, private val database: ATOMDatabase) {
    suspend fun pullOnOpen() = withContext(Dispatchers.IO) {
        // Implement Google Drive pull logic
    }
    suspend fun pushOnClose() = withContext(Dispatchers.IO) {
        // Implement Google Drive push logic
    }
    suspend fun startFiveMinutePush() = withContext(Dispatchers.IO) {
        while(true) {
            delay(5 * 60 * 1000L)
            pushOnClose()
        }
    }
}