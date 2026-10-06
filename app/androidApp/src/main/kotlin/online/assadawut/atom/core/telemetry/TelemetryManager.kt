package online.assadawut.atom.core.telemetry

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.app.ActivityManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import online.assadawut.atom.database.ATOMDatabase
import online.assadawut.atom.database.TelemetryRecord
import java.util.UUID

class TelemetryManager(private val context: Context, private val database: ATOMDatabase) {

    suspend fun captureAndSync() {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = level * 100 / scale.toFloat()
        
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val availableRamMb = memoryInfo.availMem / (1024 * 1024)

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkType = cm.activeNetwork?.let {
            val nc = cm.getNetworkCapabilities(it)
            when {
                nc?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WIFI"
                nc?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "CELLULAR"
                else -> "OTHER"
            }
        } ?: "OFFLINE"

        val record = TelemetryRecord(
            id = "telemetry_${UUID.randomUUID()}",
            batteryLevel = batteryPct,
            isCharging = isCharging,
            availableRamMb = availableRamMb,
            networkType = networkType,
            timestamp = System.currentTimeMillis()
        )
        database.dao().saveTelemetry(record)
    }
}
