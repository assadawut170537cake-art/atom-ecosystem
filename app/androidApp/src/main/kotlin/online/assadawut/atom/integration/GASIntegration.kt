package online.assadawut.atom.integration

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import online.assadawut.atom.network.ATOMHttpClient

class GASIntegration(
    private val webAppUrl: String
) {
    private val client = ATOMHttpClient.create()

    suspend fun executeScript(action: String, payloadJson: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.post(webAppUrl) {
                contentType(ContentType.Application.Json)
                setBody("""{"action":"$action","data":$payloadJson}""")
            }
            if (response.status.isSuccess()) {
                response.bodyAsText()
            } else {
                error("GAS Error HTTP ${response.status.value}")
            }
        }
    }
}
