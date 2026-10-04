package com.aivo.aivosdk.agent

import com.aivo.aivosdk.agent.tools.cryptoTool
import com.aivo.aivosdk.agent.tools.weatherTool
import com.aivo.sdk.AgentRole
import com.aivo.sdk.SafetyRule
import com.aivo.sdk.StylePreset
import com.aivo.sdk.agent
import com.aivo.sdk.runtime.agent.AgentDefinition

/**
 * Specialist research agent that uses tools to gather live data.
 *
 * Demonstrates:
 * - Assigning tools to an agent via the `tools(...)` builder function.
 * - Applying a [SafetyRule] to prevent hallucination.
 * - Setting a custom [StylePreset] for response formatting.
 */
fun createResearcherAgent(): AgentDefinition =
    agent("researcher") {
        name = "Research Specialist"
        role = AgentRole.RESEARCHER
        description = "Gathers facts, retrieves live data via tools, and produces concise notes."
        instructions("Use tools to gather real-time data when needed. Summarize findings concisely.")
        style = StylePreset.CONCISE.toString()
        rules(SafetyRule.NEVER_HALLUCINATE_SOURCES)
        tools(weatherTool, cryptoTool)
    }
