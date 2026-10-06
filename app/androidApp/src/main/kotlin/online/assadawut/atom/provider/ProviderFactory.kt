package online.assadawut.atom.provider

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.HttpTimeout
import online.assadawut.atom.BuildConfig
import online.assadawut.atom.core.model.Provider

class ProviderFactory {
    private val httpClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 60000
            connectTimeoutMillis = 15000
            socketTimeoutMillis = 60000
        }
    }

    fun create(provider: Provider, selectedModel: String = ""): LLMProvider {
        return when (provider) {
            Provider.GEMINI -> GeminiProvider(
                apiKey = BuildConfig.GEMINI_API_KEY,
                model = selectedModel.ifBlank { "gemini-3.8-flash" }
            )
            Provider.OPENAI -> OpenAIProvider(
                client = httpClient,
                baseUrl = "https://api.openai.com/v1",
                apiKey = BuildConfig.OPENAI_API_KEY,
                model = selectedModel.ifBlank { "gpt-4o" }
            )
            Provider.GROK -> OpenAIProvider(
                client = httpClient,
                baseUrl = "https://api.x.ai/v1",
                apiKey = BuildConfig.GROK_API_KEY,
                model = selectedModel.ifBlank { "grok-beta" }
            )
            Provider.DAHL -> DahlClient(
                client = httpClient,
                baseUrl = "https://inference.dahl.global/v1",
                apiKey = BuildConfig.DAHL_API_KEY
            )
        }
    }
}
