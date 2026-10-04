package com.aivo.sdk.middleware

import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.core.model.LlmRequest
import com.aivo.sdk.core.model.LlmResponse
import com.aivo.sdk.core.model.LlmStreamEvent
import com.aivo.sdk.core.model.ProviderCapabilities
import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.core.port.LlmProvider
import com.aivo.sdk.core.port.Telemetry
import com.aivo.sdk.core.port.TelemetryEvent
import com.aivo.sdk.core.util.Clock
import com.aivo.sdk.core.util.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * [LlmProvider] decorator that emits structured [TelemetryEvent] instances to a [Telemetry] sink.
 *
 * Captures call initiation, duration, token usage, cost, and failure reason.
 */
public class TelemetryLlmProvider(
    private val delegate: LlmProvider,
    private val telemetry: Telemetry,
    private val clock: Clock = SystemClock,
) : LlmProvider {

    override val id: ProviderId get() = delegate.id
    override val capabilities: ProviderCapabilities get() = delegate.capabilities

    override suspend fun generate(request: LlmRequest): LlmResponse {
        val startMs = clock.nowMillis()
        val meta = extractMetadata(request)

        telemetry.record(
            TelemetryEvent.LlmCallStarted(
                runId = meta.runId,
                conversationId = meta.conversationId,
                agentId = meta.agentId,
                providerId = id,
                model = request.model,
                stepIndex = meta.stepIndex,
                startedAtMs = startMs,
            )
        )

        try {
            val response = delegate.generate(request)
            val durationMs = clock.nowMillis() - startMs

            telemetry.record(
                TelemetryEvent.LlmCallCompleted(
                    runId = meta.runId,
                    conversationId = meta.conversationId,
                    agentId = meta.agentId,
                    providerId = id,
                    model = request.model,
                    stepIndex = meta.stepIndex,
                    durationMs = durationMs,
                    inputTokens = response.usage?.inputTokens,
                    outputTokens = response.usage?.outputTokens,
                    reasoningTokens = response.usage?.reasoningTokens,
                    costUsd = response.usage?.costUsd,
                    retryCount = 0,
                )
            )
            return response
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val durationMs = clock.nowMillis() - startMs
            telemetry.record(
                TelemetryEvent.LlmCallFailed(
                    runId = meta.runId,
                    conversationId = meta.conversationId,
                    agentId = meta.agentId,
                    providerId = id,
                    model = request.model,
                    stepIndex = meta.stepIndex,
                    durationMs = durationMs,
                    errorType = e::class.simpleName ?: "UnknownError",
                    retryCount = 0,
                )
            )
            throw e
        }
    }

    override fun stream(request: LlmRequest): Flow<LlmStreamEvent> = flow {
        val startMs = clock.nowMillis()
        val meta = extractMetadata(request)

        telemetry.record(
            TelemetryEvent.LlmCallStarted(
                runId = meta.runId,
                conversationId = meta.conversationId,
                agentId = meta.agentId,
                providerId = id,
                model = request.model,
                stepIndex = meta.stepIndex,
                startedAtMs = startMs,
            )
        )

        try {
            var completedResponse: LlmResponse? = null
            delegate.stream(request).collect { event ->
                if (event is LlmStreamEvent.Completed) {
                    completedResponse = event.response
                }
                emit(event)
            }

            val durationMs = clock.nowMillis() - startMs
            telemetry.record(
                TelemetryEvent.LlmCallCompleted(
                    runId = meta.runId,
                    conversationId = meta.conversationId,
                    agentId = meta.agentId,
                    providerId = id,
                    model = request.model,
                    stepIndex = meta.stepIndex,
                    durationMs = durationMs,
                    inputTokens = completedResponse?.usage?.inputTokens,
                    outputTokens = completedResponse?.usage?.outputTokens,
                    reasoningTokens = completedResponse?.usage?.reasoningTokens,
                    costUsd = completedResponse?.usage?.costUsd,
                    retryCount = 0,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val durationMs = clock.nowMillis() - startMs
            telemetry.record(
                TelemetryEvent.LlmCallFailed(
                    runId = meta.runId,
                    conversationId = meta.conversationId,
                    agentId = meta.agentId,
                    providerId = id,
                    model = request.model,
                    stepIndex = meta.stepIndex,
                    durationMs = durationMs,
                    errorType = e::class.simpleName ?: "UnknownError",
                    retryCount = 0,
                )
            )
            throw e
        }
    }

    private data class RequestMeta(
        val runId: String,
        val conversationId: ConversationId,
        val agentId: String,
        val stepIndex: Int,
    )

    private fun extractMetadata(request: LlmRequest): RequestMeta {
        val runId = request.extras["_runId"]?.jsonPrimitive?.contentOrNull ?: "run-default"
        val conversationId = ConversationId(
            request.extras["_conversationId"]?.jsonPrimitive?.contentOrNull ?: "conversation-default"
        )
        val agentId = request.extras["_agentId"]?.jsonPrimitive?.contentOrNull ?: "agent-default"
        val stepIndex = request.extras["_stepIndex"]?.jsonPrimitive?.intOrNull ?: 1
        return RequestMeta(runId, conversationId, agentId, stepIndex)
    }
}
