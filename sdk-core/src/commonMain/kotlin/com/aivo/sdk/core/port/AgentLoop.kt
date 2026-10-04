package com.aivo.sdk.core.port

import com.aivo.sdk.core.model.AgentRef
import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.core.model.Message
import com.aivo.sdk.core.model.RunState
import com.aivo.sdk.core.model.ToolCall
import com.aivo.sdk.core.model.ToolResult
import com.aivo.sdk.core.model.Usage

/**
 * Controls which tools are offered to the model during a [LoopContext.think] turn.
 */
public sealed interface ToolSelection {
    /** The model automatically decides whether to call tools or respond with text. */
    public object Auto : ToolSelection

    /** Tool calling is disabled for this turn; model must produce text. */
    public object None : ToolSelection

    /** Forces the model to call a specific tool. */
    public data class Required(public val toolName: String) : ToolSelection

    /** Restricts the model to a subset of the agent's authorized tools. */
    public data class Subset(public val toolNames: Set<String>) : ToolSelection
}

/**
 * The result of a single [LoopContext.think] inference turn.
 */
public sealed interface Turn {
    /** The model responded with text without requesting tool calls. */
    public data class Text(
        public val text: String,
        public val usage: Usage? = null,
    ) : Turn

    /** The model requested one or more tool calls. */
    public data class Call(
        public val calls: List<ToolCall>,
        public val usage: Usage? = null,
    ) : Turn
}

/**
 * Outcome of an [AgentLoop] execution.
 */
public sealed interface LoopOutcome {
    /** The loop completed successfully with a final response. */
    public data class Completed(
        public val text: String,
        public val usage: Usage? = null,
    ) : LoopOutcome

    /** Control is handed off to another agent with a specified task. */
    public data class HandedOff(
        public val targetAgent: AgentRef,
        public val task: String,
    ) : LoopOutcome
}

/**
 * Interface that all agent reasoning loops must implement.
 *
 * Encapsulates the agentic control flow (e.g. ReAct, single turn, sequence, supervisor).
 */
public interface AgentLoop {
    public val name: String
    public suspend fun run(context: LoopContext): LoopOutcome
}

/**
 * Safe runtime environment provided to an [AgentLoop].
 *
 * All safety limits (step limits, token budgets, timeouts, delegation depth, and tool allow-lists)
 * are enforced **inside** these primitives.
 */
public interface LoopContext {
    public val agentId: String
    public val conversationId: ConversationId
    public val runId: String
    public val agentPath: List<String>
    public val input: String
    public val state: RunState
    public val stepCount: Int

    /** Calls the model once; streams deltas to events; accounts for steps and tokens. */
    public suspend fun think(
        tools: ToolSelection = ToolSelection.Auto,
        promptOverride: String? = null,
    ): Turn

    /** Executes a tool call from the turn; validates allow-list, policy, and timeout. */
    public suspend fun act(call: ToolCall): ToolResult

    /** Executes multiple tool calls; executes concurrently or sequentially according to policy. */
    public suspend fun act(calls: List<ToolCall>): List<ToolResult>

    /** Delegates a sub-task to another agent; isolated context, shared state blackboard. */
    public suspend fun delegate(
        target: AgentRef,
        task: String,
        context: Map<String, String> = emptyMap(),
    ): String

    /** Runs multiple branches concurrently in safe child contexts. */
    public suspend fun <T> parallel(
        branches: List<suspend (LoopContext) -> T>,
    ): List<T>

    /** Appends an explicit message to conversation history. */
    public suspend fun append(message: Message)

    /** Completes the loop with a final text response. */
    public fun finish(text: String, usage: Usage? = null): LoopOutcome.Completed

    /** Hands off execution to another agent. */
    public fun handoff(target: AgentRef, task: String = input): LoopOutcome.HandedOff
}
