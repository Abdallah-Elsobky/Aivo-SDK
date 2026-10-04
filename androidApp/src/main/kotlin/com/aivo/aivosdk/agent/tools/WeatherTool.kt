package com.aivo.aivosdk.agent.tools

import com.aivo.sdk.ParamType
import com.aivo.sdk.core.port.Tool
import com.aivo.sdk.tool

/**
 * Sample tool: returns simulated current weather for a given city.
 *
 * In a production app, the [execute] block would make a real HTTP call to a
 * weather API (e.g., OpenWeatherMap). This sample returns a static string
 * so the demo works offline without any external API key.
 *
 * Demonstrates:
 * - Defining a [Tool] with the `tool(name, description) { ... }` DSL.
 * - Declaring a required string parameter with [ParamType.String].
 * - Accessing arguments inside the `execute { args -> ... }` block.
 */
val weatherTool: Tool = tool("get_weather", "Fetch current weather and temperature for a city") {
    param("city", "City name (e.g. Cairo, London, Tokyo, New York)", type = ParamType.String, required = true)
    execute { args ->
        val city = args.stringOrNull("city")
            ?: args.stringOrNull("location")
            ?: "Unknown"
        // TODO: Replace with a real weather API call in production.
        "Weather in $city: Sunny, 27°C, Humidity 45%, Wind 12 km/h"
    }
}
