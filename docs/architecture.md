# Aivo SDK — Architecture Guide

## 1. Overview
Aivo SDK is a provider-agnostic, production-grade Agentic AI SDK built for Kotlin Multiplatform (JVM, Android, iOS).

### Design Philosophy
- **Clean Architecture:** Inward-pointing dependencies. Core has zero dependencies on HTTP, Ktor, or vendors.
- **SOLID Compliance:** High cohesion and loose coupling. New capabilities are added by creating new implementations, not modifying existing core code.
- **Unified DX:** Level 1 (raw model), Level 2 (single agent with tools), Level 3 (multi-agent supervisor with streaming).

---

## 2. Layer Map

```
┌──────────────────────────────────────────────────────────────┐
│  LAYER 4 — Facade / Composition Root                         │
│  sdk: AivoSdk DSL, iOS shim, public facade                   │
├──────────────────────────────────────────────────────────────┤
│  LAYER 3 — Application Services                              │
│  sdk-runtime:      agent loop, tools, memory, delegation     │
│  sdk-agent-config: Markdown/JSON agent loaders, ResourceReader│
│  sdk-middleware:   retry, logging, telemetry decorators      │
├──────────────────────────────────────────────────────────────┤
│  LAYER 2 — Infrastructure Adapters                           │
│  sdk-provider-ollama                                         │
│  sdk-provider-openai-compatible (and OpenRouter preset)      │
│  sdk-provider-gemini (Interactions API)                      │
│  sdk-transport: Ktor, auth strategies, SSE/NDJSON decoders   │
├──────────────────────────────────────────────────────────────┤
│  LAYER 1 — Domain / Ports                                    │
│  sdk-core: immutable model + ports (interfaces) only         │
└──────────────────────────────────────────────────────────────┘
```

---

## 3. Dependency Rules
1. `sdk-core` depends on nothing outside Kotlin standard libraries, coroutines, and serialization.
2. `sdk-runtime` depends only on `sdk-core`. It never knows Ktor or HTTP exists.
3. `sdk-provider-*` modules depend on `sdk-core` and `sdk-transport`. They never depend on `sdk-runtime`.
4. Concrete wiring occurs strictly within the composition root (`sdk`).

---

> For a complete, deep-dive explanation with sequence diagrams, layer workflows, and model architecture, see the [SDK Architecture Explained Guide](SDK_ARCHITECTURE_EXPLAINED.md).
