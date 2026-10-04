package com.aivo.sdk.transport

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout

internal actual fun createEngine(config: TransportConfig): HttpClient = HttpClient(OkHttp) {
    install(HttpTimeout) {
        connectTimeoutMillis = config.connectTimeoutMs
        // Note: streamIdleTimeoutMs is enforced by HttpLlmProvider, not Ktor.
        // requestTimeoutMs applies to non-streaming calls only.
        requestTimeoutMillis = config.requestTimeoutMs
    }
    // Apply user escape hatch last so it can override anything.
    config.httpClientConfigurer?.invoke(this)
}
