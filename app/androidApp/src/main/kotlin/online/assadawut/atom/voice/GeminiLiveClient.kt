package online.assadawut.atom.voice

import android.content.Context
import android.util.Base64
import okhttp3.*
import okio.ByteString

class GeminiLiveClient(private val context: Context) {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    fun connect() {
        val apiKey = online.assadawut.atom.BuildConfig.GEMINI_API_KEY
        val request = Request.Builder()
            .url("wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey")
            .build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {}
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {}
        })
    }
    fun sendAudio(pcm16Data: ByteArray) {
        val base64 = Base64.encodeToString(pcm16Data, Base64.NO_WRAP)
        val json = """{"realtimeInput": {"mediaChunks": [{"mimeType": "audio/pcm;rate=16000", "data": "$base64"}]}}"""
        webSocket?.send(json)
    }
    fun disconnect() {
        webSocket?.close(1000, "User requested")
    }
}