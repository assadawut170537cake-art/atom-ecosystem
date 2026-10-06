package online.assadawut.atom.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import online.assadawut.atom.core.model.AgentPersona

class HandoffManager {
    private val _activeAgent = MutableStateFlow(AgentPersona.ATOM)
    val activeAgent: StateFlow<AgentPersona> = _activeAgent

    fun requestHandoff(target: AgentPersona, reason: String) {
        // Log reason, update state
        _activeAgent.value = target
    }
}