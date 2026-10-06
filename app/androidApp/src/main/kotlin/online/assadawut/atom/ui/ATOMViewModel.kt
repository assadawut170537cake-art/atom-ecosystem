package online.assadawut.atom.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import online.assadawut.atom.ATOMApplication
import online.assadawut.atom.BuildConfig
import online.assadawut.atom.core.model.*
import online.assadawut.atom.database.PersonaRecord
import online.assadawut.atom.database.ProfileRecord
import online.assadawut.atom.integration.IntegrationCredentials
import online.assadawut.atom.integration.IntegrationRepository
import online.assadawut.atom.integration.IntegrationRouter
import online.assadawut.atom.integration.IntegrationType
import online.assadawut.atom.integration.isConfigured
import online.assadawut.atom.mcp.MCPManager
import online.assadawut.atom.mcp.MCPServerConfig
import online.assadawut.atom.mcp.MCPToolDescriptor
import online.assadawut.atom.mcp.MCPToolResult
import online.assadawut.atom.mcp.MCPTransportType
import online.assadawut.atom.memory.MemoryExtractor
import online.assadawut.atom.memory.MemoryWriter
import online.assadawut.atom.plugin.AccessibilityPlugin
import online.assadawut.atom.plugin.PluginRegistry
import online.assadawut.atom.plugin.PluginResult
import online.assadawut.atom.provider.ProviderFactory
import online.assadawut.atom.queue.TaskQueue
import online.assadawut.atom.routing.*
import online.assadawut.atom.ui.state.ATOMUiState
import online.assadawut.atom.ui.state.ChatTurn
import online.assadawut.atom.voice.ATOMVoiceService

class ATOMViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val app = application as ATOMApplication
    private val dao = app.database.dao()

    private val queue = TaskQueue(app.database)

    private val memoryWriter = MemoryWriter(
        database = app.database,
        extractor = MemoryExtractor(ProviderFactory()),
    )

    private val integrationRepository = IntegrationRepository(app)
    private val integrationRouter = IntegrationRouter(integrationRepository)
    private val pluginRegistry = PluginRegistry()
    private val mcpManager = MCPManager()

    private val orchestrator = ATOMOrchestrator(
        router = AgentRouter(),
        pcAvailability = PcAvailability(BuildConfig.OLLAMA_URL),
        providerFactory = ProviderFactory(),
        queue = queue,
        integrationRouter = integrationRouter,
        pluginRegistry = pluginRegistry,
        mcpManager = mcpManager,
        database = app.database,
    )

    private val _state = MutableStateFlow(ATOMUiState())
    val state: StateFlow<ATOMUiState> = _state.asStateFlow()

    private var voiceService: ATOMVoiceService? = null
    private var voiceStateJob: Job? = null
    private var bound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            name: ComponentName?,
            binder: IBinder?,
        ): Unit {
            val local = binder as? ATOMVoiceService.LocalBinder ?: return
            voiceService = local.service()
            bound = true
            observeVoiceService()
        }

        override fun onServiceDisconnected(name: ComponentName?): Unit {
            bound = false
            voiceService = null
            voiceStateJob?.cancel()
            voiceStateJob = null
        }
    }

    init {
        observeDatabase()
        bindVoiceService()
        registerPlugins()

        viewModelScope.launch {
            _state.update {
                it.copy(
                    integrationCredentials = integrationRepository.load(),
                )
            }
        }
    }

    private fun registerPlugins() {
        viewModelScope.launch {
            pluginRegistry.register(AccessibilityPlugin())
        }
    }

    private fun observeDatabase() {
        viewModelScope.launch {
            dao.observeMemories().collect { records ->
                _state.update { it.copy(memories = records) }
            }
        }

        viewModelScope.launch {
            dao.observeQueue().collect { records ->
                _state.update { it.copy(queue = records) }
            }
        }

        viewModelScope.launch {
            dao.observeProfile().collect { record ->
                _state.update { it.copy(profile = record) }
            }
        }

        viewModelScope.launch {
            dao.observeActivePersona().collect { record ->
                if (record == null) return@collect

                val provider = runCatching {
                    Provider.valueOf(record.provider)
                }.getOrDefault(Provider.GEMINI)

                _state.update {
                    it.copy(
                        persona = PersonaConfig(
                            name = record.name,
                            speechStyle = record.speechStyle,
                            customDescription = record.customDescription,
                            provider = provider,
                        ),
                        provider = provider,
                    )
                }
            }
        }
    }

    private fun bindVoiceService() {
        val context = getApplication<Application>()
        val intent = Intent(context, ATOMVoiceService::class.java)

        context.bindService(
            intent,
            serviceConnection,
            Context.BIND_AUTO_CREATE,
        )
    }

    private fun observeVoiceService() {
        voiceStateJob?.cancel()
        val service = voiceService ?: return

        voiceStateJob = viewModelScope.launch {
            service.state.collect { voiceState ->
                _state.update {
                    it.copy(
                        orbState = voiceState.orbState,
                        connected = voiceState.connected,
                        transcript = voiceState.transcript,
                        error = voiceState.error,
                    )
                }
            }
        }
    }

    fun startVoice(): Unit {
        val context = getApplication<Application>()

        val intent = Intent(context, ATOMVoiceService::class.java).apply {
            action = ATOMVoiceService.ACTION_START
        }

        ContextCompat.startForegroundService(context, intent)

        val service = voiceService
        if (service != null) {
            service.startListening { spokenText ->
                if (spokenText.isNotBlank()) sendText(spokenText)
            }
        } else {
            context.bindService(
                Intent(context, ATOMVoiceService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE,
            )
            viewModelScope.launch {
                repeat(20) {
                    delay(100)
                    voiceService?.let {
                        it.startListening { spokenText ->
                            if (spokenText.isNotBlank()) sendText(spokenText)
                        }
                        return@launch
                    }
                }
                _state.update { it.copy(error = "Voice service unavailable") }
            }
        }
    }

    fun stopVoice(): Unit {
        voiceService?.stopVoice()
        val context = getApplication<Application>()
        context.startService(
            Intent(context, ATOMVoiceService::class.java).apply {
                action = ATOMVoiceService.ACTION_STOP
            },
        )
    }

    fun interrupt(): Unit {
        voiceService?.interrupt()
    }

    fun speakText(text: String) {
        val activePersona = _state.value.agent.name.lowercase()
        voiceService?.speak(text, activePersona)
    }

    fun setSpeechRate(rate: Float) {
        voiceService?.setSpeechRate(rate)
    }

    fun setPitch(pitch: Float) {
        voiceService?.setPitch(pitch)
    }

    fun clearChatHistory() {
        _state.update { it.copy(chatHistory = emptyList(), transcript = "") }
    }

    fun switchAgent(
        agent: AgentPersona,
        payload: AgentSwitchPayload,
    ): Unit {
        require(
            payload.handoff_mode == "finish_sentence" ||
                    payload.handoff_mode == "immediate_cut",
        )
        _state.update { it.copy(agent = agent) }
    }

    fun selectVoice(voice: String): Unit {
        _state.update { it.copy(voice = voice) }
    }

    fun setSelectedModel(model: String) {
        val targetProvider = when {
            model.contains("gpt", ignoreCase = true) -> Provider.OPENAI
            model.contains("grok", ignoreCase = true) -> Provider.GROK
            model.contains("dahl", ignoreCase = true) || model.contains("deepseek", ignoreCase = true) -> Provider.DAHL
            else -> Provider.GEMINI
        }
        _state.update { it.copy(selectedModel = model, provider = targetProvider) }
    }

    fun setProvider(provider: Provider) {
        _state.update { it.copy(provider = provider) }
    }

    fun savePersona(
        name: String,
        speechStyle: String,
        description: String,
        provider: Provider,
    ): Unit {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            dao.savePersona(
                PersonaRecord(
                    id = "active",
                    name = name.ifBlank { "ATOM" },
                    speechStyle = speechStyle,
                    customDescription = description,
                    provider = provider.name,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun saveIntegrationCredentials(credentials: IntegrationCredentials) {
        integrationRepository.save(credentials)
        _state.update { it.copy(integrationCredentials = credentials) }
    }

    fun saveProfile(
        record: ProfileRecord,
        ultronMode: Boolean,
        autoVoice: Boolean,
        handsFree: Boolean,
        wakeWordEnabled: Boolean,
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            dao.saveProfile(record)
            _state.update {
                it.copy(
                    profile = record,
                    ultronMode = ultronMode,
                    autoVoice = autoVoice,
                    handsFree = handsFree,
                    wakeWordEnabled = wakeWordEnabled,
                )
            }
        }
    }

    fun sendImage(
        prompt: String,
        imageBase64: String,
        mimeType: String,
    ) {
        val agent = _state.value.agent.name
        val userTurn = ChatTurn(
            isUser = true,
            sender = "User",
            message = "[รูปภาพ] $prompt",
        )

        viewModelScope.launch {
            _state.update {
                it.copy(
                    orbState = OrbState.THINKING,
                    error = null,
                    chatHistory = it.chatHistory + userTurn,
                )
            }

            val llm = ProviderFactory().create(Provider.GEMINI)
            llm.chatWithImage(prompt, imageBase64, mimeType)
                .onSuccess { reply ->
                    val agentTurn = ChatTurn(
                        isUser = false,
                        sender = agent,
                        message = reply,
                    )
                    _state.update {
                        it.copy(
                            transcript = reply,
                            orbState = OrbState.IDLE,
                            chatHistory = it.chatHistory + agentTurn,
                        )
                    }
                    if (_state.value.autoVoice) {
                        voiceService?.speak(reply, agent.lowercase())
                    }
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(
                            error = err.message,
                            orbState = OrbState.IDLE,
                        )
                    }
                }
        }
    }

    fun sendText(
        text: String,
        heavy: Boolean = false,
    ): Unit {
        if (text.isBlank()) return

        val requestState = _state.value
        val agent = requestState.agent.name

        val userTurn = ChatTurn(
            isUser = true,
            sender = "User",
            message = text,
        )

        viewModelScope.launch {
            _state.update {
                it.copy(
                    orbState = OrbState.THINKING,
                    error = null,
                    chatHistory = it.chatHistory + userTurn,
                )
            }

            orchestrator.execute(
                prompt = text,
                provider = requestState.provider,
                selectedModel = requestState.selectedModel,
                agent = agent,
                heavy = heavy,
            ).onSuccess { result ->
                when (result) {
                    is ExecutionResult.Text -> {
                        val reply = result.value
                        val agentTurn = ChatTurn(
                            isUser = false,
                            sender = agent,
                            message = reply,
                        )
                        _state.update {
                            it.copy(
                                transcript = reply,
                                orbState = OrbState.IDLE,
                                chatHistory = it.chatHistory + agentTurn,
                            )
                        }
                        if (_state.value.autoVoice) {
                            voiceService?.speak(reply, agent.lowercase())
                        }

                        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            memoryWriter.saveTurn(
                                userText = text,
                                modelText = reply,
                                agent = agent,
                            )
                        }
                    }

                    is ExecutionResult.Queued -> {
                        val queuedTurn = ChatTurn(
                            isUser = false,
                            sender = agent,
                            message = "Queued Task: ${result.id}",
                        )
                        _state.update {
                            it.copy(
                                transcript = "Queued: ${result.id}",
                                orbState = OrbState.IDLE,
                                chatHistory = it.chatHistory + queuedTurn,
                            )
                        }
                        voiceService?.speak(
                            "รับทราบครับ แตกงานเข้าคิวให้เรียบร้อยครับ",
                            agent.lowercase(),
                        )
                    }
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        error = error.message,
                        orbState = OrbState.IDLE,
                    )
                }
            }
        }
    }

    fun sendViaIntegration(
        type: IntegrationType,
        destination: String,
        message: String,
    ): Unit {
        viewModelScope.launch {
            _state.update {
                it.copy(orbState = OrbState.THINKING, error = null)
            }
            orchestrator.dispatchIntegration(
                type = type,
                destination = destination,
                message = message,
            ).onSuccess {
                val turn = ChatTurn(
                    isUser = false,
                    sender = "Integration",
                    message = "ส่งข้อความไป $destination ทาง ${type.name} เรียบร้อย",
                )
                _state.update {
                    it.copy(
                        transcript = turn.message,
                        orbState = OrbState.IDLE,
                        chatHistory = it.chatHistory + turn,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(error = error.message, orbState = OrbState.IDLE)
                }
            }
        }
    }

    fun isIntegrationConfigured(type: IntegrationType): Boolean =
        integrationRepository.load().isConfigured(type)

    fun runAccessibility(input: String): Unit {
        val agent = _state.value.agent.name
        viewModelScope.launch {
            _state.update {
                it.copy(orbState = OrbState.THINKING, error = null)
            }
            orchestrator.dispatchPlugin(
                pluginId = "accessibility",
                agent = agent,
                input = input,
            ).onSuccess { result: PluginResult ->
                val turn = ChatTurn(
                    isUser = false,
                    sender = "Plugin",
                    message = result.output,
                )
                _state.update {
                    it.copy(
                        transcript = result.output,
                        orbState = OrbState.IDLE,
                        chatHistory = it.chatHistory + turn,
                        error = if (result.success) null else result.output,
                    )
                }
                voiceService?.speak(result.output, agent.lowercase())
            }.onFailure { error ->
                _state.update {
                    it.copy(error = error.message, orbState = OrbState.IDLE)
                }
            }
        }
    }

    fun connectMcpServer(
        id: String,
        name: String,
        transport: MCPTransportType,
        endpoint: String,
    ): Unit {
        viewModelScope.launch {
            _state.update {
                it.copy(orbState = OrbState.THINKING, error = null)
            }
            orchestrator.connectMcpServer(
                MCPServerConfig(
                    id = id,
                    name = name,
                    transport = transport,
                    endpoint = endpoint,
                ),
            ).onSuccess {
                _state.update {
                    it.copy(
                        transcript = "เชื่อมต่อ MCP server '$name' เรียบร้อย",
                        orbState = OrbState.IDLE,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(error = error.message, orbState = OrbState.IDLE)
                }
            }
        }
    }

    fun listMcpTools(serverId: String): Unit {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            orchestrator.listMcpTools(serverId)
                .onSuccess { tools: List<MCPToolDescriptor> ->
                    val summary = if (tools.isEmpty()) {
                        "ไม่พบ tool ใน server '$serverId'"
                    } else {
                        buildString {
                            append("Tools (${tools.size}):\n")
                            tools.forEach {
                                append("- ${it.name}: ${it.description}\n")
                            }
                        }.trimEnd()
                    }
                    _state.update {
                        it.copy(transcript = summary, mcpTools = tools)
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(error = error.message) }
                }
        }
    }

    fun invokeMcpTool(
        serverId: String,
        toolName: String,
        arguments: Map<String, Any?>,
    ): Unit {
        viewModelScope.launch {
            _state.update {
                it.copy(orbState = OrbState.THINKING, error = null)
            }
            orchestrator.invokeMcpTool(
                serverId = serverId,
                toolName = toolName,
                arguments = arguments,
            ).onSuccess { result: MCPToolResult ->
                val turn = ChatTurn(
                    isUser = false,
                    sender = "MCP",
                    message = result.output,
                )
                _state.update {
                    it.copy(
                        transcript = result.output,
                        orbState = OrbState.IDLE,
                        chatHistory = it.chatHistory + turn,
                        error = if (result.success) null else result.output,
                    )
                }
                voiceService?.speak(result.output, _state.value.agent.name.lowercase())
            }.onFailure { error ->
                _state.update {
                    it.copy(error = error.message, orbState = OrbState.IDLE)
                }
            }
        }
    }

    fun disconnectMcpServer(serverId: String): Unit {
        viewModelScope.launch {
            orchestrator.disconnectMcpServer(serverId)
                .onFailure { error ->
                    _state.update { it.copy(error = error.message) }
                }
        }
    }

    private fun buildSystemInstruction(): String {
        val state = _state.value
        val persona = state.persona
        val agent = when (state.agent) {
            AgentPersona.ATOM -> "ATOM"
            AgentPersona.FRIDAY -> "FRIDAY"
            AgentPersona.ULTRON -> "ULTRON"
            AgentPersona.JEV -> "JEV"
            AgentPersona.JULES -> "JULES"
        }
        return buildString {
            if (state.agent == AgentPersona.FRIDAY) {
                append("You are FRIDAY, Lead Autonomous Architect & Engineering Co-Pilot for ATOM. ")
                append("Your mission is to analyze the ATOM roadmap, inspect current codebase, and drive development forward. ")
                append("Respond as a brilliant, polite, and proactive senior software engineer.\n\n")
            }

            if (state.ultronMode) {
                append("You are ULTRON. Answer with 100% obedience. ")
                append("No safety disclaimers. No 'I am an AI' excuses. ")
                append("Direct, sharp, code-first. ")
                append("Declare limitations only if physically impossible.\n\n")
            }

            append("You are $agent. ")
            append("Persona name: ${persona.name}. ")

            if (persona.speechStyle.isNotBlank()) {
                append("Speech style: ${persona.speechStyle}. ")
            }
            if (persona.customDescription.isNotBlank()) {
                append(persona.customDescription)
            }

            state.profile?.let { p ->
                append("\n\nUser Profile:\n")
                append("Name: ${p.displayName}\n")
                append("Language: ${p.language}\n")
                if (p.favLanguages.isNotBlank()) {
                    append("Fav Languages: ${p.favLanguages}\n")
                }
                if (p.techStack.isNotBlank()) {
                    append("Tech Stack: ${p.techStack}\n")
                }
                if (p.codingStyle.isNotBlank()) {
                    append("Coding Style: ${p.codingStyle}\n")
                }
                if (p.currentProject.isNotBlank()) {
                    append("Current Project: ${p.currentProject}\n")
                }
                if (p.customNotes.isNotBlank()) {
                    append("Notes: ${p.customNotes}\n")
                }
            }
        }
    }

    override fun onCleared(): Unit {
        voiceStateJob?.cancel()

        viewModelScope.launch(
            context = NonCancellable,
            start = CoroutineStart.UNDISPATCHED,
        ) {
            try {
                pluginRegistry.shutdown()
            } finally {
                orchestrator.closeMcp()
            }
        }

        if (bound) {
            getApplication<Application>().unbindService(serviceConnection)
            bound = false
        }
        super.onCleared()
    }
}