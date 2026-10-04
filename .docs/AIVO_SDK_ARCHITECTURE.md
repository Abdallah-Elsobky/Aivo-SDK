# Aivo SDK — Architecture Guide
> **Version:** 1.0.0 · **For:** Contributors, SDK Maintainers, Advanced Users

---

## Table of Contents
1. [Overview & Design Philosophy](#1-overview--design-philosophy)
2. [Layered Architecture](#2-layered-architecture)
3. [Module Map](#3-module-map)
4. [Dependency Rules](#4-dependency-rules)
5. [Domain Model (`sdk-core`)](#5-domain-model-sdk-core)
6. [Ports Design](#6-ports-design)
7. [Provider Layer: WireProtocol + HttpLlmProvider](#7-provider-layer-wireprotocol--httpllmprovider)
8. [StreamAssembler](#8-streamassembler)
9. [Streaming Decoders](#9-streaming-decoders)
10. [Middleware: Decorator Chain](#10-middleware-decorator-chain)
11. [Tool System](#11-tool-system)
12. [Agent System](#12-agent-system)
13. [Agent Runtime Loop](#13-agent-runtime-loop)
14. [Multi-Agent: Supervisor & Delegation](#14-multi-agent-supervisor--delegation)
15. [Memory & Context Window](#15-memory--context-window)
16. [Error Hierarchy](#16-error-hierarchy)
17. [Observability & Security Design](#17-observability--security-design)
18. [The AivoSdk Composition Root](#18-the-aivosdk-composition-root)
19. [Testing Architecture](#19-testing-architecture)
20. [SOLID Compliance Review](#20-solid-compliance-review)
21. [Adding a New Provider (Extension Contract)](#21-adding-a-new-provider-extension-contract)

---

## 1. Overview & Design Philosophy

Aivo SDK is a **provider-agnostic Agentic AI SDK** built on Kotlin Multiplatform. The core design philosophy is:

> **Configuration, not code changes.** Adding a new LLM provider, agent, tool, memory store, or context strategy should require writing one new class and registering it — never modifying core SDK files.

Three guiding principles underpin every design decision:

1. **Clean Architecture** — dependencies point **inward only**. The domain model in `sdk-core` knows nothing about Ktor, JSON wire formats, or any specific provider.
2. **SOLID** — every class has one job; new behaviors are added by new classes (Open/Closed); all providers are substitutable (Liskov); interfaces are small and role-specific (Interface Segregation); high-level modules depend on abstractions (Dependency Inversion).
3. **Testability first** — every behavior is verified by automated tests that run without network access. Real providers are mocked at the HTTP wire level using Ktor `MockEngine` and pre-recorded JSON fixtures.

---

## 2. Layered Architecture

```
┌──────────────────────────────────────────────────────────────┐
│  LAYER 4 — Facade / Composition Root                         │
│  sdk: AivoSdk DSL, iOS shim, re-exported public API          │
├──────────────────────────────────────────────────────────────┤
│  LAYER 3 — Application Services                              │
│  sdk-runtime:        agent loop, tools, memory, delegation   │
│  sdk-agent-config:   Markdown/JSON/YAML agent loaders        │
│  sdk-middleware:     retry, logging, telemetry decorators    │
├──────────────────────────────────────────────────────────────┤
│  LAYER 2 — Infrastructure Adapters                           │
│  sdk-provider-ollama                                         │
│  sdk-provider-openai-compatible (+ OpenRouter preset)        │
│  sdk-provider-gemini (Interactions API)                      │
│  sdk-transport: Ktor, auth, SSE/NDJSON, HttpLlmProvider      │
├──────────────────────────────────────────────────────────────┤
│  LAYER 1 — Domain / Ports                                    │
│  sdk-core: domain model + ports (interfaces) only            │
└──────────────────────────────────────────────────────────────┘
```

**Key rule:** arrows always point **down** (toward `sdk-core`). Higher layers know about lower ones; lower layers know nothing about higher ones.

---

## 3. Module Map

```
aivo-sdk/
│
├── build-logic/                  ← Gradle convention plugins
│   ├── kotlin-multiplatform/
│   ├── android-library/
│   └── published-library/
│
├── gradle/libs.versions.toml     ← Single version catalog
│
├── sdk-core/                     ← Domain model + ports ONLY
│   └── src/commonMain/
│       ├── model/                ← Message, ToolCall, Usage, etc.
│       ├── port/                 ← LlmProvider, Tool, MemoryStore, etc.
│       ├── error/                ← SdkException hierarchy
│       ├── stream/               ← StreamAssembler
│       └── util/                 ← SecretString, IdGenerator, Clock, etc.
│
├── sdk-transport/                ← Generic HTTP machinery (not provider-specific)
│   └── src/commonMain/
│       ├── HttpClientFactory.kt
│       ├── HttpLlmProvider.kt    ← The only HTTP-aware LlmProvider implementation
│       ├── auth/                 ← AuthStrategy, BearerToken, HeaderKey
│       ├── decoder/              ← SseDecoder, NdjsonDecoder
│       ├── protocol/             ← WireProtocol interface, RawFrame, Endpoint
│       └── error/                ← HTTP status → SdkException mapping
│
├── sdk-provider-ollama/          ← Ollama WireProtocol ONLY
│   └── src/commonMain/
│       ├── OllamaWireProtocol.kt
│       └── OllamaProvider.kt     ← factory
│
├── sdk-provider-openai-compatible/
│   └── src/commonMain/
│       ├── OpenAiCompatibleWireProtocol.kt
│       ├── OpenAiCompatibleProvider.kt
│       └── OpenRouterProvider.kt  ← preset for openrouter.ai
│
├── sdk-provider-gemini/
│   └── src/commonMain/
│       ├── GeminiWireProtocol.kt
│       └── GeminiProvider.kt
│
├── sdk-middleware/               ← LlmProvider decorators
│   └── src/commonMain/
│       ├── RetryingLlmProvider.kt
│       ├── LoggingLlmProvider.kt
│       └── TelemetryLlmProvider.kt
│
├── sdk-runtime/                  ← Agent loop, tools, memory
│   └── src/commonMain/
│       ├── agent/
│       │   ├── AgentRuntime.kt
│       │   ├── AgentEvent.kt
│       │   └── AgentLimits.kt
│       ├── tool/
│       │   ├── ToolRegistry.kt
│       │   ├── ToolExecutor.kt
│       │   ├── ToolValidator.kt
│       │   ├── schema/           ← JSON Schema DSL + generator
│       │   └── policy/
│       ├── memory/
│       │   ├── InMemoryMemoryStore.kt
│       │   └── context/          ← KeepAll, SlidingWindow, TokenBudget
│       ├── delegation/
│       │   └── AgentAsToolStrategy.kt
│       └── prompt/
│           └── PromptRenderer.kt
│
├── sdk-agent-config/             ← File-based agent loaders
│   └── src/
│       ├── commonMain/
│       │   ├── AgentDefinitionLoader.kt
│       │   ├── MarkdownAgentLoader.kt
│       │   └── JsonAgentLoader.kt
│       ├── androidMain/          ← ResourceReader actual for Android assets
│       ├── iosMain/              ← ResourceReader actual for iOS bundle
│       └── jvmMain/              ← ResourceReader actual for classpath/file
│
├── sdk/                          ← Composition root + public API facade
│   └── src/commonMain/
│       ├── AivoSdk.kt            ← The DSL entry point
│       ├── AivoSdkBuilder.kt
│       └── ios/                  ← iOS interop shim
│
├── sdk-testing/                  ← Test utilities (NOT shipped to users)
│   └── src/commonMain/
│       ├── FakeLlmProvider.kt
│       ├── InMemoryTelemetry.kt
│       ├── FixedClock.kt
│       ├── SequentialIdGenerator.kt
│       └── fixtures/             ← Pre-recorded JSON response strings
│
└── samples/
    ├── cli-jvm/                  ← Banking supervisor demo (JVM)
    └── android-chat/             ← Streaming UI (Android)
```

---

## 4. Dependency Rules

### Allowed Dependency Graph
```
sdk ────────────────────────────────────────────────► sdk-runtime
sdk ────────────────────────────────────────────────► sdk-agent-config
sdk ────────────────────────────────────────────────► sdk-middleware
sdk ────────────────────────────────────────────────► sdk-provider-ollama
sdk ────────────────────────────────────────────────► sdk-provider-openai-compatible
sdk ────────────────────────────────────────────────► sdk-provider-gemini
sdk-runtime ───────────────────────────────────────► sdk-core
sdk-agent-config ──────────────────────────────────► sdk-core, sdk-runtime
sdk-middleware ─────────────────────────────────────► sdk-core
sdk-provider-* ─────────────────────────────────────► sdk-core, sdk-transport
sdk-transport ──────────────────────────────────────► sdk-core
sdk-core ───────────────────────────────────────────► (nothing)
sdk-testing ────────────────────────────────────────► sdk-core
```

### Forbidden Edges (enforced by architecture test)
```
sdk-core → anything else
sdk-provider-* → sdk-runtime
sdk-provider-* → sdk-provider-*
sdk-runtime → sdk-provider-*
sdk-runtime → sdk-transport
```

**Why `runtime → transport` is forbidden:** The agent loop calls only the `LlmProvider` interface. It is completely agnostic of Ktor, HTTP, or any wire format. This is the most important invariant in the entire system.

---

## 5. Domain Model (`sdk-core`)

The domain model is the **language of the SDK**. Everything is expressed in these types, and they must be free of any provider-specific information.

### Class Diagram
```
ModelRef ──────────────────► ProviderId
ConversationId

Message (sealed)
  ├─ System(text: String)
  ├─ User(parts: List<ContentPart>)
  ├─ Assistant(parts, toolCalls, reasoning?, providerMetadata)
  └─ Tool(callId, toolName, result: ToolResult)

ContentPart (sealed)
  └─ Text(text: String)   ← v1 only

ToolCall(id, name, arguments: JsonObject)

ToolResult (sealed)
  ├─ Success(content: JsonElement)
  └─ Failure(kind: ToolFailureKind, message: String)

LlmRequest(model, messages, tools, toolChoice, options, extras)
LlmResponse(message: Assistant, finishReason, usage?, model?, responseId?)
GenerationOptions(temperature?, topP?, maxOutputTokens?, stop, seed?, reasoning?)
Usage(inputTokens?, outputTokens?, reasoningTokens?, cachedInputTokens?, costUsd?)

ProviderMetadata(entries: Map<String, JsonElement>)  ← opaque per-provider data
```

### Why `ProviderMetadata`?
Some providers carry state that must survive a round-trip back to the **same** provider. Example: Gemini's `previous_interaction_id` and thought `signature` values. These are stored in `ProviderMetadata` under namespaced keys (e.g., `gemini.interactionId`, `gemini.thoughtSignature`). The `GeminiWireProtocol` reads them when encoding the next request. No other module ever interprets them.

### Why `extras: JsonObject` in `LlmRequest`?
Some callers need to pass provider-specific parameters not covered by the domain model. The adapter **merges** `extras` into the outgoing JSON body. This is the only approved escape hatch. It is never populated from model output (security invariant).

---

## 6. Ports Design

Ports are the **interfaces** owned by `sdk-core` that define what the SDK needs from the outside world. All concrete implementations live in outer layers and are injected by the composition root.

### Interface Segregation in Action
Each port is **small and role-specific**. No implementer is forced to implement methods it doesn't need.

| Port | Purpose | Default Impl |
|---|---|---|
| `LlmProvider` | Call an LLM | `HttpLlmProvider` (one per vendor via `WireProtocol`) |
| `Tool` | Execute a tool | App-supplied; DSL helpers in `sdk-runtime` |
| `MemoryStore` | Persist conversation history | `InMemoryMemoryStore` |
| `Logger` | Log SDK events | Platform-specific via `expect/actual` |
| `Telemetry` | Record observability events | `NoOpTelemetry` (plug in your own) |
| `Clock` | Current time | `SystemClock` (real), `FixedClock` (tests) |
| `IdGenerator` | Generate unique IDs | `UuidIdGenerator`, `SequentialIdGenerator` (tests) |
| `CredentialsProvider` | Provide auth secrets | App-supplied (supports token refresh) |
| `TokenEstimator` | Estimate token count | `CharCountEstimator` (`ceil(chars/4)`) |
| `ResourceReader` | Read agent files | `expect/actual` per platform |

---

## 7. Provider Layer: WireProtocol + HttpLlmProvider

This is the core architectural insight that makes providers cheap to add.

### The Problem
Each LLM provider has a different:
- Endpoint path and HTTP method
- Auth header format
- Request JSON shape
- Response JSON shape
- Streaming protocol (SSE vs NDJSON)
- Tool declaration format
- Tool call response format
- Error response format
- Finish reason vocabulary

### The Solution: Split Into Two Responsibilities

```
┌────────────────────────────────────────────────────────┐
│  HttpLlmProvider (sdk-transport)                       │
│                                                        │
│  • Builds Ktor HttpClient                              │
│  • Applies AuthStrategy per request (suspend)          │
│  • Sets headers (User-Agent, auth, extras)             │
│  • Sends request                                       │
│  • Checks HTTP status → maps to SdkException           │
│  • Reads response body / streams frames                │
│  • Delegates all JSON work to WireProtocol             │
│  • Manages streamIdle timeout                          │
└─────────────────────────┬──────────────────────────────┘
                          │ calls
                          ▼
┌────────────────────────────────────────────────────────┐
│  WireProtocol (implemented per vendor)                 │
│                                                        │
│  • endpoint(request, stream): Endpoint                 │
│  • encode(request, stream): JsonObject    [pure fn]    │
│  • decode(body): LlmResponse              [pure fn]    │
│  • decodeStream(frames): Flow<LlmStreamEvent> [pure]   │
│  • mapError(status, body, headers): ProviderException  │
└────────────────────────────────────────────────────────┘
```

**`WireProtocol` methods are pure functions over JSON.** They have no Ktor dependency. This is why they're so easy to unit test with pre-recorded fixture strings — no network or `MockEngine` needed.

### Factory Pattern
```kotlin
// Users call a simple factory, not the internal types
val provider: LlmProvider = OllamaProvider(OllamaConfig(
    id = ProviderId("local"),
    baseUrl = "http://localhost:11434",
    credentials = None,
    capabilities = …
))

// Internally, this creates:
HttpLlmProvider(
    id = config.id,
    capabilities = config.capabilities,
    transport = HttpTransport(httpClient, config.baseUrl),
    protocol = OllamaWireProtocol()
)
```

---

## 8. StreamAssembler

**Problem:** Without it, every `WireProtocol.decodeStream` would need to re-implement the same stateful accumulation logic (building text from deltas, assembling tool call arguments, tracking reasoning, constructing the final `LlmResponse`).

**Solution:** `StreamAssembler` in `sdk-core` owns that accumulation logic **once**.

### Flow
```
WireProtocol.decodeStream(frames)
   produces: Flow<LlmStreamEvent>   ← stateless, just translates wire chunks to events

StreamAssembler.assemble(events)
   maintains: textBuffer, reasoningBuffer, toolCallBuffers[]
   on TextDelta       → append to textBuffer
   on ReasoningDelta  → append to reasoningBuffer
   on ToolCallStarted → open new toolCallBuffer[index]
   on ToolCallArgumentsDelta → append to toolCallBuffers[index]
   on Completed       → validate the final response against accumulated state
   emits: the same Flow<LlmStreamEvent> (pass-through) + final Completed with assembled LlmResponse
```

### Why Not in Transport?
`StreamAssembler` is in `sdk-core` because it operates entirely on `LlmStreamEvent` — the domain type. It has no dependency on Ktor, JSON, or any provider.

---

## 9. Streaming Decoders

Both decoders are in `sdk-transport/decoder/` and are implemented in `commonMain` (no platform code).

### SseDecoder
Handles:
- `data: {…}` — dispatch on blank line
- `event: foo` — event type (used by Gemini)
- `id: abc` — event id
- `: comment lines` — skip (used by OpenRouter as keep-alives)
- Multi-line `data:` values
- CRLF and LF line endings
- Partial chunks (the decoder must buffer across Ktor `readUTF8Line()` calls)

### NdjsonDecoder
- One JSON object per non-empty line
- Skip blank lines
- `{"error": …}` line → emit as `RawFrame.Error`

### Why Not the Ktor SSE Plugin?
OpenAI-compatible and Gemini use **POST** requests for streaming, not GET. The Ktor SSE plugin is designed for GET-based EventSource (browser SSE). POST SSE requires reading the body as a raw byte stream and parsing frames manually. This is more portable and gives us full control over error handling and partial-chunk behavior.

---

## 10. Middleware: Decorator Chain

Each middleware is an `LlmProvider` that wraps another `LlmProvider`. The pattern is the classic **Decorator** (also known as the Wrapper pattern).

```
User code calls:
  Telemetry(
    Logging(
      Retrying(
        OllamaProvider  ← actual HTTP provider
      )
    )
  )

Each decorator transparently delegates to the next,
adding behavior before/after/around the call.
```

### RetryingLlmProvider — Key Design Decisions

**Jitter:** Without jitter, multiple clients retry at exactly the same time after a `RateLimitException` (thundering herd). Jitter adds a random delay within the backoff window to spread retries.

**Pre-first-event rule:** For streaming, a retry after the first `TextDelta` has been emitted would cause duplicate output to the user. Retry is only safe before the first frame arrives. After that, the error propagates as-is.

**`Retry-After` header:** When a 429 response includes this header, use it exactly. Respecting the server's backoff instruction is more reliable than our own calculation.

### LoggingLlmProvider — Security
By design, this logger **never** touches the `Authorization` header, API keys, or message content by default. Even when `logPayloads = true`, all data passes through `Redactor` first. This is enforced by the API design — the logger never receives the raw HTTP request/response.

### TelemetryLlmProvider — Extensibility
The `TelemetryEvent` types are designed so that an OpenTelemetry bridge can be added as a `Telemetry` implementation without any changes to the SDK. The bridge would translate `LlmCallStarted/Completed` into OTel spans, `ToolExecuted` into span events, etc.

---

## 11. Tool System

### ToolExecutor Pipeline
```
Input: List<ToolCall> from assistant message
│
├─ 1. Lookup — find Tool in ToolRegistry (unknown → ToolResult.Failure(UNKNOWN_TOOL))
├─ 2. Validate — check args against JSON Schema subset
│       invalid → ToolResult.Failure(INVALID_ARGUMENTS, "what is wrong")
│       (returned to the model so it can self-correct)
├─ 3. Policy — ToolPolicy.decide(spec, call, context)
│       Allow → continue
│       Deny(reason) → ToolResult.Failure(DENIED, reason)
│       RequireConfirmation → call ConfirmationHandler (suspend)
│             timeout waiting → Deny
├─ 4. Timeout — withTimeout(spec.timeout ?: defaultTimeout)
│       TimeoutCancellationException → ToolResult.Failure(TIMEOUT, "…")
├─ 5. Execute — tool.execute(call, context)
│       exception → ToolResult.Failure(EXECUTION_FAILED, safeMessage)
│       (stack traces are NEVER included in the result)
└─ 6. Truncate — if result.content.toString().length > maxToolResultChars
                 → append "…[truncated]"

Output: List<ToolResult> in the SAME ORDER as input ToolCalls
```

### Parallelism with Ordered Results
When `parallelToolExecution = true`, tool calls run concurrently (bounded by `parallelToolConcurrency`). However, the results are always returned in the **same order** as the input calls. This is critical because the `Message.Tool` messages must correspond positionally to the `ToolCall` entries in the assistant message — the LLM is sensitive to this ordering.

Implementation: `async { }` each call, then `awaitAll()` in order.

### JSON Schema Validation
The validator implements a **subset** of JSON Schema Draft-07:
- `type` (string, number, integer, boolean, array, object, null)
- `required` (list of required property names)
- `enum` (exact value match)
- `properties` (recursive validation)
- `items` (array element validation)
- Basic `minimum`, `maximum`, `minLength`, `maxLength`

This is sufficient for the tool parameter schemas the SDK generates and the vast majority of hand-authored schemas. Full JSON Schema validation is not needed and would add significant complexity.

---

## 12. Agent System

### AgentDefinition as Pure Data
An `AgentDefinition` is a plain data class with no methods and no behavior. It declares **what an agent is**, not **what it does**. The runtime interprets the definition at execution time.

This separation makes agents:
- **Testable** — just compare data structures
- **Serializable** — can be stored or transmitted
- **Swappable** — the same runtime can execute any definition

### File Loading Pipeline
```
ResourceReader (expect/actual per platform)
     │ reads bytes/text by path
     ▼
AgentDefinitionLoader.load(text): AgentDefinition
     │
     ├─ MarkdownAgentLoader  → parse YAML front matter + body as systemPrompt
     ├─ JsonAgentLoader      → deserialize JSON object
     └─ KotlinDslLoader      → AgentDefinitionBuilder result
     │
     ▼
Aggregated validation
     │ collect all errors (file name + line) before throwing
     ▼
AgentRegistry.register(definition)
```

### PromptRenderer
Simple `{{variable}}` substitution. No expression evaluation, no code execution. In **strict mode** (default), an undefined variable is a `ConfigurationException`. This prevents silent errors where a prompt is sent with a literal `{{accountId}}` placeholder because the caller forgot to pass the variable.

---

## 13. Agent Runtime Loop

### The Loop is the Heart of the SDK

```
stream(input):
  1. ENTER: emit RunStarted
  2. resolve agent from AgentRegistry
  3. MUTEX: acquire per-conversation lock (QUEUE or REJECT policy)
  4. load history from MemoryStore
  5. append User message to history; emit AgentEntered

  for step in 1..limits.maxSteps:
    6. emit StepStarted(step)
    7. select messages from contextStrategy (respecting tool-message invariants)
    8. render systemPrompt with PromptRenderer
    9. build LlmRequest (model, messages, tools = agent.tools + delegation tools, options)
    10. LlmRequestTransformer.transform(request)  ← optional
    11. collect provider.stream(request):
          - emit TextDelta, ReasoningDelta, ToolCallStarted, ToolCallArgumentsDelta
          - final Completed event → extract LlmResponse
    12. append assistant message to history
    13. if assistant.toolCalls is empty:
          persist history delta to MemoryStore
          emit StepCompleted, RunCompleted
          return AgentResult
    14. execute all tool calls via ToolExecutor:
          - delegation pseudo-tools → recursive child runs
          - regular tools → pipeline (validate, policy, timeout, execute, truncate)
    15. ToolResultTransformer.transform(results)  ← optional
    16. append Tool messages to history (one per call, SAME ORDER as tool calls)
    17. ATOMIC persist: assistant message + all tool results together
    18. emit StepCompleted; loop

  if maxSteps exhausted: throw MaxStepsExceeded

CLEANUP: always release mutex; on cancellation: do not persist partial state
```

### The Per-Conversation Mutex
Only one run may be active per `ConversationId` at a time. A second call to `stream()` for the same conversation either:
- **Queues** and waits (QUEUE policy, default)
- **Rejects** immediately with `ConcurrentRunException` (REJECT policy)

This prevents interleaved writes to the `MemoryStore`.

### Atomic Persistence
The assistant message and **all** its tool results are persisted **together** in a single `MemoryStore.append` call. This means a crash can leave the run in one of two states:
1. Before the persist → the step was never saved → the run simply doesn't exist in history.
2. After the persist → the complete step is in history → recovery is clean.

It can never leave an orphaned tool call (a tool call in history with no corresponding tool result).

---

## 14. Multi-Agent: Supervisor & Delegation

### Agent-as-Tool Strategy

Delegation is implemented as **ordinary tool calling** from the LLM's perspective. The supervisor model doesn't need special training or a new protocol — it simply calls a tool named `delegate_to_payments` and gets back the child's answer as a tool result.

```
Supervisor receives: "Transfer $50 to Ahmed"

→ LLM decides to call tool: delegate_to_payments
  { "task": "Transfer $50 to Ahmed", "context": "User account: ACC-001" }

→ ToolExecutor intercepts this "tool call":
    - creates a new ConversationId for the child
    - runs PaymentsAgent.run(AgentInput(task, context))
    - waits for child AgentResult
    - returns child.text as ToolResult.Success

→ Supervisor LLM receives the payment confirmation
→ Supervisor responds to the user
```

### Event Forwarding
Child agent events are forwarded into the parent's `Flow` with an **extended `agentPath`**:
```
parent events:  agentPath = ["supervisor"]
child events:   agentPath = ["supervisor", "payments"]
grandchild:     agentPath = ["supervisor", "payments", "transfer_processor"]
```

This gives the UI full visibility into nested delegation without separate event streams.

### Cycle Detection
The `agentPath` list itself serves as the cycle detector. When `AgentAsToolStrategy` is about to start a child run for agent `X`, it checks if `X` is already in the current `agentPath`. If yes, it throws `DelegationCycleException` — preventing A→B→A→B infinite loops.

---

## 15. Memory & Context Window

### Why ContextWindowStrategy?
LLMs have a fixed context window (e.g., 8,192 tokens). When conversation history grows beyond this, the request will fail. The `ContextWindowStrategy` decides **which messages to keep**.

### The Tool-Message Invariant
The most critical rule for all context strategies:

> **Never send an assistant message that contains `toolCalls` without sending all of its corresponding `Message.Tool` responses in the same request.**

If we trimmed the tool calls but not the results (or vice versa), the LLM would receive an incoherent conversation. The strategy must always drop tool call + result groups as a unit.

### TokenBudget Strategy
```
select(messages, budget):
  always_keep = [System, latest User message]
  remaining   = reverse(history) — build from newest backward
  
  for group in groupByAssistantStep(remaining):
    if group.estimatedTokens fits in remaining budget:
      include group
      reduce budget
    else:
      stop (drop this and all older groups)
  
  return always_keep + selected_groups (in original order)
```

---

## 16. Error Hierarchy

```
SdkException (sealed)
│
├─ ConfigurationException(problems: List<String>)
│     Thrown at AivoSdk { } build time with ALL problems collected.
│     Never thrown mid-run.
│
├─ UnsupportedCapabilityException(capability: String)
│     Thrown fail-fast when agent requests a capability the provider lacks.
│     (e.g., tool calling on a provider with toolCalling = false)
│
├─ ProviderException(providerId, httpStatus?, retryable: Boolean)
│   ├─ AuthenticationException           → 401/403 → not retryable
│   ├─ RateLimitException(retryAfter?)   → 429 → retryable (with wait)
│   ├─ InvalidRequestException           → 400/422 → not retryable
│   ├─ ModelNotFoundException            → 404 → not retryable
│   ├─ ContentFilteredException          → safety block → not retryable
│   ├─ ServerException                   → 5xx → retryable
│   ├─ NetworkException                  → IO failure → retryable
│   ├─ TimeoutException                  → retryable
│   └─ ProtocolException                → bad response format → not retryable
│
├─ ToolException(kind)
│     Only thrown if the tool system itself fails catastrophically.
│     Normal tool failures → ToolResult.Failure (not an exception).
│
├─ AgentException (sealed)
│   ├─ MaxStepsExceeded
│   ├─ MaxDelegationDepthExceeded
│   ├─ DelegationCycle
│   ├─ UnknownAgent
│   ├─ RunTimeout
│   └─ TokenBudgetExceeded
│
└─ MemoryException
      Wraps storage errors from MemoryStore.
```

### Design Decisions
- **Sealed hierarchy:** every `catch` block is exhaustive and can `when`-check without `else`.
- **No `kotlin.Result` in public API:** idiomatic Kotlin coroutines use `throw` for errors. `runCatchingSdk {}` is a convenience wrapper for callers who prefer the functional style.
- **Error bodies are sanitized:** provider error responses are truncated to ≤ 2KB and passed through `Redactor` before being included in exceptions. API keys and auth headers are never included.

---

## 17. Observability & Security Design

### Observability — Separation of Concerns
```
Logger (events happening)
│
├─ LoggingLlmProvider → LLM call lifecycle
├─ AgentRuntime       → run/step lifecycle
└─ ToolExecutor       → tool execution (no content by default)

Telemetry (metrics for monitoring)
│
├─ TelemetryLlmProvider → LlmCallStarted/Completed/Failed
│                          (tokens, cost, latency, provider, model)
└─ AgentRuntime         → AgentRunStarted/Completed/Failed
                          (steps, total usage, agentId, runId)

Redactor (safety gate for content)
│
├─ Strips Authorization, x-goog-api-key, configured secret patterns
└─ Called before ANY content goes into logs or telemetry
```

### Security Model — Threat Mitigation
| Threat | Mitigation |
|---|---|
| API key in mobile binary | Gateway pattern; `CredentialsProvider` is suspend (fetches token at request time) |
| Key in logs | `SecretString.toString() = "***"`; `LoggingLlmProvider` never logs headers |
| Prompt injection via tool output | Tool results placed in `tool` role messages; runtime never executes instructions from tool output |
| Runaway loops / cost explosion | Default limits on steps, delegations, tokens, timeouts |
| Over-permissive tools | `ToolRisk` levels; `ToolPolicy`; human confirmation for HIGH-risk |
| Secrets in exception messages | Error bodies sanitized; `Redactor` applied; auth headers never in exceptions |
| Provider-stateful data leakage | Gemini `previous_interaction_id` chains: documented data retention implications in `security.md` |
| Insecure transport | HTTPS enforced by default; `allowInsecure = true` requires explicit opt-in and logs a warning |

---

## 18. The AivoSdk Composition Root

`AivoSdk { }` is the only place where concrete types are assembled. This is the application of the **Dependency Inversion Principle** at the architectural level: high-level modules (runtime, agents) depend on abstractions (ports), and the composition root wires up the concrete implementations.

### Wiring Sequence
```kotlin
AivoSdkBuilder.build():
  1. Create HttpClientFactory → HttpTransport
  2. For each provider config:
       baseProtocol = OllamaWireProtocol() / OpenAiCompatibleWireProtocol() / etc.
       base = HttpLlmProvider(id, capabilities, transport, baseProtocol)
       decorated = TelemetryLlmProvider(
                     LoggingLlmProvider(
                       RetryingLlmProvider(base, retryPolicy),
                     logger, logPayloads),
                   telemetry, clock)
       ProviderRegistry.register(decorated)

  3. Build ToolRegistry from all registered tools
  4. Build AgentRegistry from all loaded agent definitions
  5. Validate everything: no missing tools, no missing agents,
     no missing delegates, no unknown providers, no cycles
     → throw ConfigurationException(allProblems) if any errors

  6. Build InMemoryMemoryStore (or user-provided)
  7. Build ContextWindowStrategy
  8. Build ToolExecutor(registry, policy, confirmationHandler, clock)
  9. Build AgentRuntime(providers, agents, tools, memory, contextStrategy)
  10. Return immutable AivoSdk

The resulting AivoSdk is immutable and thread-safe.
```

### Build-Time Validation
All validation is **aggregated**. The builder does not throw on the first error. It collects all problems and throws `ConfigurationException(problems)` with the full list. This is a significant DX improvement — developers see all their mistakes at once instead of fixing one error and discovering the next.

---

## 19. Testing Architecture

### Test Pyramid
```
                  ┌─────────────┐
                  │  Live tests │  (skipped in CI; require env vars)
                 ┌┴─────────────┴┐
                 │ Integration   │  (samples smoke tests)
                ┌┴───────────────┴┐
                │ Contract tests  │  (LlmProviderContractTest; MockEngine)
               ┌┴─────────────────┴┐
               │  Unit tests       │  (FakeLlmProvider, fixtures, Turbine)
              ┌┴───────────────────┴┐
              │  Architecture tests │  (forbidden edges, commonMain purity)
             └─────────────────────┘
```

### FakeLlmProvider
`FakeLlmProvider` is the key testing primitive. It implements `LlmProvider` and allows tests to script responses:
```kotlin
val fake = FakeLlmProvider(
    responses = listOf(
        FakeResponse.Text("The balance is $500."),
        FakeResponse.ToolCall(name = "get_balance", arguments = { "account_id" to "ACC-001" }),
        FakeResponse.Text("The balance is $500."),  // second call after tool result
    )
)
```
The runtime loop sees it as a real `LlmProvider` — the Liskov substitution principle makes this work.

### Contract Test
`LlmProviderContractTest` is an **abstract test class**. Every provider module has a test class that:
1. Extends `LlmProviderContractTest`
2. Provides a `MockEngine` pre-loaded with fixture responses
3. Gets all contract tests for free

Any behavioral change that breaks the contract (e.g., `stream()` not ending with `Completed`) will fail the contract test for that provider, catching the regression immediately.

### No Real Network in CI
- Provider tests use `MockEngine` with pre-recorded JSON fixtures.
- `FakeLlmProvider` is used for all runtime tests.
- `FixedClock` and `SequentialIdGenerator` make tests deterministic.
- Virtual time (kotlinx-coroutines-test) replaces real delays in retry/backoff tests.

---

## 20. SOLID Compliance Review

| Principle | Evidence |
|---|---|
| **S**ingle Responsibility | `WireProtocol` only maps JSON; `HttpLlmProvider` only does HTTP; `SseDecoder` only frames bytes; `ToolExecutor` only runs tools; `AgentRuntime` only orchestrates the loop; `StreamAssembler` only accumulates deltas |
| **O**pen/Closed | Adding a new provider = new `WireProtocol` + factory. Adding a new tool = new `Tool`. Adding a new memory backend = new `MemoryStore`. Adding a new context strategy = new `ContextWindowStrategy`. **Zero edits to existing files.** |
| **L**iskov | `LlmProviderContractTest` enforces the behavioral contract for every `LlmProvider`. `FakeLlmProvider` passes the same contract tests as real providers. |
| **I**nterface Segregation | `MemoryStore` has 3 methods; `Logger` has 1 method; `CredentialsProvider` has 1 method; `Clock` has 1 method; `IdGenerator` has 1 method. Interceptors are separate interfaces, not one fat `RunHook`. |
| **D**ependency Inversion | `AgentRuntime` imports `LlmProvider` (interface from `sdk-core`), never `OllamaProvider` (concrete from `sdk-provider-ollama`). The composition root in `sdk` is the only place concrete types appear together. |

---

## 21. Adding a New Provider (Extension Contract)

This is the acid test for the architecture. You should be able to add a new LLM provider **without modifying any existing file** except adding a DSL extension function in `sdk`.

### Step-by-Step

**1. Create the module**
```
sdk-provider-mymodel/
└── src/commonMain/
    ├── MyModelWireProtocol.kt
    └── MyModelProvider.kt
```

**2. Implement `WireProtocol`**
```kotlin
internal class MyModelWireProtocol : WireProtocol {
    override val framing = StreamFraming.SSE

    override fun endpoint(request: LlmRequest, stream: Boolean) =
        Endpoint(method = HttpMethod.Post, path = "/v1/chat")

    override fun encode(request: LlmRequest, stream: Boolean): JsonObject {
        // Map domain request → MyModel JSON body
    }

    override fun decode(body: String): LlmResponse {
        // Parse MyModel response → domain LlmResponse
    }

    override fun decodeStream(frames: Flow<RawFrame>): Flow<LlmStreamEvent> {
        // Translate SSE/NDJSON frames → LlmStreamEvent deltas
    }

    override fun mapError(status: Int, body: String, headers: Headers): ProviderException {
        // Map HTTP errors → typed exceptions
    }
}
```

**3. Expose a factory**
```kotlin
public class MyModelConfig(
    val id: ProviderId,
    val baseUrl: String = "https://api.mymodel.com",
    val credentials: CredentialsProvider,
)

public fun MyModelProvider(config: MyModelConfig): LlmProvider =
    HttpLlmProvider(
        id = config.id,
        capabilities = ProviderCapabilities(streaming = true, toolCalling = true, …),
        transport = HttpTransport(HttpClientFactory.create(TransportConfig(…)), config.baseUrl),
        protocol = MyModelWireProtocol()
    )
```

**4. Add DSL extension in `sdk`**
```kotlin
// In sdk module only
fun ProvidersBuilder.myModel(id: String, block: MyModelConfig.() -> Unit) {
    register(MyModelProvider(MyModelConfig(ProviderId(id)).apply(block)))
}
```

**5. Extend `LlmProviderContractTest`**
```kotlin
class MyModelProviderTest : LlmProviderContractTest() {
    override fun createProvider() = MyModelProvider(
        MyModelConfig(id = ProviderId("test"), credentials = NoCredentials)
    )
    override fun mockEngine() = MockEngine { /* load fixtures */ }
}
```

**6. Add notes to `docs/providers.md`**

That's it. The test proves the invariant: zero edits to `sdk-core`, `sdk-runtime`, `sdk-transport`, or any other provider module.
