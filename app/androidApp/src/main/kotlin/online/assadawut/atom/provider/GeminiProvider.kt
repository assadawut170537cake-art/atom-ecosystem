package online.assadawut.atom.provider

import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import online.assadawut.atom.network.ATOMHttpClient
import java.util.UUID

class GeminiProvider(
    private val apiKey: String,
    private val model: String = "gemini-3.8-flash",
) : LLMProvider {

    private val gson = Gson()
    private val client = ATOMHttpClient.create()

    companion object {
        private const val PERSONA_INSTRUCTION =
            "คุณคือผู้ช่วยคนสนิท พูดภาษาไทยด้วยน้ำเสียงและภาษาพูดที่เป็นธรรมชาติเหมือนมนุษย์คุยกันจริงๆ " +
                    "พูดจาเป็นกันเอง สุภาพ มีหางเสียง (ครับ) ไม่พูดเหมือนหุ่นยนต์ " +
                    "ตอบสั้นกระชับ 2-3 ประโยค ห้ามใช้สัญลักษณ์พิเศษ เช่น * # หรือสัญลักษณ์โค้ด เพื่อให้เสียงพูดอ่านออกมาเหมือนคนพูดที่สุด"
    }

    override suspend fun chatWithTools(
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
    ): Result<LLMResponse> {
        return try {
            Result.success(
                doChat(messages = messages, tools = tools),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: ProviderException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(
                ProviderException(
                    providerName = "Gemini",
                    message = e.message ?: "Unknown Gemini error",
                    cause = e,
                ),
            )
        }
    }

    private suspend fun doChat(
        messages: List<ChatMessage>,
        tools: List<ToolSpec>,
    ): LLMResponse {

        if (apiKey.isBlank()) {
            throw ProviderException(
                providerName = "Gemini",
                message = "GEMINI_API_KEY is empty",
            )
        }

        val systemMessage = messages
            .firstOrNull { it.role.equals("system", ignoreCase = true) }

        val finalSystemInstruction = buildString {
            systemMessage?.content?.takeIf { it.isNotBlank() }?.let {
                append(it)
                append("\n\n")
            }
            append(PERSONA_INSTRUCTION)
        }.trim()

        val contents = messages
            .filterNot { it.role.equals("system", ignoreCase = true) }
            .map { msg ->
                val role = when {
                    msg.role.equals("assistant", true) -> "model"
                    msg.role.equals("tool", true) -> "function"
                    else -> "user"
                }

                val parts: List<Map<String, Any?>> = if (msg.role.equals("tool", true)) {
                    listOf(
                        mapOf(
                            "functionResponse" to mapOf(
                                "name" to (msg.name ?: ""),
                                "response" to mapOf("content" to msg.content),
                            ),
                        ),
                    )
                } else {
                    listOf(mapOf("text" to msg.content))
                }

                mapOf("role" to role, "parts" to parts)
            }

        val requestBody = mutableMapOf<String, Any>(
            "contents" to contents,
            "systemInstruction" to mapOf(
                "parts" to listOf(mapOf("text" to finalSystemInstruction)),
            ),
        )

        if (tools.isNotEmpty()) {
            val functionDeclarations = tools.map { tool ->
                mapOf(
                    "name" to tool.name,
                    "description" to tool.description,
                    "parameters" to runCatching {
                        gson.fromJson(tool.parametersJson, JsonObject::class.java)
                    }.getOrElse { JsonObject() },
                )
            }

            requestBody["tools"] = listOf(
                mapOf("functionDeclarations" to functionDeclarations),
            )
            requestBody["toolConfig"] = mapOf(
                "functionCallingConfig" to mapOf("mode" to "AUTO"),
            )
        }

        val url =
            "https://generativelanguage.googleapis.com/v1beta/" +
                    "models/$model:generateContent"

        val response = client.post(url) {
            header("x-goog-api-key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(gson.toJson(requestBody))
        }

        if (!response.status.isSuccess()) {
            val body = runCatching { response.bodyAsText() }.getOrDefault("")
            throw ProviderException(
                providerName = "Gemini",
                message = "HTTP ${response.status.value}: ${body.take(512)}",
            )
        }

        val root = gson.fromJson(
            response.bodyAsText(),
            JsonObject::class.java,
        )

        val candidates = root.getAsJsonArray("candidates")
            ?: throw ProviderException("Gemini", "response missing candidates")

        if (candidates.size() == 0) {
            throw ProviderException("Gemini", "response has no candidates")
        }

        val content = candidates[0].asJsonObject.getAsJsonObject("content")
            ?: throw ProviderException("Gemini", "candidate missing content")

        val parts = content.getAsJsonArray("parts")
            ?: throw ProviderException("Gemini", "content missing parts")

        val functionCalls = mutableListOf<ToolCall>()
        val textParts = mutableListOf<String>()

        parts.forEach { element ->
            val part = element.asJsonObject

            val text = part.get("text")?.takeIf { !it.isJsonNull }?.asString
            if (!text.isNullOrBlank()) {
                textParts.add(text)
            }

            val fc = part.getAsJsonObject("functionCall")
            if (fc != null) {
                val name = fc.get("name")?.asString.orEmpty()
                val args = fc.getAsJsonObject("args")
                functionCalls.add(
                    ToolCall(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        argumentsJson = gson.toJson(args ?: JsonObject()),
                    ),
                )
            }
        }

        return if (functionCalls.isNotEmpty()) {
            LLMResponse.ToolCalls(functionCalls)
        } else {
            val rawText = textParts.joinToString("\n")
            val cleanText = rawText
                .replace(Regex("<think>[\\s\\S]*?</think>"), "")
                .replace(Regex("<thinking>[\\s\\S]*?</thinking>"), "")
                .trim()
            LLMResponse.Text(cleanText)
        }
    }

    override suspend fun chatWithImage(
        prompt: String,
        imageBase64: String,
        mimeType: String,
    ): Result<String> = runCatching {
        if (apiKey.isBlank()) {
            throw ProviderException(
                providerName = "Gemini",
                message = "GEMINI_API_KEY is empty",
            )
        }

        val inlineData = mapOf(
            "mimeType" to mimeType,
            "data" to imageBase64
        )
        val textPart = mapOf("text" to prompt)
        val parts = listOf(
            mapOf("inlineData" to inlineData),
            textPart
        )
        val content = mapOf(
            "role" to "user",
            "parts" to parts
        )
        val requestBody = mapOf(
            "contents" to listOf(content),
            "systemInstruction" to mapOf(
                "parts" to listOf(mapOf("text" to PERSONA_INSTRUCTION))
            )
        )

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

        val response = client.post(url) {
            header("x-goog-api-key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(gson.toJson(requestBody))
        }

        if (!response.status.isSuccess()) {
            val body = runCatching { response.bodyAsText() }.getOrDefault("")
            throw ProviderException(
                providerName = "Gemini",
                message = "HTTP ${response.status.value}: ${body.take(512)}",
            )
        }

        val root = gson.fromJson(response.bodyAsText(), JsonObject::class.java)
        val candidates = root.getAsJsonArray("candidates")
            ?: throw ProviderException("Gemini", "response missing candidates")

        if (candidates.size() == 0) {
            throw ProviderException("Gemini", "response has no candidates")
        }

        val resContent = candidates[0].asJsonObject.getAsJsonObject("content")
            ?: throw ProviderException("Gemini", "candidate missing content")

        val resParts = resContent.getAsJsonArray("parts")
            ?: throw ProviderException("Gemini", "content missing parts")

        val textParts = mutableListOf<String>()
        resParts.forEach { element ->
            val part = element.asJsonObject
            val text = part.get("text")?.takeIf { !it.isJsonNull }?.asString
            if (!text.isNullOrBlank()) {
                textParts.add(text)
            }
        }

        val rawText = textParts.joinToString("\n")
        rawText.replace(Regex("<think>[\\s\\S]*?</think>"), "")
               .replace(Regex("<thinking>[\\s\\S]*?</thinking>"), "")
               .trim()
    }
}
