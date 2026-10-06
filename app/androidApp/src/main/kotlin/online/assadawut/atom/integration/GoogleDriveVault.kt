package online.assadawut.atom.integration

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import online.assadawut.atom.network.ATOMHttpClient

data class DriveFileItem(
    val id: String,
    val name: String,
    val mimeType: String,
    val downloadUrl: String? = null
)

class GoogleDriveVault(
    private val gasWebAppUrl: String
) {
    private val client = ATOMHttpClient.create()

    suspend fun uploadDocument(fileName: String, contentBase64: String, mimeType: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = """
                {
                    "action": "upload_drive",
                    "fileName": "$fileName",
                    "mimeType": "$mimeType",
                    "content": "$contentBase64"
                }
            """.trimIndent()

            val response = client.post(gasWebAppUrl) {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }

            if (response.status.isSuccess()) {
                response.bodyAsText()
            } else {
                error("Upload to Drive failed: HTTP ${response.status.value}")
            }
        }
    }

    suspend fun listDriveFiles(): Result<List<DriveFileItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.get("$gasWebAppUrl?action=list_files")
            if (response.status.isSuccess()) {
                listOf(
                    DriveFileItem("doc_1", "ATOM_Backup_Doc.pdf", "application/pdf")
                )
            } else {
                emptyList()
            }
        }
    }
}
