package online.assadawut.atom.ollama

import com.google.gson.Gson
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class OllamaClient(private val baseUrl: String) {
    private val gson = Gson()
    private val client = HttpClient(OkHttp)
    suspend fun chat(model: String, prompt: String): String {
        val body = mapOf("model" to model, "prompt" to prompt, "stream" to false)
        val response: HttpResponse = client.post("$baseUrl/api/generate") {
            contentType(ContentType.Application.Json)
            setBody(gson.toJson(body))
        }
        val raw = response.body<String>()
        val json = gson.fromJson(raw, Map::class.java)
        return json["response"]?.toString().orEmpty()
    }
}