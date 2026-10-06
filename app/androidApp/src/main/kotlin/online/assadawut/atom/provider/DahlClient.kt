package online.assadawut.atom.provider

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

class DahlClient(
    private val client: HttpClient,
    private val baseUrl: String,
    private val apiKey: String,
) : LLMProvider {

    private val gson = Gson()
    private val model = "zai-org/GLM-5.3-Flash"

    override suspend fun chatWithTools(
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
    ): Result<LLMResponse> {
        return try {
            Result.success(
                doChat(messages = messages),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: ProviderException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(
                ProviderException(
                    providerName = "Dahl",
                    message = e.message ?: "Unknown Dahl error",
                    cause = e,
                ),
            )
        }
    }

    private suspend fun doChat(
        messages: List<ChatMessage>,
    ): LLMResponse {

        if (apiKey.isBlank()) {
            throw ProviderException(
                providerName = "Dahl",
                message = "DAHL_API_KEY is empty",
            )
        }

        val requestMessages = messages.map { msg ->
            val map = mutableMapOf<String, Any>(
                "role" to msg.role,
                "content" to msg.content,
            )
            if (msg.role == "tool" && msg.toolCallId != null) {
                map["tool_call_id"] = msg.toolCallId
            }
            map
        }

        val requestBody = mutableMapOf<String, Any>(
            "model" to model,
            "messages" to requestMessages,
            "temperature" to 0.7,
            "max_tokens" to 512,
            "chat_template_kwargs" to mapOf(
                "thinking" to false,
            ),
        )

        val url = if (baseUrl.endsWith("/")) {
            "${baseUrl}chat/completions"
        } else {
            "$baseUrl/chat/completions"
        }

        val response = client.post(url) {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(gson.toJson(requestBody))
        }

        if (!response.status.isSuccess()) {
            val body = runCatching { response.bodyAsText() }.getOrDefault("")
            throw ProviderException(
                providerName = "Dahl",
                message = "HTTP ${response.status.value}: ${body.take(512)}",
            )
        }

        val root = gson.fromJson(
            response.bodyAsText(),
            JsonObject::class.java,
        )

        val choices = root.getAsJsonArray("choices")
            ?: throw ProviderException("Dahl", "response missing choices")

        if (choices.size() == 0) {
            throw ProviderException("Dahl", "response has no choices")
        }

        val message = choices[0].asJsonObject.getAsJsonObject("message")
            ?: throw ProviderException("Dahl", "choice missing message")

        val contentElement = message.get("content")
            ?: throw ProviderException("Dahl", "message missing content")

        if (contentElement.isJsonNull) {
            throw ProviderException("Dahl", "message.content is null")
        }

        val rawText = contentElement.asString

        val cleanText = rawText
            .replace(Regex("<think>[\\s\\S]*?</think>"), "")
            .replace(Regex("<thinking>[\\s\\S]*?</thinking>"), "")
            .trim()

        return LLMResponse.Text(cleanText)
    }
}