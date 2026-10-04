package com.aivo.sdk.core

import com.aivo.sdk.core.model.ContentPart
import com.aivo.sdk.core.model.FinishReason
import com.aivo.sdk.core.model.LlmResponse
import com.aivo.sdk.core.model.LlmStreamEvent
import com.aivo.sdk.core.model.Message
import com.aivo.sdk.core.model.Usage
import com.aivo.sdk.core.stream.StreamAssembler
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StreamAssemblerTest {

    @Test
    fun assemble_accumulates_text_and_produces_completed_response() = runTest {
        val events = flowOf(
            LlmStreamEvent.TextDelta("Hello"),
            LlmStreamEvent.TextDelta(" world"),
            LlmStreamEvent.TextDelta("!"),
            LlmStreamEvent.Completed(
                LlmResponse(
                    message = Message.Assistant(listOf(ContentPart.Text("Hello world!"))),
                    finishReason = FinishReason.STOP,
                    usage = Usage(inputTokens = 5, outputTokens = 3),
                )
            ),
        )

        val assembler = StreamAssembler()
        val assembledEvents = assembler.assemble(events).toList()
        assertTrue(assembledEvents.isNotEmpty())

        val completed = assembledEvents.filterIsInstance<LlmStreamEvent.Completed>().singleOrNull()
        assertNotNull(completed)
        assertEquals(FinishReason.STOP, completed.response.finishReason)
        val text = completed.response.message.parts.filterIsInstance<ContentPart.Text>().joinToString("") { it.text }
        assertEquals("Hello world!", text)
    }

    @Test
    fun assemble_accumulates_reasoning_and_tool_calls() = runTest {
        val events = flowOf(
            LlmStreamEvent.ReasoningDelta("Thinking step 1..."),
            LlmStreamEvent.ReasoningDelta(" Done thinking."),
            LlmStreamEvent.ToolCallStarted(index = 0, id = "call_1", name = "get_weather"),
            LlmStreamEvent.ToolCallArgumentsDelta(index = 0, jsonFragment = "{\"city\":"),
            LlmStreamEvent.ToolCallArgumentsDelta(index = 0, jsonFragment = "\"Paris\"}"),
            LlmStreamEvent.Completed(
                LlmResponse(
                    message = Message.Assistant(parts = emptyList()),
                    finishReason = FinishReason.TOOL_CALLS,
                    usage = Usage(inputTokens = 10, outputTokens = 15),
                )
            ),
        )

        val assembler = StreamAssembler()
        val assembledEvents = assembler.assemble(events).toList()
        val completed = assembledEvents.filterIsInstance<LlmStreamEvent.Completed>().single()

        assertEquals(FinishReason.TOOL_CALLS, completed.response.finishReason)
    }
}
