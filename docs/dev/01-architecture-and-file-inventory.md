# Module & File-by-File Blueprint

This document contains an exhaustive breakdown of every module and file across the Aivo SDK. For each file, you will find its exact responsibility, how to modify it safely without violating system invariants, and specific tips for isolating and resolving bugs.

---

## 📦 1. `sdk-core` — Pure Domain Models & Ports

> **Location:** [`sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core)  
> **Rule:** This is the core kernel. It has **no dependencies** on outer layers (no Ktor, no provider logic, no runtime execution engine). It defines the vocabulary of the entire SDK.

### Errors & Serialization
* [`SdkJson.kt`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/SdkJson.kt)
  * **Role:** Shared, pre-configured `kotlinx.serialization.json.Json` instance (`ignoreUnknownKeys = true`, `encodeDefaults = true`, `explicitNulls = false`).
  * **How to edit:** Keep serialization settings lenient to prevent breaking changes when LLM providers add new JSON fields.
  * **Bug fixing:** If JSON parsing crashes with `UnknownKeyException` in any module, ensure it uses `SdkJson` instead of a vanilla `Json { }` instance.
* [`error/Exceptions.kt`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/error/Exceptions.kt)
  * **Role:** Central domain exception hierarchy rooted at [`SdkException`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/error/Exceptions.kt). Subclasses include [`ProviderException`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/error/Exceptions.kt) (with `retryable` flag and optional `retryAfterMs`), [`ConfigurationException`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/error/Exceptions.kt), [`AgentRunException`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/error/Exceptions.kt), [`RunTimeoutException`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/error/Exceptions.kt), [`DelegationCycleException`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/error/Exceptions.kt), and [`MaxStepsExceededException`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/error/Exceptions.kt).
  * **How to edit:** All new failure conditions must extend `SdkException`. Never allow external HTTP or IO exceptions to bubble past provider boundaries.
  * **Bug fixing:** Check `retryable` flags: transient errors (HTTP 429, 502, 503, 504) must have `retryable = true`; authentication errors (401, 403) and invalid payloads (400) must have `retryable = false`.

### Domain Models
* [`model/Identifiers.kt`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/model/Identifiers.kt)
  * **Role:** Strongly-typed value classes for identifiers: [`ConversationId`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/model/Identifiers.kt), [`ProviderId`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/model/Identifiers.kt), and [`ModelRef`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/model/Identifiers.kt) (parses `provider:model` strings).
  * **How to edit:** `ModelRef.parse(raw)` validates format `"provider:model"`. Maintain backward compatibility when parsing qualified strings.
  * **Bug fixing:** If an agent cannot find its provider, verify `ModelRef.parse()` correctly parsed the delimiter.
* [`model/Message.kt`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/model/Message.kt)
  * **Role:** Canonical representation of messages: `Message.System`, `Message.User`, `Message.Assistant`, and `Message.Tool`. Contains polymorphic content parts (text, reasoning) and metadata.
  * **How to edit:** Keep models immutable. `Message.Assistant` contains `toolCalls: List<ToolCall>` and `reasoning: String?`.
  * **Bug fixing:** If tool calls do not get sent back in context, check whether `Message.Tool` has the exact matching `callId`.
* [`model/LlmTypes.kt`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/model/LlmTypes.kt)
  * **Role:** Core LLM contracts: [`LlmRequest`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/model/LlmTypes.kt), [`LlmResponse`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/model/LlmTypes.kt), [`LlmStreamEvent`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/model/LlmTypes.kt) (`TextDelta`, `ReasoningDelta`, `ToolCallStarted`, `ToolCallArgumentsDelta`, `Completed`), [`ToolCall`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/model/LlmTypes.kt), [`ToolResult`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/model/LlmTypes.kt), and [`ProviderCapabilities`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/model/LlmTypes.kt).
  * **How to edit:** Any new streaming event type must be added here and handled in [`StreamAssembler.kt`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/stream/StreamAssembler.kt).
  * **Bug fixing:** Ensure `ToolCallArgumentsDelta` uses the integer `index` matching `ToolCallStarted`.
* [`model/AgentRole.kt`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/model/AgentRole.kt) & [`model/PromptCatalog.kt`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/model/PromptCatalog.kt)
  * **Role:** Built-in agent personas (`ASSISTANT`, `RESEARCHER`, `WRITER`, `SUPERVISOR`, `ORCHESTRATOR`, `SUPPORT`) and prompt catalog guidelines.
* [`model/RunState.kt`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/model/RunState.kt)
  * **Role:** Type-safe blackboard state interface ([`RunState`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/model/RunState.kt) and [`StateKey<T>`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/model/RunState.kt)) passed across agents in collaborative runs.

### Ports (Interfaces)
* [`port/Ports.kt`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/port/Ports.kt)
  * **Role:** Core architectural interfaces:
    * [`LlmProvider`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/port/Ports.kt#L31): Contract for LLM integrations (`generate` and `stream`).
    * [`Tool`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/port/Ports.kt#L70): Callable capability with `ToolSpec` and `execute(call, context)`.
    * [`MemoryStore`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/port/Ports.kt#L114): History persistence (`load`, `append`, `clear`).
    * [`Logger`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/port/Ports.kt#L148) & [`Telemetry`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/port/Ports.kt#L184): Observability abstractions.
    * [`CredentialsProvider`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/port/Ports.kt#L319): Dynamic credential loader.
    * [`ToolPolicy`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/port/Ports.kt#L387) & [`ConfirmationHandler`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/port/Ports.kt#L356): Safety gates.
* [`port/AgentLoop.kt`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/port/AgentLoop.kt)
  * **Role:** Defines the control contract between reasoning loops and the runtime environment: [`AgentLoop`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/port/AgentLoop.kt#L67), [`LoopContext`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/port/AgentLoop.kt#L78), [`Turn`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/port/AgentLoop.kt#L31), and [`LoopOutcome`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/port/AgentLoop.kt#L48).

### Stream Assembly & Utilities
* [`stream/StreamAssembler.kt`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/stream/StreamAssembler.kt)
  * **Role:** Accumulates streaming delta events into a synthetic final [`LlmStreamEvent.Completed`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/model/LlmTypes.kt) containing the fully built `LlmResponse`.
  * **How to edit:** Buffers text, reasoning, and fragmented tool-call JSON arguments into `StringBuilder`s keyed by tool index.
  * **Bug fixing:** If tool calls produced by streaming models fail JSON parsing, verify `ToolCallArgumentsDelta` fragments are concatenated without inserted spaces or lost characters.
* [`util/SecretString.kt`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/util/SecretString.kt)
  * **Role:** Protects credentials in memory; overrides `toString()` to always return `"[REDACTED]"`.

---

## ⚙️ 2. `sdk-runtime` — Orchestration Engine & Loops

> **Location:** [`sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime)  
> **Rule:** Manages conversation state, agent loops, tool security boundaries, delegation trees, and memory strategies.

