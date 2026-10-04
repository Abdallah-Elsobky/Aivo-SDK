package com.aivo.sdk.middleware

import com.aivo.sdk.core.model.LlmRequest
import com.aivo.sdk.core.model.LlmResponse
import com.aivo.sdk.core.model.LlmStreamEvent
import com.aivo.sdk.core.model.ProviderCapabilities
import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.core.port.DefaultRedactor
import com.aivo.sdk.core.port.LlmProvider
import com.aivo.sdk.core.port.Logger
import com.aivo.sdk.core.port.Redactor
import com.aivo.sdk.core.util.Clock
import com.aivo.sdk.core.util.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion

/**
 * [LlmProvider] decorator that logs request lifecycle, latencies, token usage, and errors.
 *
 * **Security:**
 * - Never logs authorization headers or API keys.
 * - Prompts and completion text are never logged unless [logPayloads] is explicitly set to `true`.
 * - When [logPayloads] is `true`, all logged text passes through [redactor] first.
 */
public class LoggingLlmProvider(
    private val delegate: LlmProvider,
    private val logger: Logger,
    private val logPayloads: Boolean = false,
    private val redactor: Redactor = DefaultRedactor(),
    private val clock: Clock = SystemClock,
    private val tag: String = "AivoSdk.Llm",
) : LlmProvider {

    override val id: ProviderId get() = delegate.id
    override val capabilities: ProviderCapabilities get() = delegate.capabilities

    override suspend fun generate(request: LlmRequest): LlmResponse {
        val startMs = clock.nowMillis()
        logger.debug(tag) {
            "LLM call started: provider='${id.value}', model='${request.model}', tools=${request.tools.size}"
        }
        if (logPayloads) {
            logger.verbose(tag) {
                "LLM request messages: ${redactor.redact(request.messages.toString())}"
            }
        }

        try {
            val response = delegate.generate(request)
            val durationMs = clock.nowMillis() - startMs
            logger.info(tag) {
                "LLM call succeeded: provider='${id.value}', model='${request.model}', duration=${durationMs}ms, " +
                    "finishReason=${response.finishReason}, usage=${response.usage?.let { "in=${it.inputTokens}, out=${it.outputTokens}" } ?: "n/a"}"
            }
            if (logPayloads) {
                logger.verbose(tag) {
                    "LLM response: ${redactor.redact(response.message.toString())}"
                }
            }
            return response
        } catch (e: CancellationException) {
            logger.debug(tag) { "LLM call cancelled: provider='${id.value}', model='${request.model}'" }
            throw e
        } catch (e: Throwable) {
            val durationMs = clock.nowMillis() - startMs
            logger.error(tag, {
                "LLM call failed: provider='${id.value}', model='${request.model}', duration=${durationMs}ms, error=${e::class.simpleName}: ${e.message}"
            }, e)
            throw e
        }
    }

    override fun stream(request: LlmRequest): Flow<LlmStreamEvent> = flow {
        val startMs = clock.nowMillis()
        logger.debug(tag) {
            "LLM stream started: provider='${id.value}', model='${request.model}', tools=${request.tools.size}"
        }
        var eventCount = 0

        try {
            delegate.stream(request).collect { event ->
                eventCount++
                if (event is LlmStreamEvent.Completed) {
                    val durationMs = clock.nowMillis() - startMs
                    logger.info(tag) {
                        "LLM stream completed: provider='${id.value}', model='${request.model}', duration=${durationMs}ms, " +
                            "events=$eventCount, finishReason=${event.response.finishReason}, " +
                            "usage=${event.response.usage?.let { "in=${it.inputTokens}, out=${it.outputTokens}" } ?: "n/a"}"
                    }
                }
                emit(event)
            }
        } catch (e: CancellationException) {
            logger.debug(tag) { "LLM stream cancelled: provider='${id.value}', model='${request.model}'" }
            throw e
        } catch (e: Throwable) {
            val durationMs = clock.nowMillis() - startMs
            logger.error(tag, {
                "LLM stream failed: provider='${id.value}', model='${request.model}', duration=${durationMs}ms, events=$eventCount, error=${e::class.simpleName}: ${e.message}"
            }, e)
            throw e
        }
    }
}
