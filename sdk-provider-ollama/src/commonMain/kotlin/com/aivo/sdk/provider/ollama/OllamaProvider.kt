package com.aivo.sdk.provider.ollama

import com.aivo.sdk.core.model.ProviderCapabilities
import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.core.port.CredentialsProvider
import com.aivo.sdk.core.port.LlmProvider
import com.aivo.sdk.transport.HttpClientFactory
import com.aivo.sdk.transport.HttpLlmProvider
import com.aivo.sdk.transport.TransportConfig
import com.aivo.sdk.transport.auth.AuthStrategy
import com.aivo.sdk.transport.auth.BearerTokenAuth
import com.aivo.sdk.transport.auth.NoAuth

/**
 * Configuration for the Ollama provider.
 *
 * @param id          The identifier used for routing (e.g., `"local"`, `"ollama-cloud"`).
 * @param baseUrl     Ollama server base URL (default: `http://localhost:11434`).
 * @param credentials Optional Bearer token. Pass `null` for unauthenticated local instances.
 * @param transport   HTTP transport settings (timeouts, headers, etc.).
 */
public data class OllamaConfig(
    val id: String,
    val baseUrl: String = "https://ollama.com",
    val credentials: CredentialsProvider? = null,
    val transport: TransportConfig = TransportConfig(),
)

/**
 * Factory function that creates a fully configured Ollama [LlmProvider].
 *
 * Internally wires [OllamaWireProtocol] into [HttpLlmProvider].
 * Users never interact with the internal types.
 *
 * ```kotlin
 * val provider = OllamaProvider(OllamaConfig(id = "local"))
 * ```
 */
public fun OllamaProvider(config: OllamaConfig): LlmProvider {
    val auth: AuthStrategy = if (config.credentials != null) {
        BearerTokenAuth(config.credentials)
    } else {
        NoAuth
    }

    return HttpLlmProvider(
        id = ProviderId(config.id),
        capabilities = OllamaCapabilities,
        baseUrl = config.baseUrl,
        httpClient = HttpClientFactory.create(config.transport),
        protocol = OllamaWireProtocol(),
        authStrategy = auth,
        extraHeaders = config.transport.extraHeaders,
        streamIdleTimeoutMs = config.transport.streamIdleTimeoutMs,
    )
}

/** Capabilities reported by all Ollama provider instances. */
private val OllamaCapabilities = ProviderCapabilities(
    streaming = true,
    toolCalling = true,
    parallelToolCalls = false,
    reasoning = true,                // Ollama supports `think: true` for thinking models.
    structuredOutput = false,
    statefulConversation = false,
)
