package com.example.shared.test

import com.aivo.sdk.AivoSdk
import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.core.model.Message
import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.runtime.agent.AgentEvent
import com.aivo.sdk.testing.FakeLlmProvider
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Shared unit test suite in commonTest verifying cross-platform Aivo SDK agent loops.
 */
class KmpAgentCommonTest {

    @Test
    fun `agent completes conversation turn with scripted mock in commonTest`() = runTest {
        val fake = FakeLlmProvider.scripted(
            id = ProviderId("kmp-fake"),
            response = Message.assistant("Multiplatform deterministic answer")
        )

        val sdk = AivoSdk {
            providers { register(fake) }
            defaultModel("kmp-fake:test")
            agents {
                define("bot") {
                    name = "KMP Bot"
                    systemPrompt = "You are a test bot."
                }
            }
            entryAgent("bot")
        }

        val result = sdk.chat(ConversationId("kmp-convo-1"), "What is KMP?")
        assertEquals("Multiplatform deterministic answer", result.text)
    }

    @Test
    fun `agent stream emits flow events in commonTest`() = runTest {
        val fake = FakeLlmProvider.scripted(
            id = ProviderId("kmp-fake"),
            response = Message.assistant("Streamed multiplatform answer")
        )

        val sdk = AivoSdk {
            providers { register(fake) }
            defaultModel("kmp-fake:test")
            agents {
                define("bot") { systemPrompt = "Streaming bot" }
            }
            entryAgent("bot")
        }

        val events = sdk.stream(ConversationId("stream-convo"), "Ping").toList()

        assertTrue(events.any { it is AgentEvent.RunStarted }, "Expected RunStarted event")
        assertTrue(events.any { it is AgentEvent.RunCompleted }, "Expected RunCompleted event")
    }
}
