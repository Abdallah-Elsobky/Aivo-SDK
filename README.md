# Aivo SDK
> **Production-grade, provider-agnostic Agentic AI SDK for Kotlin Multiplatform (Android, JVM, iOS)**

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20JVM%20%7C%20iOS-green.svg)](https://kotlinlang.org/docs/multiplatform.html)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.abdallah-elsobky/aivo-sdk.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.abdallah-elsobky/aivo-sdk)

Aivo SDK lets you build and compose autonomous multi-agent AI systems that run across any LLM provider — Ollama, OpenRouter, OpenAI-compatible endpoints, and Google Gemini — with unified streaming, tool calling, memory management, and production guardrails.

> **No API keys are ever hardcoded in this repository.** All keys are supplied at runtime via environment variables, `local.properties`, or your app's configuration layer.

---

## Features

| Feature | Description |
|---|---|
| **Provider Agnostic** | Ollama · OpenRouter · OpenAI-compatible · Google Gemini — one stable interface |
| **Declarative Agents** | Define agents in Kotlin DSL, Markdown (YAML frontmatter), or JSON |
| **Full Streaming** | Token deltas, reasoning events, tool-call progress — all via Kotlin Flow |
| **Multi-Agent Teams** | Supervisor delegation, cycle detection, depth limits, usage aggregation |
| **Custom Tool Calling** | Type-safe `tool { }` DSL with schema validation and risk classification |
| **Memory & Context** | Keep-all, sliding-window, and token-budget context strategies |
| **Production Guardrails** | Exponential backoff, human-in-the-loop confirmation, secrets redaction |
| **Cross-Platform** | Android (API 24+) · JVM 11+ · iOS (Apple Silicon & Simulator) |
| **Clean Architecture** | SOLID, ports-and-adapters — zero provider types leak into domain models |

---

## Architecture

```
┌──────────────────────────────────────────────────────┐
│  LAYER 4 — Facade / Composition Root                 │
│  sdk: AivoSdk DSL, public facade, iOS shim           │
├──────────────────────────────────────────────────────┤
│  LAYER 3 — Application Services                      │
│  sdk-runtime:      agent loop, tools, memory         │
│  sdk-agent-config: Markdown/JSON agent loaders       │
│  sdk-middleware:   retry, logging, telemetry          │
├──────────────────────────────────────────────────────┤
│  LAYER 2 — Infrastructure Adapters                   │
│  sdk-provider-ollama                                 │
│  sdk-provider-openai-compatible (+ OpenRouter)       │
│  sdk-provider-gemini                                 │
│  sdk-transport: Ktor, auth strategies, SSE/NDJSON    │
├──────────────────────────────────────────────────────┤
│  LAYER 1 — Domain / Ports                            │
│  sdk-core: immutable model + ports (interfaces only) │
└──────────────────────────────────────────────────────┘
```

---

## Installation

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

```kotlin
// build.gradle.kts (app or module)
dependencies {
    implementation("io.github.abdallah-elsobky:aivo-sdk:1.0.0")
}
```

### Multiplatform

```kotlin
// build.gradle.kts (KMP module)
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.abdallah-elsobky:aivo-sdk:1.0.0")
        }
    }
}
```

---

## Quick Start

### 1. One-Line Setup (`AivoSdk.create`)

The fastest integration path — configure a single agent with one method call.

```kotlin
// Keys from environment — never hardcoded
val sdk = AivoSdk.create(
    provider = AivoProvider.GEMINI,
    model    = "gemini-2.0-flash",
    apiKey   = System.getenv("GEMINI_API_KEY"),
)

val result = sdk.chat(ConversationId("session-1"), "Explain quantum computing in one sentence.")
println(result.text)
```

### 2. Raw LLM Client (no agent loop)

```kotlin
val sdk = AivoSdk {
    providers {
        ollama("ollama") { baseUrl("http://localhost:11434") }
    }
    defaultModel("ollama:llama3.2")
}

// Direct model access — no agent overhead
val response = sdk.llm("ollama:llama3.2").generate("What is 2 + 2?")
println(response.message.content())
```

### 3. Single Agent with Custom Tools

```kotlin
val getBalanceTool = tool("get_balance", "Retrieves account balance") {
    param("accountId", "Account ID to query", type = ParamType.String, required = true)
    execute { args ->
        val id = args.string("accountId")
        "Balance for $id: \$1,250.00 USD"   // call your real API here
    }
}

val sdk = AivoSdk {
    providers {
        openRouter("openrouter") { apiKey(System.getenv("OPENROUTER_API_KEY")!!) }
    }
    defaultModel("openrouter:deepseek/deepseek-r1-0528-qwen3-8b:free")

    tools { +getBalanceTool }

    agents {
        define("banking") {
            systemPrompt = "You are a banking assistant."
            tools("get_balance")
        }
    }
    entryAgent("banking")
}

val result = sdk.chat(ConversationId("s1"), "What's my balance for ACC-999?")
println(result.text)
```

### 4. Multi-Agent Supervisor with Streaming

```kotlin
val sdk = AivoSdk {
    providers {
        gemini("gemini") { apiKey(System.getenv("GEMINI_API_KEY")!!) }
    }
    defaultModel("gemini:gemini-2.0-flash")

    agents {
        define("supervisor") {
            systemPrompt = "You coordinate requests and delegate to specialists."
            delegates("research", "writing")
        }
        define("research") {
            systemPrompt = "You gather facts from tools."
            tools("search_web")
        }
        define("writing") {
            systemPrompt = "You turn research notes into polished responses."
        }
    }
    entryAgent("supervisor")
}

// Stream real-time events
sdk.stream(ConversationId("s2"), "Write a summary of quantum computing.").collect { event ->
    when (event) {
        is AgentEvent.AgentEntered    -> println("→ Agent: ${event.agentId}")
        is AgentEvent.TextDelta       -> print(event.text)
        is AgentEvent.ToolCallStarted -> println("\n⚙ Tool: ${event.toolName}")
        is AgentEvent.RunCompleted    -> println("\n✓ Done in ${event.result.steps} steps")
        is AgentEvent.RunFailed       -> println("\n✗ Failed: ${event.error.message}")
        else -> {}
    }
}
```

### 5. Declarative Agents from Markdown

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
---
You are a research specialist. Use tools to gather accurate data.
Never hallucinate sources.
```

```kotlin
val sdk = AivoSdk {
    // ...providers...
    agents {
        fromMarkdownPath("agents/researcher.md")
    }
}
```

---

## API Key Best Practices

> **Never put API keys in source code or checked-in config files.**

| Platform | Recommended Approach |
|---|---|
| **JVM / Server** | `System.getenv("MY_API_KEY")` — set in shell or CI/CD secrets |
| **Android** | `local.properties` → `BuildConfig` field (see below) |
| **iOS** | Secrets manager (e.g. `Info.plist` excluded from VCS, or remote config) |
| **All Platforms** | Runtime user input, a secure credentials store, or server-side proxy |

### Android: `local.properties` → `BuildConfig`

```properties
# local.properties  (this file is in .gitignore — never commit it)
GEMINI_API_KEY=AIza...
OPENROUTER_API_KEY=sk-or-...
```

```kotlin
// build.gradle.kts (android block)
android {
    buildFeatures { buildConfig = true }
    defaultConfig {
        val props = java.util.Properties().apply {
            load(file("../local.properties").inputStream())
        }
        buildConfigField("String", "GEMINI_API_KEY",
            "\"${props["GEMINI_API_KEY"] ?: ""}\"")
        buildConfigField("String", "OPENROUTER_API_KEY",
            "\"${props["OPENROUTER_API_KEY"] ?: ""}\"")
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

---

## Provider Configuration

### Ollama (local or cloud)

```kotlin
providers {
    ollama("ollama") {
        baseUrl("http://localhost:11434")  // local default
        // apiKey(...)                    // only needed for Ollama Cloud
    }
}
defaultModel("ollama:llama3.2")
```

### OpenRouter

```kotlin
providers {
    openRouter("openrouter") {
        apiKey(System.getenv("OPENROUTER_API_KEY")!!)
        // referer("https://myapp.com")  // optional
        // title("My App")               // optional
    }
}
defaultModel("openrouter:deepseek/deepseek-r1-0528-qwen3-8b:free")
```

### Google Gemini

```kotlin
providers {
    gemini("gemini") {
        apiKey(System.getenv("GEMINI_API_KEY")!!)
    }
}
defaultModel("gemini:gemini-2.0-flash")
```

### OpenAI-Compatible (any endpoint)

```kotlin
providers {
    openAiCompatible("custom") {
        baseUrl("https://api.myservice.com/v1")
        apiKey(System.getenv("MY_API_KEY")!!)
    }
}
defaultModel("custom:my-model")
```

---

## Documentation

| Document | Description |
|---|---|
| **[Developer Guide](docs/DEVELOPER_GUIDE.md)** | Start here — complete walkthrough of all features |
| [Architecture Guide](docs/architecture.md) | Layer-by-layer architecture explanation |
| [Providers](docs/providers.md) | Provider configuration reference |
| [Agents & Loaders](docs/agents.md) | Markdown/JSON agent definitions |
| [Tool System](docs/tools.md) | Tool DSL, schema, risk classification |
| [Memory & Context](docs/memory.md) | Context window strategies |
| [Security](docs/security.md) | Guardrails, confirmation gates, redaction |
| [Custom Agents & Loops](docs/CUSTOM_AGENTS_AND_LOOPS.md) | Build custom reasoning loops |
| [Architecture Deep Dive](docs/SDK_ARCHITECTURE_EXPLAINED.md) | Port-and-adapter design details |
| [Extending the SDK](docs/extending.md) | Adding custom providers and middleware |

---

## Samples

| Sample | Description |
|---|---|
| [`samples/cli-jvm`](samples/cli-jvm) | JVM CLI — multi-agent banking supervisor with streaming. Reads API keys from environment variables. |
| [`androidApp`](androidApp) | Android Jetpack Compose chat app — provider/model selection with runtime API key entry (never hardcoded). |

---

## License

Aivo SDK is open source software licensed under the [Apache 2.0 License](LICENSE). 
