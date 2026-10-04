package com.aivo.sdk.provider.gemini

import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.core.port.LlmProvider
import com.aivo.sdk.core.port.staticCredentials
import com.aivo.sdk.core.util.SecretString
import com.aivo.sdk.testing.LlmProviderContractTest
import com.aivo.sdk.testing.fixtures.GeminiFixtures
import com.aivo.sdk.transport.HttpLlmProvider
import com.aivo.sdk.transport.auth.HeaderKeyAuth
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

class GeminiProviderContractTest : LlmProviderContractTest() {

    override val testModel: String = "gemini-2.5-flash"

    override fun createProvider(): LlmProvider {
        val mockEngine = MockEngine { request ->
            val isStream = request.url.encodedPath.contains("stream") ||
                request.body.toByteArray().decodeToString().contains("\"stream\":true")
            val content = if (isStream) GeminiFixtures.SSE_STREAM else GeminiFixtures.PLAIN_RESPONSE
            val contentType = if (isStream) "text/event-stream" else "application/json"
            respond(
                content = content,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, contentType),
            )
        }

        return HttpLlmProvider(
            id = ProviderId("gemini"),
            capabilities = GeminiProvider(
                GeminiConfig(
                    id = "gemini",
                    credentials = staticCredentials(SecretString("fake-gemini-key")),
                )
            ).capabilities,
            baseUrl = "https://generativelanguage.googleapis.com",
            httpClient = HttpClient(mockEngine),
            protocol = GeminiWireProtocol(),
            authStrategy = HeaderKeyAuth("x-goog-api-key", staticCredentials(SecretString("fake-gemini-key"))),
        )
    }
}
