package online.assadawut.atom.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.launch
import online.assadawut.atom.integration.IntegrationCredentials
import online.assadawut.atom.integration.IntegrationType
import online.assadawut.atom.integration.isConfigured

@Composable
fun IntegrationSettingsScreen(
    credentials: IntegrationCredentials,
    onSave: (IntegrationCredentials) -> Unit,
    onBack: () -> Unit,
) {
    var lineToken by remember { mutableStateOf(credentials.lineToken) }
    var telegramToken by remember { mutableStateOf(credentials.telegramToken) }
    var slackToken by remember { mutableStateOf(credentials.slackToken) }
    var baseDomain by remember { mutableStateOf(credentials.baseWebhookDomain) }
    var geminiToken by remember { mutableStateOf(credentials.geminiToken) }
    var openaiToken by remember { mutableStateOf(credentials.openaiToken) }

    var statusMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val formattedDomain = baseDomain.trim().removeSuffix("/")
    val lineWebhookUrl = if (formattedDomain.isNotBlank()) "$formattedDomain/api/line/webhook" else "กรุณากรอก Base Domain"
    val telegramWebhookUrl = if (formattedDomain.isNotBlank()) "$formattedDomain/api/telegram/webhook" else "กรุณากรอก Base Domain"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(onClick = onBack) {
                Text("Back")
            }
            Text(
                text = "Integrations",
                style = MaterialTheme.typography.titleLarge
            )
        }

        // Base Webhook Domain Input
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Webhook Base Domain", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = baseDomain,
                    onValueChange = { baseDomain = it },
                    label = { Text("Base Domain (e.g. https://xxx.ngrok-free.app)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        // LINE Token & Webhook URL Display
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LINE Integration", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = lineToken,
                    onValueChange = { lineToken = it },
                    label = { Text("LINE Channel Access Token") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = if (credentials.isConfigured(IntegrationType.LINE)) "✓ ตั้งค่าแล้ว" else "ยังไม่ตั้งค่า",
                    color = if (credentials.isConfigured(IntegrationType.LINE)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
                Text("LINE Webhook URL:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                SelectionContainer {
                    Text(lineWebhookUrl, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        // Telegram Token & Webhook URL Display & Auto-Set Button
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Telegram Integration", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = telegramToken,
                    onValueChange = { telegramToken = it },
                    label = { Text("Telegram Bot Token") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = if (credentials.isConfigured(IntegrationType.TELEGRAM)) "✓ ตั้งค่าแล้ว" else "ยังไม่ตั้งค่า",
                    color = if (credentials.isConfigured(IntegrationType.TELEGRAM)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
                Text("Telegram Webhook URL:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                SelectionContainer {
                    Text(telegramWebhookUrl, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                }

                ElevatedButton(
                    onClick = {
                        if (telegramToken.isBlank() || formattedDomain.isBlank()) {
                            statusMessage = "กรุณากรอก Telegram Token และ Base Domain ก่อนกดตั้งค่า"
                            return@ElevatedButton
                        }
                        scope.launch {
                            try {
                                statusMessage = "กำลังส่งคำขอตั้งค่า Webhook ไปยัง Telegram..."
                                val client = HttpClient()
                                val targetUrl = "https://api.telegram.org/bot${telegramToken.trim()}/setWebhook?url=$telegramWebhookUrl"
                                val res: HttpResponse = client.get(targetUrl)
                                val body = res.bodyAsText()
                                statusMessage = "ผลลัพธ์จาก Telegram: $body"
                                client.close()
                            } catch (e: Exception) {
                                statusMessage = "เกิดข้อผิดพลาด: ${e.message}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("⚡ Auto-Set Telegram Webhook")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("การเชื่อมต่อ AI ข้ามระบบ (External LLM Routing)", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = geminiToken,
            onValueChange = { geminiToken = it },
            label = { Text("Gemini API Key (สำหรับเรียกใช้ข้ามแอป)") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = openaiToken,
            onValueChange = { openaiToken = it },
            label = { Text("OpenAI API Key (สำหรับเรียกใช้ข้ามแอป)") },
            modifier = Modifier.fillMaxWidth()
        )

        // Status Message Notice
        statusMessage?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = msg,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Button(
            onClick = {
                onSave(
                    IntegrationCredentials(
                        lineToken = lineToken.trim(),
                        telegramToken = telegramToken.trim(),
                        slackToken = slackToken.trim(),
                        baseWebhookDomain = baseDomain.trim(),
                        geminiToken = geminiToken.trim(),
                        openaiToken = openaiToken.trim()
                    )
                )
                statusMessage = "บันทึกการตั้งค่าเรียบร้อยแล้ว!"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save All Settings")
        }
    }
}
