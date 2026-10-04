package com.aivo.aivosdk.agent

import com.aivo.sdk.AgentRole
import com.aivo.sdk.agent
import com.aivo.sdk.runtime.agent.AgentDefinition
import com.aivo.sdk.workflow

/**
 * Deterministic code-driven pipeline agent (researcher → writer).
 *
 * Demonstrates the `workflow { ... }` DSL for code-driven orchestration
 * where execution order is defined in Kotlin rather than delegated to the model.
 *
 * This is the "pipeline" pattern: run researcher first, pass the output to writer,
 * then return the writer's response to the user.
 */
fun createPipelineAgent(
    researcher: AgentDefinition = createResearcherAgent(),
    writer: AgentDefinition = createWriterAgent(),
): AgentDefinition =
    agent("pipeline") {
        name = "Orchestrator Pipeline"
        role = AgentRole.ORCHESTRATOR
        description = "Deterministic sequential research → write pipeline."
        manages(researcher, writer)
        loop = workflow(researcher, writer) {
            val notes = run(researcher, input)
            val draft = run(writer, "Create final polished response based on research notes:\n$notes")
            finish(draft)
        }
    }
