package online.assadawut.atom.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.gson.gson

object ATOMHttpClient {
    fun create(): HttpClient {
        return HttpClient(OkHttp) {
            install(ContentNegotiation) { gson() }
        }
    }
}