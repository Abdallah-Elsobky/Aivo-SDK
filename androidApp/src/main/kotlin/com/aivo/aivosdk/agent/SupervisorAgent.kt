package com.aivo.aivosdk.agent

import com.aivo.sdk.AgentRole
import com.aivo.sdk.runtime.agent.AgentDefinition
import com.aivo.sdk.supervisor

/**
 * Supervisor agent that orchestrates a research-and-write multi-agent team.
 *
 * Demonstrates [AgentRole.SUPERVISOR] and how to use the `manages(...)` builder
 * to declare sub-agents that the supervisor can delegate to.
 *
 * The supervisor uses the model's built-in reasoning to decide which sub-agent
 * to call and in what order — this is the "model-driven orchestration" pattern.
 */
fun createSupervisorAgent(
    researcher: AgentDefinition = createResearcherAgent(),
    writer: AgentDefinition = createWriterAgent(),
): AgentDefinition =
    supervisor("editorial_lead") {
        name = "Editorial Lead"
        role = AgentRole.SUPERVISOR
        description = "Supervises the team: delegates research to researcher and writing to writer."
        instructions("Analyze the user query. Delegate data gathering to researcher, then delegate formatting to writer.")
        manages(researcher, writer)
    }
