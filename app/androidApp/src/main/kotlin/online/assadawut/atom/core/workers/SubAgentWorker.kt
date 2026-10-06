package online.assadawut.atom.core.workers

import android.content.Context
import online.assadawut.atom.ATOMApplication
import online.assadawut.atom.database.MemoryRecord

class SubAgentWorker(private val applicationContext: Context) {

    suspend fun doWork(): Boolean {
        val app = applicationContext as ATOMApplication
        val dao = app.database.dao()
        val pendingTasks = dao.getPendingTasks()

        if (pendingTasks.isEmpty()) return true

        pendingTasks.forEach { task ->
            try {
                dao.updateQueueStatus(task.id, "PROCESSING")
                
                // Simulate Sub-Agent parallel execution (In real world, this could call specific Plugin or LLM tool)
                val simulatedResult = "Sub-Agent ${task.agent} completed task: ${task.taskType}. Payload: ${task.payload}"
                
                dao.saveMemory(
                    MemoryRecord(
                        id = "mem_${task.id}",
                        content = "[BACKGROUND EXECUTION] $simulatedResult",
                        timestamp = System.currentTimeMillis()
                    )
                )

                dao.updateQueueStatus(task.id, "COMPLETED")
            } catch (e: Exception) {
                dao.updateQueueStatus(task.id, "FAILED")
            }
        }

        return true
    }
}
