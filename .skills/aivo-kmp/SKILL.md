---
name: aivo-kmp
description: Complete architecture and production integration guide for Aivo SDK in Kotlin Multiplatform (KMP) and Compose Multiplatform (CMP) projects. Use when building cross-platform AI agents, streaming chat, multi-agent systems, and tool execution sharing code across Android, iOS, JVM (Desktop/Server), and Darwin targets.
compatibility: Kotlin 2.0+, Kotlin Multiplatform, Targets: Android API 24+, iOS (Arm64, Simulator, X64), JVM 11+, Compose Multiplatform 1.7+
metadata:
  sdk-version: "1.0.1"
  artifact: "io.github.abdallah-elsobky:aivo-sdk"
  author: "Abdallah Elsobky"
  license: "Apache-2.0"
---

# 🌐 Aivo SDK for Kotlin Multiplatform (KMP) — Agent Skill

This skill teaches AI agents how to expertly design, implement, and maintain production-grade cross-platform agentic AI systems using the **Aivo SDK** (`io.github.abdallah-elsobky:aivo-sdk:1.0.1`) across **Android**, **iOS**, **JVM / Desktop**, and **Server**.

---

## ⚡ Agent Quick Context Prompt

If you need to prime any AI coding agent with the complete mental model of Aivo SDK in Kotlin Multiplatform in one prompt, provide this block:

```text
You are an expert Kotlin Multiplatform (KMP) AI architect specializing in the Aivo SDK (io.github.abdallah-elsobky:aivo-sdk:1.0.1).
Aivo SDK is a unified, provider-agnostic, Kotlin Multiplatform AI agent framework supporting Ollama, Google Gemini, OpenRouter, and any OpenAI-compatible endpoint across Android, JVM, and iOS from a single codebase.
Core Multiplatform Invariants:
1. Version Catalog Bundle: Define the 'aivo-sdk' bundle in 'gradle/libs.versions.toml' and declare 'implementation(libs.bundles.aivo.sdk)' in commonMain. This ensures complete klib symbol resolution (AgentDefinition, providers, transport, middleware) across Android, iOS, and JVM.
2. Hexagonal Clean Architecture: All LLM orchestration, agent definitions, memory strategies, and repository interfaces live in commonMain. Never leak provider or network types into UI layers.
3. Platform Security: Never hardcode API keys. Inject them via expect/actual or DI (Android BuildConfig, iOS Info.plist/ProcessInfo, JVM System.getenv) and wrap in SecretString.
4. Compose Multiplatform (CMP): Write chat UI once in commonMain using Material 3, observing StateFlow<ChatUiState> and collecting Flow<AgentEvent>.
5. iOS Swift Interoperability: For native SwiftUI apps, use the built-in iOS interop shim (AivoSdkKt.create, FlowAdapter, CancellableJob) to bridge Kotlin Flow to Swift closures.
6. Custom Tools: Implement multiplatform tools in commonMain, delegating platform-specific actions (e.g. Geolocation, file access) via expect/actual or interface injection.
7. Memory & Resilience: Use KeepAllMemoryStore, SlidingWindowMemoryStore, TokenBudgetMemoryStore, or custom SQLDelight/Room KMP MemoryStore. Configure exponential backoff with jitter.
8. Testing: Run fast, deterministic unit tests in commonTest using FakeLlmProvider.scripted(...) and InMemoryTelemetry from 'io.github.abdallah-elsobky:aivo-sdk-testing:1.0.1'.
Write clean, idiomatic Kotlin Multiplatform code with Coroutines, Flow, and MVI/MVVM architecture.
```

---

## 📋 Table of Contents

