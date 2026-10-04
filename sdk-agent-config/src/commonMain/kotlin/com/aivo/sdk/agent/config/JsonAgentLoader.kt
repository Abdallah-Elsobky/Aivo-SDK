package com.aivo.sdk.agent.config

import com.aivo.sdk.core.SdkJson
import com.aivo.sdk.core.error.ConfigurationException
import com.aivo.sdk.runtime.agent.AgentDefinition
import kotlinx.serialization.SerializationException

/**
 * Loads [AgentDefinition] instances from JSON strings.
 */
public object JsonAgentLoader {

    public fun parse(jsonContent: String): AgentDefinition {
        return try {
            SdkJson.decodeFromString<AgentDefinition>(jsonContent)
        } catch (e: SerializationException) {
            throw ConfigurationException(listOf("Failed to parse JSON agent definition: ${e.message}"))
        }
    }
}
