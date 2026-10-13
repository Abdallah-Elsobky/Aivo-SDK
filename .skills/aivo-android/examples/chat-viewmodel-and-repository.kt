package com.example.aivo.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aivo.sdk.AivoProvider
import com.aivo.sdk.AivoSdk
import com.aivo.sdk.core.error.AuthenticationException
import com.aivo.sdk.core.error.RateLimitException
import com.aivo.sdk.core.error.SdkException
import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.core.port.ConfirmationHandler
import com.aivo.sdk.provider.gemini.GeminiModel
import com.aivo.sdk.runtime.agent.AgentEvent
import com.example.aivo.ui.ChatUiState
import com.example.aivo.ui.PendingConfirmation
import com.example.aivo.ui.UiMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// -------------------------------------------------------------------------------------
// 1. Repository Interface & Implementation with Instance Caching
// -------------------------------------------------------------------------------------

interface ChatRepository {
    fun streamResponse(
        conversationId: ConversationId,
        userMessage: String,
        confirmationBridge: ConfirmationHandler? = null
    ): Flow<AgentEvent>
}

class ChatRepositoryImpl(
    private val apiKeyProvider: () -> String,
    private val provider: AivoProvider = AivoProvider.GEMINI,
    private val modelId: String = GeminiModel.GEMINI_3_8_FLASH.modelId
) : ChatRepository {

    @Volatile private var cachedSdk: AivoSdk? = null
    @Volatile private var cachedKey: String? = null

    @Synchronized
    private fun getSdk(confirmationBridge: ConfirmationHandler?): AivoSdk {
        val currentKey = apiKeyProvider()
        if (cachedSdk == null || cachedKey != currentKey) {
            cachedKey = currentKey
            cachedSdk = AivoSdk {
                providers {
                    gemini(provider.id) {
                        apiKey(currentKey)
                    }
                }
                defaultModel(modelId)
                resilience {
                    retry {
                        maxAttempts = 3
                        baseDelayMs = 1_000L
                        jitter = true
                    }
                }
                security {
                    confirmationBridge?.let { this.confirmationHandler = it }
                }
                agents {
                    define("assistant") {
                        name = "Android Assistant"
                        systemPrompt = "You are a concise, helpful Android assistant."
                    }
                }
                entryAgent("assistant")
            }
        }
        return cachedSdk!!
    }

    override fun streamResponse(
        conversationId: ConversationId,
        userMessage: String,
        confirmationBridge: ConfirmationHandler?
    ): Flow<AgentEvent> {
        val sdk = getSdk(confirmationBridge)
        return sdk.stream(conversationId, userMessage)
    }
}

// -------------------------------------------------------------------------------------
// 2. ViewModel Orchestrating State, Events, and Human Approval Dialogs
// -------------------------------------------------------------------------------------

class ChatViewModel(
    private val repository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var pendingDeferred: CompletableDeferred<Boolean>? = null

    // Confirmation bridge passed to SDK: suspends until user responds in UI
    private val confirmationBridge = ConfirmationHandler { toolName, args ->
        val deferred = CompletableDeferred<Boolean>()
        pendingDeferred = deferred
        _uiState.update {
            it.copy(
                pendingConfirmation = PendingConfirmation(
                    toolName = toolName,
                    argumentsJson = args.toString(),
                    onConfirm = { approved ->
                        resolveConfirmation(approved)
                    }
                )
            )
        }
        deferred.await()
    }

    fun resolveConfirmation(approved: Boolean) {
        pendingDeferred?.complete(approved)
        pendingDeferred = null
        _uiState.update { it.copy(pendingConfirmation = null) }
    }

    fun sendMessage(messageText: String, conversationId: ConversationId = ConversationId("android-session")) {
        if (messageText.isBlank()) return

        // Add user message to UI
        val userMsg = UiMessage(id = System.currentTimeMillis().toString(), content = messageText, isUser = true)
        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                isGenerating = true,
                errorMessage = null,
                activeToolName = null
            )
        }

        viewModelScope.launch {
            var assistantMsgId = ""
            var accumulatedText = ""
            var accumulatedReasoning = ""

            try {
                repository.streamResponse(conversationId, messageText, confirmationBridge)
                    .collect { event ->
                        when (event) {
                            is AgentEvent.AgentEntered -> {
                                _uiState.update { it.copy(activeAgent = event.agentName) }
                            }
                            is AgentEvent.TextDelta -> {
                                if (assistantMsgId.isEmpty()) {
                                    assistantMsgId = (System.currentTimeMillis() + 1).toString()
                                    _uiState.update {
                                        it.copy(messages = it.messages + UiMessage(assistantMsgId, "", isUser = false))
                                    }
                                }
                                accumulatedText += event.text
                                updateAssistantMessage(assistantMsgId, accumulatedText, accumulatedReasoning)
                            }
                            is AgentEvent.ReasoningDelta -> {
                                accumulatedReasoning += event.text
                                updateAssistantMessage(assistantMsgId, accumulatedText, accumulatedReasoning)
                            }
                            is AgentEvent.ToolCallStarted -> {
                                _uiState.update { it.copy(activeToolName = event.toolName) }
                            }
                            is AgentEvent.ToolCallExecuted -> {
                                _uiState.update { it.copy(activeToolName = null) }
                            }
                            is AgentEvent.RunCompleted -> {
                                _uiState.update { it.copy(isGenerating = false, activeToolName = null) }
                            }
                            is AgentEvent.RunFailed -> {
                                handleException(event.error)
                            }
                            else -> Unit
                        }
                    }
            } catch (e: Throwable) {
                handleException(e)
            } finally {
                _uiState.update { it.copy(isGenerating = false) }
            }
        }
    }

    private fun updateAssistantMessage(msgId: String, content: String, reasoning: String) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { msg ->
                    if (msg.id == msgId) msg.copy(content = content, reasoning = reasoning.ifBlank { null })
                    else msg
                }
            )
        }
    }

    private fun handleException(e: Throwable) {
        val errorText = when (e) {
            is AuthenticationException -> "Invalid API key. Please check your credentials."
            is RateLimitException -> "Rate limit exceeded. Please wait a few moments."
            is SdkException -> "Aivo SDK Error: ${e.message}"
            else -> e.localizedMessage ?: "An unexpected error occurred"
        }
        _uiState.update { it.copy(errorMessage = errorText, isGenerating = false) }
    }
}
