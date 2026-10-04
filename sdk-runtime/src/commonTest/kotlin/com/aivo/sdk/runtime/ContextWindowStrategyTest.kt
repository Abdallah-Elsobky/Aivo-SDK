package com.aivo.sdk.runtime

import com.aivo.sdk.core.model.ContentPart
import com.aivo.sdk.core.model.Message
import com.aivo.sdk.core.model.ToolCall
import com.aivo.sdk.core.model.ToolResult
import com.aivo.sdk.runtime.memory.context.KeepAllStrategy
import com.aivo.sdk.runtime.memory.context.SlidingWindowStrategy
import com.aivo.sdk.runtime.memory.context.TokenBudgetStrategy
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextWindowStrategyTest {

    @Test
    fun keep_all_strategy_preserves_everything() {
        val messages = listOf(
            Message.System("Prompt"),
            Message.User("Hi"),
            Message.Assistant(listOf(ContentPart.Text("Hello!"))),
        )
        val selected = KeepAllStrategy.select(messages)
        assertEquals(messages, selected)
    }

    @Test
    fun sliding_window_preserves_atomic_tool_call_and_result_group() {
        val toolCall = ToolCall("call_1", "get_balance", buildJsonObject { })
        val toolResult = ToolResult.Success(buildJsonObject { })

        val messages = listOf(
            Message.System("System prompt"),
            Message.User("Old message 1"),
            Message.Assistant(listOf(ContentPart.Text("Old reply 1"))),
            Message.User("Old message 2"),
            Message.Assistant(listOf(ContentPart.Text("Old reply 2"))),
            // Atomic tool group: Assistant with tool calls + Tool result
            Message.Assistant(parts = emptyList(), toolCalls = listOf(toolCall)),
            Message.Tool("call_1", "get_balance", toolResult),
            Message.Assistant(listOf(ContentPart.Text("Your balance is 100."))),
            Message.User("Latest message"),
        )

        val strategy = SlidingWindowStrategy(maxHistoryUnits = 2)
        val selected = strategy.select(messages)

        // System message and latest user must be present
        assertTrue(selected.any { it is Message.System })
        assertEquals(Message.User("Latest message"), selected.last())

        // Invariant: If Assistant with toolCalls is present, Tool result MUST be present
        val hasAssistantToolCall = selected.any { it is Message.Assistant && it.toolCalls.isNotEmpty() }
        val hasToolMessage = selected.any { it is Message.Tool }
        assertEquals(hasAssistantToolCall, hasToolMessage, "Tool calls and tool results must never be orphaned")
    }

    @Test
    fun token_budget_strategy_keeps_system_and_latest_user() {
        val messages = listOf(
            Message.System("System prompt"),
            Message.User("Short user message"),
            Message.Assistant(listOf(ContentPart.Text("Assistant answer"))),
            Message.User("Latest query"),
        )

        val strategy = TokenBudgetStrategy(maxTokens = 20)
        val selected = strategy.select(messages)

        assertTrue(selected.any { it is Message.System })
        assertEquals(Message.User("Latest query"), selected.last())
    }
}
