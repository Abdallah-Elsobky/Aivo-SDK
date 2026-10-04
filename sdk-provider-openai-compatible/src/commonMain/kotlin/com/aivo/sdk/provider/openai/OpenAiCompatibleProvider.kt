package com.aivo.sdk.provider.openai

import com.aivo.sdk.core.model.ProviderCapabilities
import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.core.port.CredentialsProvider
import com.aivo.sdk.core.port.LlmProvider
import com.aivo.sdk.transport.HttpClientFactory
import com.aivo.sdk.transport.HttpLlmProvider
import com.aivo.sdk.transport.TransportConfig
import com.aivo.sdk.transport.auth.BearerTokenAuth

/**
 * Configuration for any OpenAI-compatible provider.
 *
 * @param id         Routing identifier (e.g., `"openai"`, `"groq"`, `"together"`).
 * @param baseUrl    Provider API base URL (e.g., `"https://api.openai.com/v1"`).
 * @param credentials Bearer token provider.
 * @param extraHeaders Additional request headers (e.g., OpenRouter's `HTTP-Referer`).
 * @param transport  Transport configuration (timeouts, user agent, etc.).
 */
public data class OpenAiCompatibleConfig(
    val id: String,
    val baseUrl: String,
    val credentials: CredentialsProvider,
    val extraHeaders: Map<String, String> = emptyMap(),
    val transport: TransportConfig = TransportConfig(),
)

/**
 * Factory function for any OpenAI-compatible provider (OpenAI, Groq, Together, vLLM, etc.).
 */
public fun OpenAiCompatibleProvider(config: OpenAiCompatibleConfig): LlmProvider =
    HttpLlmProvider(
        id = ProviderId(config.id),
        capabilities = OpenAiCompatibleCapabilities,
        baseUrl = config.baseUrl,
        httpClient = HttpClientFactory.create(config.transport),
        protocol = OpenAiCompatibleWireProtocol(providerId = ProviderId(config.id)),
        authStrategy = BearerTokenAuth(config.credentials),
        extraHeaders = config.extraHeaders + config.transport.extraHeaders,
        streamIdleTimeoutMs = config.transport.streamIdleTimeoutMs,
    )

/**
 * Preset for [OpenRouter](https://openrouter.ai) — adds required routing headers.
 *
 * @param httpReferer  Your site URL for OpenRouter analytics (recommended).
 * @param xTitle       Your app name for OpenRouter analytics (optional).
 */
public fun OpenRouterProvider(
    id: String = "openrouter",
    baseUrl: String = "https://openrouter.ai/api/v1",
    credentials: CredentialsProvider,
    httpReferer: String? = null,
    xTitle: String? = null,
    transport: TransportConfig = TransportConfig(),
): LlmProvider {
    val headers = buildMap {
        httpReferer?.let { put("HTTP-Referer", it) }
        xTitle?.let { put("X-Title", it) }
    }
    return OpenAiCompatibleProvider(
        OpenAiCompatibleConfig(
            id = id,
            baseUrl = baseUrl,
            credentials = credentials,
            extraHeaders = headers,
            transport = transport,
        )
    )
}

private val OpenAiCompatibleCapabilities = ProviderCapabilities(
    streaming = true,
    toolCalling = true,
    parallelToolCalls = true,
    reasoning = true,    // DeepSeek-R1 style reasoning_content is supported.
    structuredOutput = false,
    statefulConversation = false,
)
