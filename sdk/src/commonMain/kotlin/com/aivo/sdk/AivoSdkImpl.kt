package com.aivo.sdk

import com.aivo.sdk.core.error.ConfigurationException
import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.core.model.ModelRef
import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.core.port.LlmProvider
import com.aivo.sdk.runtime.agent.AgentEvent
import com.aivo.sdk.runtime.agent.AgentRegistry
import com.aivo.sdk.runtime.agent.AgentResult
import com.aivo.sdk.runtime.agent.AgentRuntime
import com.aivo.sdk.runtime.tool.ToolRegistry
import kotlinx.coroutines.flow.Flow

internal class AivoSdkImpl(
    override val defaultModel: ModelRef?,
    override val entryAgentId: String?,
    override val providers: Map<ProviderId, LlmProvider>,
    override val agents: AgentRegistry,
    override val tools: ToolRegistry,
    override val runtime: AgentRuntime,
) : AivoSdk {

    override fun llm(model: String): LlmClient = llm(ModelRef.parse(model))

    override fun llm(model: ModelRef): LlmClient {
        val provider = providers[model.provider]
            ?: throw ConfigurationException(
                listOf("Provider '${model.provider.value}' for model '${model.model}' is not registered.")
            )
        return DefaultLlmClient(model, provider)
    }

    override suspend fun chat(
        conversationId: ConversationId,
        message: String,
        agentId: String?,
        variables: Map<String, String>,
    ): AgentResult {
        val targetAgent = agentId ?: entryAgentId
            ?: throw ConfigurationException(
                listOf("No agentId provided and no entryAgent configured.")
            )
        return runtime.run(
            agentId = targetAgent,
            input = message,
            conversationId = conversationId,
            variables = variables,
        )
    }

    override fun stream(
        conversationId: ConversationId,
        message: String,
        agentId: String?,
        variables: Map<String, String>,
    ): Flow<AgentEvent> {
        val targetAgent = agentId ?: entryAgentId
            ?: throw ConfigurationException(
                listOf("No agentId provided and no entryAgent configured.")
            )
        return runtime.stream(
            agentId = targetAgent,
            input = message,
            conversationId = conversationId,
            variables = variables,
        )
    }

    override fun agent(id: String): AgentHandle = AgentHandle(id, runtime)

    override fun describe(): String {
        return buildString {
            appendLine("=== Aivo SDK Configuration ===")
            appendLine("Default Model: ${defaultModel ?: "none"}")
            appendLine("Entry Agent:   ${entryAgentId ?: "none"}")
            appendLine("Providers:     ${providers.keys.joinToString { it.value }}")
            appendLine("--- Agents ---")
            for (agent in agents.all()) {
                val toolsStr = if (agent.tools.isEmpty()) "—" else agent.tools.joinToString(", ")
                val delegatesStr = if (agent.delegates.isEmpty()) "—" else agent.delegates.joinToString(", ")
                val loopStr = agent.loop?.name ?: "tool_calling"
                val roleStr = agent.role?.title ?: "—"
                appendLine("${agent.id.padEnd(16)} role: ${roleStr.padEnd(20)} tools: ${toolsStr.padEnd(20)} delegates: ${delegatesStr.padEnd(20)} loop: $loopStr")
            }
        }
    }
}
