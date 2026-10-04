package com.aivo.sdk

import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.core.model.ProviderModel
import com.aivo.sdk.provider.gemini.GeminiModel
import com.aivo.sdk.provider.ollama.OllamaModel
import com.aivo.sdk.provider.openai.OpenRouterModel

/**
 * Top-level enum representing supported LLM providers with pre-configured cloud base URLs.
 */
public enum class AivoProvider(
    public val id: String,
    public val displayName: String,
    public val defaultCloudBaseUrl: String,
) {
    OLLAMA(
        id = "ollama",
        displayName = "Ollama",
        defaultCloudBaseUrl = "https://ollama.com/api/chat",
    ),
    OPEN_ROUTER(
        id = "openrouter",
        displayName = "OpenRouter",
        defaultCloudBaseUrl = "https://openrouter.ai/api/v1",
    ),
    GEMINI(
        id = "gemini",
        displayName = "Gemini",
        defaultCloudBaseUrl = "https://generativelanguage.googleapis.com",
    );

    public val providerId: ProviderId
        get() = ProviderId(id)

    /**
     * All strongly-typed models available for this provider.
     */
    public val models: List<ProviderModel>
        get() = when (this) {
            OLLAMA -> OllamaModel.entries
            OPEN_ROUTER -> OpenRouterModel.entries
            GEMINI -> GeminiModel.entries
        }

    /**
     * Models available on the free tier for this provider.
     */
    public val freeModels: List<ProviderModel>
        get() = when (this) {
            OLLAMA -> OllamaModel.freeModels
            OPEN_ROUTER -> OpenRouterModel.freeModels
            GEMINI -> GeminiModel.freeModels
        }

    /**
     * Models available on the paid tier for this provider.
     */
    public val paidModels: List<ProviderModel>
        get() = when (this) {
            OLLAMA -> OllamaModel.paidModels
            OPEN_ROUTER -> OpenRouterModel.paidModels
            GEMINI -> GeminiModel.paidModels
        }

    /**
     * The recommended default model for this provider.
     */
    public val defaultModel: ProviderModel
        get() = when (this) {
            OLLAMA -> OllamaModel.default
            OPEN_ROUTER -> OpenRouterModel.default
            GEMINI -> GeminiModel.default
        }

    public companion object {
        public fun fromId(id: String): AivoProvider? =
            entries.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) }
    }
}
