package online.assadawut.atom.ui.state

import online.assadawut.atom.core.model.AgentPersona
import online.assadawut.atom.core.model.OrbState
import online.assadawut.atom.core.model.PersonaConfig
import online.assadawut.atom.core.model.Provider
import online.assadawut.atom.database.MemoryRecord
import online.assadawut.atom.database.ProfileRecord
import online.assadawut.atom.database.QueueRecord
import online.assadawut.atom.integration.IntegrationCredentials
import online.assadawut.atom.mcp.MCPToolDescriptor

data class ChatTurn(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val sender: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ATOMUiState(
    val orbState: OrbState = OrbState.IDLE,
    val agent: AgentPersona = AgentPersona.ATOM,
    val persona: PersonaConfig = PersonaConfig(provider = Provider.GEMINI),
    val provider: Provider = Provider.GEMINI,
    val voice: String = "",
    val connected: Boolean = false,
    val transcript: String = "",
    val memories: List<MemoryRecord> = emptyList(),
    val chatHistory: List<ChatTurn> = emptyList(),
    val queue: List<QueueRecord> = emptyList(),
    val error: String? = null,
    val mcpTools: List<MCPToolDescriptor> = emptyList(),
    val integrationCredentials: IntegrationCredentials = IntegrationCredentials(),
    
    val profile: ProfileRecord? = null,
    val ultronMode: Boolean = false,
    val autoVoice: Boolean = true,
    val handsFree: Boolean = false,
    val wakeWordEnabled: Boolean = false,
    val selectedModel: String = "gemini-3.8-flash"
)
