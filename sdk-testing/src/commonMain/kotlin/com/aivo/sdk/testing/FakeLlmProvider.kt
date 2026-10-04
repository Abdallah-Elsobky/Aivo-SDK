package com.aivo.sdk.testing

import com.aivo.sdk.core.model.ContentPart
import com.aivo.sdk.core.model.FinishReason
import com.aivo.sdk.core.model.LlmRequest
import com.aivo.sdk.core.model.LlmResponse
import com.aivo.sdk.core.model.LlmStreamEvent
import com.aivo.sdk.core.model.Message
import com.aivo.sdk.core.model.ProviderCapabilities
import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.core.model.ToolCall
import com.aivo.sdk.core.model.Usage
import com.aivo.sdk.core.port.LlmProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject

/**
 * Scripted responses for [FakeLlmProvider].
 */
public sealed interface FakeResponse {
    public data class Text(
        val text: String,
        val usage: Usage? = Usage(inputTokens = 10, outputTokens = 10),
    ) : FakeResponse

    public data class ToolCalls(
        val calls: List<ToolCall>,
        val usage: Usage? = Usage(inputTokens = 15, outputTokens = 15),
    ) : FakeResponse

    public data class Error(val throwable: Throwable) : FakeResponse

    public companion object {
        public fun ToolCall(name: String, arguments: JsonObject, id: String = "call_1"): FakeResponse =
            ToolCalls(listOf(com.aivo.sdk.core.model.ToolCall(id = id, name = name, arguments = arguments)))
    }
}

/**
 * Deterministic test double for [LlmProvider].
 *
 * Emits responses from a scripted queue and records all received requests.
 */
public class FakeLlmProvider(
    override val id: ProviderId = ProviderId("fake"),
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        streaming = true,
        toolCalling = true,
        parallelToolCalls = true,
        reasoning = true,
        structuredOutput = false,
        statefulConversation = false,
    ),
    responses: List<FakeResponse> = emptyList(),
) : LlmProvider {

    private val responseQueue = responses.toMutableList()
    private val _receivedRequests = mutableListOf<LlmRequest>()
    public val receivedRequests: List<LlmRequest> get() = _receivedRequests.toList()

    public fun enqueue(response: FakeResponse) {
        responseQueue.add(response)
    }

    override suspend fun generate(request: LlmRequest): LlmResponse {
        _receivedRequests.add(request)
        val next = pollResponse()

        return when (next) {
            is FakeResponse.Text -> LlmResponse(
                message = Message.Assistant(listOf(ContentPart.Text(next.text))),
                finishReason = FinishReason.STOP,
                usage = next.usage,
            )
            is FakeResponse.ToolCalls -> LlmResponse(
                message = Message.Assistant(parts = emptyList(), toolCalls = next.calls),
                finishReason = FinishReason.TOOL_CALLS,
                usage = next.usage,
            )
            is FakeResponse.Error -> throw next.throwable
        }
    }

    override fun stream(request: LlmRequest): Flow<LlmStreamEvent> = flow {
        _receivedRequests.add(request)
        val next = pollResponse()

        when (next) {
            is FakeResponse.Text -> {
                emit(LlmStreamEvent.TextDelta(next.text))
                val response = LlmResponse(
                    message = Message.Assistant(listOf(ContentPart.Text(next.text))),
                    finishReason = FinishReason.STOP,
                    usage = next.usage,
                )
                emit(LlmStreamEvent.Completed(response))
            }
            is FakeResponse.ToolCalls -> {
                for ((index, call) in next.calls.withIndex()) {
                    emit(LlmStreamEvent.ToolCallStarted(index = index, id = call.id, name = call.name))
                    emit(LlmStreamEvent.ToolCallArgumentsDelta(index = index, jsonFragment = call.arguments.toString()))
                }
                val response = LlmResponse(
                    message = Message.Assistant(parts = emptyList(), toolCalls = next.calls),
                    finishReason = FinishReason.TOOL_CALLS,
                    usage = next.usage,
                )
                emit(LlmStreamEvent.Completed(response))
            }
            is FakeResponse.Error -> throw next.throwable
        }
    }

    private fun pollResponse(): FakeResponse {
        if (responseQueue.isEmpty()) {
            return FakeResponse.Text("Default fake response")
        }
        return responseQueue.removeAt(0)
    }
}
