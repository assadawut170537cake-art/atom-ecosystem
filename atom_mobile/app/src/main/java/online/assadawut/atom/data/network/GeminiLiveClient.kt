package online.assadawut.atom.data.network

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class GeminiLiveClient(private val okHttpClient: OkHttpClient) {

    private var webSocket: WebSocket? = null
    private val gson = Gson()

    private val _audioFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 100)
    val audioFlow: SharedFlow<ByteArray> = _audioFlow.asSharedFlow()

    private val _textFlow = MutableSharedFlow<String>(extraBufferCapacity = 100)
    val textFlow: SharedFlow<String> = _textFlow.asSharedFlow()

    fun connect(apiKey: String, systemInstruction: String) {
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(url).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                sendSetup(systemInstruction)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                handleServerMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)
                webSocket.cancel()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                t.printStackTrace()
            }
        })
    }

    private fun sendSetup(systemInstruction: String) {
        val setupMsg = JsonObject().apply {
            add("setup", JsonObject().apply {
                addProperty("model", "models/gemini-2.0-flash-exp")
                
                add("generationConfig", JsonObject().apply {
                    val responseModalities = com.google.gson.JsonArray().apply { add("AUDIO") }
                    add("responseModalities", responseModalities)
                })

                add("systemInstruction", JsonObject().apply {
                    val partsArray = com.google.gson.JsonArray()
                    partsArray.add(JsonObject().apply {
                        addProperty("text", systemInstruction)
                    })
                    add("parts", partsArray)
                })
            })
        }
        webSocket?.send(gson.toJson(setupMsg))
    }

    fun sendAudio(pcmData: ByteArray) {
        val base64Audio = Base64.encodeToString(pcmData, Base64.NO_WRAP)
        
        // Wrap audio in realtimeInput frame (or realtime_input as some implementations might use)
        val realtimeInputMsg = JsonObject().apply {
            add("realtimeInput", JsonObject().apply {
                val mediaChunksArray = com.google.gson.JsonArray()
                mediaChunksArray.add(JsonObject().apply {
                    addProperty("mimeType", "audio/pcm;rate=16000")
                    addProperty("data", base64Audio)
                })
                add("mediaChunks", mediaChunksArray)
            })
        }
        webSocket?.send(gson.toJson(realtimeInputMsg))
    }

    private fun handleServerMessage(text: String) {
        try {
            val jsonObject = JsonParser.parseString(text).asJsonObject
            
            // Handle both camelCase and snake_case depending on API exact version mapping
            val serverContent = jsonObject.getAsJsonObject("serverContent") ?: jsonObject.getAsJsonObject("server_content")
            
            if (serverContent != null) {
                val modelTurn = serverContent.getAsJsonObject("modelTurn") ?: serverContent.getAsJsonObject("model_turn")
                if (modelTurn != null) {
                    val parts = modelTurn.getAsJsonArray("parts")
                    if (parts != null) {
                        for (partElement in parts) {
                            val part = partElement.asJsonObject
                            
                            // Check for text transcripts
                            if (part.has("text")) {
                                _textFlow.tryEmit(part.get("text").asString)
                            }
                            
                            // Check for inlineData (audio bytes)
                            val inlineData = part.getAsJsonObject("inlineData") ?: part.getAsJsonObject("inline_data")
                            if (inlineData != null && inlineData.has("data")) {
                                val base64Data = inlineData.get("data").asString
                                val audioBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                _audioFlow.tryEmit(audioBytes)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnected")
        webSocket = null
    }
}
