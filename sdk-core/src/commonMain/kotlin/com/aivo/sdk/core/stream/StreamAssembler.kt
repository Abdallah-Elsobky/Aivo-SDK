package com.aivo.sdk.core.stream

import com.aivo.sdk.core.model.ContentPart
import com.aivo.sdk.core.model.FinishReason
import com.aivo.sdk.core.model.LlmResponse
import com.aivo.sdk.core.model.LlmStreamEvent
import com.aivo.sdk.core.model.Message
import com.aivo.sdk.core.model.ProviderMetadata
import com.aivo.sdk.core.model.ToolCall
import com.aivo.sdk.core.model.Usage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Accumulates streaming [LlmStreamEvent] deltas into a final [LlmStreamEvent.Completed] response.
 *
 * **Responsibility:** Every [WireProtocol][com.aivo.sdk.transport.protocol.WireProtocol]
 * translates raw wire frames into the delta events below. This class owns accumulation logic
 * **once** — no adapter re-implements it.
 *
 * **Usage:** wrap the protocol's [Flow<LlmStreamEvent>] with [assemble]. All delta events are
 * passed through unchanged so the UI can render incrementally. A synthetic [LlmStreamEvent.Completed]
 * is emitted as the last event with the fully assembled [LlmResponse].
 *
 * If the upstream flow already contains a [LlmStreamEvent.Completed] (e.g., non-streaming
 * fallback), it is forwarded unchanged and accumulation stops.
 */
public class StreamAssembler(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    /**
     * Wraps [source] so all delta events pass through and the final [LlmStreamEvent.Completed]
     * carries the fully assembled response.
     */
    public fun assemble(source: Flow<LlmStreamEvent>): Flow<LlmStreamEvent> = flow {
        val textBuffer = StringBuilder()
        val reasoningBuffer = StringBuilder()
        // Keyed by tool-call index; each value accumulates the JSON argument fragment string.
        val toolCallBuilders = mutableMapOf<Int, ToolCallBuilder>()
        var completed: LlmStreamEvent.Completed? = null

        source.collect { event ->
            when (event) {
                is LlmStreamEvent.TextDelta -> {
                    textBuffer.append(event.text)
                    emit(event)
                }

                is LlmStreamEvent.ReasoningDelta -> {
                    reasoningBuffer.append(event.text)
                    emit(event)
                }

                is LlmStreamEvent.ToolCallStarted -> {
                    toolCallBuilders[event.index] = ToolCallBuilder(
                        id = event.id,
                        name = event.name,
                    )
                    emit(event)
                }

                is LlmStreamEvent.ToolCallArgumentsDelta -> {
                    toolCallBuilders[event.index]?.argBuffer?.append(event.jsonFragment)
                    emit(event)
                }

                is LlmStreamEvent.Completed -> {
                    // If the upstream already computed a full response, forward it directly.
                    // This happens when the protocol falls back to non-streaming mode.
                    completed = event
                }
            }
        }

        val finalCompleted = completed ?: buildCompleted(
            text = textBuffer.toString(),
            reasoning = reasoningBuffer.takeIf { it.isNotEmpty() }?.toString(),
            toolCallBuilders = toolCallBuilders,
            json = json,
        )
        emit(finalCompleted)
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private fun buildCompleted(
        text: String,
        reasoning: String?,
        toolCallBuilders: Map<Int, ToolCallBuilder>,
        json: Json,
    ): LlmStreamEvent.Completed {
        val toolCalls = toolCallBuilders.entries
            .sortedBy { it.key }
            .map { (_, builder) -> builder.build(json) }

        val finishReason = when {
            toolCalls.isNotEmpty() -> FinishReason.TOOL_CALLS
            text.isNotEmpty() -> FinishReason.STOP
            else -> FinishReason.STOP
        }

        val parts = buildList {
            if (text.isNotEmpty()) add(ContentPart.Text(text))
        }

        val assistantMessage = Message.Assistant(
            parts = parts,
            toolCalls = toolCalls,
            reasoning = reasoning,
            providerMetadata = ProviderMetadata.Empty,
        )

        return LlmStreamEvent.Completed(
            response = LlmResponse(
                message = assistantMessage,
                finishReason = finishReason,
                usage = Usage.Zero,
                model = null,
                responseId = null,
            )
        )
    }

    // ─── Internal builder ─────────────────────────────────────────────────────

    private class ToolCallBuilder(
        val id: String,
        val name: String,
        val argBuffer: StringBuilder = StringBuilder(),
    ) {
        fun build(json: Json): ToolCall {
            val argString = argBuffer.toString()
            val arguments: JsonObject = if (argString.isBlank()) {
                JsonObject(emptyMap())
            } else {
                runCatching { json.parseToJsonElement(argString) as? JsonObject }
                    .getOrNull() ?: JsonObject(emptyMap())
            }
            return ToolCall(id = id, name = name, arguments = arguments)
        }
    }
}
