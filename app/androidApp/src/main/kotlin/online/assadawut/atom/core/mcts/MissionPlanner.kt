package online.assadawut.atom.core.mcts

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import online.assadawut.atom.database.ATOMDatabase
import online.assadawut.atom.database.QueueRecord
import online.assadawut.atom.provider.LLMProvider
import online.assadawut.atom.provider.LLMResponse
import java.util.UUID

data class PlannedTask(
    val step: Int,
    val agent: String,
    val action: String,
    val payload: String,
    val priority: Int
)

class MissionPlanner(private val database: ATOMDatabase) {

    private val gson = Gson()
    private val taskListType = object : TypeToken<List<PlannedTask>>() {}.type

    suspend fun decomposeAndQueue(prompt: String, provider: LLMProvider): Result<String> = runCatching {
        val systemPrompt = """
            You are the MCTS Mission Planner (Phase 1: Decomposition).
            Analyze the user's complex request and break it down into smaller, parallelizable tasks.
            You must return ONLY a JSON array of tasks. Do not use Markdown formatting like ```json.
            Each task should have:
            - "step": Execution order (1, 2, 3...)
            - "agent": Which agent should handle this? (FRIDAY for external actions/scripts, ULTRON for deep analysis, ATOM for general)
            - "action": Brief description of the task
            - "payload": The actual prompt or command for the sub-agent to execute
            - "priority": Priority level (10 = highest, 1 = lowest)
            
            Example:
            [
              {"step": 1, "agent": "FRIDAY", "action": "Fetch crypto prices", "payload": "Get latest BTC and ETH prices", "priority": 10},
              {"step": 2, "agent": "ULTRON", "action": "Analyze trend", "payload": "Analyze BTC trend based on prices", "priority": 5}
            ]
        """.trimIndent()

        val messages = listOf(
            online.assadawut.atom.provider.ChatMessage(role = "system", content = systemPrompt),
            online.assadawut.atom.provider.ChatMessage(role = "user", content = prompt)
        )

        val response = provider.chatWithTools(messages, emptyList()).getOrThrow()
        
        val jsonString = when(response) {
            is LLMResponse.Text -> response.content
            else -> throw IllegalStateException("Unexpected LLM Response type")
        }

        val cleanJson = jsonString.replace("```json", "").replace("```", "").trim()
        
        val plannedTasks: List<PlannedTask> = gson.fromJson(cleanJson, taskListType)
        
        val queueRecords = plannedTasks.map { task ->
            QueueRecord(
                id = "task_${UUID.randomUUID().toString().take(8)}",
                agent = task.agent,
                taskType = task.action,
                payload = task.payload,
                priority = task.priority,
                status = "PENDING",
                timestamp = System.currentTimeMillis()
            )
        }

        database.dao().enqueueAll(queueRecords)
        
        "✅ MCTS Mission Planner: แยกคำสั่งออกเป็น ${queueRecords.size} งานย่อย และนำเข้าคิวประมวลผลคู่ขนานเรียบร้อยแล้ว"
    }
}
