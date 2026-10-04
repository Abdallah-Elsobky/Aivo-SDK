package com.aivo.sdk.runtime.agent

import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.core.model.ToolCall
import com.aivo.sdk.core.model.Usage
import kotlinx.serialization.Serializable

/**
 * Result returned upon successful completion of an agent run.
 *
 * @param runId           Unique identifier of this run.
 * @param conversationId  The conversation this run was executed within.
 * @param agentId         The root agent that executed the run.
 * @param text            The final text response produced by the agent.
 * @param toolCalls       Any tool calls invoked during the run.
 * @param steps           The number of LLM <-> tool loop steps executed.
 * @param usage           Total accumulated token and cost usage across all steps.
 */
@Serializable
public data class AgentResult(
    val runId: String,
    val conversationId: ConversationId,
    val agentId: String,
    val text: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val steps: Int,
    val usage: Usage? = null,
)
