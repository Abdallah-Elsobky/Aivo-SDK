package com.aivo.sdk.runtime.agent

import com.aivo.sdk.core.error.SdkException
import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.core.model.ToolResult
import kotlinx.serialization.Serializable

/**
 * Event stream emitted during [AgentRuntime.stream].
 *
 * Every event includes [agentPath] (e.g. `["supervisor"]`, `["supervisor", "billing"]`),
 * allowing the UI to visualise multi-agent delegation hierarchies seamlessly.
 */
public sealed interface AgentEvent {
    public val agentPath: List<String>

    public data class RunStarted(
        val runId: String,
        val conversationId: ConversationId,
        val agentId: String,
        override val agentPath: List<String>,
    ) : AgentEvent

    public data class AgentEntered(
        val agentId: String,
        override val agentPath: List<String>,
    ) : AgentEvent

    public data class StepStarted(
        val stepIndex: Int,
        override val agentPath: List<String>,
    ) : AgentEvent

    public data class TextDelta(
        val text: String,
        override val agentPath: List<String>,
    ) : AgentEvent

    public data class ReasoningDelta(
        val text: String,
        override val agentPath: List<String>,
    ) : AgentEvent

    public data class ToolCallStarted(
        val callId: String,
        val toolName: String,
        override val agentPath: List<String>,
    ) : AgentEvent

    public data class ToolCallArgumentsDelta(
        val callId: String,
        val delta: String,
        override val agentPath: List<String>,
    ) : AgentEvent

    public data class ToolCallExecuted(
        val callId: String,
        val toolName: String,
        val result: ToolResult,
        override val agentPath: List<String>,
    ) : AgentEvent

    public data class StepCompleted(
        val stepIndex: Int,
        override val agentPath: List<String>,
    ) : AgentEvent

    public data class RunCompleted(
        val result: AgentResult,
        override val agentPath: List<String>,
    ) : AgentEvent

    public data class RunFailed(
        val error: SdkException,
        override val agentPath: List<String>,
    ) : AgentEvent
}
