package online.assadawut.atom.queue

import online.assadawut.atom.core.model.TaskRequestPayload
import online.assadawut.atom.database.ATOMDatabase
import online.assadawut.atom.database.QueueRecord
import java.util.UUID

class TaskQueue(private val database: ATOMDatabase) {

    suspend fun submit(request: TaskRequestPayload): String {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val id = UUID.randomUUID().toString().replace("-", "").take(16)
            val record = QueueRecord(
                id = id,
                agent = request.agent,
                taskType = request.task_type,
                payload = request.payload,
                priority = request.priority,
                status = "PENDING",
                timestamp = System.currentTimeMillis()
            )
            database.dao().enqueue(record)
            id
        }
    }
}
