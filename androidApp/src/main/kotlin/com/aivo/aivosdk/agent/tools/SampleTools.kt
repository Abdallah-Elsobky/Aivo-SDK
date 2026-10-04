package com.aivo.aivosdk.agent.tools

import com.aivo.sdk.core.port.Tool

/**
 * Convenience object grouping all sample tools for easy registration.
 *
 * Usage in [AivoSdkBuilder]:
 * ```kotlin
 * AivoSdk {
 *     tools {
 *         +SampleTools.weather
 *         +SampleTools.crypto
 *         // or register all at once:
 *         register(SampleTools.all)
 *     }
 * }
 * ```
 */
object SampleTools {
    val weather: Tool get() = weatherTool
    val crypto: Tool get() = cryptoTool
    val all: List<Tool> = listOf(weatherTool, cryptoTool)
}
