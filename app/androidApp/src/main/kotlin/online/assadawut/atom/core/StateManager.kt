package online.assadawut.atom.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import online.assadawut.atom.core.model.OrbState

class StateManager {
    private val _orbState = MutableStateFlow(OrbState.IDLE)
    val orbState: StateFlow<OrbState> = _orbState

    fun setState(state: OrbState) {
        _orbState.value = state
    }
}