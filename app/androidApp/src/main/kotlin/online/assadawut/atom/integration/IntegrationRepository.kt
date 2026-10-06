package online.assadawut.atom.integration

import android.content.Context
import android.content.SharedPreferences

class IntegrationRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("atom_integrations", Context.MODE_PRIVATE)

    fun load(): IntegrationCredentials {
        val lineToken = prefs.getString("line_token", null)
        val telegramToken = prefs.getString("telegram_token", null)
        val slackToken = prefs.getString("slack_token", null)
        val baseWebhookDomain = prefs.getString("base_webhook_domain", null)

        return IntegrationCredentials(
            lineToken = lineToken ?: "",
            telegramToken = telegramToken ?: "",
            slackToken = slackToken ?: "",
            baseWebhookDomain = baseWebhookDomain ?: "https://atom-agent.ngrok-free.app"
        )
    }

    fun save(credentials: IntegrationCredentials) {
        prefs.edit()
            .putString("line_token", credentials.lineToken)
            .putString("telegram_token", credentials.telegramToken)
            .putString("slack_token", credentials.slackToken)
            .putString("base_webhook_domain", credentials.baseWebhookDomain)
            .apply()
    }
}
