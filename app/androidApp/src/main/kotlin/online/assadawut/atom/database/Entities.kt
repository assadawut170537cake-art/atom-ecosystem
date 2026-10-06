package online.assadawut.atom.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory")
data class MemoryRecord(
    @PrimaryKey val id: String,
    val content: String,
    val timestamp: Long
)

@Entity(tableName = "queue")
data class QueueRecord(
    @PrimaryKey val id: String,
    val agent: String,
    val taskType: String,
    val payload: String,
    val priority: Int,
    val status: String, // PENDING, PROCESSING, COMPLETED, FAILED
    val timestamp: Long
)

@Entity(tableName = "persona")
data class PersonaRecord(
    @PrimaryKey val id: String = "active",
    val name: String,
    val speechStyle: String,
    val customDescription: String,
    val provider: String,
    val updatedAt: Long
)

@Entity(tableName = "sync_records")
data class SyncRecordEntity(
    @PrimaryKey val id: String,
    val type: String,
    val payload: String,
    val timestamp: Long,
    val dirty: Boolean
)

@Entity(tableName = "telemetry")
data class TelemetryRecord(
    @PrimaryKey val id: String,
    val batteryLevel: Float,
    val isCharging: Boolean,
    val availableRamMb: Long,
    val networkType: String,
    val timestamp: Long
)

@Entity(tableName = "profile")
data class ProfileRecord(
    @PrimaryKey val id: String = "default",
    val displayName: String = "บอส",
    val favLanguages: String = "",
    val techStack: String = "",
    val codingStyle: String = "",
    val currentProject: String = "",
    val language: String = "th",
    val customNotes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
