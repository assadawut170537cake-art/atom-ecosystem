package online.assadawut.atom.provider

data class ChatMessage(
    val role: String,
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
    val name: String? = null,
) {
    companion object
}

data class ToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

data class ToolSpec(
    val name: String,
    val description: String,
    val parametersJson: String,
)

sealed interface LLMResponse {
    data class Text(val content: String) : LLMResponse
    data class ToolCalls(val calls: List<ToolCall>) : LLMResponse
}

fun ChatMessage.Companion.user(text: String): ChatMessage =
    ChatMessage(role = "user", content = text)

fun ChatMessage.Companion.system(text: String): ChatMessage =
    ChatMessage(role = "system", content = text)

fun ChatMessage.Companion.assistant(text: String): ChatMessage =
    ChatMessage(role = "assistant", content = text)

fun ChatMessage.Companion.toolResult(
    toolCallId: String, name: String, content: String
): ChatMessage =
    ChatMessage(role = "tool", content = content, toolCallId = toolCallId, name = name)
