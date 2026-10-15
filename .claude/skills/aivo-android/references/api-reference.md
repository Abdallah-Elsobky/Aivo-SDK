# Aivo SDK Android — API Reference

This document provides a detailed reference for all core classes, interfaces, methods, and configurations available in the **Aivo SDK** (`io.github.abdallah-elsobky:aivo-sdk:1.0.1`).

---

## 1. Top-Level Facade (`com.aivo.sdk.AivoSdk`)

```kotlin
interface AivoSdk {
    val defaultModel: ModelRef?
    val entryAgentId: String?
    val providers: Map<ProviderId, LlmProvider>
    val agents: AgentRegistry
    val tools: ToolRegistry
    val runtime: AgentRuntime

    // Single-turn chat without streaming
    suspend fun chat(
        conversationId: ConversationId,
        message: String,
        agentId: String? = null,
        variables: Map<String, String> = emptyMap()
    ): AgentResult

    // Real-time streaming
    fun stream(
        conversationId: ConversationId,
        message: String,
        agentId: String? = null,
        variables: Map<String, String> = emptyMap()
    ): Flow<AgentEvent>

    // Access specific agent handle
    fun agent(id: String): AgentHandle

    // Access raw LLM client directly (without agent loop)
    fun llm(model: String): LlmClient
    fun llm(model: ModelRef): LlmClient

    // Text introspection of all registered agents and loops
    fun describe(): String

    companion object {
        // Builder DSL invocation
        operator fun invoke(block: AivoSdkBuilder.() -> Unit): AivoSdk

        // Rapid one-line setup
        fun create(
            provider: AivoProvider,
            model: String,
            apiKey: String? = null,
            baseUrl: String? = null,
            systemPrompt: String = "You are a helpful multiplatform assistant.",
            tools: List<Tool> = emptyList()
        ): AivoSdk

        fun create(
            model: ProviderModel,
            apiKey: String? = null,
            baseUrl: String? = null,
            systemPrompt: String = "You are a helpful multiplatform assistant.",
            tools: List<Tool> = emptyList()
        ): AivoSdk
    }
}
```

---

## 2. Agent Events (`com.aivo.sdk.runtime.agent.AgentEvent`)

`aivo.stream(...)` returns a `Flow<AgentEvent>`. The sealed hierarchy includes:

| Event | Properties | Description |
|---|---|---|
| `AgentEvent.RunStarted` | `runId: String`, `conversationId: ConversationId` | Fired when the agent loop starts executing. |
| `AgentEvent.AgentEntered` | `agentId: String`, `agentName: String` | Active agent began handling the conversation turn. |
| `AgentEvent.StepStarted` | `stepIndex: Int`, `agentId: String` | Fired on each iteration of the agent's internal thought loop. |
| `AgentEvent.TextDelta` | `text: String`, `agentPath: List<String>` | Token string delta emitted during generation. |
| `AgentEvent.ReasoningDelta` | `text: String`, `agentPath: List<String>` | Chain-of-thought thinking tokens (for reasoning models). |
| `AgentEvent.ToolCallStarted` | `toolName: String`, `arguments: JsonElement` | Agent decided to invoke a tool. |
| `AgentEvent.ToolCallExecuted` | `toolName: String`, `result: String`, `isError: Boolean` | Tool execution completed and result sent back to agent. |
| `AgentEvent.StepCompleted` | `stepIndex: Int`, `agentId: String`, `usage: Usage?` | Step ended with token usage breakdown. |
| `AgentEvent.RunCompleted` | `result: AgentResult` | Conversation run finished successfully. Contains `.text` and `.steps`. |
| `AgentEvent.RunFailed` | `error: Throwable` | Execution terminated abruptly due to an unhandled exception. |

---

## 3. Tool Definition DSL (`com.aivo.sdk.core.port.tool`)

