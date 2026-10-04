package com.aivo.sdk.core.model

/**
 * Common abstraction for strongly-typed LLM provider model references.
 */
public interface ProviderModel {
    /** The raw model identifier string sent to the LLM provider API (e.g. "gpt-oss:120b", "gemini-1.5-flash"). */
    public val modelId: String

    /** Whether this model is available on the provider's free tier. */
    public val isFree: Boolean

    /** Human-friendly display name. */
    public val displayName: String
        get() = modelId

    /** The provider identifier this model belongs to (e.g. "ollama", "openrouter", "gemini"). */
    public val providerId: ProviderId

    /** Converts this model into a fully-qualified [ModelRef]. */
    public fun toModelRef(): ModelRef = ModelRef(providerId, modelId)
}
