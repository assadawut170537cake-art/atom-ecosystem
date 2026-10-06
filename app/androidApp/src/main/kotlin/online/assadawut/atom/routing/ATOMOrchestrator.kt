package online.assadawut.atom.routing

import kotlinx.coroutines.CancellationException
import online.assadawut.atom.BuildConfig
import online.assadawut.atom.core.model.Provider
import online.assadawut.atom.core.model.TaskRequestPayload
import online.assadawut.atom.integration.IntegrationRouter
import online.assadawut.atom.integration.IntegrationType
import online.assadawut.atom.mcp.MCPManager
import online.assadawut.atom.mcp.MCPServerConfig
import online.assadawut.atom.mcp.MCPToolDescriptor
import online.assadawut.atom.mcp.MCPToolResult
import online.assadawut.atom.mcp.MCPTransportType
import online.assadawut.atom.ollama.OllamaClient
import online.assadawut.atom.plugin.PluginContext
import online.assadawut.atom.plugin.PluginRegistry
import online.assadawut.atom.plugin.PluginResult
import online.assadawut.atom.provider.ProviderFactory
import online.assadawut.atom.queue.TaskQueue
import online.assadawut.atom.core.mcts.MissionPlanner
import online.assadawut.atom.database.ATOMDatabase

sealed interface ExecutionResult {

    data class Text(
        val value: String,
    ) : ExecutionResult

    data class Queued(
        val id: String,
    ) : ExecutionResult
}

class ATOMOrchestrator(
    private val router: AgentRouter,
    private val pcAvailability: PcAvailability,
    private val providerFactory: ProviderFactory,
    private val queue: TaskQueue,
    private val integrationRouter: IntegrationRouter,
    private val pluginRegistry: PluginRegistry,
    private val mcpManager: MCPManager,
    private val database: ATOMDatabase
) {

    private val ollama = OllamaClient(BuildConfig.OLLAMA_URL)
    private val toolExecutor = ToolCallingExecutor(mcpManager)
    private val missionPlanner = MissionPlanner(database)

    suspend fun execute(
        prompt: String,
        provider: Provider,
        selectedModel: String = "",
        agent: String = "ATOM",
        taskType: String = "chat",
        priority: Int = 0,
        heavy: Boolean = false,
    ): Result<ExecutionResult> = runCatching {
        val pcOnline = pcAvailability.isOnline()

        if (heavy && prompt.length > 50) {
            // MCTS Planner Phase 1: Decompose & Queue parallel sub-agent tasks
            val planResult = missionPlanner.decomposeAndQueue(prompt, providerFactory.create(provider, selectedModel)).getOrNull()
            if (planResult != null) {
                return@runCatching ExecutionResult.Queued(planResult)
            }
        }

        when (
            router.route(
                RoutingContext(
                    pcOnline = pcOnline,
                    voice = false,
                    heavy = heavy,
                ),
            )
        ) {
            Route.OLLAMA -> {
                ExecutionResult.Text(
                    ollama.chat(model = if (selectedModel.isNotBlank()) selectedModel else "atom", prompt = prompt),
                )
            }

            Route.API -> {
                val response = providerFactory
                    .create(provider, selectedModel)
                    .chat(prompt)
                    .getOrThrow()

                ExecutionResult.Text(response)
            }

            Route.QUEUE -> {
                val id = queue.submit(
                    TaskRequestPayload(
                        agent = agent,
                        task_type = taskType,
                        payload = prompt,
                        priority = priority,
                    ),
                )

                ExecutionResult.Queued(id)
            }

            Route.GEMINI_LIVE -> {
                error("Voice is handled by ATOMVoiceService")
            }
        }
    }
    
    suspend fun executeWithTools(
        prompt: String,
        provider: Provider,
        agent: String = "ATOM",
        serverId: String,
    ): Result<String> {
        return try {
            val descriptor = providerFactory.create(provider)
            val tools = mcpManager.listTools(serverId).getOrThrow()

            if (tools.isEmpty()) {
                return Result.failure(
                    IllegalStateException("No tools on server $serverId")
                )
            }

            val systemInstruction = "You are $agent. Use available tools when relevant."

            toolExecutor.run(
                provider = descriptor,
                systemInstruction = systemInstruction,
                userPrompt = prompt,
                serverId = serverId,
                tools = tools,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun dispatchIntegration(
        type: IntegrationType,
        destination: String,
        message: String,
    ): Result<Unit> = integrationRouter.send(
        type = type,
        destination = destination,
        message = message,
    )

    suspend fun dispatchPlugin(
        pluginId: String,
        agent: String,
        input: String,
    ): Result<PluginResult> = runCatching {
        pluginRegistry.execute(
            id = pluginId,
            context = PluginContext(
                agent = agent,
                input = input,
            ),
        )
    }.onFailure {
        if (it is CancellationException) {
            throw it
        }
    }

    suspend fun connectMcpServer(
        config: MCPServerConfig,
    ): Result<Unit> {
        return try {
            require(config.transport != MCPTransportType.STDIO) {
                "MCP STDIO is not supported on Android; use HTTP or SSE"
            }
            require(config.id.isNotBlank()) {
                "Empty MCP server ID"
            }
            require(config.endpoint.isNotBlank()) {
                "Empty MCP endpoint"
            }

            mcpManager.connect(config)
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun disconnectMcpServer(
        serverId: String,
    ): Result<Unit> {
        return try {
            mcpManager.disconnect(serverId)
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun listMcpTools(
        serverId: String,
    ): Result<List<MCPToolDescriptor>> = mcpManager.listTools(serverId)

    suspend fun invokeMcpTool(
        serverId: String,
        toolName: String,
        arguments: Map<String, Any?>,
    ): Result<MCPToolResult> = mcpManager.invokeTool(
        serverId = serverId,
        toolName = toolName,
        arguments = arguments,
    )

    suspend fun closeMcp(): Unit {
        mcpManager.closeAll()
    }
}
