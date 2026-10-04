package com.aivo.sdk.runtime

import com.aivo.sdk.core.error.DelegationCycleException
import com.aivo.sdk.core.error.MaxDelegationDepthExceededException
import com.aivo.sdk.core.model.ModelRef
import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.core.model.Usage
import com.aivo.sdk.runtime.agent.AgentDefinition
import com.aivo.sdk.runtime.agent.AgentLimits
import com.aivo.sdk.runtime.agent.AgentRegistry
import com.aivo.sdk.runtime.agent.AgentRuntime
import com.aivo.sdk.runtime.memory.InMemoryMemoryStore
import com.aivo.sdk.runtime.tool.ToolExecutor
import com.aivo.sdk.runtime.tool.ToolRegistry
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

class DelegationTest {

    @Test
    fun supervisor_delegates_to_child_and_aggregates_usage() = runTest {
        val fakeProvider = FakeLlmProvider(
            id = ProviderId("test-provider"),
            responses = listOf(
                // 1. Supervisor decides to delegate
                FakeResponse.ToolCall(
                    name = "delegate_to_specialist",
                    arguments = buildJsonObject {
                        put("task", "Find user profile")
                    },
                    id = "call_del_1"
                ),
                // 2. Specialist responds
                FakeResponse.Text(
                    text = "Profile for user: John Doe, Tier: Premium.",
                    usage = Usage(inputTokens = 20, outputTokens = 15)
                ),
                // 3. Supervisor gives final answer
                FakeResponse.Text(
                    text = "Here is the profile: John Doe, Premium.",
                    usage = Usage(inputTokens = 30, outputTokens = 10)
                ),
            )
        )

        val specialist = AgentDefinition(
            id = "specialist",
            name = "Specialist Agent",
            model = ModelRef(ProviderId("test-provider"), "test-model"),
            systemPrompt = "You are a specialist.",
        )

        val supervisor = AgentDefinition(
            id = "supervisor",
            name = "Supervisor Agent",
            model = ModelRef(ProviderId("test-provider"), "test-model"),
            systemPrompt = "You are the supervisor.",
            subAgents = listOf("specialist"),
        )

        val runtime = AgentRuntime(
            providers = mapOf(ProviderId("test-provider") to fakeProvider),
            agentRegistry = AgentRegistry(listOf(supervisor, specialist)),
            toolRegistry = ToolRegistry(),
            memoryStore = InMemoryMemoryStore(),
            toolExecutor = ToolExecutor(ToolRegistry()),
        )

        val result = runtime.run("supervisor", "Get profile")
        assertEquals("Here is the profile: John Doe, Premium.", result.text)

        // Usage aggregated from child + parent:
        // Child: 20 in, 15 out. Parent: 30 in, 10 out + step 1 usage (15 in, 15 out)
        assertNotNull(result.usage)
        assertTrue(result.usage!!.inputTokens!! >= 50)
        assertTrue(result.usage!!.outputTokens!! >= 25)
    }

    @Test
    fun delegation_cycle_detected_and_throws() = runTest {
        // Agent A delegates to Agent B, Agent B delegates to Agent A
        val fakeProvider = FakeLlmProvider(
            id = ProviderId("test-provider"),
            responses = listOf(
                FakeResponse.ToolCall(name = "delegate_to_agent_b", arguments = buildJsonObject { put("task", "Go to B") }),
                FakeResponse.ToolCall(name = "delegate_to_agent_a", arguments = buildJsonObject { put("task", "Go back to A") }),
            )
        )

        val agentA = AgentDefinition(
            id = "agent_a",
            name = "Agent A",
            model = ModelRef(ProviderId("test-provider"), "test-model"),
            systemPrompt = "Agent A",
            subAgents = listOf("agent_b"),
        )

        val agentB = AgentDefinition(
            id = "agent_b",
            name = "Agent B",
            model = ModelRef(ProviderId("test-provider"), "test-model"),
            systemPrompt = "Agent B",
            subAgents = listOf("agent_a"),
        )

        val runtime = AgentRuntime(
            providers = mapOf(ProviderId("test-provider") to fakeProvider),
            agentRegistry = AgentRegistry(listOf(agentA, agentB)),
            toolRegistry = ToolRegistry(),
            memoryStore = InMemoryMemoryStore(),
            toolExecutor = ToolExecutor(ToolRegistry()),
        )

        assertFailsWith<DelegationCycleException> {
            runtime.run("agent_a", "Start cycle")
        }
    }

    @Test
    fun max_delegation_depth_exceeded_throws() = runTest {
        val fakeProvider = FakeLlmProvider(
            id = ProviderId("test-provider"),
            responses = listOf(
                FakeResponse.ToolCall(name = "delegate_to_child1", arguments = buildJsonObject { put("task", "t1") }),
                FakeResponse.ToolCall(name = "delegate_to_child2", arguments = buildJsonObject { put("task", "t2") }),
            )
        )

        val root = AgentDefinition(
            id = "root",
            name = "Root",
            model = ModelRef(ProviderId("test-provider"), "test-model"),
            systemPrompt = "Root",
            subAgents = listOf("child1"),
            limits = AgentLimits(maxDelegationDepth = 1),
        )

        val child1 = AgentDefinition(
            id = "child1",
            name = "Child 1",
            model = ModelRef(ProviderId("test-provider"), "test-model"),
            systemPrompt = "Child 1",
            subAgents = listOf("child2"),
            limits = AgentLimits(maxDelegationDepth = 1),
        )

        val child2 = AgentDefinition(
            id = "child2",
            name = "Child 2",
            model = ModelRef(ProviderId("test-provider"), "test-model"),
            systemPrompt = "Child 2",
        )

        val runtime = AgentRuntime(
            providers = mapOf(ProviderId("test-provider") to fakeProvider),
            agentRegistry = AgentRegistry(listOf(root, child1, child2)),
            toolRegistry = ToolRegistry(),
            memoryStore = InMemoryMemoryStore(),
            toolExecutor = ToolExecutor(ToolRegistry()),
        )

        assertFailsWith<MaxDelegationDepthExceededException> {
            runtime.run("root", "Run beyond max depth")
        }
    }
}
