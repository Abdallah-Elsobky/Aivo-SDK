package com.aivo.sdk.provider.ollama

import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.core.port.LlmProvider
import com.aivo.sdk.testing.LlmProviderContractTest
import com.aivo.sdk.testing.fixtures.OllamaFixtures
import com.aivo.sdk.transport.HttpLlmProvider
import com.aivo.sdk.transport.auth.NoAuth
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

class OllamaProviderContractTest : LlmProviderContractTest() {

    override val testModel: String = "gemma4:31b"

    override fun createProvider(): LlmProvider {
        val mockEngine = MockEngine { request ->
            val isStream = request.body.toByteArray().decodeToString().contains("\"stream\":true")
            val content = if (isStream) OllamaFixtures.NDJSON_STREAM else OllamaFixtures.PLAIN_RESPONSE
            respond(
                content = content,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        return HttpLlmProvider(
            id = ProviderId("ollama"),
            capabilities = OllamaProvider(OllamaConfig(id = "ollama")).capabilities,
            baseUrl = "http://localhost:11434",
            httpClient = HttpClient(mockEngine),
            protocol = OllamaWireProtocol(),
            authStrategy = NoAuth,
        )
    }
}
