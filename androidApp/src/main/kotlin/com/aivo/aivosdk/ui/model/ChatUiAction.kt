package com.aivo.aivosdk.ui.model

import com.aivo.aivosdk.agent.SampleAgentMode
import com.aivo.sdk.AivoProvider
import com.aivo.sdk.core.model.ProviderModel

/**
 * User actions and events triggered from the chat UI.
 */
sealed interface ChatUiAction {
    data class InputTextChanged(val text: String) : ChatUiAction
    data object SendMessage : ChatUiAction
    data class SelectProvider(val provider: AivoProvider) : ChatUiAction
    data class SelectModel(val model: ProviderModel) : ChatUiAction
    data class SelectAgentMode(val mode: SampleAgentMode) : ChatUiAction
    data class CustomModelChanged(val name: String) : ChatUiAction
    data class SetCustomModelActive(val active: Boolean) : ChatUiAction
    data class ApiKeyChanged(val provider: AivoProvider, val key: String) : ChatUiAction
    data class SetModelMenuExpanded(val expanded: Boolean) : ChatUiAction
    data object ToggleConfigVisibility : ChatUiAction
}
