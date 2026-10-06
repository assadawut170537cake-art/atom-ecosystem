package online.assadawut.atom.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import online.assadawut.atom.data.local.entity.ChatHistory

@Dao
interface ChatHistoryDao {
    @Insert
    suspend fun insert(chatHistory: ChatHistory)

    @Query("SELECT * FROM chat_history ORDER BY timestamp ASC")
    fun getAll(): Flow<List<ChatHistory>>

    @Query("DELETE FROM chat_history")
    suspend fun clear()
}
