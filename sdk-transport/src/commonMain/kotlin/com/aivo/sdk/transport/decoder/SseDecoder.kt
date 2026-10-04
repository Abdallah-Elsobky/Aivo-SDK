package com.aivo.sdk.transport.decoder

import com.aivo.sdk.transport.protocol.RawFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Decodes a stream of raw lines from an SSE (Server-Sent Events) response body
 * into [RawFrame]s.
 *
 * Handles:
 * - `data: {…}` lines — accumulated per event block.
 * - `event: <type>` lines — stored and attached to the next emitted frame.
 * - `id: <value>` lines — parsed but not currently used (reserved for reconnection).
 * - `: <comment>` lines — silently ignored (OpenRouter keep-alives use these).
 * - Multi-line `data:` values concatenated with `\n`.
 * - CRLF and LF line endings.
 * - `data: [DONE]` — emits [RawFrame.Done] and stops.
 *
 * **Why not the Ktor SSE plugin?**
 * Providers use POST requests for streaming, not GET-based EventSource.
 * The Ktor SSE plugin targets GET/EventSource only.
 */
public class SseDecoder {
    /**
     * Wraps [lines] (a cold flow of raw UTF-8 text lines) into a flow of [RawFrame]s.
     *
     * An SSE event is dispatched when a blank line is encountered.
     * Each data line contributes to the event's data field.
     */
    public fun decode(lines: Flow<String>): Flow<RawFrame> = flow {
        val dataBuffer = StringBuilder()
        var currentEventType: String? = null

        lines.collect { rawLine ->
            // Strip CRLF if present — some servers send \r\n.
            val line = rawLine.trimEnd('\r')

            when {
                // Comment line — skip silently (OpenRouter keep-alives).
                line.startsWith(':') -> Unit

                // Event type field.
                line.startsWith("event:") -> {
                    currentEventType = line.removePrefix("event:").trim()
                }

                // Data field.
                line.startsWith("data:") -> {
                    val value = line.removePrefix("data:").trimStart(' ')
                    if (value == "[DONE]") {
                        emit(RawFrame.Done)
                        return@collect
                    }
                    if (dataBuffer.isNotEmpty()) dataBuffer.append('\n')
                    dataBuffer.append(value)
                }

                // ID field (reserved).
                line.startsWith("id:") -> Unit

                // Blank line — dispatch the accumulated event.
                line.isBlank() -> {
                    if (dataBuffer.isNotEmpty()) {
                        val data = dataBuffer.toString()
                        dataBuffer.clear()
                        emit(RawFrame.Data(data = data, eventType = currentEventType))
                        currentEventType = null
                    }
                }

                // Ignore any other lines.
                else -> Unit
            }
        }

        // Emit any trailing data that arrived without a final blank line.
        if (dataBuffer.isNotEmpty()) {
            emit(RawFrame.Data(data = dataBuffer.toString(), eventType = currentEventType))
        }
    }
}
