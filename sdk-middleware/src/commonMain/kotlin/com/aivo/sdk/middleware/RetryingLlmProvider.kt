package com.aivo.sdk.middleware

import com.aivo.sdk.core.error.ProviderException
import com.aivo.sdk.core.error.RateLimitException
import com.aivo.sdk.core.model.LlmRequest
import com.aivo.sdk.core.model.LlmResponse
import com.aivo.sdk.core.model.LlmStreamEvent
import com.aivo.sdk.core.model.ProviderCapabilities
import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.core.port.LlmProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Configuration options for [RetryingLlmProvider].
 *
 * @param maxRetries         Maximum number of retries before failing.
 * @param initialDelayMs     Base delay before the first retry attempt in milliseconds.
 * @param maxDelayMs         Upper bound on retry delay in milliseconds.
 * @param backoffMultiplier  Exponential multiplier applied per retry attempt.
 * @param jitterFactor       Random jitter factor in range [0.0, 1.0] applied to delay.
 */
public data class RetryPolicy(
    val maxRetries: Int = 3,
    val initialDelayMs: Long = 500L,
    val maxDelayMs: Long = 10_000L,
    val backoffMultiplier: Double = 2.0,
    val jitterFactor: Double = 0.2,
)

/**
 * [LlmProvider] decorator that retries transient failures with exponential backoff and jitter.
 *
 * **Behavior:**
 * - Retries only when [ProviderException.retryable] is true.
 * - When [RateLimitException.retryAfterMs] is provided by the server, respects that wait duration.
 * - For [stream], retries are **only** attempted before the first frame is emitted (pre-first-event rule)
 *   to avoid duplicate token output to the user. Once an event is emitted, any error propagates immediately.
 * - Never catches or wraps [CancellationException].
 */
public class RetryingLlmProvider(
    private val delegate: LlmProvider,
    private val policy: RetryPolicy = RetryPolicy(),
    private val delayFn: suspend (Long) -> Unit = { delay(it) },
) : LlmProvider {

    override val id: ProviderId get() = delegate.id
    override val capabilities: ProviderCapabilities get() = delegate.capabilities

    override suspend fun generate(request: LlmRequest): LlmResponse {
        var attempt = 0
        while (true) {
            try {
                return delegate.generate(request)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ProviderException) {
                if (!e.retryable || attempt >= policy.maxRetries) {
                    throw e
                }
                val delayTime = calculateDelay(attempt, e)
                attempt++
                delayFn(delayTime)
            }
        }
    }

    override fun stream(request: LlmRequest): Flow<LlmStreamEvent> = flow {
        var attempt = 0
        while (true) {
            var emittedAny = false
            try {
                delegate.stream(request).collect { event ->
                    emittedAny = true
                    emit(event)
                }
                return@flow
            } catch (e: CancellationException) {
                throw e
            } catch (e: ProviderException) {
                // Pre-first-event rule: cannot retry if anything has already reached the caller
                if (emittedAny || !e.retryable || attempt >= policy.maxRetries) {
                    throw e
                }
                val delayTime = calculateDelay(attempt, e)
                attempt++
                delayFn(delayTime)
            }
        }
    }

    private fun calculateDelay(attempt: Int, exception: ProviderException): Long {
        val retryAfter = (exception as? RateLimitException)?.retryAfterMs
        if (retryAfter != null) {
            return min(retryAfter, policy.maxDelayMs)
        }
        val exponential = (policy.initialDelayMs * policy.backoffMultiplier.pow(attempt.toDouble())).toLong()
        val bounded = min(exponential, policy.maxDelayMs)
        val jitterRange = (bounded * policy.jitterFactor).toLong()
        val jitter = if (jitterRange > 0) Random.nextLong(-jitterRange, jitterRange + 1) else 0L
        return (bounded + jitter).coerceAtLeast(0L)
    }
}