### Agent Orchestration
* [`agent/AgentRuntime.kt`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/agent/AgentRuntime.kt)
  * **Role:** The master execution pipeline:
    1. Generates run ID, manages root `agentPath`.
    2. Enforces per-conversation concurrency via `getConversationLock(conversationId)` (`QUEUE` or `REJECT`).
    3. Wraps run in `withTimeout(agent.limits.runTimeoutMs)`.
    4. Instantiates [`DefaultLoopContext`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/agent/DefaultLoopContext.kt) and loads memory.
    5. Dispatches execution to `agent.loop ?: ToolCallingLoop.Default`.
    6. Persists new messages to [`MemoryStore`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/port/Ports.kt#L114) atomically on `LoopOutcome.Completed`.
    7. Recursively invokes child agents on `LoopOutcome.HandedOff`.
  * **Bug fixing:** If a run hangs, check if a previous run on the same `ConversationId` completed its coroutine and unlocked its mutex in `finally`.
* [`agent/DefaultLoopContext.kt`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/agent/DefaultLoopContext.kt)
  * **Role:** Safe execution environment provided to loops:
    * `think()`: Applies context window strategy, renders system prompt with variables, resolves authorized tools, calls `provider.stream()`, emits UI deltas, and returns `Turn.Text` or `Turn.Call`.
    * `act()`: Resolves authorized tools, validates, executes via [`ToolExecutor`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/tool/ToolExecutor.kt), and records tool result messages.
    * `delegate()`: Enforces cycle detection and delegation depth limits before invoking child agents.
  * **Bug fixing:** If an agent claims a tool does not exist, verify that the tool name is in `agent.tools` or `agent.toolInstances`.
* [`agent/ToolCallingLoop.kt`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/agent/ToolCallingLoop.kt)
  * **Role:** Default ReAct reasoning loop. Alternates `think()` and `act()`. Contains cycle/loop protection: if the model calls the same tool repeatedly or exceeds `maxConsecutiveToolCalls`, it executes one final `think(tools = ToolSelection.None)` to force textual completion.
* [`agent/PrebuiltLoops.kt`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/agent/PrebuiltLoops.kt)
  * **Role:** Predefined control flows: [`SingleTurnLoop`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/agent/PrebuiltLoops.kt#L14), [`SequenceLoop`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/agent/PrebuiltLoops.kt#L32), [`RouterLoop`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/agent/PrebuiltLoops.kt#L52), [`ParallelLoop`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/agent/PrebuiltLoops.kt#L67), and the [`workflow`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/agent/PrebuiltLoops.kt#L124) DSL.
* [`agent/AgentDefinition.kt`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/agent/AgentDefinition.kt) & [`agent/AgentDefinitionBuilder.kt`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/agent/AgentDefinitionBuilder.kt)
  * **Role:** Immutable data definition of an agent and its fluent builder DSL (`agent("id") { ... }`, `supervisor("lead") { ... }`).

### Tool Execution & Validation
* [`tool/ToolExecutor.kt`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/tool/ToolExecutor.kt)
  * **Role:** Tool execution pipeline:
    1. Schema validation via [`ToolValidator`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/tool/ToolValidator.kt).
    2. Policy gate check via [`ToolPolicy`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/port/Ports.kt#L387).
    3. Human-in-the-loop confirmation via [`ConfirmationHandler`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/port/Ports.kt#L356).
    4. Timeout enforcement (`withTimeout(defaultTimeoutMs)`).
    5. Output truncation (`maxToolResultChars`).
  * **Bug fixing:** If confirmation prompts do not appear, check whether `requiresConfirmation` is set to `true` or if the tool risk level is `HIGH`.
* [`tool/ToolValidator.kt`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/tool/ToolValidator.kt) & [`tool/schema/JsonSchema.kt`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/tool/schema/JsonSchema.kt)
  * **Role:** Type-safe JSON Schema validator verifying types, required parameters, and string enums before invoking tool code.
* [`delegation/AgentAsToolStrategy.kt`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/delegation/AgentAsToolStrategy.kt)
  * **Role:** Wraps delegated sub-agents into pseudo-tools named `delegate_to_<agentId>`. Checks for delegation cycles against `currentAgentPath` and enforces `maxDepth`.

### Memory & Prompt Composition
* [`memory/context/ContextWindowStrategy.kt`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/memory/context/ContextWindowStrategy.kt)
  * **Role:** Controls which messages are retained when building requests: [`KeepAllStrategy`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/memory/context/ContextWindowStrategy.kt), [`SlidingWindowStrategy`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/memory/context/ContextWindowStrategy.kt) (last N messages), and [`TokenBudgetStrategy`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/memory/context/ContextWindowStrategy.kt) (fits within token budget).
  * **Bug fixing:** Always preserves the first system message if present, and preserves tool calls paired with their respective tool result messages.
* [`prompt/PromptRenderer.kt`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/prompt/PromptRenderer.kt) & [`prompt/SectionedPromptComposer.kt`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/prompt/SectionedPromptComposer.kt)
  * **Role:** Interpolates `{{variable}}` placeholders and composes structured system prompts from persona, instructions, guidelines, and team roster.

---

## 🌐 3. `sdk-transport` — HTTP, Framing & Wire Protocols

> **Location:** [`sdk-transport/src/commonMain/kotlin/com/aivo/sdk/transport/`](file:///d:/Projects/AivoSdk/sdk-transport/src/commonMain/kotlin/com/aivo/sdk/transport)  
> **Rule:** Encapsulates all Ktor HTTP client mechanics, SSE / NDJSON framing, and auth strategies. Contains **no provider-specific JSON structures**.

* [`HttpLlmProvider.kt`](file:///d:/Projects/AivoSdk/sdk-transport/src/commonMain/kotlin/com/aivo/sdk/transport/HttpLlmProvider.kt)
  * **Role:** The core HTTP implementation of [`LlmProvider`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/port/Ports.kt#L31). Handles non-streaming and streaming calls, applies [`AuthStrategy`](file:///d:/Projects/AivoSdk/sdk-transport/src/commonMain/kotlin/com/aivo/sdk/transport/auth/AuthStrategy.kt), catches Ktor network/timeout exceptions, decodes frames via [`SseDecoder`](file:///d:/Projects/AivoSdk/sdk-transport/src/commonMain/kotlin/com/aivo/sdk/transport/decoder/SseDecoder.kt) or [`NdjsonDecoder`](file:///d:/Projects/AivoSdk/sdk-transport/src/commonMain/kotlin/com/aivo/sdk/transport/decoder/NdjsonDecoder.kt), passes events to [`WireProtocol.decodeStream`](file:///d:/Projects/AivoSdk/sdk-transport/src/commonMain/kotlin/com/aivo/sdk/transport/protocol/WireProtocol.kt), and pipes output through [`StreamAssembler`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/stream/StreamAssembler.kt).
  * **Bug fixing:** Truncates error response bodies to 2 KB before delegating to `protocol.mapError` to prevent memory blowups on large HTML error pages.
* [`decoder/SseDecoder.kt`](file:///d:/Projects/AivoSdk/sdk-transport/src/commonMain/kotlin/com/aivo/sdk/transport/decoder/SseDecoder.kt)
  * **Role:** Parses Server-Sent Events from line flows (`data: ...`, `event: ...`, empty lines). Emits [`RawFrame.Data`](file:///d:/Projects/AivoSdk/sdk-transport/src/commonMain/kotlin/com/aivo/sdk/transport/protocol/WireProtocol.kt) and terminates on `[DONE]` with [`RawFrame.Done`](file:///d:/Projects/AivoSdk/sdk-transport/src/commonMain/kotlin/com/aivo/sdk/transport/protocol/WireProtocol.kt).
  * **Bug fixing:** If SSE streams hang without finishing, check if the provider emits a custom end frame rather than `[DONE]`.
* [`decoder/NdjsonDecoder.kt`](file:///d:/Projects/AivoSdk/sdk-transport/src/commonMain/kotlin/com/aivo/sdk/transport/decoder/NdjsonDecoder.kt)
  * **Role:** Parses newline-delimited JSON streams (used by Ollama).
* [`protocol/WireProtocol.kt`](file:///d:/Projects/AivoSdk/sdk-transport/src/commonMain/kotlin/com/aivo/sdk/transport/protocol/WireProtocol.kt)
  * **Role:** Interface separating HTTP transport from provider wire representations (`endpoint`, `encode`, `decode`, `decodeStream`, `mapError`).

---

## 🔌 4. Concrete Provider Modules

> **Location:** `sdk-provider-gemini`, `sdk-provider-ollama`, `sdk-provider-openai-compatible`  
> **Rule:** Implement [`WireProtocol`](file:///d:/Projects/AivoSdk/sdk-transport/src/commonMain/kotlin/com/aivo/sdk/transport/protocol/WireProtocol.kt) for specific LLM REST APIs.

* [`sdk-provider-gemini/GeminiWireProtocol.kt`](file:///d:/Projects/AivoSdk/sdk-provider-gemini/src/commonMain/kotlin/com/aivo/sdk/provider/gemini/GeminiWireProtocol.kt)
  * **Role:** Translates canonical requests to Google Gemini REST payloads (`generateContent` & `streamGenerateContent`). Handles function declarations, `functionCall` parts, thought blocks, and maps error codes to SDK exceptions.
* [`sdk-provider-ollama/OllamaWireProtocol.kt`](file:///d:/Projects/AivoSdk/sdk-provider-ollama/src/commonMain/kotlin/com/aivo/sdk/provider/ollama/OllamaWireProtocol.kt)
  * **Role:** Translates to Ollama `/api/chat` endpoints with NDJSON framing. Supports local models and Ollama Cloud auth.
* [`sdk-provider-openai-compatible/OpenAiCompatibleWireProtocol.kt`](file:///d:/Projects/AivoSdk/sdk-provider-openai-compatible/src/commonMain/kotlin/com/aivo/sdk/provider/openai/OpenAiCompatibleWireProtocol.kt)
  * **Role:** Translates to standard OpenAI `/v1/chat/completions` endpoints. Powers OpenRouter, Groq, Mistral, Together AI, and vLLM.

---

## 🧱 5. Middleware, Config & Umbrella Modules

* [`sdk-middleware/RetryingLlmProvider.kt`](file:///d:/Projects/AivoSdk/sdk-middleware/src/commonMain/kotlin/com/aivo/sdk/middleware/RetryingLlmProvider.kt)
  * **Role:** Decorator retrying transient failures with exponential backoff and jitter. Enforces the **Pre-First-Event Rule** on streaming calls.
* [`sdk-middleware/LoggingLlmProvider.kt`](file:///d:/Projects/AivoSdk/sdk-middleware/src/commonMain/kotlin/com/aivo/sdk/middleware/LoggingLlmProvider.kt)
  * **Role:** Logs requests, responses, and token counts. Masks credentials and sensitive data with [`Redactor`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/port/Ports.kt#L410).
* [`sdk-middleware/TelemetryLlmProvider.kt`](file:///d:/Projects/AivoSdk/sdk-middleware/src/commonMain/kotlin/com/aivo/sdk/middleware/TelemetryLlmProvider.kt)
  * **Role:** Records start, complete, and failure telemetry events.
* [`sdk-agent-config/MarkdownAgentLoader.kt`](file:///d:/Projects/AivoSdk/sdk-agent-config/src/commonMain/kotlin/com/aivo/sdk/agent/config/MarkdownAgentLoader.kt)
  * **Role:** Parses Markdown agent definition files with YAML frontmatter into [`AgentDefinition`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/agent/AgentDefinition.kt).
* [`sdk-agent-config/JsonAgentLoader.kt`](file:///d:/Projects/AivoSdk/sdk-agent-config/src/commonMain/kotlin/com/aivo/sdk/agent/config/JsonAgentLoader.kt)
  * **Role:** Deserializes JSON files into [`AgentDefinition`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/agent/AgentDefinition.kt).
* [`sdk/AivoSdkBuilder.kt`](file:///d:/Projects/AivoSdk/sdk/src/commonMain/kotlin/com/aivo/sdk/AivoSdkBuilder.kt) & [`sdk/AivoSdkImpl.kt`](file:///d:/Projects/AivoSdk/sdk/src/commonMain/kotlin/com/aivo/sdk/AivoSdkImpl.kt)
  * **Role:** Top-level DSL builder, configuration validator, dependency graph cycle checker, and runtime wiring.
* [`sdk-testing/FakeLlmProvider.kt`](file:///d:/Projects/AivoSdk/sdk-testing/src/commonMain/kotlin/com/aivo/sdk/testing/FakeLlmProvider.kt)
  * **Role:** Fully deterministic, offline test double for unit testing agents, loops, and tools without network access.
