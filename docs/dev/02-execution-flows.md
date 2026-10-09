# Execution Flows & Subsystem Lifecycles

This document details the exact execution paths, data transformations, and sequence diagrams for all major flows in the Aivo SDK.

---

## ⚡ Flow 1: Level 1 — Raw LLM Client Call

Used when interacting directly with a language model without spawning agent reasoning loops or maintaining state.

```mermaid
sequenceDiagram
    autonumber
    actor App as Application Code
    participant SDK as AivoSdkImpl
    participant Client as DefaultLlmClient
    participant MW as Middleware Chain
    participant HTTP as HttpLlmProvider
    participant Wire as WireProtocol
    participant Assembler as StreamAssembler

    App->>SDK: sdk.llm("gemini:gemini-2.0-flash").stream("Hello")
    SDK->>SDK: Resolve provider by ProviderId ("gemini")
    SDK->>Client: DefaultLlmClient(model, provider)
    Client->>MW: provider.stream(LlmRequest)
    Note over MW: Telemetry -> Logging -> Retrying
    MW->>HTTP: HttpLlmProvider.stream(request)
    HTTP->>Wire: protocol.endpoint(request, stream=true)
    HTTP->>Wire: protocol.encode(request, stream=true)
    HTTP->>HTTP: Execute Ktor POST with AuthStrategy
    HTTP->>Wire: protocol.decodeStream(rawFrames)
    HTTP->>Assembler: assembler.assemble(events)
    Assembler-->>App: Emit TextDelta, ReasoningDelta...
    Assembler-->>App: Emit Completed(LlmResponse)
```

