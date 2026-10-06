package online.assadawut.atom.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ContextManager {
    private val _currentContext = MutableStateFlow<String>("")
    val currentContext: StateFlow<String> = _currentContext

    fun updateContext(context: String) {
        _currentContext.value = context
    }
}