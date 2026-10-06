package online.assadawut.atom.provider

import io.ktor.client.*

class GrokProvider(
    private val apiKey: String,
    private val client: HttpClient
) : LLMProvider {
    private val delegate = OpenAIProvider(
        client = client,
        baseUrl = "https://api.groq.com/openai/v1",
        apiKey = apiKey,
        model = "qwen-2.5-32b"
    )

    override suspend fun chatWithTools(
        messages: List<ChatMessage>,
        tools: List<ToolSpec>
    ): Result<LLMResponse> = delegate.chatWithTools(messages, tools)
}
