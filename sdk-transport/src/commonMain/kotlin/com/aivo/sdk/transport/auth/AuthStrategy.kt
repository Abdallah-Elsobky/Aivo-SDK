package com.aivo.sdk.transport.auth

import com.aivo.sdk.core.port.CredentialsProvider
import com.aivo.sdk.core.util.SecretString
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

/**
 * Strategy for attaching authentication credentials to an HTTP request.
 *
 * Credentials are resolved per-request (`suspend`) so rotating/gateway tokens work seamlessly.
 */
public interface AuthStrategy {
    /**
     * Applies authentication to [builder].
     * Called once per request before the body is sent.
     */
    public suspend fun apply(builder: HttpRequestBuilder)
}

/** Attaches `Authorization: Bearer <token>`. Used by Ollama (cloud) and OpenAI-compatible providers. */
public class BearerTokenAuth(
    private val credentialsProvider: CredentialsProvider,
) : AuthStrategy {
    override suspend fun apply(builder: HttpRequestBuilder) {
        val token: SecretString = credentialsProvider.credentials()
        builder.header(HttpHeaders.Authorization, "Bearer ${token.reveal()}")
    }
}

/** Attaches a custom header (e.g., `x-goog-api-key` for Gemini). */
public class HeaderKeyAuth(
    private val headerName: String,
    private val credentialsProvider: CredentialsProvider,
) : AuthStrategy {
    override suspend fun apply(builder: HttpRequestBuilder) {
        val key: SecretString = credentialsProvider.credentials()
        builder.header(headerName, key.reveal())
    }
}

/** No authentication — suitable for local Ollama instances. */
public object NoAuth : AuthStrategy {
    override suspend fun apply(builder: HttpRequestBuilder): Unit = Unit
}
