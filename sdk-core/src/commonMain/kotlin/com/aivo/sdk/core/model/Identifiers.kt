package com.aivo.sdk.core.model

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/**
 * Identifies an LLM provider (e.g., "ollama", "openrouter", "gemini").
 * Used as part of [ModelRef] and to route requests to the correct [com.aivo.sdk.core.port.LlmProvider].
 */
@JvmInline
@Serializable
public value class ProviderId(public val value: String) {
    init {
        require(value.isNotBlank()) { "ProviderId must not be blank" }
    }

    override fun toString(): String = value
}

/**
 * Identifies a conversation. Scopes conversation history and per-conversation locks.
 */
@JvmInline
@Serializable
public value class ConversationId(public val value: String) {
    init {
        require(value.isNotBlank()) { "ConversationId must not be blank" }
    }

    override fun toString(): String = value
}

/**
 * A fully-qualified model reference in the format `"providerId:modelName"`.
 *
 * The split is on the **first colon only** so that model names containing colons
 * (e.g., `"ollama:gemma4:31b"`) or slashes (e.g., OpenRouter model paths) are handled correctly.
 *
 * Examples:
 * - `"ollama:gemma4:31b"` → provider=`"ollama"`, model=`"gemma4:31b"`
 * - `"openrouter:deepseek/deepseek-v4.1-flash"` → provider=`"openrouter"`, model=`"deepseek/deepseek-v4.1-flash"`
 */
@Serializable
public data class ModelRef(
    public val provider: ProviderId,
    public val model: String,
) {
    public companion object {
        /**
         * Parses a `"providerId:modelName"` string. Splits on the **first** colon.
         * @throws IllegalArgumentException if the string has no colon.
         */
        public fun parse(ref: String): ModelRef {
            val colonIndex = ref.indexOf(':')
            require(colonIndex > 0) {
                "ModelRef '$ref' must be in the format 'providerId:modelName'"
            }
            return ModelRef(
                provider = ProviderId(ref.substring(0, colonIndex)),
                model = ref.substring(colonIndex + 1),
            )
        }
    }

    /** Returns `"providerId:modelName"`. */
    override fun toString(): String = "${provider.value}:$model"
}
