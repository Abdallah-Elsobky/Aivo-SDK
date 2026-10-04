package com.aivo.sdk.core.port

import com.aivo.sdk.core.model.AgentRole
import com.aivo.sdk.core.model.OutputFormat

/**
 * Summary of a managed team member agent, rendered in supervisor prompts.
 */
public data class TeamMemberSummary(
    public val id: String,
    public val role: String?,
    public val description: String,
)

/**
 * Context passed to [PromptComposer] when assembling an agent's rendered system prompt.
 */
public data class PromptComposeContext(
    public val role: AgentRole? = null,
    public val goal: String? = null,
    public val responsibilities: List<String> = emptyList(),
    public val instructions: List<String> = emptyList(),
    public val rules: List<String> = emptyList(),
    public val style: String? = null,
    public val teamMembers: List<TeamMemberSummary> = emptyList(),
    public val outputFormat: OutputFormat? = null,
    public val customContext: String? = null,
    public val rawSystemPrompt: String? = null,
    public val variables: Map<String, String> = emptyMap(),
)

/**
 * Port for composing an agent's rendered system prompt from structured components.
 */
public interface PromptComposer {
    public fun compose(context: PromptComposeContext): String
}
