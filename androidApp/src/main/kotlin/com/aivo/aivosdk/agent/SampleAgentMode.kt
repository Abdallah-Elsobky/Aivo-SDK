package com.aivo.aivosdk.agent

/**
 * Demonstrates different agent architecture patterns supported by the Aivo SDK.
 *
 * Use this as a reference for how developers can define and select between multiple
 * agent configurations at runtime.
 *
 * Each mode corresponds to a specific orchestration pattern:
 * - [STANDARD]: Single-turn assistant — simplest integration point.
 * - [TOOLS]: Single agent with custom tool calls (weather, crypto price APIs).
 * - [SUPERVISOR]: Multi-agent supervisor model that delegates to specialist sub-agents.
 * - [PIPELINE]: Deterministic code-driven workflow (researcher → writer).
 */
enum class SampleAgentMode(
    val label: String,
    val agentId: String,
    val icon: String,
    val description: String,
) {
    STANDARD(
        label = "Chat",
        agentId = "standard_assistant",
        icon = "💬",
        description = "Single-agent assistant with a concise style preset",
    ),
    TOOLS(
        label = "Tools",
        agentId = "tools_agent",
        icon = "⚡",
        description = "Agent with custom API tools (weather, crypto price)",
    ),
    SUPERVISOR(
        label = "Supervisor",
        agentId = "editorial_lead",
        icon = "👥",
        description = "Supervisor delegates to researcher & writer sub-agents",
    ),
    PIPELINE(
        label = "Pipeline",
        agentId = "pipeline",
        icon = "🔄",
        description = "Deterministic sequential workflow: research → write",
    ),
}
