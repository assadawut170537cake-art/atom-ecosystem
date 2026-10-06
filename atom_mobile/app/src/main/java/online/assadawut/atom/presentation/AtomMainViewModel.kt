package online.assadawut.atom.presentation

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import online.assadawut.atom.data.network.GeminiLiveClient
import online.assadawut.atom.data.repository.SettingsRepository
import online.assadawut.atom.data.voice.AudioPlayer
import online.assadawut.atom.data.voice.AudioRecorder
import online.assadawut.atom.domain.IntentRouter
import online.assadawut.atom.domain.PersonaManager
import online.assadawut.atom.presentation.components.AssistantState

class AtomMainViewModel(context: Context) : ViewModel() {

    private val audioRecorder = AudioRecorder()
    private val audioPlayer = AudioPlayer(context)
    private val geminiClient = GeminiLiveClient(OkHttpClient())
    val settingsRepository = SettingsRepository(context)

    private val _assistantState = MutableStateFlow(AssistantState.IDLE)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _orbColor = MutableStateFlow(Color.Cyan)
    val orbColor: StateFlow<Color> = _orbColor.asStateFlow()

    private val _currentAgent = MutableStateFlow("ATOM")
    val currentAgent: StateFlow<String> = _currentAgent.asStateFlow()

    private val _transcript = MutableStateFlow("พร้อมคุยสตรีมสดแล้วครับ แตะปุ่มไมค์หรือลูกแก้วเพื่อเปิดสายคุยได้เลย")
    val transcript: StateFlow<String> = _transcript.asStateFlow()

    private var isConnected = false

    init {
        // Handle incoming audio from Gemini Live WebSocket
        viewModelScope.launch(Dispatchers.IO) {
            geminiClient.audioFlow.collect { pcmData ->
                _assistantState.value = AssistantState.SPEAKING
                audioPlayer.play(pcmData)
            }
        }

        // Handle incoming text transcript from Gemini Live WebSocket
        viewModelScope.launch(Dispatchers.IO) {
            geminiClient.textFlow.collect { text ->
                _transcript.value = text
                
                // Check if user wants to switch agents (Handoff Protocol)
                val switchIntent = IntentRouter.analyzeTranscript(text)
                if (switchIntent != null) {
                    switchAgent(switchIntent.targetAgent)
                }
            }
        }
    }

    fun toggleVoiceSession() {
        if (!isConnected) {
            startSession()
        } else {
            stopSession()
        }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            settingsRepository.saveGeminiApiKey(key)
        }
    }

    private fun startSession() {
        viewModelScope.launch(Dispatchers.IO) {
            val apiKey = settingsRepository.geminiApiKey.firstOrNull() ?: ""
            if (apiKey.isBlank()) {
                _transcript.value = "⚠️ กรุณาใส่ Gemini API Key ในการตั้งค่าก่อนเริ่มคุยสดครับ"
                return@launch
            }

            val prompt = PersonaManager.getSystemInstruction(_currentAgent.value)

            _assistantState.value = AssistantState.THINKING
            geminiClient.connect(apiKey, prompt)
            isConnected = true

            _assistantState.value = AssistantState.LISTENING

            // Stream continuous audio from mic to Gemini Live API
            audioRecorder.startRecording().collect { pcmData ->
                if (isConnected) {
                    geminiClient.sendAudio(pcmData)
                }
            }
        }
    }

    fun stopSession() {
        isConnected = false
        audioPlayer.stop()
        geminiClient.disconnect()
        _assistantState.value = AssistantState.IDLE
    }

    private fun switchAgent(agentName: String) {
        _currentAgent.value = agentName
        _orbColor.value = when (agentName) {
            "FRIDAY" -> Color(0xFF00FF88) // Emerald Green
            "ULTRON" -> Color(0xFFFF3333) // Flame Red
            else -> Color.Cyan // ATOM
        }

        if (isConnected) {
            // Reconnect with new Persona Prompt seamlessly
            stopSession()
            startSession()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopSession()
    }
}
