package com.aivo.sdk.core

import com.aivo.sdk.core.error.AuthenticationException
import com.aivo.sdk.core.error.ConfigurationException
import com.aivo.sdk.core.error.MaxStepsExceededException
import com.aivo.sdk.core.error.RateLimitException
import com.aivo.sdk.core.error.SdkException
import com.aivo.sdk.core.model.ProviderId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExceptionsTest {

    @Test
    fun configuration_exception_collects_all_problems() {
        val problems = listOf("Problem 1", "Problem 2")
        val ex = ConfigurationException(problems)
        assertEquals(problems, ex.problems)
        assertTrue(ex.message?.contains("Problem 1") == true)
        assertTrue(ex.message?.contains("Problem 2") == true)
    }

    @Test
    fun rate_limit_exception_is_retryable() {
        val ex = RateLimitException(ProviderId("openai"), retryAfterMs = 2000L, message = "Rate limit reached")
        assertTrue(ex.retryable)
        assertEquals(2000L, ex.retryAfterMs)
        assertEquals(429, ex.httpStatus)
    }

    @Test
    fun authentication_exception_is_not_retryable() {
        val ex = AuthenticationException(ProviderId("gemini"), 401, "Invalid API key")
        assertFalse(ex.retryable)
        assertEquals(401, ex.httpStatus)
    }

    @Test
    fun agent_exception_hierarchy() {
        val ex: SdkException = MaxStepsExceededException("assistant", 10)
        assertTrue(ex is MaxStepsExceededException)
        assertEquals("assistant", ex.agentId)
        assertEquals(10, ex.maxSteps)
    }
}
