package online.assadawut.atom.routing

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import online.assadawut.atom.mcp.MCPManager
import online.assadawut.atom.mcp.MCPToolDescriptor
import online.assadawut.atom.provider.ChatMessage
import online.assadawut.atom.provider.LLMProvider
import online.assadawut.atom.provider.LLMResponse
import online.assadawut.atom.provider.ToolSpec
import online.assadawut.atom.provider.system
import online.assadawut.atom.provider.user
import online.assadawut.atom.provider.toolResult

class ToolCallingExecutor(
    private val mcpManager: MCPManager,
) {
    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, Any?>>() {}.type

    suspend fun run(
        provider: LLMProvider,
        systemInstruction: String,
        userPrompt: String,
        serverId: String,
        tools: List<MCPToolDescriptor>,
        maxIterations: Int = 5,
    ): Result<String> {
        return try {
            val toolSpecs = tools.map {
                ToolSpec(
                    name = it.name,
                    description = it.description,
                    parametersJson = it.inputSchema
                )
            }

            val messages = mutableListOf(
                ChatMessage.system(systemInstruction),
                ChatMessage.user(userPrompt)
            )

            var iterations = 0

            while (iterations < maxIterations) {
                val response = provider.chatWithTools(messages, toolSpecs).getOrThrow()

                when (response) {
                    is LLMResponse.Text -> {
                        return Result.success(response.content)
                    }
                    is LLMResponse.ToolCalls -> {
                        messages.add(
                            ChatMessage(
                                role = "assistant",
                                content = "",
                                toolCalls = response.calls
                            )
                        )

                        for (call in response.calls) {
                            val args = try {
                                gson.fromJson<Map<String, Any?>>(call.argumentsJson, mapType)
                            } catch (e: Exception) {
                                messages.add(
                                    ChatMessage.toolResult(
                                        toolCallId = call.id,
                                        name = call.name,
                                        content = "Error parsing JSON arguments: ${e.message}"
                                    )
                                )
                                continue
                            }

                            val mcpResult = mcpManager.invokeTool(serverId, call.name, args).getOrThrow()
                            
                            messages.add(
                                ChatMessage.toolResult(
                                    toolCallId = call.id,
                                    name = call.name,
                                    content = mcpResult.output
                                )
                            )
                        }
                    }
                }
                iterations++
            }

            Result.failure(IllegalStateException("Tool calling exceeded max iterations"))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}
