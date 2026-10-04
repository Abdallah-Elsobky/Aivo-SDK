package com.aivo.sdk

import com.aivo.sdk.core.model.GenerationOptions
import com.aivo.sdk.core.model.LlmRequest
import com.aivo.sdk.core.model.LlmResponse
import com.aivo.sdk.core.model.LlmStreamEvent
import com.aivo.sdk.core.model.Message
import com.aivo.sdk.core.model.ModelRef
import com.aivo.sdk.core.model.ToolSpec
import com.aivo.sdk.core.port.LlmProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/**
 * Level 1 Public API: Direct LLM interface for a specific model without running the agent loop.
 */
public interface LlmClient {
    public val model: ModelRef
    public val provider: LlmProvider

    public suspend fun generate(
        messages: List<Message>,
        tools: List<ToolSpec> = emptyList(),
        options: GenerationOptions = GenerationOptions(),
        extras: JsonObject = JsonObject(emptyMap()),
    ): LlmResponse

    public suspend fun generate(prompt: String): LlmResponse =
        generate(listOf(Message.User(prompt)))

    public fun stream(
        messages: List<Message>,
        tools: List<ToolSpec> = emptyList(),
        options: GenerationOptions = GenerationOptions(),
        extras: JsonObject = JsonObject(emptyMap()),
    ): Flow<LlmStreamEvent>

    public fun stream(prompt: String): Flow<LlmStreamEvent> =
        stream(listOf(Message.User(prompt)))
}

internal class DefaultLlmClient(
    override val model: ModelRef,
    override val provider: LlmProvider,
) : LlmClient {
    override suspend fun generate(
        messages: List<Message>,
        tools: List<ToolSpec>,
        options: GenerationOptions,
        extras: JsonObject,
    ): LlmResponse {
        val request = LlmRequest(
            model = model.model,
            messages = messages,
            tools = tools,
            options = options,
            extras = extras,
        )
        return provider.generate(request)
    }

    override fun stream(
        messages: List<Message>,
        tools: List<ToolSpec>,
        options: GenerationOptions,
        extras: JsonObject,
    ): Flow<LlmStreamEvent> {
        val request = LlmRequest(
            model = model.model,
            messages = messages,
            tools = tools,
            options = options,
            extras = extras,
        )
        return provider.stream(request)
    }
}
