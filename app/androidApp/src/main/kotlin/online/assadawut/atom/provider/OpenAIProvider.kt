package online.assadawut.atom.provider

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class OpenAIProvider(
    private val client: HttpClient,
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) : LLMProvider {

    private val gson = Gson()

    override suspend fun chatWithTools(
        messages: List<ChatMessage>,
        tools: List<ToolSpec>
    ): Result<LLMResponse> = runCatching {
        val requestMessages = messages.map { msg ->
            val map = mutableMapOf<String, Any>(
                "role" to msg.role,
                "content" to msg.content
            )
            if (msg.role == "tool" && msg.toolCallId != null) {
                map["tool_call_id"] = msg.toolCallId
            }
            if (msg.role == "assistant" && msg.toolCalls.isNotEmpty()) {
                map["tool_calls"] = msg.toolCalls.map { call ->
                    mapOf(
                        "id" to call.id,
                        "type" to "function",
                        "function" to mapOf(
                            "name" to call.name,
                            "arguments" to call.argumentsJson
                        )
                    )
                }
            }
            map
        }

        val requestBody = mutableMapOf<String, Any>(
            "model" to model,
            "messages" to requestMessages
        )

        if (tools.isNotEmpty()) {
            requestBody["tools"] = tools.map { tool ->
                mapOf(
                    "type" to "function",
                    "function" to mapOf(
                        "name" to tool.name,
                        "description" to tool.description,
                        "parameters" to gson.fromJson(tool.parametersJson, JsonObject::class.java)
                    )
                )
            }
            requestBody["tool_choice"] = "auto"
        }

        val url = if (baseUrl.endsWith("/")) "${baseUrl}chat/completions" else "$baseUrl/chat/completions"
        val response = client.post(url) {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(gson.toJson(requestBody))
        }

        if (!response.status.isSuccess()) {
            error("OpenAI API error: ${response.status.value} - ${response.bodyAsText()}")
        }

        val json = gson.fromJson(response.bodyAsText(), JsonObject::class.java)
        val message = json.getAsJsonArray("choices").get(0).asJsonObject.getAsJsonObject("message")

        if (message.has("tool_calls")) {
            val toolCallsArray = message.getAsJsonArray("tool_calls")
            val toolCalls = toolCallsArray.map { element ->
                val call = element.asJsonObject
                val function = call.getAsJsonObject("function")
                ToolCall(
                    id = call.get("id").asString,
                    name = function.get("name").asString,
                    argumentsJson = function.get("arguments").asString
                )
            }
            LLMResponse.ToolCalls(toolCalls)
        } else {
            LLMResponse.Text(message.get("content").asString)
        }
    }
}
