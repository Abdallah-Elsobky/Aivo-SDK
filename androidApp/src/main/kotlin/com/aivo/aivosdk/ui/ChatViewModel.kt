package com.aivo.aivosdk.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aivo.aivosdk.data.ChatRepository
import com.aivo.aivosdk.data.ChatRepositoryImpl
import com.aivo.aivosdk.ui.model.ChatMessage
import com.aivo.aivosdk.ui.model.ChatUiAction
import com.aivo.aivosdk.ui.model.ChatUiState
import com.aivo.sdk.AivoProvider
import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.runtime.agent.AgentEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing state and business logic for the agent chat screen.
 *
 * Demonstrates a clean integration of [AivoSdk] streaming into a Jetpack Compose
 * ViewModel, collecting [AgentEvent] emissions and updating UI state reactively.
 *
 * All API keys are stored in [ChatUiState.apiKeys] and **never hardcoded** here.
 * The developer enters their own key via the configuration panel at runtime.
 */
class ChatViewModel @JvmOverloads constructor(
    private val repository: ChatRepository = ChatRepositoryImpl(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** A stable conversation ID for the current session. */
    private val conversationId = ConversationId("android-sample-session")

    fun onAction(action: ChatUiAction) {
        when (action) {
            is ChatUiAction.InputTextChanged -> {
                _uiState.update { it.copy(inputText = action.text) }
            }
            is ChatUiAction.SendMessage -> {
                sendMessage()
            }
            is ChatUiAction.SelectProvider -> {
                _uiState.update {
                    it.copy(
                        selectedProvider = action.provider,
                        selectedModel = action.provider.defaultModel,
                        isCustomModel = false,
                    )
                }
            }
            is ChatUiAction.SelectModel -> {
                _uiState.update {
                    it.copy(
                        selectedModel = action.model,
                        isCustomModel = false,
                        modelMenuExpanded = false,
                    )
                }
            }
            is ChatUiAction.SelectAgentMode -> {
                _uiState.update { it.copy(selectedAgentMode = action.mode) }
            }
            is ChatUiAction.CustomModelChanged -> {
                _uiState.update { it.copy(customModelName = action.name) }
            }
            is ChatUiAction.SetCustomModelActive -> {
                _uiState.update { it.copy(isCustomModel = action.active, modelMenuExpanded = false) }
            }
            is ChatUiAction.ApiKeyChanged -> {
                _uiState.update {
                    val updatedKeys = it.apiKeys.toMutableMap()
                    updatedKeys[action.provider] = action.key
                    it.copy(apiKeys = updatedKeys)
                }
            }
            is ChatUiAction.SetModelMenuExpanded -> {
                _uiState.update { it.copy(modelMenuExpanded = action.expanded) }
            }
            is ChatUiAction.ToggleConfigVisibility -> {
                _uiState.update { it.copy(showConfig = !it.showConfig) }
            }
        }
    }

    private fun sendMessage() {
        val state = _uiState.value
        val textToSend = state.inputText.trim()
        if (textToSend.isBlank() || state.isGenerating) return

        // Guard: Require an API key for cloud providers.
        // Ollama running locally does not require a key.
        if (state.currentApiKey.isBlank() && state.selectedProvider != AivoProvider.OLLAMA) {
            val warningMsg = ChatMessage(
                id = "sys-${System.currentTimeMillis()}",
                sender = "System",
                text = "⚠️ Please enter an API key for ${state.selectedProvider.displayName} via the 🔑 Key button above.",
                isUser = false,
            )
            _uiState.update {
                it.copy(
                    messages = it.messages + warningMsg,
                    showConfig = true,
                )
            }
            return
        }

        val userMsgId = "user-${System.currentTimeMillis()}"
        val userMsg = ChatMessage(id = userMsgId, sender = "You", text = textToSend, isUser = true)

        val assistantMsgId = "assistant-${System.currentTimeMillis()}"
        val assistantMsg = ChatMessage(
            id = assistantMsgId,
            sender = "${state.selectedAgentMode.label} (${state.selectedProvider.displayName})",
            text = "",
            isUser = false,
        )

        _uiState.update {
            it.copy(
                inputText = "",
                isGenerating = true,
                messages = it.messages + userMsg + assistantMsg,
            )
        }

        viewModelScope.launch {
            repository.streamAgentResponse(
                provider = state.selectedProvider,
                modelId = state.effectiveModelId,
                apiKey = state.currentApiKey,
                agentId = state.selectedAgentMode.agentId,
                message = textToSend,
                conversationId = conversationId,
            )
                .catch { error ->
                    updateMessage(assistantMsgId) { current ->
                        current.copy(
                            text = if (current.text.isNotBlank()) current.text else "Error: ${error.message}",
                            activeTool = null,
                        )
                    }
                    _uiState.update { it.copy(isGenerating = false) }
                }
                .collect { event ->
                    when (event) {
                        is AgentEvent.AgentEntered -> {
                            updateMessage(assistantMsgId) { current ->
                                current.copy(
                                    activeTool = "Agent active: ${event.agentId} (${event.agentPath.joinToString(" → ")})"
                                )
                            }
                        }
                        is AgentEvent.TextDelta -> {
                            updateMessage(assistantMsgId) { current ->
                                current.copy(
                                    text = current.text + event.text,
                                    activeTool = null,
                                )
                            }
                        }
                        is AgentEvent.ToolCallStarted -> {
                            updateMessage(assistantMsgId) { current ->
                                current.copy(
                                    activeTool = "Running tool: ${event.toolName}…"
                                )
                            }
                        }
                        is AgentEvent.ToolCallExecuted -> {
                            updateMessage(assistantMsgId) { current ->
                                current.copy(
                                    activeTool = "Analysing ${event.toolName} result…"
                                )
                            }
                        }
                        is AgentEvent.RunCompleted -> {
                            updateMessage(assistantMsgId) { current ->
                                current.copy(
                                    text = if (current.text.isNotBlank()) current.text else event.result.text,
                                    activeTool = null,
                                )
                            }
                            _uiState.update { it.copy(isGenerating = false) }
                        }
                        is AgentEvent.RunFailed -> {
                            updateMessage(assistantMsgId) { current ->
                                current.copy(
                                    text = if (current.text.isNotBlank()) current.text else "Failed: ${event.error.message}",
                                    activeTool = null,
                                )
                            }
                            _uiState.update { it.copy(isGenerating = false) }
                        }
                        else -> {}
                    }
                }
            _uiState.update { it.copy(isGenerating = false) }
        }
    }

    private fun updateMessage(messageId: String, transform: (ChatMessage) -> ChatMessage) {
        _uiState.update { state ->
            val updatedMessages = state.messages.map { msg ->
                if (msg.id == messageId) transform(msg) else msg
            }
            state.copy(messages = updatedMessages)
        }
    }
}
