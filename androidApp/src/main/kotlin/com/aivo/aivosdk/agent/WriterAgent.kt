package com.aivo.aivosdk.agent

import com.aivo.sdk.AgentRole
import com.aivo.sdk.StylePreset
import com.aivo.sdk.agent
import com.aivo.sdk.runtime.agent.AgentDefinition

/**
 * Specialist agent that turns research notes into polished, user-facing responses.
 *
 * Demonstrates assigning a [StylePreset.TECHNICAL] style to shape the agent's tone.
 */
fun createWriterAgent(): AgentDefinition =
    agent("writer") {
        name = "Writer Specialist"
        role = AgentRole.WRITER
        description = "Turns research notes into a clean, engaging response for the user."
        style = StylePreset.TECHNICAL.toString()
    }
