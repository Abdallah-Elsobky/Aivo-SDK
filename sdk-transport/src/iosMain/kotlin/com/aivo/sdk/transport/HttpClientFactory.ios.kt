package com.aivo.sdk.transport

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout

internal actual fun createEngine(config: TransportConfig): HttpClient = HttpClient(Darwin) {
    install(HttpTimeout) {
        connectTimeoutMillis = config.connectTimeoutMs
        requestTimeoutMillis = config.requestTimeoutMs
    }
    config.httpClientConfigurer?.invoke(this)
}
