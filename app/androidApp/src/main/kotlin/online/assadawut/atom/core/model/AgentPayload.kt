package online.assadawut.atom.core.model

data class AgentSwitchPayload(
    val agent_id: String,
    val handoff_mode: String
)
data class TaskRequestPayload(
    val agent: String,
    val task_type: String,
    val payload: String,
    val priority: Int
)
data class SyncEnvelope(
    val device_id: String,
    val timestamp: Long,
    val records: List<String>
)