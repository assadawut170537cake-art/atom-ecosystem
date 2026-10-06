package online.assadawut.atom.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import online.assadawut.atom.data.local.dao.ChatHistoryDao
import online.assadawut.atom.data.local.dao.MemoryDao
import online.assadawut.atom.data.local.entity.ChatHistory
import online.assadawut.atom.data.local.entity.Memory

@Database(entities = [ChatHistory::class, Memory::class], version = 1, exportSchema = false)
abstract class AtomDatabase : RoomDatabase() {
    abstract fun chatHistoryDao(): ChatHistoryDao
    abstract fun memoryDao(): MemoryDao
}
