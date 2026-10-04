package com.aivo.sdk.provider.openai

import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.core.port.LlmProvider
import com.aivo.sdk.core.port.staticCredentials
import com.aivo.sdk.core.util.SecretString
import com.aivo.sdk.testing.LlmProviderContractTest
import com.aivo.sdk.testing.fixtures.OpenAiFixtures
import com.aivo.sdk.transport.HttpLlmProvider
import com.aivo.sdk.transport.auth.BearerTokenAuth
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

class OpenAiCompatibleProviderContractTest : LlmProviderContractTest() {

    override val testModel: String = "deepseek/deepseek-v4.1-flash"

    override fun createProvider(): LlmProvider {
        val mockEngine = MockEngine { request ->
            val isStream = request.body.toByteArray().decodeToString().contains("\"stream\":true")
            val content = if (isStream) OpenAiFixtures.SSE_STREAM else OpenAiFixtures.PLAIN_RESPONSE
            val contentType = if (isStream) "text/event-stream" else "application/json"
            respond(
                content = content,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, contentType),
            )
        }

        return HttpLlmProvider(
            id = ProviderId("openai"),
            capabilities = OpenAiCompatibleProvider(
                OpenAiCompatibleConfig(
                    id = "openai",
                    baseUrl = "https://api.openai.com/v1",
                    credentials = staticCredentials(SecretString("test-key")),
                )
            ).capabilities,
            baseUrl = "https://api.openai.com/v1",
            httpClient = HttpClient(mockEngine),
            protocol = OpenAiCompatibleWireProtocol(ProviderId("openai")),
            authStrategy = BearerTokenAuth(staticCredentials(SecretString("test-key"))),
        )
    }
}
