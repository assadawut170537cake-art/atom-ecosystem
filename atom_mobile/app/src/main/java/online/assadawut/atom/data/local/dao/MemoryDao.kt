package online.assadawut.atom.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import online.assadawut.atom.data.local.entity.Memory

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: Memory)

    @Query("SELECT * FROM memory")
    fun getAll(): Flow<List<Memory>>

    @Query("SELECT * FROM memory WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): Memory?

    @Query("DELETE FROM memory")
    suspend fun clear()

    @Query("DELETE FROM memory WHERE `key` = :key")
    suspend fun deleteByKey(key: String)
}
