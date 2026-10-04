package com.aivo.aivosdk.agent

import com.aivo.sdk.runtime.agent.AgentDefinition

/**
 * Registry holding all sample agent definitions for the Aivo SDK demo app.
 *
 * This shows developers how to create and register multiple agents that can
 * work independently or as a team. The registry ensures shared agent instances
 * (e.g., researcher and writer) are consistent across supervisor and pipeline setups.
 *
 * **Developer Note:** In your own app, you would register agents directly with
 * [AivoSdk] via the [AivoSdkBuilder.AgentsDsl] block, or define them in
 * Markdown/JSON files and load them with [fromMarkdownPath] / [fromJsonPath].
 */
class SampleAgentRegistry(
    val standardAssistant: AgentDefinition,
    val toolsAgent: AgentDefinition,
    val researcher: AgentDefinition,
    val writer: AgentDefinition,
    val supervisor: AgentDefinition,
    val pipeline: AgentDefinition,
) {
    /**
     * Top-level agents that map to selectable [SampleAgentMode] entries.
     *
     * The supervisor and pipeline agents internally reference researcher and writer
     * as managed/pipeline sub-agents; they are registered automatically when the
     * parent agent is added to the SDK.
     */
    val primaryAgents: List<AgentDefinition> = listOf(
        standardAssistant,
        toolsAgent,
        supervisor,
        pipeline,
    )

    /**
     * All agent definitions, including both primary mode entrypoints and specialist sub-agents.
     */
    val allAgents: List<AgentDefinition> = listOf(
        standardAssistant,
        toolsAgent,
        researcher,
        writer,
        supervisor,
        pipeline,
    )

    companion object {
        /**
         * Creates a complete [SampleAgentRegistry] with all sample agents configured.
         *
         * @param providerDisplayName Optional display name for branding the assistant.
         */
        fun create(providerDisplayName: String = "AI"): SampleAgentRegistry {
            val researcher = createResearcherAgent()
            val writer = createWriterAgent()
            val supervisor = createSupervisorAgent(researcher, writer)
            val pipeline = createPipelineAgent(researcher, writer)
            val standard = createStandardAssistantAgent(providerDisplayName)
            val tools = createToolsAgent()

            return SampleAgentRegistry(
                standardAssistant = standard,
                toolsAgent = tools,
                researcher = researcher,
                writer = writer,
                supervisor = supervisor,
                pipeline = pipeline,
            )
        }
    }
}
