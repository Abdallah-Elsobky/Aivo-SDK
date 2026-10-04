package com.aivo.sdk.runtime.agent

import com.aivo.sdk.core.model.AgentRole
import com.aivo.sdk.core.model.GenerationOptions
import com.aivo.sdk.core.model.ModelRef
import com.aivo.sdk.core.model.OutputFormat
import com.aivo.sdk.core.model.StateKey
import com.aivo.sdk.core.port.AgentLoop
import com.aivo.sdk.core.port.PromptComposer
import com.aivo.sdk.core.port.Tool
import com.aivo.sdk.runtime.prompt.SectionedPromptComposer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Pure data declaration of an agent.
 *
 * Contains no runtime behavior or mutable state; interpreted by [AgentRuntime] at execution time.
 *
 * @param id              Unique identifier within the SDK instance (e.g., `"supervisor"`, `"billing"`).
 * @param name            Human-readable name.
 * @param description     Plain-English summary of what this agent does (used when delegated to).
 * @param model           The LLM model backing this agent (`"providerId:modelName"`).
 * @param systemPrompt    System instructions guiding the agent's behavior. Supports `{{variable}}` templates.
 * @param instructions    Specific instructions or step directives.
 * @param tools           List of tool names this agent has permission to invoke.
 * @param subAgents       List of agent IDs this agent can delegate sub-tasks to.
 * @param limits          Execution boundaries and timeouts.
 * @param options         Generation options (temperature, topP, etc.).
 * @param role            Strongly-typed role defining archetype and baseline responsibilities.
 * @param goal            Primary objective the agent must achieve.
 * @param responsibilities Explicit list of responsibilities.
 * @param rules           Safety and operational constraints.
 * @param style           Tone, prose, and formatting directive.
 * @param outputFormat    Structured format requirement (e.g. JSON, Markdown, Approval verdict).
 * @param customContext   Additional background context.
 * @param saveResultAs    StateKey on the blackboard where this agent's final text output is saved.
 * @param loop            Custom reasoning loop implementing [AgentLoop]. Defaults to null (uses standard ToolCallingLoop).
 * @param promptComposer  Composer for assembling the system prompt. Defaults to [SectionedPromptComposer.Default].
 * @param toolInstances   Concrete [Tool] references attached directly to this agent.
 */
@Serializable
public data class AgentDefinition(
    val id: String,
    val name: String = id,
    val description: String? = null,
    val model: ModelRef? = null,
    val systemPrompt: String = "",
    val instructions: List<String> = emptyList(),
    val tools: List<String> = emptyList(),
    val subAgents: List<String> = emptyList(),
    val limits: AgentLimits = AgentLimits(),
    val options: GenerationOptions = GenerationOptions(),
    val role: AgentRole? = null,
    val goal: String? = null,
    val responsibilities: List<String> = emptyList(),
    val rules: List<String> = emptyList(),
    val style: String? = null,
    val outputFormat: OutputFormat? = null,
    val customContext: String? = null,
    val saveResultAs: String? = null,
    @Transient
    val loop: AgentLoop? = null,
    @Transient
    val promptComposer: PromptComposer = SectionedPromptComposer.Default,
    @Transient
    val toolInstances: List<Tool> = emptyList(),
    @Transient
    val managedAgentDefinitions: List<AgentDefinition> = emptyList(),
) {
    public val delegates: List<String> get() = subAgents
    public val saveResultKey: StateKey<String>? get() = saveResultAs?.let { StateKey.string(it) }
}
