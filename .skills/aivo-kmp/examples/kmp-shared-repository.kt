package com.example.shared.data

import com.aivo.sdk.AivoProvider
import com.aivo.sdk.AivoSdk
import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.provider.gemini.GeminiModel
import com.aivo.sdk.runtime.agent.AgentEvent
import kotlinx.coroutines.flow.Flow

/**
 * Shared repository in commonMain managing Aivo SDK conversations.
 */
interface KmpChatRepository {
    fun streamResponse(
        conversationId: String,
        userPrompt: String
    ): Flow<AgentEvent>
}

class KmpChatRepositoryImpl(
    private val apiKeyProvider: () -> String,
    private val provider: AivoProvider = AivoProvider.GEMINI,
    private val modelId: String = GeminiModel.GEMINI_3_8_FLASH.modelId
) : KmpChatRepository {

    @Volatile private var cachedSdk: AivoSdk? = null
    @Volatile private var cachedKey: String? = null

    private fun getOrCreateSdk(): AivoSdk {
        val currentKey = apiKeyProvider()
        if (cachedSdk == null || cachedKey != currentKey) {
            cachedKey = currentKey
            cachedSdk = AivoSdk {
                providers {
                    when (provider) {
                        AivoProvider.GEMINI -> gemini(provider.id) { apiKey(currentKey) }
                        AivoProvider.OLLAMA -> ollama(provider.id) { apiKey(currentKey) }
                        AivoProvider.OPEN_ROUTER -> openRouter(provider.id) { apiKey(currentKey) }
                    }
                }
                defaultModel(modelId)

                memory {
                    slidingWindow(maxMessages = 25)
                }

                resilience {
                    retry {
                        maxAttempts = 3
                        baseDelayMs = 1_000L
                        jitter = true
                    }
                }

                agents {
                    define("assistant") {
                        name = "KMP Assistant"
                        systemPrompt = "You are a helpful cross-platform assistant."
                    }
                }
                entryAgent("assistant")
            }
        }
        return cachedSdk!!
    }

    override fun streamResponse(
        conversationId: String,
        userPrompt: String
    ): Flow<AgentEvent> {
        val sdk = getOrCreateSdk()
        return sdk.stream(ConversationId(conversationId), userPrompt)
    }
}
