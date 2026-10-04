package com.aivo.sdk.runtime

import com.aivo.sdk.core.model.ContentPart
import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.core.model.FinishReason
import com.aivo.sdk.core.model.LlmRequest
import com.aivo.sdk.core.model.LlmResponse
import com.aivo.sdk.core.model.LlmStreamEvent
import com.aivo.sdk.core.model.Message
import com.aivo.sdk.core.model.ModelRef
import com.aivo.sdk.core.model.ProviderCapabilities
import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.core.port.LlmProvider
import com.aivo.sdk.runtime.agent.AgentDefinition
import com.aivo.sdk.runtime.agent.AgentRegistry
import com.aivo.sdk.runtime.agent.AgentRuntime
import com.aivo.sdk.runtime.memory.InMemoryMemoryStore
import com.aivo.sdk.runtime.tool.ToolExecutor
import com.aivo.sdk.runtime.tool.ToolRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CancellationStressTest {

    /**
     * An infinite streaming provider that emits chunks indefinitely until cancelled.
     */
    private class InfiniteStreamingProvider(override val id: ProviderId = ProviderId("infinite")) : LlmProvider {
        override val capabilities: ProviderCapabilities = ProviderCapabilities(streaming = true)

        override suspend fun generate(request: LlmRequest): LlmResponse {
            return LlmResponse(
                message = Message.Assistant(listOf(ContentPart.Text("Done"))),
                finishReason = FinishReason.STOP,
            )
        }

        override fun stream(request: LlmRequest): Flow<LlmStreamEvent> = flow {
            var counter = 0
            while (true) {
                emit(LlmStreamEvent.TextDelta("chunk-${counter++} "))
                delay(5)
            }
        }
    }

    @Test
    fun concurrent_streaming_cancellations_do_not_deadlock_or_corrupt_state() = runTest {
        val provider = InfiniteStreamingProvider()
        val agent = AgentDefinition(
            id = "stress-agent",
            name = "Stress Agent",
            model = ModelRef(provider.id, "model"),
            systemPrompt = "You are a test agent",
        )
        val memory = InMemoryMemoryStore()
        val runtime = AgentRuntime(
            providers = mapOf(provider.id to provider),
            agentRegistry = AgentRegistry(listOf(agent)),
            toolRegistry = ToolRegistry(),
            memoryStore = memory,
            toolExecutor = ToolExecutor(ToolRegistry()),
        )

        val concurrentRuns = 30
        var cancellationCount = 0

        val jobs = (1..concurrentRuns).map { index ->
            val convId = ConversationId("conv-$index")
            launch {
                val job = launch {
                    try {
                        runtime.stream(
                            agentId = "stress-agent",
                            input = "Hello $index",
                            conversationId = convId,
                        ).collect {
                            // Collect chunks
                        }
                    } catch (e: CancellationException) {
                        cancellationCount++
                        throw e
                    }
                }
                // Let the stream start and emit frames
                delay((index % 5 + 1) * 10L)
                job.cancel()
                job.join()
            }
        }

        jobs.joinAll()

        // All 30 streams should have been cancelled cleanly
        assertEquals(concurrentRuns, cancellationCount)

        // Verify runtime is still completely operational after mass cancellation
        val normalProvider = com.aivo.sdk.testing.FakeLlmProvider(
            id = provider.id,
            responses = listOf(com.aivo.sdk.testing.FakeResponse.Text("System recovered successfully!"))
        )
        val healthyRuntime = AgentRuntime(
            providers = mapOf(provider.id to normalProvider),
            agentRegistry = AgentRegistry(listOf(agent)),
            toolRegistry = ToolRegistry(),
            memoryStore = memory,
            toolExecutor = ToolExecutor(ToolRegistry()),
        )

        val recoveryResult = healthyRuntime.run("stress-agent", "Are you alive?")
        assertEquals("System recovered successfully!", recoveryResult.text)
    }
}
