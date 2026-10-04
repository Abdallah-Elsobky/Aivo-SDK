package com.aivo.sdk

import com.aivo.sdk.core.error.ConfigurationException
import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.core.model.ToolResult
import com.aivo.sdk.runtime.agent.AgentEvent
import com.aivo.sdk.runtime.tool.tool
import com.aivo.sdk.testing.FakeLlmProvider
import com.aivo.sdk.testing.FakeResponse
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AivoSdkBuilderTest {

    @Test
    fun level1_dx_raw_llm_client() = runTest {
        val fake = FakeLlmProvider(
            responses = listOf(
                FakeResponse.Text("Hello from Level 1!"),
            )
        )

        val ai = AivoSdk {
            providers {
                register("ollama", fake)
            }
            defaultModel("ollama:gemma4:31b")
        }

        val client = ai.llm("ollama:gemma4:31b")
        assertNotNull(client)

        val response = client.generate("Say hello")
        val text = response.message.parts.filterIsInstance<com.aivo.sdk.core.model.ContentPart.Text>().joinToString("") { it.text }
        assertEquals("Hello from Level 1!", text)
    }

    @Test
    fun level2_dx_single_agent_with_tool() = runTest {
        val fake = FakeLlmProvider(
            responses = listOf(
                FakeResponse.ToolCall("get_balance", buildJsonObject { put("accountId", "101") }),
                FakeResponse.Text("Your balance is $500."),
            )
        )

        val balanceTool = tool("get_balance", "Returns balance") {
            parameters { string("accountId", required = true) }
            execute { _, _ -> ToolResult.Success(buildJsonObject { put("balance", 500) }) }
        }

        val ai = AivoSdk {
            providers { register("openai", fake) }
            defaultModel("openai:gpt-4o")

            tools { register(balanceTool) }

            agents {
                define("assistant") {
                    name = "Assistant"
                    systemPrompt = "You are a banking assistant."
                    tools("get_balance")
                }
            }

            entryAgent("assistant")
        }

        val result = ai.chat(ConversationId("c1"), "What is my balance?")
        assertEquals("Your balance is $500.", result.text)
        assertEquals(2, result.steps)
    }

    @Test
    fun level3_dx_streaming_events() = runTest {
        val fake = FakeLlmProvider(
            responses = listOf(
                FakeResponse.Text("Stream completed."),
            )
        )

        val ai = AivoSdk {
            providers { register("ollama", fake) }
            defaultModel("ollama:gemma4:31b")
            agents {
                define("assistant") {
                    systemPrompt = "You stream answers."
                }
            }
            entryAgent("assistant")
        }

        val events = ai.stream(ConversationId("c2"), "Stream to me").toList()
        assertTrue(events.any { it is AgentEvent.RunStarted })
        assertTrue(events.any { it is AgentEvent.TextDelta })
        assertTrue(events.any { it is AgentEvent.RunCompleted })
    }

    @Test
    fun builder_validates_and_reports_all_problems() {
        val ex = assertFailsWith<ConfigurationException> {
            AivoSdk {
                // missing defaultModel, missing provider, missing tool, missing entry agent
                agents {
                    define("agent_1") {
                        systemPrompt = "Prompt"
                        tools("non_existent_tool")
                        delegates("non_existent_delegate")
                    }
                }
                entryAgent("unknown_entry_agent")
            }
        }

        assertTrue(ex.problems.any { it.contains("unknown_entry_agent") })
        assertTrue(ex.problems.any { it.contains("non_existent_tool") })
        assertTrue(ex.problems.any { it.contains("non_existent_delegate") })
    }

    @Test
    fun registering_same_tool_instance_twice_is_idempotent() {
        val fake = FakeLlmProvider()
        val ai = AivoSdk {
            providers { register("fake", fake) }
            defaultModel("fake:model")
            tools {
                // Registering the exact same tool directly or via register(tool(...))
                register(
                    tool("check_status", "Checks status") {
                        execute { _, _ -> ToolResult.Success(buildJsonObject { put("status", "OK") }) }
                    }
                )
            }
            agents {
                define("assistant") {
                    systemPrompt = "test"
                    tools("check_status")
                }
            }
        }
        assertNotNull(ai)
    }

    @Test
    fun registering_distinct_tools_with_same_name_fails() {
        val fake = FakeLlmProvider()
        val ex = assertFailsWith<ConfigurationException> {
            AivoSdk {
                providers { register("fake", fake) }
                defaultModel("fake:model")
                tools {
                    register(tool("duplicate_tool") { execute { _, _ -> ToolResult.Success(buildJsonObject {}) } })
                    register(tool("duplicate_tool") { execute { _, _ -> ToolResult.Success(buildJsonObject {}) } })
                }
                agents {
                    define("assistant") {
                        systemPrompt = "test"
                        tools("duplicate_tool")
                    }
                }
            }
        }
        assertTrue(ex.problems.any { it.contains("Duplicate tool name: 'duplicate_tool'") })
    }
}
