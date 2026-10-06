package online.assadawut.atom.provider

interface LLMProvider {
    suspend fun chat(prompt: String): Result<String> =
        chatWithTools(
            messages = listOf(ChatMessage.user(prompt)),
            tools = emptyList(),
        ).map { response ->
            when (response) {
                is LLMResponse.Text -> response.content
                is LLMResponse.ToolCalls ->
                    error("Provider returned tool calls without tools")
            }
        }

    suspend fun chatWithTools(
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
    ): Result<LLMResponse>

    suspend fun chatWithImage(
        prompt: String,
        imageBase64: String,
        mimeType: String,
    ): Result<String> {
        return Result.failure(UnsupportedOperationException("Provider does not support vision"))
    }
}
