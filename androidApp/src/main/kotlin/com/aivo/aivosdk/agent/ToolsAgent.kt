package com.aivo.aivosdk.agent

import com.aivo.aivosdk.agent.tools.cryptoTool
import com.aivo.aivosdk.agent.tools.weatherTool
import com.aivo.sdk.AgentRole
import com.aivo.sdk.StylePreset
import com.aivo.sdk.agent
import com.aivo.sdk.runtime.agent.AgentDefinition

/**
 * Specialized agent equipped with custom API tools for live data retrieval.
 *
 * Demonstrates how to give a single agent multiple tools and write
 * effective instructions that guide tool use without over-calling.
 */
fun createToolsAgent(): AgentDefinition =
    agent("tools_agent") {
        name = "API Tools Specialist"
        role = AgentRole.SUPPORT
        style = StylePreset.CONCISE.toString()
        instructions(
            "You have access to tools for live weather and cryptocurrency prices. " +
            "Call the appropriate tool to answer user inquiries. When you receive the tool result, " +
            "synthesize the facts into a direct, friendly, and helpful response for the user. " +
            "Do not call tools repeatedly if you already have the answer."
        )
        tools(weatherTool, cryptoTool)
    }
