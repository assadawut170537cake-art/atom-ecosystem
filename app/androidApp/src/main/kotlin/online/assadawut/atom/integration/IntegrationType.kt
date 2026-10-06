package online.assadawut.atom.integration

enum class IntegrationType {
    LINE, TELEGRAM, SLACK, GEMINI_EXTERNAL, OPENAI_EXTERNAL
}

fun IntegrationCredentials.isConfigured(type: IntegrationType): Boolean = when(type) {
    IntegrationType.LINE -> !lineToken.isNullOrBlank()
    IntegrationType.TELEGRAM -> !telegramToken.isNullOrBlank()
    IntegrationType.SLACK -> !slackToken.isNullOrBlank()
    IntegrationType.GEMINI_EXTERNAL -> !geminiToken.isNullOrBlank()
    IntegrationType.OPENAI_EXTERNAL -> !openaiToken.isNullOrBlank()
}
