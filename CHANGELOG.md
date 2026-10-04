# Changelog

All notable changes to this project will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-09-27

### Added
- **Core Domain & Ports (`sdk-core`):**
  - Immutable domain model: `Message` (`System`, `User`, `Assistant`, `Tool`), `ContentPart`, `ToolCall`, `ToolResult`, `Usage`, `FinishReason`, `ModelRef`, `ProviderId`, `ConversationId`.
  - Port abstractions: `LlmProvider`, `Tool`, `MemoryStore`, `Logger`, `Telemetry`, `Clock`, `IdGenerator`, `CredentialsProvider`.
  - `StreamAssembler` for unified token and delta accumulation across all providers.
  - Complete sealed `SdkException` hierarchy.
  - `SecretString` security type with masked `toString()` and zero serialization.
- **Transport Layer (`sdk-transport`):**
  - Generic `HttpLlmProvider` executing HTTP mechanics, auth, status checks, error mapping.
  - `SseDecoder` handling multi-line SSE frames, comments, and `[DONE]` terminators.
  - `NdjsonDecoder` handling newline-delimited JSON frames and mid-stream errors.
  - Pluggable `AuthStrategy` (`BearerTokenAuth`, `HeaderKeyAuth`, `NoAuth`).
- **Provider Adapters:**
  - `sdk-provider-ollama`: Ollama NDJSON wire protocol with tool calling and reasoning support.
  - `sdk-provider-openai-compatible`: OpenAI-compatible wire protocol with OpenRouter preset (`HTTP-Referer`, `X-Title`).
  - `sdk-provider-gemini`: Google Gemini Interactions API (`/v1beta/interactions`) with thought signatures and stateful sessions.
- **Middleware Decorators (`sdk-middleware`):**
  - `RetryingLlmProvider`: Exponential backoff with jitter and pre-first-event streaming rule.
  - `LoggingLlmProvider`: Request lifecycle logging with opt-in payload logging passed through `Redactor`.
  - `TelemetryLlmProvider`: Structured telemetry recording for durations, costs, and token accounting.
- **Agent Runtime (`sdk-runtime`):**
  - Orchestrator agent loop with full streaming support.
  - `ToolExecutor` pipeline: Lookup -> Validate -> Policy Gate -> Timeout -> Execute -> Truncate.
  - JSON Schema validation DSL subset.
  - Multi-agent delegation via `AgentAsToolStrategy` with cycle detection and depth bounds.
  - Memory and context strategies: `KeepAllStrategy`, `SlidingWindowStrategy`, `TokenBudgetStrategy`.
  - Strict and lenient `PromptRenderer`.
- **Agent Configuration (`sdk-agent-config`):**
  - Markdown agent loader with YAML frontmatter.
  - JSON agent loader.
  - Programmatic Kotlin DSL loader (`agent { ... }`).
  - Platform `ResourceReader` for Android assets, iOS bundle, and JVM classpath/file.
- **Facade (`sdk`):**
  - High-level composition root DSL: `AivoSdk { ... }`.
  - Unified Level 1, Level 2, and Level 3 DX.
  - iOS interop shim with `FlowAdapter` and `CancellableJob`.
- **Samples & Testing:**
  - `samples/cli-jvm`: Banking supervisor CLI scenario.
  - `androidApp`: Jetpack Compose streaming chat UI.
  - Contract test suite (`LlmProviderContractTest`) and pre-recorded JSON fixture library.
