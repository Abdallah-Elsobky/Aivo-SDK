package com.aivo.sdk.transport

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel as KtorLogLevel
import io.ktor.client.plugins.logging.Logging

/**
 * Configuration for the Ktor [HttpClient] created by [HttpClientFactory].
 *
 * @param connectTimeoutMs   Maximum time to establish a TCP connection (ms).
 * @param requestTimeoutMs   Maximum total time for a non-streaming request (ms).
 * @param streamIdleTimeoutMs Maximum allowed silence between streaming frames (ms).
 *                           Enforced separately by [HttpLlmProvider]; not a Ktor timeout.
 * @param userAgent          Value of the `User-Agent` header.
 * @param extraHeaders       Additional headers appended to every request.
 * @param allowInsecure      If `true`, allows plain HTTP. **Never set to `true` in production.**
 * @param httpClientConfigurer Optional escape hatch for engine-specific configuration
 *                             (certificate pinning, proxy, etc.).
 */
public data class TransportConfig(
    val connectTimeoutMs: Long = 10_000L,
    val requestTimeoutMs: Long = 60_000L,
    val streamIdleTimeoutMs: Long = 30_000L,
    val userAgent: String = "AivoSdk/1.0",
    val extraHeaders: Map<String, String> = emptyMap(),
    val allowInsecure: Boolean = false,
    val httpClientConfigurer: (HttpClientConfig<*>.() -> Unit)? = null,
)

/**
 * Builds a Ktor [HttpClient] from [TransportConfig].
 *
 * The returned client is **not** closed automatically — callers (composition root) own its lifecycle.
 */
public object HttpClientFactory {
    public fun create(config: TransportConfig): HttpClient = createEngine(config)
}

/** Kotlin Multiplatform — delegates to the correct engine per platform. */
internal expect fun createEngine(config: TransportConfig): HttpClient
