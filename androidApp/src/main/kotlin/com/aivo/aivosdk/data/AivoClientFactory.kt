package com.aivo.aivosdk.data

import com.aivo.aivosdk.agent.SampleAgentRegistry
import com.aivo.sdk.AivoProvider
import com.aivo.sdk.AivoSdk

/**
 * Factory responsible for initializing and configuring [AivoSdk] instances
 * for the sample app.
 *
 * **This is sample app code, not part of the SDK itself.**
 *
 * In your own application, you would configure [AivoSdk] directly in your
 * dependency injection setup (Hilt, Koin, etc.) or in a ViewModel.
 *
 * Key principles demonstrated here:
 * - API keys are **never hardcoded** — they are passed in at runtime.
 * - The SDK instance is created fresh per unique (provider, model, apiKey) triple.
 * - Agent registration uses [SampleAgentRegistry] as a convenient grouping pattern.
 */
object AivoClientFactory {

    /**
     * Creates a fully configured [AivoSdk] instance.
     *
     * @param provider The LLM provider to use.
     * @param modelId The model identifier (e.g. `"gemma2:9b"` for Ollama, `"gemini-2.0-flash"` for Gemini).
     * @param apiKey The API key for the provider. Pass blank for Ollama local instances.
     */
    fun createSdk(
        provider: AivoProvider,
        modelId: String,
        apiKey: String,
    ): AivoSdk {
        // Build the sample agent registry for the chosen provider
        val registry = SampleAgentRegistry.create(provider.displayName)

        return AivoSdk {
            providers {
                val baseUrl = provider.defaultCloudBaseUrl
                when (provider) {
                    AivoProvider.OLLAMA -> ollama(provider.id) {
                        baseUrl(baseUrl)
                        if (apiKey.isNotBlank()) apiKey(apiKey)
                    }
                    AivoProvider.OPEN_ROUTER -> openRouter(provider.id) {
                        baseUrl(baseUrl)
                        if (apiKey.isNotBlank()) apiKey(apiKey)
                    }
                    AivoProvider.GEMINI -> gemini(provider.id) {
                        baseUrl(baseUrl)
                        if (apiKey.isNotBlank()) apiKey(apiKey)
                    }
                }
            }

            // Qualify the model ID with the provider prefix if not already qualified
            val qualifiedModel = if (modelId.startsWith("${provider.id}:")) modelId
                                 else "${provider.id}:$modelId"
            defaultModel(qualifiedModel)

            agents {
                for (agent in registry.allAgents) {
                    +agent
                }
            }

            entryAgent(registry.standardAssistant.id)
        }
    }
}
