package com.aivo.sdk.runtime.agent

import com.aivo.sdk.core.port.AgentLoop
import com.aivo.sdk.core.port.LoopContext
import com.aivo.sdk.core.port.LoopOutcome
import com.aivo.sdk.core.port.ToolSelection
import com.aivo.sdk.core.port.Turn

/**
 * Standard ReAct-style reasoning loop:
 * Think -> Act on tool calls -> Think -> ... -> Finish.
 *
 * All step limits, token budgets, and timeouts are enforced inside [LoopContext.think] and [LoopContext.act].
 * Detects duplicate/repeating tool calls and forces text synthesis if a model loops.
 */
public class ToolCallingLoop(
    public val maxConsecutiveToolCalls: Int = 5,
) : AgentLoop {
    override val name: String = "tool_calling"

    override suspend fun run(context: LoopContext): LoopOutcome {
        var consecutiveToolCalls = 0
        val seenSignatures = mutableSetOf<String>()

        while (true) {
            when (val turn = context.think()) {
                is Turn.Text -> {
                    return context.finish(turn.text)
                }
                is Turn.Call -> {
                    consecutiveToolCalls++
                    val signatures = turn.calls.map { "${it.name}:${it.arguments}" }
                    val isDuplicate = signatures.any { it in seenSignatures }
                    seenSignatures.addAll(signatures)

                    context.act(turn.calls)

                    // If model loops on the same tool or hits threshold, force final answer with tools disabled
                    if (isDuplicate || consecutiveToolCalls >= maxConsecutiveToolCalls) {
                        val finalTurn = context.think(tools = ToolSelection.None)
                        return when (finalTurn) {
                            is Turn.Text -> context.finish(finalTurn.text)
                            is Turn.Call -> context.finish("Completed tool executions.")
                        }
                    }
                }
            }
        }
    }

    public companion object {
        public val Default: ToolCallingLoop = ToolCallingLoop()
    }
}
