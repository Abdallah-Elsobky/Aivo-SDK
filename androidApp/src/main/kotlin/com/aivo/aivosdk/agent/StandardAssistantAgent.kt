package com.aivo.aivosdk.agent

import com.aivo.sdk.AgentRole
import com.aivo.sdk.StylePreset
import com.aivo.sdk.agent
import com.aivo.sdk.runtime.agent.AgentDefinition

/**
 * Standard single-turn assistant agent.
 *
 * Demonstrates the simplest possible agent configuration: a single agent with
 * a system prompt and a [StylePreset].
 */
fun createStandardAssistantAgent(providerDisplayName: String = "AI"): AgentDefinition =
    agent("standard_assistant") {
        name = "$providerDisplayName Assistant"
        role = AgentRole.ASSISTANT
        style = StylePreset.CONCISE.toString()
        systemPrompt = "You are a helpful, concise AI assistant."
    }
