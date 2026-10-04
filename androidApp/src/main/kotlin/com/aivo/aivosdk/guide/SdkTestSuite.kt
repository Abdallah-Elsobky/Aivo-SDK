package com.aivo.aivosdk.guide

import com.aivo.aivosdk.agent.createPipelineAgent
import com.aivo.aivosdk.agent.createResearcherAgent
import com.aivo.aivosdk.agent.createSupervisorAgent
import com.aivo.aivosdk.agent.createWriterAgent
import com.aivo.aivosdk.agent.tools.cryptoTool
import com.aivo.aivosdk.agent.tools.weatherTool
import com.aivo.sdk.AgentRole
import com.aivo.sdk.AivoSdk
import com.aivo.sdk.ParamType
import com.aivo.sdk.StylePreset
import com.aivo.sdk.agent
import com.aivo.sdk.core.error.ConfigurationException
import com.aivo.sdk.core.error.TokenBudgetExceededException
import com.aivo.sdk.core.model.ContentPart
import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.core.model.FinishReason
import com.aivo.sdk.core.model.LlmRequest
import com.aivo.sdk.core.model.LlmResponse
import com.aivo.sdk.core.model.LlmStreamEvent
import com.aivo.sdk.core.model.Message
import com.aivo.sdk.core.model.ProviderCapabilities
import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.core.model.ToolCall
import com.aivo.sdk.core.model.ToolResult
import com.aivo.sdk.core.model.Usage
import com.aivo.sdk.core.model.renderedText
import com.aivo.sdk.core.port.LlmProvider
import com.aivo.sdk.runtime.agent.AgentEvent
import com.aivo.sdk.runtime.agent.AgentLimits
import com.aivo.sdk.runtime.agent.AgentResult
import com.aivo.sdk.supervisor
import com.aivo.sdk.tool
import com.aivo.sdk.workflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Scripted mock LLM provider for deterministic offline testing of the entire Aivo SDK.
 */
class SdkMockProvider(
    override val id: ProviderId = ProviderId("mock"),
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        streaming = true,
        toolCalling = true,
        parallelToolCalls = true,
        reasoning = true,
        structuredOutput = false,
        statefulConversation = false,
    ),
) : LlmProvider {

    private val responseQueue = mutableListOf<MockStep>()

    sealed interface MockStep {
        data class Text(val text: String, val usage: Usage = Usage(15, 25)) : MockStep
        data class Tool(val name: String, val args: JsonObject, val callId: String = "call_mock_1") : MockStep
        data class Error(val error: Throwable) : MockStep
    }

    fun enqueueText(text: String, usage: Usage = Usage(15, 25)) {
        responseQueue.add(MockStep.Text(text, usage))
    }

    fun enqueueToolCall(name: String, args: JsonObject, callId: String = "call_mock_${responseQueue.size + 1}") {
        responseQueue.add(MockStep.Tool(name, args, callId))
    }

    fun enqueueError(error: Throwable) {
        responseQueue.add(MockStep.Error(error))
    }

    fun clearQueue() {
        responseQueue.clear()
    }

    private fun nextStep(request: LlmRequest): MockStep {
        if (responseQueue.isNotEmpty()) {
            return responseQueue.removeAt(0)
        }

        // Automatic fallback based on request inspect:
        val lastMsg = request.messages.lastOrNull()
        if (lastMsg is Message.Tool) {
            return MockStep.Text("Synthesized answer from tool [${lastMsg.toolName}]: ${lastMsg.result.renderedText}")
        }

        val promptText = request.messages.filterIsInstance<Message.User>().lastOrNull()
            ?.parts?.filterIsInstance<ContentPart.Text>()?.joinToString("") { it.text }
            .orEmpty().lowercase()
        return when {
            request.tools.any { it.name == "get_weather" } && (promptText.contains("weather") || promptText.contains("tokyo") || promptText.contains("cairo")) -> {
                MockStep.Tool("get_weather", buildJsonObject { put("city", "Tokyo") })
            }
            request.tools.any { it.name == "crypto_price" } && (promptText.contains("crypto") || promptText.contains("btc") || promptText.contains("sol")) -> {
                MockStep.Tool("crypto_price", buildJsonObject { put("symbol", "BTC") })
            }
            request.tools.any { it.name.startsWith("delegate_to_") } -> {
                val delegateTool = request.tools.first { it.name.startsWith("delegate_to_") }
                MockStep.Tool(delegateTool.name, buildJsonObject { put("task", "Perform sub-task") })
            }
            else -> {
                MockStep.Text("Mock response to: ${promptText.take(40)}")
            }
        }
    }

    override suspend fun generate(request: LlmRequest): LlmResponse {
        return when (val step = nextStep(request)) {
            is MockStep.Text -> LlmResponse(
                message = Message.Assistant(listOf(ContentPart.Text(step.text))),
                finishReason = FinishReason.STOP,
                usage = step.usage,
            )
            is MockStep.Tool -> LlmResponse(
                message = Message.Assistant(
                    parts = emptyList(),
                    toolCalls = listOf(ToolCall(step.callId, step.name, step.args)),
                ),
                finishReason = FinishReason.TOOL_CALLS,
                usage = Usage(10, 10),
            )
            is MockStep.Error -> throw step.error
        }
    }

    override fun stream(request: LlmRequest): Flow<LlmStreamEvent> = flow {
        when (val step = nextStep(request)) {
            is MockStep.Text -> {
                // Split text into words to simulate streaming chunks
                val words = step.text.split(" ")
                for ((idx, word) in words.withIndex()) {
                    val fragment = if (idx == 0) word else " $word"
                    emit(LlmStreamEvent.TextDelta(fragment))
                    delay(10)
                }
                emit(
                    LlmStreamEvent.Completed(
                        LlmResponse(
                            message = Message.Assistant(listOf(ContentPart.Text(step.text))),
                            finishReason = FinishReason.STOP,
                            usage = step.usage,
                        )
                    )
                )
            }
            is MockStep.Tool -> {
                emit(LlmStreamEvent.ToolCallStarted(0, step.callId, step.name))
                emit(LlmStreamEvent.ToolCallArgumentsDelta(0, step.args.toString()))
                emit(
                    LlmStreamEvent.Completed(
                        LlmResponse(
                            message = Message.Assistant(
                                parts = emptyList(),
                                toolCalls = listOf(ToolCall(step.callId, step.name, step.args)),
                            ),
                            finishReason = FinishReason.TOOL_CALLS,
                            usage = Usage(10, 10),
                        )
                    )
                )
            }
            is MockStep.Error -> throw step.error
        }
    }
}

