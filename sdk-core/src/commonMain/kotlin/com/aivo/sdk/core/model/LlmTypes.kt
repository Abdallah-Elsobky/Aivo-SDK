package com.aivo.sdk.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ─────────────────────────────────────────────────────────────────────────────
// FinishReason
// ─────────────────────────────────────────────────────────────────────────────

/** Normalized reason the model stopped generating. Adapters map provider strings to this enum. */
@Serializable
public enum class FinishReason {
    /** Normal completion — the model finished its response. */
    STOP,
    /** Maximum output token limit reached. */
    LENGTH,
    /** The model requested one or more tool calls. */
    TOOL_CALLS,
    /** Response blocked by a content policy filter. */
    CONTENT_FILTER,
    /** Provider reported an error during generation. */
    ERROR,
    /** Any other finish reason not covered above (raw value stored in [ProviderMetadata]). */
    OTHER,
}

// ─────────────────────────────────────────────────────────────────────────────
// Usage — token and cost accounting
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Token consumption and estimated cost for a single LLM call or an aggregated run.
 *
 * All fields are nullable because not every provider returns every value.
 * Use [plus] to safely aggregate usage across multiple calls.
 */
@Serializable
public data class Usage(
    public val inputTokens: Int? = null,
    public val outputTokens: Int? = null,
    public val reasoningTokens: Int? = null,
    public val cachedInputTokens: Int? = null,
    public val costUsd: Double? = null,
) {
    /**
     * Null-safe addition. Missing values are treated as zero for numeric fields.
     * Costs are summed only if at least one operand has a cost.
     */
    public operator fun plus(other: Usage): Usage = Usage(
        inputTokens       = nullableSum(inputTokens, other.inputTokens),
        outputTokens      = nullableSum(outputTokens, other.outputTokens),
        reasoningTokens   = nullableSum(reasoningTokens, other.reasoningTokens),
        cachedInputTokens = nullableSum(cachedInputTokens, other.cachedInputTokens),
        costUsd           = nullableDoubleSum(costUsd, other.costUsd),
    )

    public companion object {
        /** An empty usage record — useful as an accumulator initial value. */
        public val Zero: Usage = Usage()

        private fun nullableSum(a: Int?, b: Int?): Int? =
            if (a == null && b == null) null else (a ?: 0) + (b ?: 0)

        private fun nullableDoubleSum(a: Double?, b: Double?): Double? =
            if (a == null && b == null) null else (a ?: 0.0) + (b ?: 0.0)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ReasoningOptions — controls chain-of-thought / thinking mode
// ─────────────────────────────────────────────────────────────────────────────

/** Options for models that support visible chain-of-thought / thinking. */
@Serializable
public data class ReasoningOptions(
    /** Ask the model to think before answering. */
    public val enabled: Boolean = true,
    /** Maximum tokens to spend on reasoning (provider-dependent). */
    public val maxTokens: Int? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
// GenerationOptions
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Provider-agnostic generation parameters.
 *
 * Adapters map these to their provider-specific equivalents. `null` means
 * "use the provider's default" and the field is omitted from the request body.
 */
@Serializable
public data class GenerationOptions(
    public val temperature: Double? = null,
    public val topP: Double? = null,
    public val maxOutputTokens: Int? = null,
    public val stop: List<String> = emptyList(),
    public val seed: Long? = null,
    public val reasoning: ReasoningOptions? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
// ToolSpec — describes a tool to the LLM
// ─────────────────────────────────────────────────────────────────────────────

/** Risk level of a tool — used by [com.aivo.sdk.core.port.ToolPolicy] to decide confirmation. */
@Serializable
public enum class ToolRisk { LOW, MEDIUM, HIGH }

/**
 * The declaration the SDK sends to the LLM to advertise a callable function.
 *
 * @param name                 Must match `^[a-zA-Z0-9_-]{1,64}$` (validated at registration).
 * @param description          Plain-English purpose — sent verbatim to the model.
 * @param parameters           JSON Schema object describing the function arguments.
 * @param risk                 Governs confirmation requirements.
 * @param requiresConfirmation If true, the runtime invokes the app's [com.aivo.sdk.core.port.ConfirmationHandler]
 *                             before executing the tool.
 */
@Serializable
public data class ToolSpec(
    public val name: String,
    public val description: String,
    public val parameters: JsonObject,
    public val risk: ToolRisk = ToolRisk.LOW,
    public val requiresConfirmation: Boolean = false,
)

// ─────────────────────────────────────────────────────────────────────────────
// ToolChoice — how the model should select tool calls
// ─────────────────────────────────────────────────────────────────────────────

/** Controls how the LLM decides whether and which tool to call. */
@Serializable
public sealed interface ToolChoice {
    /** Let the model decide whether to call a tool. */
    @Serializable
    public object Auto : ToolChoice

    /** Force the model to call at least one tool. */
    @Serializable
    public object Required : ToolChoice

    /** Prevent the model from calling any tool. */
    @Serializable
    public object None : ToolChoice

    /** Force the model to call a specific named tool. */
    @Serializable
    public data class Named(val name: String) : ToolChoice
}

// ─────────────────────────────────────────────────────────────────────────────
// LlmRequest / LlmResponse / LlmStreamEvent
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Everything the SDK needs to make a single LLM call.
 *
 * @param model      The bare model name as the provider expects it (e.g., `"gemma4:31b"`).
 *                   The caller strips the provider prefix before building this request.
 * @param messages   Full conversation history including system, user, assistant, and tool messages.
 * @param tools      Tool declarations to send. Empty for pure-chat calls.
 * @param toolChoice Governs tool selection behaviour.
 * @param options    Generation parameters.
 * @param extras     Escape-hatch JSON merged into the outgoing request body by the adapter.
 *                   **Never populated from model output.**
 */
@Serializable
public data class LlmRequest(
    public val model: String,
    public val messages: List<Message>,
    public val tools: List<ToolSpec> = emptyList(),
    public val toolChoice: ToolChoice = ToolChoice.Auto,
    public val options: GenerationOptions = GenerationOptions(),
    public val extras: JsonObject = JsonObject(emptyMap()),
)

/**
 * The result of a non-streaming LLM call.
 *
 * For streaming calls the final [LlmStreamEvent.Completed] carries an equivalent [LlmResponse].
 */
@Serializable
public data class LlmResponse(
    public val message: Message.Assistant,
    public val finishReason: FinishReason,
    public val usage: Usage? = null,
    public val model: String? = null,
    public val responseId: String? = null,
)

/** Events emitted by a streaming LLM call. The final event is always [Completed]. */
@Serializable
public sealed interface LlmStreamEvent {

    /** A text content delta. Accumulate to reconstruct the full response text. */
    @Serializable
    @kotlinx.serialization.SerialName("text_delta")
    public data class TextDelta(public val text: String) : LlmStreamEvent

    /** A reasoning / thinking delta. Only emitted by models with visible chain-of-thought. */
    @Serializable
    @kotlinx.serialization.SerialName("reasoning_delta")
    public data class ReasoningDelta(public val text: String) : LlmStreamEvent

    /** The start of a new tool call stream. [index] keys this call's argument fragments. */
    @Serializable
    @kotlinx.serialization.SerialName("tool_call_started")
    public data class ToolCallStarted(
        public val index: Int,
        public val id: String,
        public val name: String,
    ) : LlmStreamEvent

    /** An incremental JSON fragment for the tool call at [index]. Accumulate to build the full args. */
    @Serializable
    @kotlinx.serialization.SerialName("tool_call_arguments_delta")
    public data class ToolCallArgumentsDelta(
        public val index: Int,
        public val jsonFragment: String,
    ) : LlmStreamEvent

    /**
     * The final event in every successful stream. Carries the fully assembled [LlmResponse].
     * Exactly one [Completed] is emitted per stream; no events follow it.
     */
    @Serializable
    @kotlinx.serialization.SerialName("completed")
    public data class Completed(public val response: LlmResponse) : LlmStreamEvent
}

// ─────────────────────────────────────────────────────────────────────────────
// ProviderCapabilities
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Declares what a provider can and cannot do.
 * The runtime uses this to fail-fast when an agent requests an unsupported capability
 * rather than silently degrading.
 */
@Serializable
public data class ProviderCapabilities(
    public val streaming: Boolean = true,
    public val toolCalling: Boolean = false,
    public val parallelToolCalls: Boolean = false,
    public val reasoning: Boolean = false,
    public val structuredOutput: Boolean = false,
    public val statefulConversation: Boolean = false,
)
