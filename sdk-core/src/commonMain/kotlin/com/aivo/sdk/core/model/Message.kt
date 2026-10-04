package com.aivo.sdk.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ─────────────────────────────────────────────────────────────────────────────
// ContentPart — building blocks of a message payload
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single part of a message's content.
 *
 * v1 ships with [Text] only. The sealed interface shape allows [Image], [Audio], and [File]
 * to be added in future versions without breaking existing serialization.
 */
@Serializable
public sealed interface ContentPart {

    /** A plain text content part. */
    @Serializable
    @SerialName("text")
    public data class Text(public val text: String) : ContentPart
}

// ─────────────────────────────────────────────────────────────────────────────
// ToolCall — a single function invocation requested by the assistant
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A function call requested by the assistant message.
 *
 * @param id       Unique call identifier. Adapters that receive no id (e.g., Ollama)
 *                 must generate one via [com.aivo.sdk.core.util.IdGenerator].
 * @param name     The tool function name.
 * @param arguments The arguments as a JSON object. Adapters that receive a JSON **string**
 *                 (e.g., OpenAI-compatible) must parse it. On parse failure the arguments
 *                 are set to an empty object and the failure is signalled via [ToolResult.Failure].
 */
@Serializable
public data class ToolCall(
    public val id: String,
    public val name: String,
    public val arguments: JsonObject,
)

// ─────────────────────────────────────────────────────────────────────────────
// ToolResult — the outcome of executing a ToolCall
// ─────────────────────────────────────────────────────────────────────────────

/** Classifies why a tool execution failed. Used by [ToolResult.Failure]. */
@Serializable
public enum class ToolFailureKind {
    UNKNOWN_TOOL,
    INVALID_ARGUMENTS,
    DENIED,
    TIMEOUT,
    EXECUTION_FAILED,
}

/**
 * The result of executing a [ToolCall].
 *
 * [Success] content is sent back to the model; [Failure] message is also sent back
 * so the model can self-correct. Neither variant ever contains stack traces or secrets.
 */
@Serializable
public sealed interface ToolResult {

    /** The tool ran successfully and produced [content] as its output. */
    @Serializable
    @SerialName("success")
    public data class Success(public val content: JsonElement) : ToolResult {
        public constructor(text: String) : this(kotlinx.serialization.json.JsonPrimitive(text))
    }

    /**
     * The tool failed for [kind] reason.
     * @param message A safe, user-visible explanation (no stack traces, no internals).
     */
    @Serializable
    @SerialName("failure")
    public data class Failure(
        public val kind: ToolFailureKind,
        public val message: String,
    ) : ToolResult {
        public constructor(message: String) : this(ToolFailureKind.EXECUTION_FAILED, message)
    }
}

/**
 * Returns the human-readable text payload of this tool result, suitable for sending to LLM APIs.
 */
public val ToolResult.renderedText: String
    get() = when (this) {
        is ToolResult.Success -> when (val c = content) {
            is kotlinx.serialization.json.JsonPrimitive -> c.content
            else -> c.toString()
        }
        is ToolResult.Failure -> message
    }

// ─────────────────────────────────────────────────────────────────────────────
// ProviderMetadata — opaque per-provider round-trip data
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Opaque per-provider metadata that must survive a round-trip back to the **same** provider.
 *
 * Examples:
 * - Gemini stores `previous_interaction_id` under `"gemini.interactionId"`.
 * - Gemini stores thought `signature` under `"gemini.thoughtSignature"`.
 * - OpenRouter stores reasoning details for reference.
 *
 * Only the owning adapter reads or writes these entries. They are **never logged**.
 */
@Serializable
public data class ProviderMetadata(
    public val entries: Map<String, JsonElement>,
) {
    public companion object {
        /** Sentinel representing an absent / empty metadata bag. */
        public val Empty: ProviderMetadata = ProviderMetadata(emptyMap())
    }

    /** Returns true if this metadata container is empty. */
    public fun isEmpty(): Boolean = entries.isEmpty()

    /** Returns a new [ProviderMetadata] with [key] set to [value]. */
    public fun with(key: String, value: JsonElement): ProviderMetadata =
        copy(entries = entries + (key to value))

    /** Returns the value for [key], or `null` if absent. */
    public operator fun get(key: String): JsonElement? = entries[key]
}

// ─────────────────────────────────────────────────────────────────────────────
// Message — the conversation turn model
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single turn in a conversation. The sealed hierarchy maps to the role concept
 * used by all major LLM providers, normalized to a provider-agnostic representation.
 */
@Serializable
public sealed interface Message {

    /**
     * A system-level instruction message. Adapters handle the provider-specific
     * placement (e.g., a dedicated `system` key, or the first `messages` entry).
     */
    @Serializable
    @SerialName("system")
    public data class System(public val text: String) : Message

    /** A human/user turn. */
    @Serializable
    @SerialName("user")
    public data class User(public val parts: List<ContentPart>) : Message {
        /** Convenience constructor for a single text message. */
        public constructor(text: String) : this(listOf(ContentPart.Text(text)))
    }

    /**
     * An assistant (model) response.
     *
     * @param parts             The visible text parts.
     * @param toolCalls         Any function calls the assistant requests (may be empty).
     * @param reasoning         Normalized "thinking" / chain-of-thought text, if the
     *                          provider exposes it (e.g., DeepSeek `reasoning`, Gemini `thought`).
     * @param providerMetadata  Opaque data for the same-provider round-trip (e.g., Gemini
     *                          `previous_interaction_id`). Never interpreted outside the adapter.
     */
    @Serializable
    @SerialName("assistant")
    public data class Assistant(
        public val parts: List<ContentPart>,
        public val toolCalls: List<ToolCall> = emptyList(),
        public val reasoning: String? = null,
        public val providerMetadata: ProviderMetadata = ProviderMetadata.Empty,
    ) : Message {
        public val text: String get() = parts.filterIsInstance<ContentPart.Text>().joinToString("") { it.text }
    }

    /**
     * A tool/function result, keyed to [callId] and [toolName] from the corresponding [ToolCall].
     */
    @Serializable
    @SerialName("tool")
    public data class Tool(
        public val callId: String,
        public val toolName: String,
        public val result: ToolResult,
    ) : Message
}
