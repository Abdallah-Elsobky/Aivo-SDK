package com.aivo.sdk.transport.decoder

import com.aivo.sdk.transport.protocol.RawFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Decodes a stream of raw lines from an NDJSON (Newline-Delimited JSON) response body
 * into [RawFrame]s.
 *
 * Rules:
 * - Skip blank lines.
 * - Each non-blank line must be a JSON object.
 * - If a line contains `{"error": "…"}`, emit [RawFrame.Error].
 * - Otherwise emit [RawFrame.Data] with the raw JSON string.
 *
 * Used by: **Ollama** (`stream: true` responses).
 */
public class NdjsonDecoder(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    public fun decode(lines: Flow<String>): Flow<RawFrame> = flow {
        lines.collect { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank()) return@collect

            // Quick check before full parse — avoids allocating JsonElement for error lines.
            if (line.contains("\"error\"")) {
                val parsed = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull()
                if (parsed != null && parsed.containsKey("error")) {
                    emit(RawFrame.Error(line))
                    return@collect
                }
            }

            emit(RawFrame.Data(data = line))
        }
    }
}
