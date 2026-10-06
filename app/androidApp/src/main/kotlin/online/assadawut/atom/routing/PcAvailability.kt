package online.assadawut.atom.routing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class PcAvailability(private val ollamaUrl: String = "http://100.64.0.1:11434") {
    suspend fun isOnline(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL("$ollamaUrl/api/tags").openConnection() as HttpURLConnection
            connection.connectTimeout = 1000
            connection.readTimeout = 1000
            connection.requestMethod = "GET"
            connection.responseCode in 200..299
        }.getOrDefault(false)
    }
}