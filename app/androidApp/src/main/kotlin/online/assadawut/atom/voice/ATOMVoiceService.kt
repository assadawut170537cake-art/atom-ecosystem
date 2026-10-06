package online.assadawut.atom.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import online.assadawut.atom.core.model.OrbState
import java.util.Locale

data class VoicePersonaConfig(
    val voiceName: String,
    val pitch: Float,
    val speechRate: Float,
    val styleDescription: String
)

data class VoiceServiceState(
    val orbState: OrbState = OrbState.IDLE,
    val connected: Boolean = false,
    val transcript: String = "",
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val activePersona: String = "atom",
    val continuousVoiceMode: Boolean = false,
    val error: String? = null
)

class ATOMVoiceService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        private const val CHANNEL_ID = "atom_voice_channel"
        private const val NOTIFICATION_ID = 1001

        val PERSONAS = mapOf(
            "atom" to VoicePersonaConfig(
                voiceName = "NaturalHuman",
                pitch = 1.0f,
                speechRate = 1.0f,
                styleDescription = "Natural human voice, warm, friendly, conversational Thai"
            ),
            "friday" to VoicePersonaConfig(
                voiceName = "NaturalFemale",
                pitch = 1.02f,
                speechRate = 1.0f,
                styleDescription = "Natural female human voice, polite, warm, pleasant"
            ),
            "ultron" to VoicePersonaConfig(
                voiceName = "NaturalMale",
                pitch = 0.95f,
                speechRate = 1.0f,
                styleDescription = "Natural male human voice, calm, direct, polite"
            )
        )
    }

    inner class LocalBinder : Binder() {
        fun service(): ATOMVoiceService = this@ATOMVoiceService
    }

    private val binder = LocalBinder()
    private val _state = MutableStateFlow(VoiceServiceState())
    val state: StateFlow<VoiceServiceState> = _state

    private var tts: TextToSpeech? = null
    private var ttsInitialized = false

    private var speechRecognizer: SpeechRecognizer? = null
    private var speechCallback: ((String) -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        tts = TextToSpeech(applicationContext, this)
        initSpeechRecognizer()
    }

    private fun initSpeechRecognizer() {
        mainHandler.post {
            if (SpeechRecognizer.isRecognitionAvailable(applicationContext)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            _state.update { it.copy(orbState = OrbState.LISTENING) }
                        }

                        override fun onBeginningOfSpeech() {
                            _state.update { it.copy(orbState = OrbState.LISTENING) }
                        }

                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {
                            _state.update { it.copy(orbState = OrbState.THINKING) }
                        }

                        override fun onError(error: Int) {
                            _state.update { it.copy(orbState = OrbState.IDLE, error = null) }
                        }

                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val spokenText = matches?.firstOrNull() ?: ""
                            _state.update { it.copy(orbState = OrbState.IDLE, transcript = spokenText, error = null) }
                            if (spokenText.isNotBlank()) {
                                speechCallback?.invoke(spokenText)
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val thLocale = Locale("th", "TH")
            val result = tts?.setLanguage(thLocale)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.US
            } else {
                // Try selecting high quality / network neural Thai voice if available
                tts?.voices?.find { voice ->
                    voice.locale == thLocale && (voice.name.contains("network") || voice.name.contains("local-x"))
                }?.let { bestVoice ->
                    tts?.voice = bestVoice
                }
            }

            ttsInitialized = true

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _state.update { it.copy(orbState = OrbState.SPEAKING) }
                }

                override fun onDone(utteranceId: String?) {
                    _state.update { it.copy(orbState = OrbState.IDLE) }
                }

                override fun onError(utteranceId: String?) {
                    _state.update { it.copy(orbState = OrbState.IDLE) }
                }
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val notification = createNotification("ATOM Voice Engine Active")
                startForeground(NOTIFICATION_ID, notification)
                _state.update { it.copy(connected = true) }
            }
            ACTION_STOP -> {
                stopVoice()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun startListening(onResult: (String) -> Unit) {
        speechCallback = onResult
        _state.update { it.copy(continuousVoiceMode = true, connected = true) }
        startListeningInternal()
    }

    private fun startListeningInternal() {
        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    initSpeechRecognizer()
                }
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "th-TH")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "th-TH")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
                }
                speechRecognizer?.startListening(intent)
                _state.update { it.copy(orbState = OrbState.LISTENING) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun speak(text: String, persona: String = "atom") {
        if (!ttsInitialized || text.isBlank()) return

        val cleanSpeechText = text.replace(Regex("""```[\s\S]*?```"""), "รายละเอียดบนหน้าจอครับ")
            .replace(Regex("""[\*\#_>~\-]"""), "")
            .trim()

        val personaCfg = PERSONAS[persona.lowercase()] ?: PERSONAS["atom"]!!

        _state.update {
            it.copy(
                orbState = OrbState.SPEAKING,
                transcript = cleanSpeechText,
                activePersona = persona.lowercase(),
                pitch = personaCfg.pitch,
                speechRate = personaCfg.speechRate
            )
        }

        val hasThai = cleanSpeechText.any { it in '\u0E00'..'\u0E7F' }
        tts?.language = if (hasThai) Locale("th", "TH") else Locale.US

        tts?.setSpeechRate(personaCfg.speechRate)
        tts?.setPitch(personaCfg.pitch)

        val params = android.os.Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "atom_utt_${System.currentTimeMillis()}")
        tts?.speak(cleanSpeechText, TextToSpeech.QUEUE_FLUSH, params, "atom_utt_${System.currentTimeMillis()}")
    }

    fun setSpeechRate(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 2.0f)
        _state.update { it.copy(speechRate = clamped) }
        tts?.setSpeechRate(clamped)
    }

    fun setPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.5f, 2.0f)
        _state.update { it.copy(pitch = clamped) }
        tts?.setPitch(clamped)
    }

    fun startVoice(voice: String, systemInstruction: String) {
        _state.update { it.copy(connected = true, continuousVoiceMode = true) }
    }

    fun stopVoice() {
        _state.update { it.copy(continuousVoiceMode = false, orbState = OrbState.IDLE, connected = false) }
        mainHandler.post { speechRecognizer?.stopListening() }
        tts?.stop()
    }

    fun interrupt() {
        mainHandler.post { speechRecognizer?.stopListening() }
        tts?.stop()
        _state.update { it.copy(orbState = OrbState.IDLE) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ATOM Voice Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ATOM Assistant")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        mainHandler.post { speechRecognizer?.destroy() }
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
