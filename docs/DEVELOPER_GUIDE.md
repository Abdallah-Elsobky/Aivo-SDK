# Aivo SDK — Developer Guide

> Complete reference for integrating the Aivo SDK into your Kotlin Multiplatform project.

---

## Table of Contents

1. [Installation](#1-installation)
2. [Security — API Key Best Practices](#2-security--api-key-best-practices)
3. [Quick Start — 1 Minute Setup](#3-quick-start--1-minute-setup)
4. [Level 1 — Raw LLM Client](#4-level-1--raw-llm-client)
5. [Level 2 — Single Agent with Tools](#5-level-2--single-agent-with-tools)
6. [Level 3 — Multi-Agent Supervisor](#6-level-3--multi-agent-supervisor)
7. [Level 4 — Custom Reasoning Loops](#7-level-4--custom-reasoning-loops)
8. [Provider Configuration](#8-provider-configuration)
9. [Tool System](#9-tool-system)
10. [Agent Definitions](#10-agent-definitions)
11. [Declarative Agents (Markdown & JSON)](#11-declarative-agents-markdown--json)
12. [Memory & Context Window Strategies](#12-memory--context-window-strategies)
13. [Streaming Events](#13-streaming-events)
14. [Security & Confirmation Gates](#14-security--confirmation-gates)
15. [Resilience — Retries & Timeouts](#15-resilience--retries--timeouts)
16. [Observability — Logging & Telemetry](#16-observability--logging--telemetry)
17. [Testing with FakeLlmProvider](#17-testing-with-fakellmprovider)
18. [Android Integration](#18-android-integration)
19. [iOS Integration (Kotlin/Native)](#19-ios-integration-kotlinnative)

---

## 1. Installation

### Gradle (Kotlin DSL)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}
```

**Android / JVM:**
```kotlin
// build.gradle.kts
dependencies {
    implementation("com.aivo:aivo-sdk:1.0.0")
}
```

**Kotlin Multiplatform:**
```kotlin
// build.gradle.kts
kotlin {
    androidTarget()
    iosArm64()
    iosSimulatorArm64()
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation("com.aivo:aivo-sdk:1.0.0")
        }
    }
}
```

---

## 2. Security — API Key Best Practices

> ⚠️ **CRITICAL:** Never put API keys directly in source code or checked-in config files.

### JVM / Server

```kotlin
// ✅ CORRECT: Read from environment variables
val geminiKey      = System.getenv("GEMINI_API_KEY")      ?: error("Set GEMINI_API_KEY")
val openRouterKey  = System.getenv("OPENROUTER_API_KEY")  ?: error("Set OPENROUTER_API_KEY")

// ❌ WRONG: Never do this
val key = "AIzaSy..."  // Hardcoded — DO NOT COMMIT
```

### Android — `local.properties` Pattern

```properties
# local.properties  <-- this file is already in .gitignore
GEMINI_API_KEY=AIzaSy...
OPENROUTER_API_KEY=sk-or-...
```

```kotlin
// build.gradle.kts (android block)
android {
    buildFeatures { buildConfig = true }

    defaultConfig {
        // Read keys from local.properties at build time
        val localProps = java.util.Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.let(::load)
        }
        buildConfigField("String", "GEMINI_API_KEY",
            "\"${localProps.getProperty("GEMINI_API_KEY", "")}\"")
        buildConfigField("String", "OPENROUTER_API_KEY",
            "\"${localProps.getProperty("OPENROUTER_API_KEY", "")}\"")
    }
}
```

```kotlin
// In your app code
val sdk = AivoSdk {
    providers {
        gemini("gemini") { apiKey(BuildConfig.GEMINI_API_KEY) }
    }
}
```

### Dynamic (Runtime) Credentials

If your users supply their own API keys:

```kotlin
val sdk = AivoSdk {
    providers {
        gemini("gemini") {
            // Supply credentials lazily from a secure store
            credentials {
                SecretString(userCredentialStore.getKey("gemini"))
            }
        }
    }
}
```

---

## 3. Quick Start — 1 Minute Setup

Use `AivoSdk.create(...)` for the fastest path to a working chat session:

```kotlin
import com.aivo.sdk.AivoSdk
import com.aivo.sdk.AivoProvider
import com.aivo.sdk.core.model.ConversationId

// Single-line SDK setup
val sdk = AivoSdk.create(
    provider     = AivoProvider.GEMINI,
    model        = "gemini-2.0-flash",
    apiKey       = System.getenv("GEMINI_API_KEY"),
    systemPrompt = "You are a helpful assistant.",
)

// Chat (suspending)
val result = sdk.chat(
    conversationId = ConversationId("my-session"),
    message        = "Explain quantum computing in one sentence.",
)
println(result.text)
```

> `AivoSdk.create` creates a single assistant agent with the given system prompt.
> For more control (custom tools, multi-agent, streaming), use the full builder DSL.

---

## 4. Level 1 — Raw LLM Client

Access LLM capabilities directly without an agent loop:

```kotlin
val sdk = AivoSdk {
    providers {
        ollama("ollama") { baseUrl("http://localhost:11434") }
    }
    defaultModel("ollama:llama3.2")
}

// Single generate call
val response = sdk.llm("ollama:llama3.2").generate("What is 2 + 2?")
println(response.message.content())

// Generate with conversation history
val history = listOf(
    Message.System("You are a math tutor."),
    Message.User("What is 5 × 7?"),
    Message.Assistant("5 × 7 = 35"),
    Message.User("Add 3 to that."),
)
val r2 = sdk.llm("ollama:llama3.2").generate(history)
println(r2.message.content())  // "38"

// Streaming tokens
sdk.llm("ollama:llama3.2").stream("Write a haiku about Kotlin.").collect { event ->
    when (event) {
        is LlmStreamEvent.TextDelta -> print(event.text)
        is LlmStreamEvent.Done      -> println()
        else -> {}
    }
}
```

---

## 5. Level 2 — Single Agent with Tools

Define an agent with a system prompt and custom tools:

```kotlin
import com.aivo.sdk.*
import com.aivo.sdk.core.model.ConversationId

// 1. Define tools
val getWeatherTool = tool("get_weather", "Returns current weather for a city") {
    param("city", "City name", type = ParamType.String, required = true)
    execute { args ->
        val city = args.string("city")
        fetchWeather(city)  // your real API call
    }
}

// 2. Build the SDK
val sdk = AivoSdk {
    providers {
        openRouter("openrouter") {
            apiKey(System.getenv("OPENROUTER_API_KEY")!!)
        }
    }
    defaultModel("openrouter:deepseek/deepseek-r1-0528-qwen3-8b:free")

    tools { +getWeatherTool }

    agents {
        define("weather_assistant") {
            name         = "Weather Assistant"
            systemPrompt = "You answer weather questions using the get_weather tool."
            tools("get_weather")
        }
    }
    entryAgent("weather_assistant")
}

// 3. Chat
val result = sdk.chat(ConversationId("s1"), "What's the weather in Cairo?")
println(result.text)   // "Currently in Cairo: Sunny, 31°C"
println(result.steps)  // number of tool-call + LLM iterations
```

---

## 6. Level 3 — Multi-Agent Supervisor

Route requests through a supervisor that delegates to specialist agents:

```kotlin
val sdk = AivoSdk {
    providers {
        gemini("gemini") { apiKey(System.getenv("GEMINI_API_KEY")!!) }
    }
    defaultModel("gemini:gemini-2.0-flash")

    tools {
        register(getBalanceTool)
        register(transferMoneyTool)   // ToolRisk.HIGH — triggers confirmation
        register(getProductInfoTool)
    }

    agents {
        // Supervisor: routes to the right specialist
        define("supervisor") {
            systemPrompt = """
                You are the primary banking assistant.
                Delegate balance/transfer requests → 'payments'.
                Delegate product questions → 'customer_service'.
            """.trimIndent()
            delegates("payments", "customer_service")
        }

        // Payments specialist
        define("payments") {
            systemPrompt = "Execute financial operations securely."
            tools("get_balance", "transfer_money")
        }

        // Customer service specialist
        define("customer_service") {
            systemPrompt = "Answer questions about our banking products."
            tools("get_product_information")
        }
    }
    entryAgent("supervisor")

    // Human-in-the-loop confirmation for HIGH-risk tools
    security {
        confirmationHandler = { call, spec ->
            // Suspends until your UI resolves this — e.g. a dialog
            showConfirmationDialog("Approve ${spec.name}?", call.arguments)
        }
    }
}

// Streaming conversation
sdk.stream(ConversationId("s2"), "Transfer \$50 to Ahmed.").collect { event ->
    when (event) {
        is AgentEvent.AgentEntered    -> println("Agent: ${event.agentId}")
        is AgentEvent.TextDelta       -> print(event.text)
        is AgentEvent.ToolCallStarted -> println("\nTool: ${event.toolName}")
        is AgentEvent.RunCompleted    -> println("\nDone in ${event.result.steps} steps")
        is AgentEvent.RunFailed       -> println("\nError: ${event.error.message}")
        else -> {}
    }
}
```

---

## 7. Level 4 — Custom Reasoning Loops

For deterministic orchestration where you control execution order in code:

```kotlin
// Deterministic pipeline: research → write
val researchAgent = agent("researcher") {
    systemPrompt = "You gather facts and produce concise research notes."
}
val writerAgent = agent("writer") {
    systemPrompt = "You turn research notes into polished prose."
}

val pipeline = agent("pipeline") {
    loop = workflow(researchAgent, writerAgent) {
        val notes = run(researchAgent, input)
        val draft = run(writerAgent, "Write a response based on:\n$notes")
        finish(draft)
    }
}

val sdk = AivoSdk {
    providers { gemini("gemini") { apiKey(...) } }
    defaultModel("gemini:gemini-2.0-flash")
    agents {
        +pipeline
        // researchAgent and writerAgent are automatically collected
    }
    entryAgent("pipeline")
}
```

### Custom AgentLoop

Implement your own fully custom reasoning loop:

```kotlin
class MyCustomLoop : AgentLoop {
    override val name = "my_custom_loop"

    override suspend fun run(context: LoopContext): LoopOutcome {
        // Your custom orchestration logic here
        val response = context.llm.generate(context.messages)
        if (shouldRetry(response)) {
            return LoopOutcome.Continue(context.withNewMessage(response.message))
        }
        return LoopOutcome.Done(response.message.content())
    }
}

// Use in agent definition
val myAgent = agent("custom") {
    loop = MyCustomLoop()
}
```

---

## 8. Provider Configuration

### Ollama

```kotlin
providers {
    ollama("ollama") {
        baseUrl("http://localhost:11434")   // local Ollama
        // baseUrl("https://ollama.com")   // Ollama Cloud (requires apiKey)
        // apiKey("...")                   // only for Ollama Cloud
    }
}
defaultModel("ollama:llama3.2")
// Other models: "ollama:gemma2:9b", "ollama:qwen2.5:14b", "ollama:mistral"
```

### OpenRouter

```kotlin
providers {
    openRouter("openrouter") {
        apiKey(System.getenv("OPENROUTER_API_KEY")!!)
        // Optional headers for leaderboard ranking:
        // referer("https://yourapp.com")
        // title("Your App Name")
    }
}
defaultModel("openrouter:deepseek/deepseek-r1-0528-qwen3-8b:free")  // free tier
// Other models: "openrouter:anthropic/claude-3.5-sonnet", "openrouter:openai/gpt-4o"
```

### Google Gemini

```kotlin
providers {
    gemini("gemini") {
        apiKey(System.getenv("GEMINI_API_KEY")!!)
        // baseUrl(...)  // optional override
    }
}
defaultModel("gemini:gemini-2.0-flash")
// Other models: "gemini:gemini-2.5-pro", "gemini:gemini-1.5-flash"
```

### OpenAI-Compatible

```kotlin
providers {
    openAiCompatible("together") {
        baseUrl("https://api.together.xyz/v1")
        apiKey(System.getenv("TOGETHER_API_KEY")!!)
        // header("X-Custom-Header", "value")  // optional extra headers
    }
}
defaultModel("together:meta-llama/Llama-3-8b-chat-hf")
```

### Multiple Providers

```kotlin
val sdk = AivoSdk {
    providers {
        ollama("ollama") { baseUrl("http://localhost:11434") }
        gemini("gemini") { apiKey(System.getenv("GEMINI_API_KEY")!!) }
        openRouter("openrouter") { apiKey(System.getenv("OPENROUTER_API_KEY")!!) }
    }

    agents {
        // Each agent can target a different provider
        define("local") {
            model("ollama:llama3.2")
            systemPrompt = "You are a fast local agent."
        }
        define("advanced") {
            model("gemini:gemini-2.5-pro")
            systemPrompt = "You handle complex reasoning tasks."
        }
    }
}
```

---

## 9. Tool System

### Basic Tool

```kotlin
val myTool = tool("search_web", "Searches the internet for information") {
    param("query", "The search query", type = ParamType.String, required = true)
    param("maxResults", "Maximum number of results", type = ParamType.Integer, required = false)

    execute { args ->
        val query = args.string("query")
        val max   = args.intOrNull("maxResults") ?: 5
        searchApi.search(query, max)  // return a String result
    }
}
```

### Tool with Risk Classification

```kotlin
val deleteFileTool = tool("delete_file", "Deletes a file from the system") {
    risk(ToolRisk.HIGH)
    requiresConfirmation(true)   // triggers confirmationHandler before execution

    param("filePath", "Absolute path of the file to delete", type = ParamType.String, required = true)

    execute { args ->
        val path = args.string("filePath")
        File(path).delete()
        "File deleted: $path"
    }
}
```

### Toolbox (grouping tools)

```kotlin
val financeToolbox = toolbox("finance", getBalanceTool, transferTool, transactionHistoryTool)

val sdk = AivoSdk {
    tools { +financeToolbox }  // registers all tools in the toolbox
    agents {
        define("finance") {
            tools(financeToolbox)  // grant the entire toolbox to this agent
        }
    }
}
```

### Inline Tools (agent-scoped)

```kotlin
agents {
    define("calculator") {
        systemPrompt = "You perform calculations."

        // Define tools directly inside the agent block
        tool("add", "Adds two numbers") {
            param("a", "First number", type = ParamType.Number, required = true)
            param("b", "Second number", type = ParamType.Number, required = true)
            execute { args ->
                val result = args.double("a") + args.double("b")
                "Result: $result"
            }
        }
    }
}
```

---

## 10. Agent Definitions

### Agent Builder DSL

```kotlin
agent("my_agent") {
    // Identity
    name        = "My Agent"
    description = "What this agent does"
    role        = AgentRole.ASSISTANT  // ASSISTANT | RESEARCHER | WRITER | SUPERVISOR | ORCHESTRATOR

    // Prompt
    systemPrompt = "You are a helpful assistant."
    instructions("Additional instructions appended to system prompt.")

    // Style & Safety
    style = StylePreset.CONCISE.toString()  // CONCISE | TECHNICAL | CREATIVE | ANALYTICAL
    rules(SafetyRule.NEVER_HALLUCINATE_SOURCES)

    // Model (overrides SDK default)
    model("gemini:gemini-2.5-pro")

    // Tools
    tools("get_balance", "transfer_money")  // reference by name
    // or
    tools(getBalanceTool, transferMoneyTool) // reference by instance

    // Multi-agent delegation
    delegates("specialist_1", "specialist_2")
}
```

### Supervisor Agent DSL

```kotlin
val researcher = agent("researcher") { ... }
val writer     = agent("writer") { ... }

supervisor("lead") {
    name = "Team Lead"
    systemPrompt = "Coordinate the team."
    manages(researcher, writer)  // automatically registers sub-agents
}
```

### Agent Roles

| Role | Use Case |
|---|---|
| `ASSISTANT`    | General-purpose chat agent |
| `RESEARCHER`   | Data gathering, tool-heavy agent |
| `WRITER`       | Formatting and prose generation |
| `SUPERVISOR`   | Model-driven delegation to sub-agents |
| `ORCHESTRATOR` | Code-driven workflow pipelines |
| `SUPPORT`      | Domain specialist |

---

## 11. Declarative Agents (Markdown & JSON)

### Markdown Format

```markdown
<!-- agents/researcher.md -->
---
id: researcher
name: Research Specialist
role: RESEARCHER
model: openrouter:deepseek/deepseek-r1-0528-qwen3-8b:free
tools:
  - search_web
  - get_weather
style: CONCISE
---
You are a research specialist. Use your tools to gather accurate, current information.
Never invent sources — only report what your tools return.
```

```kotlin
val sdk = AivoSdk {
    agents {
        fromMarkdownPath("agents/researcher.md")
        fromMarkdownPath("agents/writer.md")
    }
}
```

### JSON Format

```json
{
  "id": "customer_service",
  "name": "Customer Service",
  "role": "SUPPORT",
  "systemPrompt": "You answer customer questions politely.",
  "tools": ["get_product_info", "get_faq"],
  "style": "CONCISE"
}
```

```kotlin
val sdk = AivoSdk {
    agents {
        fromJsonPath("agents/customer_service.json")
    }
}
```

---

## 12. Memory & Context Window Strategies

```kotlin
val sdk = AivoSdk {
    memory {
        // Strategy 1: Keep all messages (default) — use for short sessions
        keepAll()

        // Strategy 2: Sliding window — keep the last N messages
        slidingWindow(maxMessages = 20)

        // Strategy 3: Token budget — trim to fit within model context
        tokenBudget(maxTokens = 4096)

        // Custom memory store (e.g., persisted to a database)
        store = MyDatabaseMemoryStore()
    }
}
```

---

## 13. Streaming Events

All events emitted by `sdk.stream(...)`:

```kotlin
sdk.stream(conversationId, "User message").collect { event ->
    when (event) {
        is AgentEvent.AgentEntered     -> println("Entered: ${event.agentId}, path: ${event.agentPath}")
        is AgentEvent.StepStarted      -> println("Step ${event.stepIndex}")
        is AgentEvent.TextDelta        -> print(event.text)          // streaming token
        is AgentEvent.ThinkingDelta    -> print(event.text)          // reasoning token
        is AgentEvent.ToolCallStarted  -> println("Calling: ${event.toolName}")
        is AgentEvent.ToolCallExecuted -> println("Result for: ${event.toolName}")
        is AgentEvent.AgentExited      -> println("Exited: ${event.agentId}")
        is AgentEvent.RunCompleted     -> {
            println("Done! Text: ${event.result.text}")
            println("Steps: ${event.result.steps}")
            println("Tokens: ${event.result.usage}")
        }
        is AgentEvent.RunFailed        -> println("Failed: ${event.error.message}")
    }
}
```

---

## 14. Security & Confirmation Gates

### Human-in-the-Loop Confirmation

```kotlin
val sdk = AivoSdk {
    security {
        // Called before any tool marked requiresConfirmation(true)
        confirmationHandler = { call, spec ->
            // Return true to allow, false to deny
            // This suspends — perfect for showing a UI dialog
            withContext(Dispatchers.Main) {
                showConfirmationDialog(
                    title = "Approve ${spec.name}?",
                    body  = "Arguments: ${call.arguments}",
                )
            }
        }

        // Custom tool execution policy
        toolPolicy = { tool, call ->
            when (tool.risk) {
                ToolRisk.HIGH -> ToolPolicyDecision.RequireConfirmation
                ToolRisk.LOW  -> ToolPolicyDecision.Allow
                else          -> ToolPolicyDecision.Allow
            }
        }
    }
}
```

### Secrets Redaction in Logs

```kotlin
val sdk = AivoSdk {
    observability {
        logPayloads = true  // enable detailed logging

        redactor = DefaultRedactor(
            additionalPatterns = listOf(
                Regex("""account_number:\s*\d+"""),
                Regex("""ssn:\s*\d{3}-\d{2}-\d{4}"""),
            )
        )
    }
}
```

---

## 15. Resilience — Retries & Timeouts

```kotlin
val sdk = AivoSdk {
    resilience {
        // Retry policy with exponential backoff
        retry {
            maxAttempts    = 3
            baseDelayMs    = 1_000L   // 1 second
            maxDelayMs     = 30_000L  // 30 seconds max
            jitter         = true     // add ±20% jitter to prevent thundering herd
        }

        // Network timeouts
        timeouts {
            connectTimeoutMs     = 10_000L  // 10s connect timeout
            requestTimeoutMs     = 60_000L  // 60s overall request timeout
            streamIdleTimeoutMs  = 30_000L  // 30s idle timeout for streaming
        }
    }

    runtime {
        maxSteps         = 10       // max tool-call iterations per agent run
        toolTimeoutMs    = 30_000L  // max time per tool execution
        runTimeoutMs     = 120_000L // max total agent run time
    }
}
```

---

## 16. Observability — Logging & Telemetry

```kotlin
val sdk = AivoSdk {
    observability {
        // Built-in console logger
        logger = object : Logger {
            override fun log(level: LogLevel, tag: String, message: String) {
                println("[$level][$tag] $message")
            }
        }

        // OpenTelemetry adapter (example)
        telemetry = object : Telemetry {
            override fun recordSpan(name: String, attributes: Map<String, String>, durationMs: Long) {
                myOtelTracer.record(name, attributes, durationMs)
            }
            override fun recordCounter(name: String, delta: Long, attributes: Map<String, String>) {
                myOtelMeter.record(name, delta, attributes)
            }
        }

        logPayloads = false  // set true to log full request/response bodies (careful with PII)
    }
}
```

---

## 17. Testing with FakeLlmProvider

The `sdk-testing` module provides a `FakeLlmProvider` for offline unit tests:

```kotlin
// build.gradle.kts
dependencies {
    testImplementation("com.aivo:aivo-sdk-testing:1.0.0")
}
```

```kotlin
import com.aivo.sdk.testing.FakeLlmProvider
import com.aivo.sdk.testing.FakeResponse

@Test
fun `agent correctly calls transfer tool`() = runTest {
    val fakeProvider = FakeLlmProvider(
        id = ProviderId("fake"),
        responses = listOf(
            // First LLM call: model decides to call the tool
            FakeResponse.ToolCall(
                name = "transfer_money",
                arguments = buildJsonObject {
                    put("to", "Ahmed")
                    put("amount", 50.0)
                }
            ),
            // Second LLM call: model formats the final answer
            FakeResponse.Text("Transfer of \$50 to Ahmed was successful."),
        )
    )

    val sdk = AivoSdk {
        providers { register(fakeProvider) }
        defaultModel("fake:model-1")
        tools { +transferMoneyTool }
        agents {
            define("payments") {
                systemPrompt = "Handle transfers."
                tools("transfer_money")
            }
        }
        entryAgent("payments")
    }

    val result = sdk.chat(ConversationId("test"), "Transfer \$50 to Ahmed")
    assertTrue(result.text.contains("successful"))
    assertEquals(2, result.steps)
}
```

### Contract Tests for Custom Providers

```kotlin
// Extend LlmProviderContractTest to verify your provider implementation
class MyProviderContractTest : LlmProviderContractTest() {
    override fun createProvider() = MyCustomProvider(/* ... */)
}
```

---

## 18. Android Integration

### Recommended Pattern: ViewModel + Repository

```kotlin
// MyRepository.kt
class AiRepository {
    private val sdk: AivoSdk by lazy {
        AivoSdk {
            providers {
                gemini("gemini") { apiKey(BuildConfig.GEMINI_API_KEY) }
            }
            defaultModel("gemini:gemini-2.0-flash")
            agents {
                define("assistant") {
                    systemPrompt = "You are a helpful assistant."
                }
            }
            entryAgent("assistant")
        }
    }

    fun streamResponse(sessionId: String, message: String): Flow<AgentEvent> =
        sdk.agent("assistant").stream(message, ConversationId(sessionId))
}

// MyViewModel.kt
class MyViewModel(private val repo: AiRepository = AiRepository()) : ViewModel() {
    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    fun sendMessage(message: String) {
        viewModelScope.launch {
            repo.streamResponse("session-1", message).collect { event ->
                if (event is AgentEvent.TextDelta) {
                    _text.update { it + event.text }
                }
            }
        }
    }
}
```

### Network Permission

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
```

### Proguard / R8 Rules

```proguard
# proguard-rules.pro
-keep class com.aivo.sdk.** { *; }
-keep class kotlinx.serialization.** { *; }
-dontwarn okhttp3.**
```

---

## 19. iOS Integration (Kotlin/Native)

The SDK compiles to a native framework for iOS. Use it from Swift:

```swift
// ContentView.swift
import AivoSdk  // import the generated Kotlin framework

let sdk = AivoSdkCompanion.shared.invoke { builder in
    builder.providers { providers in
        providers.gemini(id: "gemini") { dsl in
            dsl.apiKey(ProcessInfo.processInfo.environment["GEMINI_API_KEY"] ?? "")
        }
    }
    builder.defaultModel(modelRef: "gemini:gemini-2.0-flash")
    builder.agents { agents in
        agents.define(id: "assistant") { agent in
            agent.systemPrompt = "You are a helpful assistant."
        }
    }
    builder.entryAgent(agentId: "assistant")
}

// Run chat (from a Task or async context)
Task {
    let result = try await sdk.chat(
        conversationId: ConversationId(value: "ios-session"),
        message: "Hello from iOS!"
    )
    print(result.text)
}
```

> **Note:** For streaming on iOS, use the `stream(...)` method which returns a Kotlin Flow.
> Bridge it to Swift's `AsyncSequence` using the KMP-NativeCoroutines library.

---

## Appendix: Complete DSL Reference

```kotlin
AivoSdk {
    // Global default model (used when an agent doesn't specify one)
    defaultModel("providerId:modelName")

    // Entry agent (used when no agentId is passed to chat/stream)
    entryAgent("my_default_agent")

    providers {
        ollama("ollama")                    { baseUrl("..."); apiKey("...") }
        openRouter("openrouter")            { apiKey("..."); referer("..."); title("...") }
        gemini("gemini")                    { apiKey("..."); baseUrl("...") }
        openAiCompatible("custom")          { baseUrl("..."); apiKey("..."); header("K", "V") }
        register(myCustomLlmProvider)       // custom provider
    }

    tools {
        +myTool                             // operator plus
        +myToolbox                          // entire toolbox
        register(tool1, tool2)
        tool("inline_tool", "description") { /* builder */ }
    }

    agents {
        define("id")                        { /* agent builder */ }
        supervisor("lead")                  { /* supervisor builder */ }
        fromMarkdown(markdownString)
        fromMarkdownPath("path/to/agent.md")
        fromJson(jsonString)
        fromJsonPath("path/to/agent.json")
        register(agentDefinition)
    }

    memory {
        keepAll()
        slidingWindow(maxMessages = 20)
        tokenBudget(maxTokens = 4096)
        store = myCustomMemoryStore
    }

    runtime {
        maxSteps              = 10
        maxDelegationDepth    = 3
        toolTimeoutMs         = 30_000L
        runTimeoutMs          = 120_000L
        parallelToolExecution = true
        concurrencyPolicy     = ConcurrencyPolicy.QUEUE
    }

    resilience {
        retry {
            maxAttempts = 3
            baseDelayMs = 1_000L
            maxDelayMs  = 30_000L
            jitter      = true
        }
        timeouts {
            connectTimeoutMs    = 10_000L
            requestTimeoutMs    = 60_000L
            streamIdleTimeoutMs = 30_000L
        }
    }

    security {
        confirmationHandler = { call, spec -> /* return Boolean */ true }
        toolPolicy          = DefaultToolPolicy
        allowInsecure       = false
    }

    observability {
        logger      = myLogger
        telemetry   = myTelemetry
        logPayloads = false
        redactor    = DefaultRedactor()
    }

    // Testing only — inject deterministic clock / IDs
    clock(myFixedClock)
    idGenerator(mySequentialIdGenerator)
}
```
