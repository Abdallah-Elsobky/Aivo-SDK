package com.aivo.sdk.runtime

import app.cash.turbine.test
import com.aivo.sdk.core.error.MaxStepsExceededException
import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.core.model.ModelRef
import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.core.model.ToolResult
import com.aivo.sdk.runtime.agent.AgentDefinition
import com.aivo.sdk.runtime.agent.AgentEvent
import com.aivo.sdk.runtime.agent.AgentLimits
import com.aivo.sdk.runtime.agent.AgentRegistry
import com.aivo.sdk.runtime.agent.AgentRuntime
import com.aivo.sdk.runtime.memory.InMemoryMemoryStore
import com.aivo.sdk.runtime.tool.ToolExecutor
import com.aivo.sdk.runtime.tool.ToolRegistry
import com.aivo.sdk.runtime.tool.tool
import com.aivo.sdk.testing.FakeLlmProvider
import com.aivo.sdk.testing.FakeResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentRuntimeTest {

    @Test
    fun run_single_shot_text_response() = runTest {
        val fakeProvider = FakeLlmProvider(
            id = ProviderId("test-provider"),
            responses = listOf(
                FakeResponse.Text("Hello! I am ready to help."),
            )
        )

        val agent = AgentDefinition(
            id = "assistant",
            name = "Test Assistant",
            model = ModelRef(ProviderId("test-provider"), "test-model"),
            systemPrompt = "You are a test assistant.",
        )

        val memory = InMemoryMemoryStore()
        val runtime = AgentRuntime(
            providers = mapOf(ProviderId("test-provider") to fakeProvider),
            agentRegistry = AgentRegistry(listOf(agent)),
            toolRegistry = ToolRegistry(),
            memoryStore = memory,
            toolExecutor = ToolExecutor(ToolRegistry()),
        )

        val result = runtime.run("assistant", "Hi there!")
        assertEquals("Hello! I am ready to help.", result.text)
        assertEquals(1, result.steps)

        // Verify history persisted in memory
        val history = memory.load(result.conversationId)
        assertEquals(2, history.size) // User message + Assistant message
    }

    @Test
    fun run_with_tool_call_and_final_response() = runTest {
        val fakeProvider = FakeLlmProvider(
            id = ProviderId("test-provider"),
            responses = listOf(
                FakeResponse.ToolCall(
                    name = "get_weather",
                    arguments = buildJsonObject { put("city", "Cairo") },
                ),
                FakeResponse.Text("The weather in Cairo is 25°C and sunny."),
            )
        )

        val weatherTool = tool("get_weather", "Returns current weather") {
            parameters {
                string("city", required = true)
            }
            execute { call, _ ->
                ToolResult.Success(buildJsonObject { put("temp", "25°C"); put("condition", "sunny") })
            }
        }

        val agent = AgentDefinition(
            id = "assistant",
            name = "Test Assistant",
            model = ModelRef(ProviderId("test-provider"), "test-model"),
            systemPrompt = "You are a test assistant.",
            tools = listOf("get_weather"),
        )

        val toolRegistry = ToolRegistry(listOf(weatherTool))
        val memory = InMemoryMemoryStore()
        val runtime = AgentRuntime(
            providers = mapOf(ProviderId("test-provider") to fakeProvider),
            agentRegistry = AgentRegistry(listOf(agent)),
            toolRegistry = toolRegistry,
            memoryStore = memory,
            toolExecutor = ToolExecutor(toolRegistry),
        )

        val result = runtime.run("assistant", "What's the weather in Cairo?")
        assertEquals("The weather in Cairo is 25°C and sunny.", result.text)
        assertEquals(2, result.steps)

        val history = memory.load(result.conversationId)
        // User + Assistant (Tool Call) + Tool (Result) + Assistant (Final Text)
        assertEquals(4, history.size)
    }

    @Test
    fun stream_emits_ordered_events_to_completion() = runTest {
        val fakeProvider = FakeLlmProvider(
            id = ProviderId("test-provider"),
            responses = listOf(
                FakeResponse.Text("Streaming test passed."),
            )
        )

        val agent = AgentDefinition(
            id = "assistant",
            name = "Test Assistant",
            model = ModelRef(ProviderId("test-provider"), "test-model"),
            systemPrompt = "You are a test assistant.",
        )

        val runtime = AgentRuntime(
            providers = mapOf(ProviderId("test-provider") to fakeProvider),
            agentRegistry = AgentRegistry(listOf(agent)),
            toolRegistry = ToolRegistry(),
            memoryStore = InMemoryMemoryStore(),
            toolExecutor = ToolExecutor(ToolRegistry()),
        )

        runtime.stream("assistant", "Hello").test {
            assertTrue(awaitItem() is AgentEvent.RunStarted)
            assertTrue(awaitItem() is AgentEvent.AgentEntered)
            assertTrue(awaitItem() is AgentEvent.StepStarted)
            assertTrue(awaitItem() is AgentEvent.TextDelta)
            assertTrue(awaitItem() is AgentEvent.StepCompleted)
            val completed = awaitItem()
            assertTrue(completed is AgentEvent.RunCompleted)
            assertEquals("Streaming test passed.", completed.result.text)
            awaitComplete()
        }
    }

    @Test
    fun max_steps_exceeded_throws_exception() = runTest {
        // Enqueue 5 consecutive tool calls when maxSteps = 2
        val fakeProvider = FakeLlmProvider(
            id = ProviderId("test-provider"),
            responses = (1..5).map {
                FakeResponse.ToolCall(
                    name = "dummy",
                    arguments = buildJsonObject { },
                )
            }
        )

        val dummyTool = tool("dummy", "Dummy") {
            execute { _, _ -> ToolResult.Success(buildJsonObject { }) }
        }

        val agent = AgentDefinition(
            id = "looping_agent",
            name = "Looping Agent",
            model = ModelRef(ProviderId("test-provider"), "test-model"),
            systemPrompt = "Loop",
            tools = listOf("dummy"),
            limits = AgentLimits(maxSteps = 2),
        )

        val toolRegistry = ToolRegistry(listOf(dummyTool))
        val runtime = AgentRuntime(
            providers = mapOf(ProviderId("test-provider") to fakeProvider),
            agentRegistry = AgentRegistry(listOf(agent)),
            toolRegistry = toolRegistry,
            memoryStore = InMemoryMemoryStore(),
            toolExecutor = ToolExecutor(toolRegistry),
        )

        assertFailsWith<MaxStepsExceededException> {
            runtime.run("looping_agent", "start")
        }
    }
}
