package com.aivo.sdk.middleware

import com.aivo.sdk.core.error.AuthenticationException
import com.aivo.sdk.core.error.RateLimitException
import com.aivo.sdk.core.error.ServerException
import com.aivo.sdk.core.model.ContentPart
import com.aivo.sdk.core.model.FinishReason
import com.aivo.sdk.core.model.LlmRequest
import com.aivo.sdk.core.model.LlmResponse
import com.aivo.sdk.core.model.LlmStreamEvent
import com.aivo.sdk.core.model.Message
import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.testing.FakeLlmProvider
import com.aivo.sdk.testing.FakeResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RetryingLlmProviderTest {

    @Test
    fun generate_retries_transient_failure_and_succeeds() = runTest {
        val fake = FakeLlmProvider(
            responses = listOf(
                FakeResponse.Error(ServerException(ProviderId("fake"), 500, "Internal Server Error")),
                FakeResponse.Text("Success on attempt 2"),
            )
        )

        var delayedTotal = 0L
        val retrying = RetryingLlmProvider(
            delegate = fake,
            policy = RetryPolicy(maxRetries = 3, initialDelayMs = 100L, jitterFactor = 0.0),
            delayFn = { delayedTotal += it },
        )

        val request = LlmRequest("model", listOf(Message.User("Hi")))
        val response = retrying.generate(request)

        val text = response.message.parts.filterIsInstance<ContentPart.Text>().joinToString("") { it.text }
        assertEquals("Success on attempt 2", text)
        assertEquals(100L, delayedTotal)
    }

    @Test
    fun generate_does_not_retry_non_retryable_exception() = runTest {
        val fake = FakeLlmProvider(
            responses = listOf(
                FakeResponse.Error(AuthenticationException(ProviderId("fake"), 401, "Bad API Key")),
            )
        )

        var retryCount = 0
        val retrying = RetryingLlmProvider(
            delegate = fake,
            policy = RetryPolicy(maxRetries = 3),
            delayFn = { retryCount++ },
        )

        val request = LlmRequest("model", listOf(Message.User("Hi")))
        assertFailsWith<AuthenticationException> {
            retrying.generate(request)
        }
        assertEquals(0, retryCount)
    }

    @Test
    fun generate_never_catches_cancellation_exception() = runTest {
        val fake = FakeLlmProvider(
            responses = listOf(
                FakeResponse.Error(CancellationException("Job cancelled")),
            )
        )

        val retrying = RetryingLlmProvider(delegate = fake)
        val request = LlmRequest("model", listOf(Message.User("Hi")))

        assertFailsWith<CancellationException> {
            retrying.generate(request)
        }
    }
}
