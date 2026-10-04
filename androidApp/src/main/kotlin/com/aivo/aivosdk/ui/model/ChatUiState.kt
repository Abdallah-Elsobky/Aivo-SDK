package com.aivo.aivosdk.ui.model

import com.aivo.aivosdk.agent.SampleAgentMode
import com.aivo.sdk.AivoProvider
import com.aivo.sdk.core.model.ProviderModel

/**
 * Immutable state model representing the complete UI state of the chat screen.
 *
 * All API key fields default to blank — no keys are ever hardcoded in source.
 * Developers enter their own keys at runtime via the configuration panel.
 */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val selectedProvider: AivoProvider = AivoProvider.OLLAMA,
    val selectedModel: ProviderModel = AivoProvider.OLLAMA.defaultModel,
    val isCustomModel: Boolean = false,
    val customModelName: String = "",
    val selectedAgentMode: SampleAgentMode = SampleAgentMode.STANDARD,
    // API keys are always blank by default — developers enter them at runtime.
    val apiKeys: Map<AivoProvider, String> = emptyMap(),
    val showConfig: Boolean = false,
    val modelMenuExpanded: Boolean = false,
) {
    val currentApiKey: String
        get() = apiKeys[selectedProvider].orEmpty()

    val effectiveModelId: String
        get() = if (isCustomModel && customModelName.isNotBlank()) customModelName.trim() else selectedModel.modelId
}
