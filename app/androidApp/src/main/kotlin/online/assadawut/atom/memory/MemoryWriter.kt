package online.assadawut.atom.memory

import online.assadawut.atom.database.ATOMDatabase
import online.assadawut.atom.database.MemoryRecord
import online.assadawut.atom.provider.ProviderFactory

class MemoryExtractor(private val providerFactory: ProviderFactory)

class MemoryWriter(
    private val database: ATOMDatabase,
    private val extractor: MemoryExtractor
) {
    suspend fun saveTurn(userText: String, modelText: String, agent: String) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val timestamp = System.currentTimeMillis()
            val record = MemoryRecord(
                id = "mem_$timestamp",
                content = "User: $userText\n$agent: $modelText",
                timestamp = timestamp
            )
            database.dao().saveMemory(record)
        }
    }
}
