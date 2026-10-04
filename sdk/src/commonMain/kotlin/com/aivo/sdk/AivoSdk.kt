package com.aivo.sdk

import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.core.model.ModelRef
import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.core.port.LlmProvider
import com.aivo.sdk.runtime.agent.AgentEvent
import com.aivo.sdk.runtime.agent.AgentRegistry
import com.aivo.sdk.runtime.agent.AgentResult
import com.aivo.sdk.runtime.agent.AgentRuntime
import com.aivo.sdk.runtime.tool.ToolRegistry
import kotlinx.coroutines.flow.Flow

import com.aivo.sdk.core.model.ProviderModel
import com.aivo.sdk.core.port.Tool

/**
 * Top-level instance of the Aivo SDK.
 *
 * Thread-safe and immutable after creation.
 */
public interface AivoSdk {
    public val defaultModel: ModelRef?
    public val entryAgentId: String?
    public val providers: Map<ProviderId, LlmProvider>
    public val agents: AgentRegistry
    public val tools: ToolRegistry
    public val runtime: AgentRuntime

    /**
     * Level 1 DX: Access raw LLM capabilities without running an agent loop.
     */
    public fun llm(model: String): LlmClient

    /**
     * Level 1 DX: Access raw LLM capabilities without running an agent loop.
     */
    public fun llm(model: ModelRef): LlmClient

    /**
     * Level 2 & 3 DX: Run an agent conversation to completion and return the final [AgentResult].
     */
    public suspend fun chat(
        conversationId: ConversationId,
        message: String,
        agentId: String? = null,
        variables: Map<String, String> = emptyMap(),
    ): AgentResult

    /**
     * Level 2 & 3 DX: Stream events during an agent conversation run.
     */
    public fun stream(
        conversationId: ConversationId,
        message: String,
        agentId: String? = null,
        variables: Map<String, String> = emptyMap(),
    ): Flow<AgentEvent>

    /**
     * Level 3 & 4 DX: Access a specific agent by ID to run or stream directly.
     */
    public fun agent(id: String): AgentHandle

    /**
     * Diagnostic introspection returning a formatted string describing all registered agents,
     * their roles, tools, delegation targets, and loop engines.
     */
    public fun describe(): String


    public companion object {
        /**
         * Creates an [AivoSdk] instance using the builder DSL.
         */
        public operator fun invoke(block: AivoSdkBuilder.() -> Unit): AivoSdk {
            return AivoSdkBuilder().apply(block).build()
        }

        /**
         * Simple factory to create an [AivoSdk] instance without boilerplate.
         *
         * The SDK automatically configures cloud endpoints, routing, and agent setup.
         */
        public fun create(
            provider: AivoProvider,
            model: String,
            apiKey: String? = null,
            baseUrl: String? = null,
            systemPrompt: String = "You are a helpful multiplatform assistant.",
            tools: List<Tool> = emptyList(),
        ): AivoSdk {
            return AivoSdk {
                providers {
                    val resolvedBase = baseUrl ?: provider.defaultCloudBaseUrl
                    when (provider) {
                        AivoProvider.OLLAMA -> {
                            ollama(provider.id) {
                                baseUrl(resolvedBase)
                                if (!apiKey.isNullOrBlank()) apiKey(apiKey)
                            }
                        }
                        AivoProvider.OPEN_ROUTER -> {
                            openRouter(provider.id) {
                                baseUrl(resolvedBase)
                                if (!apiKey.isNullOrBlank()) apiKey(apiKey)
                            }
                        }
                        AivoProvider.GEMINI -> {
                            gemini(provider.id) {
                                baseUrl(resolvedBase)
                                if (!apiKey.isNullOrBlank()) apiKey(apiKey)
                            }
                        }
                    }
                }

                val qualifiedModel = if (model.startsWith("${provider.id}:")) model else "${provider.id}:$model"
                defaultModel(qualifiedModel)

                if (tools.isNotEmpty()) {
                    tools {
                        for (t in tools) register(t)
                    }
                }

                agents {
                    define("assistant") {
                        name = "${provider.displayName} Assistant"
                        this.systemPrompt = systemPrompt
                        if (tools.isNotEmpty()) {
                            tools(tools.map { it.spec.name })
                        }
                    }
                }

                entryAgent("assistant")
            }
        }

        /**
         * Type-safe factory creating an [AivoSdk] instance with any [ProviderModel] (e.g. [OllamaModel], [OpenRouterModel], [GeminiModel]).
         */
        public fun create(
            model: ProviderModel,
            apiKey: String? = null,
            baseUrl: String? = null,
            systemPrompt: String = "You are a helpful multiplatform assistant.",
            tools: List<Tool> = emptyList(),
        ): AivoSdk {
            val provider = AivoProvider.fromId(model.providerId.value)
                ?: error("Unsupported provider: ${model.providerId.value}")
            return create(
                provider = provider,
                model = model.modelId,
                apiKey = apiKey,
                baseUrl = baseUrl,
                systemPrompt = systemPrompt,
                tools = tools,
            )
        }
    }
}

/**
 * Ergonomic handle to a registered agent for direct run and streaming invocation.
 */
public class AgentHandle(
    public val id: String,
    private val runtime: AgentRuntime,
) {
    public suspend fun run(
        message: String,
        conversationId: ConversationId = ConversationId(com.aivo.sdk.core.util.UuidIdGenerator.next()),
        variables: Map<String, String> = emptyMap(),
    ): AgentResult = runtime.run(id, message, conversationId, variables)

    public fun stream(
        message: String,
        conversationId: ConversationId = ConversationId(com.aivo.sdk.core.util.UuidIdGenerator.next()),
        variables: Map<String, String> = emptyMap(),
    ): Flow<AgentEvent> = runtime.stream(id, message, conversationId, variables)
}