1. [KMP Dependencies & Gradle Setup](#1-kmp-dependencies--gradle-setup)
2. [Platform-Specific API Key Injection](#2-platform-specific-api-key-injection)
3. [Cross-Platform Clean Architecture](#3-cross-platform-clean-architecture)
4. [SDK Configuration in `commonMain`](#4-sdk-configuration-in-commonmain)
5. [Providers & Model Catalogs](#5-providers--model-catalogs)
6. [Compose Multiplatform (CMP) Streaming Chat](#6-compose-multiplatform-cmp-streaming-chat)
7. [Native iOS Swift Interoperability](#7-native-ios-swift-interoperability)
8. [Multiplatform Custom Tools](#8-multiplatform-custom-tools)
9. [Cross-Platform Persistence (SQLDelight MemoryStore)](#9-cross-platform-persistence)
10. [Multi-Agent Supervisor Teams](#10-multi-agent-supervisor-teams)
11. [Testing in `commonTest` with Fake doubles](#11-testing-in-commontest-with-fake-doubles)
12. [Reference Links & Deep Dives](#12-reference-links--deep-dives)

---

## 1. KMP Dependencies & Gradle Setup

In Kotlin Multiplatform (KMP), `commonMain` compiles multiplatform metadata and target-specific klibs across Android, iOS, and JVM. To guarantee complete symbol and classpath resolution across all submodules (including `AgentDefinition`, providers, transport, and middleware), use the **Gradle Version Catalog bundle**:

### `gradle/libs.versions.toml`
```toml
[versions]
aivo = "1.0.1"

[libraries]
aivo-sdk = { module = "io.github.abdallah-elsobky:aivo-sdk", version.ref = "aivo" }
aivo-sdk-runtime = { module = "io.github.abdallah-elsobky:sdk-runtime", version.ref = "aivo" }
aivo-sdk-core = { module = "io.github.abdallah-elsobky:sdk-core", version.ref = "aivo" }
aivo-sdk-transport = { module = "io.github.abdallah-elsobky:sdk-transport", version.ref = "aivo" }
aivo-sdk-middleware = { module = "io.github.abdallah-elsobky:sdk-middleware", version.ref = "aivo" }
aivo-sdk-agent-config = { module = "io.github.abdallah-elsobky:sdk-agent-config", version.ref = "aivo" }
aivo-sdk-provider-ollama = { module = "io.github.abdallah-elsobky:sdk-provider-ollama", version.ref = "aivo" }
aivo-sdk-provider-gemini = { module = "io.github.abdallah-elsobky:sdk-provider-gemini", version.ref = "aivo" }
aivo-sdk-provider-openai = { module = "io.github.abdallah-elsobky:sdk-provider-openai-compatible", version.ref = "aivo" }

[bundles]
aivo-sdk = [
    "aivo-sdk",
    "aivo-sdk-core",
    "aivo-sdk-runtime",
    "aivo-sdk-transport",
    "aivo-sdk-middleware",
    "aivo-sdk-agent-config",
    "aivo-sdk-provider-ollama",
    "aivo-sdk-provider-gemini",
    "aivo-sdk-provider-openai"
]
```

### `shared/build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    // 1. Android Target
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    // 2. iOS Targets
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        iosX64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SharedAiKit"
            isStatic = true
            // Export aivo-sdk so Swift can access AivoSdkKt and FlowAdapter directly
            export(libs.aivo.sdk)
        }
    }

    // 3. JVM Desktop / Server Target
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            // 🤖 Aivo SDK Multiplatform Complete Bundle
            implementation(libs.bundles.aivo.sdk)

            // Coroutines & Serialization
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

            // Compose Multiplatform (Optional for shared UI)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
        }

        commonTest.dependencies {
            // 🧪 Aivo SDK Testing Double Library
            implementation("io.github.abdallah-elsobky:aivo-sdk-testing:1.0.1")
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
        }
    }
}
```

---

## 2. Platform-Specific API Key Injection

Inject API keys at runtime so `commonMain` remains independent of host-environment mechanisms:

### Option A: `expect` / `actual` Secret Provider
```kotlin
// commonMain
expect object PlatformSecretProvider {
    fun getApiKey(providerId: String): String
}

// androidMain
actual object PlatformSecretProvider {
    actual fun getApiKey(providerId: String): String = when (providerId) {
        "gemini" -> BuildConfig.GEMINI_API_KEY
        "ollama" -> BuildConfig.OLLAMA_API_KEY
        else -> ""
    }
}

// iosMain
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSBundle

actual object PlatformSecretProvider {
    actual fun getApiKey(providerId: String): String {
        return NSProcessInfo.processInfo.environment[providerId.uppercase() + "_API_KEY"] as? String
            ?: NSBundle.mainBundle.objectForInfoDictionaryKey(providerId.uppercase() + "_API_KEY") as? String
            ?: ""
    }
}

// desktopMain / jvmMain
actual object PlatformSecretProvider {
    actual fun getApiKey(providerId: String): String =
        System.getenv(providerId.uppercase() + "_API_KEY") ?: ""
}
```

### Option B: Dependency Injection via Constructor
Pass the resolved key when constructing the shared repository from platform entry points:
```kotlin
// commonMain
class ChatRepository(private val apiKeyProvider: () -> String)
```

---

## 3. Cross-Platform Clean Architecture

Structure your KMP shared module with Hexagonal Architecture:

```
┌─────────────────────────────────────────────────────────────┐
│ PRESENTATION LAYER (commonMain)                             │
│ - Compose Multiplatform ChatScreen (Android / Desktop / iOS) │
│ - Common ViewModel / Component (StateFlow<ChatUiState>)     │
├─────────────────────────────────────────────────────────────┤
│ DOMAIN LAYER (commonMain)                                   │
│ - ChatUseCase · SendMessageUseCase · Domain Entities        │
├─────────────────────────────────────────────────────────────┤
│ DATA / REPOSITORY LAYER (commonMain)                        │
│ - ChatRepositoryImpl                                        │
│ - AivoSdk caching factory                                   │
│ - MemoryStore (KeepAll, SlidingWindow, or SQLDelight)       │
├─────────────────────────────────────────────────────────────┤
│ AIVO SDK FACADE & ENGINE (commonMain)                       │
│ - AivoSdk · Providers (Gemini / Ollama / OpenRouter)         │
│ - Agent loop · ToolExecutor · Ktor Transport                │
├─────────────────────────────────────────────────────────────┤
│ PLATFORM BRIDGES (androidMain / iosMain / jvmMain)          │
│ - Swift FlowAdapter shim (iOS)                              │
│ - BuildConfig / Environment variables                       │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. SDK Configuration in `commonMain`

### Rapid Setup (`AivoSdk.create`)
```kotlin
import com.aivo.sdk.AivoSdk
import com.aivo.sdk.AivoProvider
import com.aivo.sdk.provider.gemini.GeminiModel
import com.aivo.sdk.core.model.ConversationId

class SharedAiAssistant(apiKey: String) {
    private val aivo = AivoSdk.create(
        model = GeminiModel.GEMINI_3_8_FLASH,
        apiKey = apiKey,
        systemPrompt = "You are a concise cross-platform assistant."
    )

    fun streamChat(conversationId: String, prompt: String) =
        aivo.stream(ConversationId(conversationId), prompt)
}
```

### Full Multiplatform Builder DSL (`AivoSdk { ... }`)
```kotlin
import com.aivo.sdk.AivoSdk
import com.aivo.sdk.AivoProvider
import com.aivo.sdk.provider.gemini.GeminiModel

val sharedAivo = AivoSdk {
    providers {
        gemini(AivoProvider.GEMINI.id) {
            apiKey(PlatformSecretProvider.getApiKey("gemini"))
        }
    }
    defaultModel(GeminiModel.GEMINI_3_8_FLASH)

    memory {
        slidingWindow(maxMessages = 30)
    }

    resilience {
        retry {
            maxAttempts = 3
            baseDelayMs = 1_000L
            maxDelayMs = 10_000L
            jitter = true
        }
    }

    runtime {
        maxSteps = 10
        toolTimeoutMs = 15_000L
    }

    agents {
        define("assistant") {
            name = "KMP Assistant"
            systemPrompt = "You are a cross-platform AI assistant."
        }
    }
    entryAgent("assistant")
}
```

---

## 5. Providers & Model Catalogs

Use strongly-typed model constants across all platforms:

| Provider | Enum Class | Recommended Default | Free Tier Model |
|---|---|---|---|
| Google Gemini | `GeminiModel` | `GeminiModel.GEMINI_3_8_FLASH` | Yes (`gemini-3.8-flash`) |
| Ollama Cloud | `OllamaModel` | `OllamaModel.GPT_OSS_120B` | Yes (`gpt-oss:120b`) |
| OpenRouter | `OpenRouterModel` | `OpenRouterModel.LING_3_0_FLASH_SANTE_FREE` | Yes (Free models catalogue) |
| Custom OpenAI | String | `"providerId:modelName"` | N/A |

### Custom Self-Hosted / vLLM / LiteLLM Proxy in KMP:
```kotlin
providers {
    openAiCompatible("internal-vllm") {
        baseUrl("https://ai-proxy.company.internal/v1")
        apiKey(PlatformSecretProvider.getApiKey("vllm"))
    }
}
defaultModel("internal-vllm:qwen-2.5-72b-instruct")
```

---

## 6. Compose Multiplatform (CMP) Streaming Chat

Write a single chat screen in `commonMain` that compiles and runs identically on Android, iOS, and Desktop:

```kotlin
// commonMain
@Composable
fun CommonChatScreen(
    messages: List<UiMessage>,
    isGenerating: Boolean,
    onSendMessage: (String) -> Unit
) {
    Scaffold(
        bottomBar = {
            CommonChatInput(isGenerating = isGenerating, onSend = onSendMessage)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            items(messages) { msg ->
                CommonMessageBubble(msg)
            }
        }
    }
}
```
See [examples/compose-multiplatform-chat.kt](./examples/compose-multiplatform-chat.kt) for the complete implementation.

---

## 7. Native iOS Swift Interoperability

When building a native **SwiftUI** frontend on top of your shared KMP business logic, Aivo SDK provides a built-in iOS interop shim (`FlowAdapter` + `CancellableJob`):

```swift
// Swift / SwiftUI ViewModel
import SharedAiKit

class ChatObservableModel: ObservableObject {
    @Published var outputText: String = ""
    @Published var isStreaming: Bool = false
    private var activeJob: CancellableJob? = nil

    private lazy var aivo = AivoSdkKt.create(
        provider: AivoProvider.gemini,
        model: GeminiModel.gemini38Flash.modelId,
        apiKey: ProcessInfo.processInfo.environment["GEMINI_API_KEY"] ?? ""
    )

    func send(prompt: String) {
        self.outputText = ""
        self.isStreaming = true
        
        self.activeJob = aivo.stream(
            conversationId: ConversationId(value: "ios-session"),
            message: prompt
        ).subscribe(
            onEach: { [weak self] event in
                DispatchQueue.main.async {
                    if let textDelta = event as? AgentEvent.TextDelta {
                        self?.outputText += textDelta.text
                    }
                }
            },
            onError: { [weak self] error in
                DispatchQueue.main.async {
                    self?.isStreaming = false
                    print("Stream error: \(error)")
                }
            },
            onComplete: { [weak self] in
                DispatchQueue.main.async {
                    self?.isStreaming = false
                }
            }
        )
    }

    func cancel() {
        activeJob?.cancel()
        activeJob = nil
        isStreaming = false
    }
}
```
See [examples/ios-swiftui-chat-view.swift](./examples/ios-swiftui-chat-view.swift) for SwiftUI View integration.

---

## 8. Multiplatform Custom Tools

Tools can be pure Kotlin logic or delegate to platform capabilities:

```kotlin
// commonMain
val calculateTaxTool = tool("calculate_tax", "Calculates tax based on amount and rate") {
    param("amount", "Base currency amount", type = ParamType.Double, required = true)
    param("rate", "Tax rate (e.g. 0.15 for 15%)", type = ParamType.Double, required = true)

    execute { args ->
        val amount = args.double("amount")
        val rate = args.double("rate")
        val tax = amount * rate
        """{"tax": $tax, "total": ${amount + tax}}"""
    }
}
```

For platform capabilities (e.g. device storage, sensors), pass an `expect/actual` bridge into tool execution. See [references/multiplatform-tools.md](./references/multiplatform-tools.md).

---

## 9. Cross-Platform Persistence

Use SQLDelight or Room KMP to implement `MemoryStore` across Android, iOS, and Desktop:

```kotlin
// commonMain
class SqlDelightMemoryStore(private val queries: ConversationQueries) : MemoryStore {
    override suspend fun load(conversationId: ConversationId): List<Message> {
        return queries.selectByConversation(conversationId.value).executeAsList().map {
            Message(role = Role.valueOf(it.role), content = it.content)
        }
    }

    override suspend fun append(conversationId: ConversationId, messages: List<Message>) {
        messages.forEach {
            queries.insertMessage(conversationId.value, it.role.name, it.content(), System.currentTimeMillis())
        }
    }

    override suspend fun clear(conversationId: ConversationId) {
        queries.deleteConversation(conversationId.value)
    }
}
```
See [examples/kmp-sqldelight-memory-store.kt](./examples/kmp-sqldelight-memory-store.kt).

---

## 10. Multi-Agent Supervisor Teams

Define multi-agent collaboration loops in `commonMain`:

```kotlin
val aivo = AivoSdk {
    providers {
        gemini(AivoProvider.GEMINI.id) { apiKey(PlatformSecretProvider.getApiKey("gemini")) }
    }
    defaultModel(GeminiModel.GEMINI_3_8_FLASH)

    tools {
        +calculateTaxTool
        +queryRemoteCatalogTool
    }

    agents {
        define("supervisor") {
            name = "Team Lead"
            systemPrompt = "Delegate calculations to 'finance_agent' and lookups to 'catalog_agent'."
            delegates("finance_agent", "catalog_agent")
        }

        define("finance_agent") {
            name = "Finance Specialist"
            systemPrompt = "Handle tax calculations using tools."
            tools("calculate_tax")
        }

        define("catalog_agent") {
            name = "Catalog Specialist"
            systemPrompt = "Handle product queries."
            tools("query_catalog")
        }
    }
    entryAgent("supervisor")
}
```

---

## 11. Testing in `commonTest` with Fake Doubles

All business logic in `commonMain` can be verified in `commonTest` without network connections or API keys:

```kotlin
// commonTest
import com.aivo.sdk.AivoSdk
import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.core.model.Message
import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.testing.FakeLlmProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedAssistantTest {

    @Test
    fun `test shared agent execution offline`() = runTest {
        val fake = FakeLlmProvider.scripted(
            id = ProviderId("mock-llm"),
            response = Message.assistant("KMP Verified Response")
        )

        val sdk = AivoSdk {
            providers { register(fake) }
            defaultModel("mock-llm:test-model")
            agents {
                define("assistant") { systemPrompt = "Test bot" }
            }
            entryAgent("assistant")
        }

        val result = sdk.chat(ConversationId("session-1"), "Ping")
        assertEquals("KMP Verified Response", result.text)
    }
}
```

---

## 12. Reference Links & Deep Dives

- [KMP API Reference](./references/kmp-api-reference.md)
- [iOS Swift Interoperability Guide](./references/ios-swift-interop.md)
- [Multiplatform Tools Guide](./references/multiplatform-tools.md)
- [Compose Multiplatform Chat Example](./examples/compose-multiplatform-chat.kt)
- [iOS SwiftUI Chat View Example](./examples/ios-swiftui-chat-view.swift)
- [KMP Shared Repository & Caching](./examples/kmp-shared-repository.kt)
- [SQLDelight Cross-Platform MemoryStore](./examples/kmp-sqldelight-memory-store.kt)
- [Multiplatform Unit Tests](./examples/kmp-multiplatform-tests.kt)
- [KMP Gradle Build Template](./templates/kmp-build.gradle.kts.template)