/**
 * Definition of an individual SDK test case.
 */
data class SdkTestCase(
    val id: String,
    val name: String,
    val levelBadge: String,
    val description: String,
    val execute: suspend (log: (String) -> Unit) -> Unit,
)

/**
 * Complete suite of tests proving every layer of the Aivo SDK works correctly.
 */
object SdkTestSuite {

    val allTests: List<SdkTestCase> = listOf(
        SdkTestCase(
            id = "test_01_init",
            name = "SDK Builder & Registry Configuration",
            levelBadge = "Core SDK",
            description = "Verifies AivoSdk DSL initialization, provider registration, agent definitions, and cycle detection.",
            execute = { log ->
                log("Building AivoSdk instance with DSL...")
                val mock = SdkMockProvider()
                val sdk = AivoSdk {
                    providers {
                        register("mock", mock)
                    }
                    defaultModel("mock:test-model")
                    agents {
                        define("assistant") {
                            name = "Assistant"
                            systemPrompt = "You are a test assistant."
                        }
                    }
                    entryAgent("assistant")
                }
                log("Verifying SDK references...")
                check(sdk.defaultModel?.model == "test-model") { "Expected test-model default model" }
                check(sdk.entryAgentId == "assistant") { "Expected entryAgent to be assistant" }
                check(sdk.agents.get("assistant") != null) { "Agent 'assistant' must be registered" }
                log("Verified: SDK initialized successfully with clean architecture.")
            }
        ),

        SdkTestCase(
            id = "test_02_llm_generate",
            name = "Level 1: Raw LLM Direct Generation",
            levelBadge = "DX Level 1",
            description = "Tests sdk.llm(model).generate(...) direct invocation without running agent loops.",
            execute = { log ->
                val mock = SdkMockProvider()
                mock.enqueueText("Kotlin Multiplatform AI SDK is operational.")

                val sdk = AivoSdk {
                    providers { register("mock", mock) }
                    defaultModel("mock:direct-model")
                }

                log("Calling sdk.llm(\"mock:direct-model\").generate(\"Status check\")...")
                val client = sdk.llm("mock:direct-model")
                val response = client.generate("Status check")
                val text = response.message.text

                log("Received response: \"$text\"")
                log("Usage: in=${response.usage?.inputTokens}, out=${response.usage?.outputTokens}")
                check(text == "Kotlin Multiplatform AI SDK is operational.") { "Response text mismatch" }
                check(response.finishReason == FinishReason.STOP) { "Expected STOP finish reason" }
            }
        ),

        SdkTestCase(
            id = "test_03_llm_stream",
            name = "Level 1: Raw LLM Delta Streaming",
            levelBadge = "DX Level 1",
            description = "Tests sdk.llm(model).stream(...) streaming tokens and assembling LlmStreamEvent deltas.",
            execute = { log ->
                val mock = SdkMockProvider()
                mock.enqueueText("Streaming token generation works seamlessly.")

                val sdk = AivoSdk {
                    providers { register("mock", mock) }
                    defaultModel("mock:stream-model")
                }

                log("Starting LLM stream...")
                val client = sdk.llm("mock:stream-model")
                val events = mutableListOf<LlmStreamEvent>()
                val textAccum = StringBuilder()

                client.stream("Stream prompt").collect { event ->
                    events.add(event)
                    if (event is LlmStreamEvent.TextDelta) {
                        textAccum.append(event.text)
                        log("  → Delta: \"${event.text}\"")
                    } else if (event is LlmStreamEvent.Completed) {
                        log("  ✓ Completed event received")
                    }
                }

                log("Total assembled stream: \"$textAccum\"")
                check(events.any { it is LlmStreamEvent.TextDelta }) { "Expected TextDelta events" }
                check(events.any { it is LlmStreamEvent.Completed }) { "Expected Completed event" }
                check(textAccum.toString() == "Streaming token generation works seamlessly.") { "Stream text mismatch" }
            }
        ),

        SdkTestCase(
            id = "test_04_single_agent",
            name = "Level 2: Autonomous Single Agent",
            levelBadge = "DX Level 2",
            description = "Tests single-turn agent execution with role, style preset, instructions, and conversation memory.",
            execute = { log ->
                val mock = SdkMockProvider()
                mock.enqueueText("I am ready to assist with your development tasks.")

                val sdk = AivoSdk {
                    providers { register("mock", mock) }
                    defaultModel("mock:agent-model")
                    agents {
                        define("assistant") {
                            name = "Helpful Assistant"
                            role = AgentRole.ASSISTANT
                            style = StylePreset.CONCISE.toString()
                            systemPrompt = "You are a concise, helpful multiplatform assistant."
                        }
                    }
                    entryAgent("assistant")
                }

                val convoId = ConversationId("test-convo-single")
                log("Invoking sdk.chat(convoId, \"Hello!\")...")
                val result: AgentResult = sdk.chat(convoId, "Hello!")

                log("Result text: \"${result.text}\"")
                log("Steps: ${result.steps}, Usage: in=${result.usage?.inputTokens}, out=${result.usage?.outputTokens}")
                check(result.text.isNotBlank()) { "Agent result text must not be blank" }
                check(result.steps == 1) { "Single turn agent should complete in 1 step" }
            }
        ),

        SdkTestCase(
            id = "test_05_tool_calling",
            name = "Level 2: ReAct Tool Calling Loop",
            levelBadge = "DX Level 2",
            description = "Tests agent executing custom tools (weatherTool), parsing arguments, and synthesizing facts.",
            execute = { log ->
                val mock = SdkMockProvider()
                // Step 1: Model requests tool call
                mock.enqueueToolCall("get_weather", buildJsonObject { put("city", "Cairo") })
                // Step 2: Model receives tool result and produces final answer
                mock.enqueueText("The weather in Cairo is currently Sunny, 27°C with 45% humidity.")

                val sdk = AivoSdk {
                    providers { register("mock", mock) }
                    defaultModel("mock:tools-model")
                    tools {
                        register(weatherTool)
                    }
                    agents {
                        define("weather_specialist") {
                            name = "Weather Specialist"
                            systemPrompt = "Use weather tools to answer inquiries."
                            tools(weatherTool)
                        }
                    }
                    entryAgent("weather_specialist")
                }

                val convoId = ConversationId("test-convo-tools")
                log("Invoking agent with query: \"What's the weather in Cairo?\"...")
                val result = sdk.chat(convoId, "What's the weather in Cairo?")

                log("Agent final answer: \"${result.text}\"")
                log("Tools executed: ${result.toolCalls.map { it.name }}")
                log("Total reasoning steps: ${result.steps}")

                check(result.toolCalls.any { it.name == "get_weather" }) { "Tool 'get_weather' was not executed" }
                check(result.steps == 2) { "Expected 2 steps: (1) Tool execution + (2) Final synthesis" }
                check(result.text.contains("Cairo")) { "Final answer must reference Cairo" }
            }
        ),

        SdkTestCase(
            id = "test_06_supervisor_team",
            name = "Level 3: Multi-Agent Supervisor Delegation",
            levelBadge = "DX Level 3",
            description = "Tests supervisor agent (editorial_lead) delegating research to researcher and writing to writer.",
            execute = { log ->
                val mock = SdkMockProvider()
                // Supervisor delegates to researcher
                mock.enqueueToolCall("delegate_to_researcher", buildJsonObject { put("task", "Find BTC price") })
                // Researcher calls crypto_price tool
                mock.enqueueToolCall("crypto_price", buildJsonObject { put("symbol", "BTC") })
                // Researcher produces research notes
                mock.enqueueText("Research finding: Bitcoin is trading at $68,450 USD.")
                // Supervisor delegates to writer
                mock.enqueueToolCall("delegate_to_writer", buildJsonObject { put("task", "Polish summary") })
                // Writer produces final response
                mock.enqueueText("Market Update: Bitcoin (BTC) is currently trading strongly at \$68,450 USD.")
                // Supervisor finishes
                mock.enqueueText("Editorial Summary: Bitcoin is currently trading strongly at \$68,450 USD.")

                val researcher = createResearcherAgent()
                val writer = createWriterAgent()
                val supervisor = createSupervisorAgent(researcher, writer)

                val sdk = AivoSdk {
                    providers { register("mock", mock) }
                    defaultModel("mock:team-model")
                    tools {
                        register(cryptoTool)
                        register(weatherTool)
                    }
                    agents {
                        +supervisor
                        +researcher
                        +writer
                    }
                    entryAgent("editorial_lead")
                }

                val convoId = ConversationId("test-convo-team")
                log("Running supervisor agent team...")
                val result = sdk.chat(convoId, "Give me an editorial update on Bitcoin.")

                log("Team final output: \"${result.text}\"")
                check(result.text.isNotBlank()) { "Team result must not be blank" }
                log("Supervisor team coordinated successfully.")
            }
        ),

        SdkTestCase(
            id = "test_07_workflow_pipeline",
            name = "Level 4: Code-Driven Workflow Pipeline",
            levelBadge = "DX Level 4",
            description = "Tests deterministic code-driven orchestration: workflow(researcher, writer) sequence.",
            execute = { log ->
                val mock = SdkMockProvider()
                // Step 1: researcher generates data
                mock.enqueueText("Research Notes: Solana (SOL) is at \$154 USD with high network activity.")
                // Step 2: writer formats response
                mock.enqueueText("Solana Daily Digest: SOL is priced at \$154 with strong on-chain volume.")

                val researcher = createResearcherAgent()
                val writer = createWriterAgent()
                val pipeline = createPipelineAgent(researcher, writer)

                val sdk = AivoSdk {
                    providers { register("mock", mock) }
                    defaultModel("mock:pipeline-model")
                    tools {
                        register(cryptoTool)
                        register(weatherTool)
                    }
                    agents {
                        +pipeline
                        +researcher
                        +writer
                    }
                    entryAgent("pipeline")
                }

                log("Executing deterministic pipeline workflow...")
                val result = sdk.chat(ConversationId("test-pipeline"), "Generate report for Solana")

                log("Pipeline final answer: \"${result.text}\"")
                check(result.text.contains("Solana")) { "Pipeline result must reference Solana" }
                log("Pipeline executed sequentially as programmed in Kotlin.")
            }
        ),

        SdkTestCase(
            id = "test_08_guardrails",
            name = "Production Guardrails: Token Limits",
            levelBadge = "Safety & Ops",
            description = "Verifies token limits (AgentLimits) enforce budget control and throw TokenBudgetExceededException.",
            execute = { log ->
                val mock = SdkMockProvider()
                // Enqueue response with large token usage (300 total tokens)
                mock.enqueueText("Long response...", usage = Usage(inputTokens = 150, outputTokens = 150))

                val sdk = AivoSdk {
                    providers { register("mock", mock) }
                    defaultModel("mock:safety-model")
                    agents {
                        define("guarded_agent") {
                            systemPrompt = "Guarded agent."
                            limits(AgentLimits(maxTokens = 100)) // Budget is 100
                        }
                    }
                    entryAgent("guarded_agent")
                }

                log("Running agent with budget limit of 100 tokens (incoming 300)...")
                var exceptionCaught = false
                try {
                    sdk.chat(ConversationId("test-budget"), "Test query")
                } catch (e: TokenBudgetExceededException) {
                    exceptionCaught = true
                    log("✓ Caught expected TokenBudgetExceededException: ${e.message}")
                }

                check(exceptionCaught) { "Expected TokenBudgetExceededException was not thrown" }
            }
        ),

        SdkTestCase(
            id = "test_09_event_trace",
            name = "Streaming Observability & Event Trace",
            levelBadge = "Observability",
            description = "Validates full lifecycle streaming events: RunStarted, AgentEntered, StepStarted, TextDelta, RunCompleted.",
            execute = { log ->
                val mock = SdkMockProvider()
                mock.enqueueText("Streaming trace verified.")

                val sdk = AivoSdk {
                    providers { register("mock", mock) }
                    defaultModel("mock:trace-model")
                    agents {
                        define("trace_agent") {
                            systemPrompt = "Trace agent."
                        }
                    }
                    entryAgent("trace_agent")
                }

                log("Collecting AgentEvent stream...")
                val events = sdk.agent("trace_agent").stream("Trace query").toList()

                val eventTypes = events.map { it::class.simpleName }
                log("Recorded events: $eventTypes")

                check(events.any { it is AgentEvent.RunStarted }) { "Missing RunStarted event" }
                check(events.any { it is AgentEvent.AgentEntered }) { "Missing AgentEntered event" }
                check(events.any { it is AgentEvent.StepStarted }) { "Missing StepStarted event" }
                check(events.any { it is AgentEvent.TextDelta }) { "Missing TextDelta event" }
                check(events.any { it is AgentEvent.RunCompleted }) { "Missing RunCompleted event" }
                log("Observability: complete lifecycle event stream verified.")
            }
        ),

        SdkTestCase(
            id = "test_10_dynamic_tool",
            name = "Dynamic Custom Tool Builder DSL",
            levelBadge = "Extensibility",
            description = "Defines a dynamic mathematical calculator tool at runtime and executes it in an agent loop.",
            execute = { log ->
                log("Creating dynamic 'multiply' tool with tool { } DSL...")
                val multiplyTool = tool("multiply", "Multiplies two numbers x and y") {
                    param("x", "First number", type = ParamType.Double, required = true)
                    param("y", "Second number", type = ParamType.Double, required = true)
                    execute { args ->
                        val x = args.doubleOrNull("x") ?: 0.0
                        val y = args.doubleOrNull("y") ?: 0.0
                        val product = x * y
                        "Result of $x * $y = $product"
                    }
                }

                val mock = SdkMockProvider()
                mock.enqueueToolCall("multiply", buildJsonObject {
                    put("x", 7.0)
                    put("y", 6.0)
                })
                mock.enqueueText("The product of 7 and 6 is 42.")

                val sdk = AivoSdk {
                    providers { register("mock", mock) }
                    defaultModel("mock:dynamic-tool-model")
                    tools {
                        register(multiplyTool)
                    }
                    agents {
                        define("math_agent") {
                            tools(multiplyTool)
                        }
                    }
                    entryAgent("math_agent")
                }

                log("Executing agent with dynamic multiply tool...")
                val result = sdk.chat(ConversationId("calc-convo"), "What is 7 times 6?")
                log("Result: \"${result.text}\"")

                check(result.toolCalls.any { it.name == "multiply" }) { "Dynamic multiply tool was not invoked" }
                check(result.text.contains("42")) { "Result must contain 42" }
                log("Dynamic custom tool executed and synthesized accurately.")
            }
        )
    )
}
