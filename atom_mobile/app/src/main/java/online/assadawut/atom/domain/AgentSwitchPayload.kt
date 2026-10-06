package online.assadawut.atom.domain

/**
 * Represents a payload for switching agents.
 */
data class AgentSwitchPayload(
    val targetAgent: String,
    val mode: String // e.g., "finish_sentence" or "immediate_cut"
)
