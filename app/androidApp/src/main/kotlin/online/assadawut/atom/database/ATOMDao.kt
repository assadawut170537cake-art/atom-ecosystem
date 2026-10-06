package online.assadawut.atom.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ATOMDao {
    @Query("SELECT * FROM memory ORDER BY timestamp DESC")
    fun observeMemories(): Flow<List<MemoryRecord>>

    @Query("SELECT * FROM memory ORDER BY timestamp DESC")
    fun memories(): List<MemoryRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveMemory(record: MemoryRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveMemories(records: List<MemoryRecord>)

    @Query("SELECT * FROM queue ORDER BY priority DESC, timestamp ASC")
    fun observeQueue(): Flow<List<QueueRecord>>

    @Query("SELECT * FROM queue WHERE status = 'PENDING' ORDER BY priority DESC, timestamp ASC LIMIT 5")
    fun getPendingTasks(): List<QueueRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun enqueue(record: QueueRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun enqueueAll(records: List<QueueRecord>)

    @Query("UPDATE queue SET status = :status WHERE id = :id")
    fun updateQueueStatus(id: String, status: String)

    @Query("SELECT * FROM persona WHERE id = 'active' LIMIT 1")
    fun observeActivePersona(): Flow<PersonaRecord?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun savePersona(record: PersonaRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveTelemetry(record: TelemetryRecord)

    @Query("SELECT * FROM telemetry ORDER BY timestamp DESC LIMIT 10")
    fun getLatestTelemetry(): List<TelemetryRecord>

    @Query("SELECT * FROM profile WHERE id = 'default' LIMIT 1")
    fun observeProfile(): Flow<ProfileRecord?>

    @Query("SELECT * FROM profile WHERE id = 'default' LIMIT 1")
    fun profile(): ProfileRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveProfile(record: ProfileRecord)
}