```kotlin
val myTool = tool(name = "tool_name", description = "Tool description") {
    // Risk assessment & human approval
    risk(ToolRisk.LOW) // or ToolRisk.MEDIUM, ToolRisk.HIGH
    requiresConfirmation(false) // If true, invokes ConfirmationHandler before running

    // Parameter declarations
    param("city", "Target city name", type = ParamType.String, required = true)
    param("days", "Forecast days", type = ParamType.Int, required = false)

    // Full JSON schema alternative
    parameters {
        string("username", "Account username", required = true, minLength = 3)
        boolean("notify", "Send push alert", required = false)
        enum("status", listOf("active", "suspended"), required = true)
    }

    // Suspending execution block
    execute { args: ToolArgs ->
        val city = args.string("city")
        val days = args.intOrNull("days") ?: 1
        "Weather for $city over $days days: Sunny, 24C"
    }
}
```

### Parameter Types (`ParamType`)
- `ParamType.String`
- `ParamType.Int`
- `ParamType.Long`
- `ParamType.Double`
- `ParamType.Boolean`
- `ParamType.Array`
- `ParamType.Object`

---

## 4. Memory Configuration (`com.aivo.sdk.runtime.memory.*`)

```kotlin
AivoSdk {
    memory {
        // 1. Keep All (default)
        keepAll()

        // 2. Sliding Window (retains last N messages)
        slidingWindow(maxMessages = 30)

        // 3. Token Budget (trims older messages to stay within budget)
        tokenBudget(maxTokens = 8_000, estimator = CharCountEstimator)

        // 4. Custom Storage (e.g. Room Database)
        store = RoomMemoryStore(myDatabase.messageDao())
    }
}
```

### `MemoryStore` Interface
```kotlin
interface MemoryStore {
    suspend fun load(conversationId: ConversationId): List<Message>
    suspend fun append(conversationId: ConversationId, messages: List<Message>)
    suspend fun clear(conversationId: ConversationId)
}
```

---

## 5. Resilience & Guardrails

```kotlin
AivoSdk {
    resilience {
        retry {
            maxAttempts = 3          // Maximum retry attempts
            baseDelayMs = 1_000L     // Exponential backoff initial delay
            maxDelayMs  = 15_000L    // Delay ceiling
            jitter      = true       // Random +/-20% variation to prevent thundering herd
        }
        timeouts {
            connectTimeoutMs    = 10_000L  // HTTP connect timeout
            requestTimeoutMs    = 60_000L  // Overall turn timeout
            streamIdleTimeoutMs = 25_000L  // Idle SSE token timeout
        }
    }
    runtime {
        maxSteps              = 12       // Agent loop iteration limit
        maxDelegationDepth    = 3        // Supervisor nesting limit
        toolTimeoutMs         = 15_000L  // Timeout per tool invocation
        parallelToolExecution = true     // Concurrently run independent tool calls
        concurrencyPolicy     = ConcurrencyPolicy.QUEUE // QUEUE, REJECT, or CANCEL
    }
}
```

---

## 6. SdkException Hierarchy (`com.aivo.sdk.core.error.*`)

```
SdkException
├── ConfigurationException             (Thrown at build time if configuration is invalid)
├── UnsupportedCapabilityException     (Thrown if a provider lacks a required capability)
├── ProviderException                  (All provider & network errors)
│   ├── AuthenticationException        (HTTP 401 / 403 - bad or missing credentials)
│   ├── RateLimitException             (HTTP 429 - includes retryAfterMs)
│   ├── InvalidRequestException        (HTTP 400 / 422 - invalid payload or params)
│   ├── ModelNotFoundException         (HTTP 404 - model not accessible)
│   ├── ContentFilteredException       (Safety filters triggered)
│   ├── ServerException                (HTTP 500 / 502 / 503 / 504)
│   ├── NetworkException               (Socket / DNS / connection errors)
│   ├── TimeoutException               (Connect / request / stream idle timeouts)
│   └── ProtocolException              (JSON parsing or SSE decoding failures)
├── ToolException                      (Tool failure during execution)
├── AgentException                     (Runtime orchestration errors)
│   ├── MaxStepsExceededException      (Loop exceeded maxSteps limit)
│   ├── MaxDelegationDepthExceededException (Delegation tree too deep)
│   ├── DelegationCycleException       (Agent A delegated to B which delegated to A)
│   ├── UnknownAgentException          (Attempted to delegate to unconfigured agent)
│   ├── RunTimeoutException            (Wall-clock run timeout exceeded)
│   └── ConcurrentRunException         (ConcurrencyPolicy.REJECT triggered)
└── MemoryException                    (Storage load/save failures)
```
