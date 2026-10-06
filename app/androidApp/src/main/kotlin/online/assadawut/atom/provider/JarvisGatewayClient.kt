package online.assadawut.atom.provider

import com.google.gson.Gson
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

data class JarvisChatRequest(
    val message: String,
    val target: String
)

class JarvisGatewayClient(
    private val client: HttpClient,
    private val baseUrl: String = "http://100.64.0.1:8000"
) {
    private val gson = Gson()

    suspend fun sendMessage(message: String, target: String = "hermes"): Result<String> = runCatching {
        val url = if (baseUrl.endsWith("/")) "${baseUrl}api/chat" else "$baseUrl/api/chat"
        val payload = JarvisChatRequest(message = message, target = target)

        val response: HttpResponse = client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(gson.toJson(payload))
        }

        if (!response.status.isSuccess()) {
            error("Jarvis Gateway error: ${response.status.value} - ${response.bodyAsText()}")
        }

        val raw = response.bodyAsText()
        val resMap = runCatching { gson.fromJson(raw, Map::class.java) }.getOrNull()
        
        resMap?.get("reply")?.toString() 
            ?: resMap?.get("message")?.toString() 
            ?: resMap?.get("response")?.toString() 
            ?: raw
    }
}
