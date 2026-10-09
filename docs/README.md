# 📚 Aivo SDK Documentation

Welcome to the documentation repository for the **Aivo SDK** (`io.github.abdallah-elsobky:aivo-sdk`), the production-grade, provider-agnostic Agentic AI SDK for Kotlin Multiplatform (Android, JVM, iOS).

Please select the documentation guide relevant to your goals:

---

## 🚀 [User Guide (`docs/user-guide/`)](user-guide/README.md)
**For developers integrating Aivo SDK into mobile, desktop, or backend applications.**

* [01. Getting Started & Installation](user-guide/01-getting-started.md) — Gradle dependencies, acquiring API keys, and secure key storage.
* [02. Quick Start](user-guide/02-quick-start.md) — One-line setup (`AivoSdk.create`), Builder DSL, and raw LLM calls.
* [03. Providers & Models](user-guide/03-providers-and-models.md) — Ollama, Gemini, OpenRouter, and custom endpoints; typed catalogs vs custom strings.
* [04. Agents & Multi-Agent Teams](user-guide/04-agents-and-teams.md) — Kotlin DSL, Markdown frontmatter, JSON, and supervisor delegation.
* [05. Tools & Capabilities](user-guide/05-tools-and-capabilities.md) — Type-safe tool definitions, JSON Schema parameters, and human confirmation gates.
* [06. Real-Time Streaming & UI](user-guide/06-streaming-and-ui.md) — Kotlin `Flow<AgentEvent>`, Jetpack Compose ViewModel, and iOS Swift integration.
* [07. Memory & Context Strategies](user-guide/07-memory-and-context.md) — In-memory and Room/SQLDelight stores, sliding window, token budget, and atomic tool pairing.
* [08. Resilience & Security](user-guide/08-resilience-and-security.md) — Exponential backoff, timeouts, concurrency queue, and `SecretString` redaction.
* [09. Testing Your App](user-guide/09-testing-your-app.md) — Offline unit testing of ViewModels with `FakeLlmProvider`.
* [10. Custom Reasoning Loops](user-guide/10-custom-agents-and-loops.md) — Custom reasoning loops, workflow pipelines, and agent roles.

---

## 🛠️ [SDK Development & Architecture Guide (`docs/dev/`)](dev/README.md)
**For contributors, maintainers, and engineers working on the internals of the Aivo SDK itself.**

* [01. Architecture & File Inventory](dev/01-architecture-and-file-inventory.md) — Hexagonal layer map (Layers 1–4), inward dependency rule, and file-by-file blueprint.
* [02. Execution Flows & Lifecycles](dev/02-execution-flows.md) — Sequence diagrams for Level 1–4 execution, tool safety pipeline, memory lifecycle, and framing decoders.
* [03. Debugging & Troubleshooting Playbook](dev/03-debugging-and-troubleshooting-playbook.md) — Diagnosing streaming glitches, tool validation errors, concurrency deadlocks, and cancellation invariants.
* [04. Extending the SDK](dev/04-extending-the-sdk.md) — Adding new LLM providers, custom wire protocols, and custom memory backends.
* [Architecture Decision Records (ADR)](dev/adr/) — Historical log of fundamental architectural decisions (`0001`, `0002`, `0003`).
