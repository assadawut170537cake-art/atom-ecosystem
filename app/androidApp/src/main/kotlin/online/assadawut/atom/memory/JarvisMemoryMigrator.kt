package online.assadawut.atom.memory

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import online.assadawut.atom.database.ATOMDatabase
import online.assadawut.atom.database.MemoryRecord

data class MigratedConversation(
    val session: String?,
    val role: String?,
    val content: String?,
    val timestamp: Any?
)

data class MigratedMemoryPayload(
    val conversations: List<MigratedConversation>?
)

class JarvisMemoryMigrator(private val context: Context, private val database: ATOMDatabase) {

    suspend fun migrateIfFirstRun() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("atom_memory_migrator", Context.MODE_PRIVATE)
        val isMigrated = prefs.getBoolean("jarvis_memories_imported", false)

        if (isMigrated) return@withContext

        try {
            val jsonStream = context.assets.open("jarvis_migrated_memories.json")
            val jsonString = jsonStream.bufferedReader().use { it.readText() }
            val payload = Gson().fromJson(jsonString, MigratedMemoryPayload::class.java)

            val records = mutableListOf<MemoryRecord>()
            payload.conversations?.forEachIndexed { index, conv ->
                val contentText = conv.content ?: return@forEachIndexed
                val ts = System.currentTimeMillis() - (index * 1000L)
                records.add(
                    MemoryRecord(
                        id = "migrated_jarvis_$index",
                        content = "[JARVIS Memory #${index + 1}] (${conv.role ?: "system"}): $contentText",
                        timestamp = ts
                    )
                )
            }

            records.chunked(100).forEach { chunk ->
                chunk.forEach { database.dao().saveMemory(it) }
            }

            prefs.edit().putBoolean("jarvis_memories_imported", true).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
