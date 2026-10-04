package com.aivo.sdk.transport.protocol

import com.aivo.sdk.core.model.LlmRequest
import com.aivo.sdk.core.model.LlmResponse
import com.aivo.sdk.core.model.LlmStreamEvent
import com.aivo.sdk.core.error.ProviderException
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

// ─────────────────────────────────────────────────────────────────────────────
// StreamFraming
// ─────────────────────────────────────────────────────────────────────────────

/** The wire-level streaming protocol used by a provider. */
public enum class StreamFraming {
    /** Server-Sent Events (text/event-stream). Used by OpenAI-compatible, Gemini. */
    SSE,
    /** Newline-delimited JSON. Used by Ollama. */
    NDJSON,
}

// ─────────────────────────────────────────────────────────────────────────────
// Endpoint
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Describes the HTTP endpoint for a single LLM call.
 *
 * @param method HTTP method (always POST for all currently supported providers).
 * @param path   Relative path appended to the provider's [baseUrl].
 */
public data class Endpoint(
    public val method: HttpMethod,
    public val path: String,
)

// ─────────────────────────────────────────────────────────────────────────────
// RawFrame
// ─────────────────────────────────────────────────────────────────────────────

/** A raw decoded frame from a streaming response before domain-model translation. */
public sealed interface RawFrame {
    /** A normal data frame containing JSON (or another string payload). */
    public data class Data(val data: String, val eventType: String? = null) : RawFrame
    /** A mid-stream error frame (e.g., Ollama `{"error":"…"}` line). */
    public data class Error(val body: String) : RawFrame
    /** Signals end of stream ([DONE] for SSE, end of body for NDJSON). */
    public object Done : RawFrame
}

// ─────────────────────────────────────────────────────────────────────────────
// WireProtocol
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Maps between the provider's wire format and the SDK's domain model.
 *
 * Each provider module implements exactly one [WireProtocol].
 * [HttpLlmProvider][com.aivo.sdk.transport.HttpLlmProvider] owns all HTTP mechanics
 * and delegates the JSON translation work here.
 *
 * **Purity contract:** [encode], [decode], and [decodeStream] are pure functions over JSON.
 * They have no Ktor dependency and can be unit-tested with fixture strings without
 * a [MockEngine][io.ktor.client.engine.mock.MockEngine].
 *
 * Marked [@InternalSdkApi] — this interface is not part of the public API.
 * Users interact with provider factories ([OllamaProvider], [GeminiProvider], etc.).
 */
public interface WireProtocol {
    /** How this provider frames streaming responses. */
    public val framing: StreamFraming

    /**
     * Returns the HTTP endpoint for [request].
     * @param stream `true` when the caller intends to collect a streaming response.
     */
    public fun endpoint(request: LlmRequest, stream: Boolean): Endpoint

    /**
     * Serialises [request] to the provider's JSON body.
     * Implementations must merge [LlmRequest.extras] into the body last,
     * allowing callers to override any field.
     *
     * @param stream `true` → include `"stream": true` (or equivalent) in the body.
     */
    public fun encode(request: LlmRequest, stream: Boolean): JsonObject

    /**
     * Deserialises a complete (non-streaming) response body to [LlmResponse].
     * @throws [com.aivo.sdk.core.error.ProtocolException] if the body cannot be decoded.
     */
    public fun decode(body: String): LlmResponse

    /**
     * Translates a flow of [RawFrame]s to domain [LlmStreamEvent]s.
     *
     * Implementations produce delta events; [StreamAssembler][com.aivo.sdk.core.stream.StreamAssembler]
     * accumulates them into the final [LlmStreamEvent.Completed].
     */
    public fun decodeStream(frames: Flow<RawFrame>): Flow<LlmStreamEvent>

    /**
     * Maps an HTTP error response to the appropriate [ProviderException] subclass.
     *
     * @param status  HTTP status code.
     * @param body    Response body (truncated to ≤ 2 KB by the caller before passing here).
     * @param headers Response headers (may contain `Retry-After`).
     */
    public fun mapError(status: Int, body: String, headers: Headers): ProviderException
}
