# Aivo SDK — User Guide

Welcome to the **Aivo SDK User Guide**! This guide is designed for application developers integrating the Aivo SDK (`io.github.abdallah-elsobky:aivo-sdk`) into **Android**, **Kotlin Multiplatform (KMP)**, **iOS**, or **JVM** projects.

---

## 📖 Table of Contents

| Topic | Description |
|---|---|
| [01. Getting Started & Installation](01-getting-started.md) | Gradle dependencies for Android/KMP/JVM, acquiring provider API keys, and secure key storage patterns (`local.properties`, `BuildConfig`, environment variables). |
| [02. Quick Start](02-quick-start.md) | One-line setup (`AivoSdk.create(...)`), full Builder DSL, and direct LLM client calls without agent loops. |
| [03. Providers & Models](03-providers-and-models.md) | Configuring Ollama (default), Google Gemini, OpenRouter, and generic OpenAI-compatible endpoints; typed model catalogs vs custom strings. |
| [04. Agents & Multi-Agent Teams](04-agents-and-teams.md) | Defining agents via Kotlin DSL, Markdown frontmatter, JSON, and building supervisor teams with automated task delegation. |
| [05. Tools & Capabilities](05-tools-and-capabilities.md) | Building type-safe tools, JSON Schema parameter validation, risk classification (`ToolRisk`), and human confirmation gates. |
| [06. Real-Time Streaming & UI](06-streaming-and-ui.md) | Consuming Kotlin `Flow<AgentEvent>`, token deltas, chain-of-thought reasoning, Jetpack Compose integration, and iOS Swift bridging. |
| [07. Memory & Context Strategies](07-memory-and-context.md) | In-memory and persistent conversation storage (Room, SQLDelight), sliding window, token budget, and atomic tool pairing. |
| [08. Resilience & Security](08-resilience-and-security.md) | Built-in exponential backoff retries, timeouts, concurrency queues, and automatic credential masking (`SecretString`). |
| [09. Testing Your App](09-testing-your-app.md) | Writing fast, deterministic unit tests for your agents and ViewModels using `FakeLlmProvider` without network calls. |
| [10. Custom Reasoning Loops](10-custom-agents-and-loops.md) | Advanced autonomous workflows: Custom loops, ReAct cycles, Reflection, and Plan-and-Execute architectures. |

---

## 🚀 Fast Snippet: Hello AI in 30 Seconds

```kotlin
import com.aivo.sdk.AivoSdk
import com.aivo.sdk.AivoProvider
import com.aivo.sdk.provider.ollama.OllamaModel
import com.aivo.sdk.core.model.ConversationId

// 1. Initialize with strongly-typed model catalog
private val aivo = AivoSdk.create(
    provider = AivoProvider.OLLAMA,
    model    = OllamaModel.GPT_OSS_120B.modelId,
    apiKey   = BuildConfig.OLLAMA_API_KEY,
)

// 2. Chat
val response = aivo.chat(ConversationId("chat-1"), "What is Kotlin Multiplatform?")
println(response.text)
```
