package online.assadawut.atom.model

enum class ExecutionMode {
    LOCAL_FIRST,
    CLOUD_NATIVE
}

enum class IntentType {
    UNKNOWN,
    DIRECT_ACTION,
    CONTEXT_MEMORY_SEARCH,
    CONVERSATION_FALLBACK,
    GENERAL_CHAT
}

data class RouterDecision(
    val intent: IntentType,
    val confidenceScore: Double,
    val actionTarget: String? = null,
    val actionPayload: Map<String, String>? = null,
    val extractedQuery: String,
    val rawResponse: String = ""
)

data class VectorRecord(
    val id: String = "",
    val content: String,
    val similarityScore: Double
)

data class PipelineExecutionResult(
    val success: Boolean,
    val message: String,
    val executedStep: String,
    val modeUsed: ExecutionMode,
    val confidenceScore: Double,
    val contextData: List<VectorRecord> = emptyList(),
    val errorDetails: String? = null
)
