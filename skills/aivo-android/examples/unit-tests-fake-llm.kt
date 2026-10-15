package com.example.aivo.test

import com.aivo.sdk.AivoSdk
import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.core.model.Message
import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.runtime.agent.AgentEvent
import com.aivo.sdk.testing.FakeLlmProvider
import com.aivo.sdk.testing.InMemoryTelemetry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic unit testing using Aivo SDK test doubles from `sdk-testing`.
 */
class AivoSdkAndroidUnitTest {

    @Test
    fun `chat returns scripted response without network connection`() = runTest {
        // 1. Arrange fake provider
        val fakeProvider = FakeLlmProvider.scripted(
            id = ProviderId("fake-llm"),
            response = Message.assistant("Hello! This is a mock response from Aivo.")
        )

        val sdk = AivoSdk {
            providers {
                register(fakeProvider)
            }
            defaultModel("fake-llm:mock-model")
            agents {
                define("bot") {
                    name = "Mock Assistant"
                    systemPrompt = "You are a test bot."
                }
            }
            entryAgent("bot")
        }

        // 2. Act
        val result = sdk.chat(
            conversationId = ConversationId("test-session-1"),
            message = "Hi!"
        )

        // 3. Assert
        assertEquals("Hello! This is a mock response from Aivo.", result.text)
    }

    @Test
    fun `streaming emits deltas and records telemetry duration`() = runTest {
        val telemetry = InMemoryTelemetry()
        val fakeProvider = FakeLlmProvider.scripted(
            id = ProviderId("fake-llm"),
            response = Message.assistant("Mock streamed text")
        )

        val sdk = AivoSdk {
            providers { register(fakeProvider) }
            defaultModel("fake-llm:mock-model")
            observability {
                this.telemetry = telemetry
            }
            agents {
                define("bot") { systemPrompt = "Test bot" }
            }
            entryAgent("bot")
        }

        val events = sdk.stream(ConversationId("stream-test"), "Generate something").toList()

        // Verify stream events
        assertTrue(events.any { it is AgentEvent.RunStarted })
        assertTrue(events.any { it is AgentEvent.RunCompleted })

        // Verify telemetry captured request
        assertEquals(1, telemetry.requestEndEvents.size)
        assertTrue(telemetry.requestEndEvents.first().durationMs >= 0)
    }
}