### Trace Details
1. **Entry:** App calls `sdk.llm(model).generate(prompt)` or `sdk.llm(model).stream(prompt)`.
2. **Provider Resolution:** Resolves the decorated [`LlmProvider`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/port/Ports.kt#L31) from `providers` map using `model.provider`.
3. **Middleware Pipeline:**
   - [`TelemetryLlmProvider`](file:///d:/Projects/AivoSdk/sdk-middleware/src/commonMain/kotlin/com/aivo/sdk/middleware/TelemetryLlmProvider.kt): Emits `TelemetryEvent.LlmCallStarted`.
   - [`LoggingLlmProvider`](file:///d:/Projects/AivoSdk/sdk-middleware/src/commonMain/kotlin/com/aivo/sdk/middleware/LoggingLlmProvider.kt): Logs the outgoing request (with credentials and payloads sanitized via [`Redactor`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/port/Ports.kt#L410)).
   - [`RetryingLlmProvider`](file:///d:/Projects/AivoSdk/sdk-middleware/src/commonMain/kotlin/com/aivo/sdk/middleware/RetryingLlmProvider.kt): Prepares retry loop with exponential backoff and jitter.
4. **Transport & Framing:** [`HttpLlmProvider`](file:///d:/Projects/AivoSdk/sdk-transport/src/commonMain/kotlin/com/aivo/sdk/transport/HttpLlmProvider.kt) delegates payload encoding and framing (SSE/NDJSON) to the provider's [`WireProtocol`](file:///d:/Projects/AivoSdk/sdk-transport/src/commonMain/kotlin/com/aivo/sdk/transport/protocol/WireProtocol.kt).
5. **Stream Assembly:** [`StreamAssembler`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/stream/StreamAssembler.kt) accumulates deltas, guarantees that all tokens reach the caller in real-time, and generates a terminal `LlmStreamEvent.Completed` event.

---

## 🔄 Flow 2: Level 2 — ReAct Agent Loop & Tool Calling

The standard autonomous agent cycle: Think -> Act on tool calls -> Think -> Finish.

```mermaid
sequenceDiagram
    autonumber
    actor App as Application Code
    participant Runtime as AgentRuntime
    participant Ctx as DefaultLoopContext
    participant Loop as ToolCallingLoop
    participant Model as LLM Provider
    participant ToolExec as ToolExecutor
    participant Mem as MemoryStore

    App->>Runtime: sdk.chat(convId, "What's my balance?")
    Runtime->>Runtime: Acquire per-conversation mutex lock
    Runtime->>Mem: load(convId)
    Runtime->>Ctx: Init history + user message
    Runtime->>Loop: loop.run(loopContext)

    loop ReAct Execution Cycle
        Loop->>Ctx: think()
        Ctx->>Model: provider.stream(LlmRequest)
        Model-->>Ctx: Stream response with ToolCall("get_balance")
        Ctx-->>Loop: Return Turn.Call(calls)

        Loop->>Ctx: act(calls)
        Ctx->>ToolExec: execute(tool, call, context)
        ToolExec-->>Ctx: Return ToolResult.Success(balance)
        Ctx->>Ctx: Record Tool message in history
    end

    Loop->>Ctx: think() [Synthesis Turn]
    Ctx->>Model: provider.stream(LlmRequest with Tool results)
    Model-->>Ctx: "Your balance is $150."
    Ctx-->>Loop: Return Turn.Text("Your balance is $150.")
    Loop->>Ctx: finish(text)
    Ctx-->>Runtime: Return LoopOutcome.Completed

    Runtime->>Mem: append(convId, newMessages) [Atomic write]
    Runtime->>Runtime: Release conversation mutex lock
    Runtime-->>App: Return AgentResult
```

### Trace Details
1. **Concurrency Lock:** [`AgentRuntime`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/agent/AgentRuntime.kt) acquires `getConversationLock(conversationId)` to prevent race conditions within the same conversation.
2. **Context Assembly:** [`DefaultLoopContext.think()`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/agent/DefaultLoopContext.kt#L112) trims history via [`ContextWindowStrategy`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/memory/context/ContextWindowStrategy.kt), applies prompt templates, resolves authorized tools, and invokes the model.
3. **Tool Dispatch:** When the model outputs tool calls, [`ToolCallingLoop`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/agent/ToolCallingLoop.kt) executes them via [`DefaultLoopContext.act()`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/agent/DefaultLoopContext.kt#L232).
4. **Loop Protection:** If the model repeats the exact same tool calls or exceeds `maxConsecutiveToolCalls`, `ToolCallingLoop` forces a final turn with `ToolSelection.None` to compel textual synthesis.
5. **Persistence:** On `LoopOutcome.Completed`, all messages generated during the run are appended to [`MemoryStore`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/port/Ports.kt#L114) in a single atomic transaction.

---

## 👥 Flow 3: Level 3 — Multi-Agent Supervisor & Model-Driven Delegation

A supervisor agent delegates tasks to specialized sub-agents dynamically based on user intent.

```mermaid
graph TD
    UserQuery["User Input: 'Research battery specs and format as table'"] --> Supervisor["Supervisor Agent (lead)"]
    Supervisor -->|"1. Model decides to call tool: delegate_to_researcher"| ResearcherTool["DelegatedAgentTool(researcher)"]
    ResearcherTool -->|"2. Spawn child run with isolated context"| ResearcherAgent["Researcher Agent (researcher)"]
    ResearcherAgent -->|"3. Executes tools & compiles findings"| ResearcherResult["Research Notes Output"]
    ResearcherResult -->|"4. Returns tool output string"| ResearcherTool
    ResearcherTool -->|"5. Appended as ToolResult to Supervisor history"| Supervisor
    Supervisor -->|"6. Model decides to call tool: delegate_to_writer"| WriterTool["DelegatedAgentTool(writer)"]
    WriterTool -->|"7. Spawn child run with notes"| WriterAgent["Writer Agent (writer)"]
    WriterAgent -->|"8. Formats Markdown table"| WriterResult["Formatted Output"]
    WriterResult --> Supervisor
    Supervisor --> FinalAnswer["Final AgentResult delivered to caller"]
```

### Trace Details
1. **Dynamic Tool Generation:** When an agent specifies `delegates("researcher", "writer")`, [`DefaultLoopContext`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/agent/DefaultLoopContext.kt#L363) automatically manufactures [`DelegatedAgentTool`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/delegation/AgentAsToolStrategy.kt) instances named `delegate_to_<agentId>`.
2. **Cycle & Depth Protection:** Before executing a child agent, `DelegatedAgentTool` verifies:
   - `!currentAgentPath.contains(childId)` (prevents recursion loops; throws [`DelegationCycleException`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/error/Exceptions.kt)).
   - `currentAgentPath.size <= maxDelegationDepth` (prevents stack overflow; throws [`MaxDelegationDepthExceededException`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/error/Exceptions.kt)).
3. **Child Context Isolation:** Child agents run in an isolated child conversation (`${conversationId}_${childId}`) so sub-agent history does not pollute the supervisor's context window.
4. **Blackboard State Sharing:** If child agents define `saveResultAs`, their results are stored in the shared [`RunState`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/model/RunState.kt) blackboard accessible to all agents in the run.

---

## 🔀 Flow 4: Level 4 — Deterministic Workflows & Custom Reasoning Loops

When orchestration logic must be strictly controlled in code rather than left to LLM discretion.

```kotlin
// Example: Workflow DSL
val pipeline = agent("pipeline") {
    loop = workflow(researchAgent, writerAgent) {
        val notes = run(researchAgent, input)
        val draft = run(writerAgent, "Write response based on:\n$notes")
        finish(draft)
    }
}
```

```mermaid
sequenceDiagram
    autonumber
    participant App as Application
    participant WLoop as WorkflowScope
    participant Ctx as DefaultLoopContext
    participant Ag1 as Agent 1 (Researcher)
    participant Ag2 as Agent 2 (Writer)

    App->>WLoop: pipeline.run(input)
    WLoop->>WLoop: run(researchAgent, input)
    WLoop->>Ctx: context.delegate(researchAgent, input)
    Ctx->>Ag1: Execute Researcher Agent Loop
    Ag1-->>Ctx: Return research text
    Ctx-->>WLoop: Return notes
    WLoop->>WLoop: run(writerAgent, "Draft from: " + notes)
    WLoop->>Ctx: context.delegate(writerAgent, task)
    Ctx->>Ag2: Execute Writer Agent Loop
    Ag2-->>Ctx: Return polished prose
    Ctx-->>WLoop: Return draft
    WLoop->>Ctx: finish(draft)
    Ctx-->>App: LoopOutcome.Completed
```

### Prebuilt Loops Available:
* [`SingleTurnLoop`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/agent/PrebuiltLoops.kt#L14): Disables tool calling (`ToolSelection.None`) for pure single-shot generation.
* [`SequenceLoop`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/agent/PrebuiltLoops.kt#L32): Executes a chain of agents sequentially, feeding each output as the next input.
* [`RouterLoop`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/agent/PrebuiltLoops.kt#L52): Runs a custom selector lambda on user input to choose a single destination agent.
* [`ParallelLoop`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/agent/PrebuiltLoops.kt#L67): Executes multiple agents concurrently via `context.parallel()` and merges results using a combiner function.

---

## 🛡️ Flow 5: Tool Execution Safety Pipeline

Every tool call generated by an LLM passes through a 6-stage security and validation pipeline in [`ToolExecutor`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/tool/ToolExecutor.kt).

```mermaid
graph TD
    Call["Incoming ToolCall(name, arguments)"] --> Reg["1. Registry Lookup: Tool registered?"]
    Reg -->|No| FailUnknown["Return ToolResult.Failure(UNKNOWN_TOOL)"]
    Reg -->|Yes| Val["2. Schema Validation (ToolValidator)"]
    Val -->|Invalid Types / Missing Fields| FailArgs["Return ToolResult.Failure(INVALID_ARGUMENTS)"]
    Val -->|Valid| Policy["3. Policy Gate (ToolPolicy.decide)"]
    Policy -->|PolicyDecision.Deny| FailDeny["Return ToolResult.Failure(DENIED)"]
    Policy -->|PolicyDecision.RequireConfirmation| Gate["4. ConfirmationHandler.confirm()"]
    Gate -->|User Denied / Timeout| FailConfirm["Return ToolResult.Failure(DENIED)"]
    Gate -->|User Approved| Exec["5. Tool Execution with withTimeout"]
    Policy -->|PolicyDecision.Allow| Exec
    Exec -->|Timed Out| FailTimeout["Return ToolResult.Failure(TIMEOUT)"]
    Exec -->|Exception Thrown| FailExec["Return ToolResult.Failure(EXECUTION_FAILED)"]
    Exec -->|Success| Trunc["6. Result Truncation (truncateIfNeeded)"]
    Trunc --> SuccessResult["Emit ToolCallExecuted & Return ToolResult.Success"]
```

### Pipeline Guarantees
1. **Never Throws:** Tool implementations cannot crash the agent loop; any unhandled exception (except `CancellationException`) is caught and packaged as [`ToolResult.Failure`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/model/LlmTypes.kt).
2. **Safe Confirmation:** If a tool has `requiresConfirmation = true` or `ToolRisk.HIGH`, execution suspends until [`ConfirmationHandler`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/port/Ports.kt#L356) returns `true`.
3. **Token Guard:** Strings in `ToolResult.Success` exceeding `maxToolResultChars` are safely truncated to protect token budgets.

---

## 🧠 Flow 6: Memory & Context Window Lifecycle

How conversations are loaded, pruned to fit token limits, and persisted.

```mermaid
sequenceDiagram
    autonumber
    participant Runtime as AgentRuntime
    participant Store as MemoryStore
    participant Strat as ContextWindowStrategy
    participant Ctx as DefaultLoopContext
    participant LLM as Provider

    Runtime->>Store: load(conversationId)
    Store-->>Runtime: Return List<Message> (chronological)
    Runtime->>Ctx: Append history + current User message
    Ctx->>Strat: select(history)
    Note over Strat: Prune based on Strategy<br/>KeepAll / SlidingWindow / TokenBudget
    Strat-->>Ctx: Return selectedHistory
    Ctx->>LLM: Send [SystemPrompt + selectedHistory]
    LLM-->>Ctx: Complete assistant turn
    Runtime->>Store: append(conversationId, newMessagesSinceRunStart)
```

### Strategies in [`ContextWindowStrategy.kt`](file:///d:/Projects/AivoSdk/sdk-runtime/src/commonMain/kotlin/com/aivo/sdk/runtime/memory/context/ContextWindowStrategy.kt):
1. **`KeepAllStrategy`:** Returns all messages unmodified. Best for short tasks.
2. **`SlidingWindowStrategy(maxMessages)`:** Retains only the most recent $N$ messages. Always preserves the first system prompt if present.
3. **`TokenBudgetStrategy(maxTokens, estimator)`:** Counts tokens backwards from newest to oldest using [`TokenEstimator`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/port/Ports.kt#L335) until the budget is reached. Never drops an assistant tool call without its corresponding tool result message.

---

## 🌐 Flow 7: Wire Protocol & Transport Dispatch

How domain models are translated to provider-specific HTTP bodies and reconstructed from network streams.

```mermaid
graph LR
    Req["LlmRequest (Core Model)"] -->|"protocol.encode()"| WireReq["Provider JSON Payload"]
    WireReq -->|"Ktor HTTP POST"| WireStream["Raw Byte Stream"]
    WireStream -->|"SseDecoder / NdjsonDecoder"| Frames["RawFrame.Data / RawFrame.Done"]
    Frames -->|"protocol.decodeStream()"| DomainEvents["LlmStreamEvent Deltas"]
    DomainEvents -->|"StreamAssembler.assemble()"| CompleteStream["Flow<LlmStreamEvent> + Completed"]
```

### Key Components:
* **Framing Decoupling:** [`SseDecoder`](file:///d:/Projects/AivoSdk/sdk-transport/src/commonMain/kotlin/com/aivo/sdk/transport/decoder/SseDecoder.kt) and [`NdjsonDecoder`](file:///d:/Projects/AivoSdk/sdk-transport/src/commonMain/kotlin/com/aivo/sdk/transport/decoder/NdjsonDecoder.kt) handle HTTP line chunking and SSE syntax (`data: `, `event: `, `[DONE]`).
* **Frame Translation:** [`WireProtocol.decodeStream()`](file:///d:/Projects/AivoSdk/sdk-transport/src/commonMain/kotlin/com/aivo/sdk/transport/protocol/WireProtocol.kt) parses JSON payloads into domain events (`TextDelta`, `ToolCallStarted`, `ToolCallArgumentsDelta`).
* **Assembler Guarantees:** [`StreamAssembler`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/stream/StreamAssembler.kt) guarantees that downstream consumers always receive a well-formed `LlmStreamEvent.Completed` event containing the aggregated `LlmResponse` and usage metrics.

---

## 📄 Flow 8: Declarative Agent Loading Lifecycle

How agents declared in external files are loaded into runtime structures.

```mermaid
graph TD
    File["Markdown (.md) with YAML Frontmatter"] --> Loader["MarkdownAgentLoader.parse(content)"]
    Loader --> Split["Extract frontmatter lines between '---'"]
    Split --> Yaml["parseSimpleYaml(): Extract id, role, model, tools, delegates"]
    Split --> Prompt["Body lines -> System Prompt"]
    Yaml --> Val["Validate required fields (id, valid ModelRef)"]
    Val --> Def["AgentDefinition (Immutable)"]
    Def --> Reg["Registered in AgentRegistry via SDK Builder"]
```

### Error Handling:
If frontmatter delimiters (`---`) are missing, or required fields like `id` are absent, [`MarkdownAgentLoader`](file:///d:/Projects/AivoSdk/sdk-agent-config/src/commonMain/kotlin/com/aivo/sdk/agent/config/MarkdownAgentLoader.kt) throws [`ConfigurationException`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/error/Exceptions.kt) with an explicit list of validation issues.
