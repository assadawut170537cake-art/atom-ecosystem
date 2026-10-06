package online.assadawut.atom.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_history")
data class ChatHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val role: String,
    val message: String,
    val timestamp: Long
)
