package online.assadawut.atom.routing

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import online.assadawut.atom.mcp.MCPManager
import online.assadawut.atom.mcp.MCPToolDescriptor
import online.assadawut.atom.provider.*

class ToolCallingExecutorTest {
    @Test
    fun `test robust JSON parsing on bad tool arguments`() = runBlocking {
        val mcpManager = MCPManager()
        val executor = ToolCallingExecutor(mcpManager)

        val badProvider = object : LLMProvider {
            var calls = 0
            override suspend fun chatWithTools(
                messages: List<ChatMessage>,
                tools: List<ToolSpec>
            ): Result<LLMResponse> {
                calls++
                if (calls == 1) {
                    return Result.success(LLMResponse.ToolCalls(
                        listOf(ToolCall("123", "test_tool", "{ bad json "))
                    ))
                }
                return Result.success(LLMResponse.Text("Recovered from error"))
            }
        }

        val result = executor.run(
            provider = badProvider,
            systemInstruction = "test",
            userPrompt = "test",
            serverId = "server1",
            tools = listOf(MCPToolDescriptor("server1", "test_tool", "desc", "{}"))
        )

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow() == "Recovered from error")
    }
}
