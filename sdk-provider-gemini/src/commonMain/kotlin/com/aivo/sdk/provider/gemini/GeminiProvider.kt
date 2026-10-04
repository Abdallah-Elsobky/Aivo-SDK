package com.aivo.sdk.provider.gemini

import com.aivo.sdk.core.model.ProviderCapabilities
import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.core.port.CredentialsProvider
import com.aivo.sdk.core.port.LlmProvider
import com.aivo.sdk.transport.HttpClientFactory
import com.aivo.sdk.transport.HttpLlmProvider
import com.aivo.sdk.transport.TransportConfig
import com.aivo.sdk.transport.auth.HeaderKeyAuth

/**
 * Configuration for Google Gemini provider (Interactions API / generateContent).
 *
 * @param id          The identifier used for routing (e.g., `"gemini"`).
 * @param baseUrl     Gemini API base URL (default: `https://generativelanguage.googleapis.com`).
 * @param credentials API key credentials provider (attached as `x-goog-api-key`).
 * @param transport   HTTP transport settings (timeouts, headers, etc.).
 */
public data class GeminiConfig(
    val id: String = "gemini",
    val baseUrl: String = "https://generativelanguage.googleapis.com",
    val credentials: CredentialsProvider,
    val transport: TransportConfig = TransportConfig(),
)

/**
 * Factory function that creates a fully configured Google Gemini [LlmProvider].
 *
 * Internally wires [GeminiWireProtocol] into [HttpLlmProvider].
 *
 * ```kotlin
 * val provider = GeminiProvider(
 *     GeminiConfig(
 *         credentials = StaticCredentialsProvider("AIzaSy...")
 *     )
 * )
 * ```
 */
public fun GeminiProvider(config: GeminiConfig): LlmProvider {
    return HttpLlmProvider(
        id = ProviderId(config.id),
        capabilities = GeminiCapabilities,
        baseUrl = config.baseUrl,
        httpClient = HttpClientFactory.create(config.transport),
        protocol = GeminiWireProtocol(),
        authStrategy = HeaderKeyAuth("x-goog-api-key", config.credentials),
        extraHeaders = config.transport.extraHeaders,
        streamIdleTimeoutMs = config.transport.streamIdleTimeoutMs,
    )
}

/** Capabilities supported by Gemini Interactions / generateContent API. */
private val GeminiCapabilities = ProviderCapabilities(
    streaming = true,
    toolCalling = true,
    parallelToolCalls = true,
    reasoning = true,
    structuredOutput = false,
    statefulConversation = true,
)
