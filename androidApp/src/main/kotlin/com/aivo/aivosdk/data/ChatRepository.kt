package com.aivo.aivosdk.data

import com.aivo.sdk.AivoProvider
import com.aivo.sdk.AivoSdk
import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.runtime.agent.AgentEvent
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for interacting with Aivo SDK agent conversations.
 *
 * Abstracts the [AivoSdk] so that [ChatViewModel] is testable without
 * a real network connection.
 */
interface ChatRepository {
    fun streamAgentResponse(
        provider: AivoProvider,
        modelId: String,
        apiKey: String,
        agentId: String,
        message: String,
        conversationId: ConversationId,
    ): Flow<AgentEvent>
}

/**
 * Default [ChatRepository] implementation that caches the [AivoSdk] instance
 * so it is only rebuilt when the (provider, model, apiKey) triple changes.
 *
 * This avoids the overhead of re-initialising Ktor HTTP clients on every message.
 */
class ChatRepositoryImpl(
    private val sdkFactory: (AivoProvider, String, String) -> AivoSdk = AivoClientFactory::createSdk,
) : ChatRepository {

    private data class SdkCacheKey(
        val provider: AivoProvider,
        val modelId: String,
        val apiKey: String,
    )

    @Volatile private var cachedKey: SdkCacheKey? = null
    @Volatile private var cachedSdk: AivoSdk? = null

    @Synchronized
    private fun getOrCreateSdk(provider: AivoProvider, modelId: String, apiKey: String): AivoSdk {
        val key = SdkCacheKey(provider, modelId, apiKey)
        if (cachedKey != key || cachedSdk == null) {
            cachedKey = key
            cachedSdk = sdkFactory(provider, modelId, apiKey)
        }
        return cachedSdk!!
    }

    override fun streamAgentResponse(
        provider: AivoProvider,
        modelId: String,
        apiKey: String,
        agentId: String,
        message: String,
        conversationId: ConversationId,
    ): Flow<AgentEvent> {
        val sdk = getOrCreateSdk(provider, modelId, apiKey)
        return sdk.agent(agentId).stream(message, conversationId)
    }
}
