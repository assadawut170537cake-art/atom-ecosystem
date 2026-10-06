package online.assadawut.atom.integration

import com.google.gson.Gson

data class IntegrationCredentials(
    val lineToken: String = "",
    val telegramToken: String = "",
    val slackToken: String = "",
    val baseWebhookDomain: String = "",
    val geminiToken: String = "",
    val openaiToken: String = ""
)
