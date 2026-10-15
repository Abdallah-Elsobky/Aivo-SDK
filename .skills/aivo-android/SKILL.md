---
name: aivo-android
description: Comprehensive guide and production implementation toolkit for integrating Aivo SDK in native Android applications. Use when building AI agents, streaming chat interfaces, tool-calling workflows, multi-agent teams, or Room-backed conversation memory in Android using Jetpack Compose, ViewModel, Coroutines, and Clean Architecture.
---

# 🤖 Aivo SDK for Android — Agent Skill

> **Compatibility**: Android API 24+, Kotlin 2.0+, Jetpack Compose, Coroutines 1.8+  
> **Metadata**: SDK Version `1.0.1` | Artifact `io.github.abdallah-elsobky:aivo-sdk` | License Apache-2.0

This skill teaches AI agents how to expertly design, implement, and maintain production-ready agentic AI features in native Android applications using the **Aivo SDK** (`io.github.abdallah-elsobky:aivo-sdk:1.0.1`).

---

## ⚡ Agent Quick Context Prompt

If you need to prime any AI coding agent with the complete mental model of Aivo SDK in one prompt, provide this block:

```text
You are an expert Android AI engineer specializing in the Aivo SDK (io.github.abdallah-elsobky:aivo-sdk:1.0.1).
Aivo SDK is a provider-agnostic, Kotlin Multiplatform AI agent framework supporting Ollama, Google Gemini, OpenRouter, and any OpenAI-compatible endpoint.
Key architectural principles:
1. Strict Hexagonal Architecture: ViewModel and UseCases interact with domain interfaces and AivoSdk. Never leak provider-specific or network types into UI layers.
2. Security: Never hardcode API keys. Use local.properties -> BuildConfig -> SecretString.
3. Streaming: Responses stream via Kotlin Flow<AgentEvent> (TextDelta, ReasoningDelta, ToolCallStarted, ToolCallExecuted, StepStarted, StepCompleted, RunCompleted, RunFailed).
4. Tool Calling: Tools are defined via tool("name", "desc") { ... } DSL with typed params, risk levels (LOW, MEDIUM, HIGH), and optional ConfirmationHandler for human-in-the-loop approval.
5. Multi-Agent: Supports supervisor delegation, SingleTurnLoop, ToolCallingLoop (ReAct), SequenceLoop, RouterLoop, and ParallelLoop.
6. Memory: In-memory or custom MemoryStore (e.g. Room Database), with keepAll(), slidingWindow(N), or tokenBudget(tokens).
7. Resilience & Errors: Built-in exponential backoff retry with jitter, timeouts, and exhaustive SdkException hierarchy (AuthenticationException, RateLimitException, ModelNotFoundException, MaxStepsExceededException, etc.).
8. Testing: Use FakeLlmProvider.scripted(...) and InMemoryTelemetry from sdk-testing (1.0.1) for offline deterministic tests.
Write clean, idiomatic Kotlin using Jetpack Compose, StateFlow, Coroutines, and MVVM/Clean Architecture.
```

---

## 📋 Table of Contents

1. [Dependencies & Gradle Setup](#1-dependencies--gradle-setup)
2. [API Key Security Configuration](#2-api-key-security-configuration)
3. [Architecture Blueprint (Clean Architecture)](#3-architecture-blueprint)
4. [SDK Initialization Strategies](#4-sdk-initialization-strategies)
5. [Providers & Model Catalogs](#5-providers--model-catalogs)
6. [Building Custom Tools & Human-in-the-Loop](#6-building-custom-tools--human-in-the-loop)
7. [Jetpack Compose Streaming Chat](#7-jetpack-compose-streaming-chat)
8. [Room Database Conversation Memory](#8-room-database-conversation-memory)
9. [Multi-Agent Teams & Supervisor Pattern](#9-multi-agent-teams--supervisor-pattern)
10. [Error Handling & Production Guardrails](#10-error-handling--production-guardrails)
11. [Unit Testing with FakeLlmProvider](#11-unit-testing-with-fakellmprovider)
12. [Reference Links & Deep Dives](#12-reference-links--deep-dives)

---

## 1. Dependencies & Gradle Setup

In pure Android projects, integration is simple — a single line in your build script gives you the complete Aivo SDK runtime, because the Android Gradle Plugin (AGP) automatically resolves transitive dependencies.

### `settings.gradle.kts`
Ensure `mavenCentral()` is declared:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
```

### Version Catalog: `gradle/libs.versions.toml` (Recommended)
```toml
[versions]
aivo = "1.0.1"

[libraries]
aivo-sdk = { module = "io.github.abdallah-elsobky:aivo-sdk", version.ref = "aivo" }
aivo-sdk-testing = { module = "io.github.abdallah-elsobky:sdk-testing", version.ref = "aivo" }
```

### `app/build.gradle.kts`
```kotlin
android {
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Core Aivo SDK (Production) - Option A via Version Catalog
    implementation(libs.aivo.sdk)
    // Or Option B direct: implementation("io.github.abdallah-elsobky:aivo-sdk:1.0.1")

    // Testing Support (FakeLlmProvider, InMemoryTelemetry)
    testImplementation(libs.aivo.sdk.testing)

    // Android Architecture & Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    // Optional: Room Database for Persistent Conversation Memory
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
}
```

> **Android Transitive Resolution:** Pure Android projects only need `aivo-sdk` because AGP resolves all submodules (`sdk-core`, `sdk-runtime`, providers, transport, and middleware) transitively. (Note: For Kotlin Multiplatform projects, see `aivo-kmp` skill which uses `libs.bundles.aivo.sdk` for multiplatform klib compilation).

---

## 2. API Key Security Configuration

Never hardcode API keys or commit them to Git.

### Step 1: Add keys to `local.properties` (Root of project)
```properties
OLLAMA_API_KEY=your_ollama_key_here
GEMINI_API_KEY=AIzaSy...
OPENROUTER_API_KEY=sk-or-...
```

### Step 2: Inject keys into `BuildConfig` in `app/build.gradle.kts`
```kotlin
android {
    defaultConfig {
        val localProperties = java.util.Properties().apply {
            val localPropsFile = rootProject.file("local.properties")
            if (localPropsFile.exists()) {
                load(localPropsFile.inputStream())
            }
        }

        buildConfigField(
            "String",
            "OLLAMA_API_KEY",
            "\"${localProperties["OLLAMA_API_KEY"] ?: ""}\""
        )
        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${localProperties["GEMINI_API_KEY"] ?: ""}\""
        )
        buildConfigField(
            "String",
            "OPENROUTER_API_KEY",
            "\"${localProperties["OPENROUTER_API_KEY"] ?: ""}\""
        )
    }
}
```

### Step 3: Access via `SecretString`
The SDK internally wraps keys into `SecretString`. When printed or logged, it returns `[REDACTED]`.

---

## 3. Architecture Blueprint

Follow Clean Architecture to keep the UI decoupled from LLM providers:

```
┌─────────────────────────────────────────────────────────────┐
│  Presentation Layer (Jetpack Compose UI)                    │
│  ChatScreen · MessageBubbles · TypingIndicator · Dialogs     │
├─────────────────────────────────────────────────────────────┤
│  State Management (ViewModel)                              │
│  ChatViewModel (StateFlow<ChatUiState>, viewModelScope)     │
├─────────────────────────────────────────────────────────────┤
│  Domain Layer                                              │
│  ChatUseCase / SendMessageUseCase · Domain Entities         │
├─────────────────────────────────────────────────────────────┤
│  Data / Repository Layer                                   │
│  ChatRepository · AivoClientFactory (caches AivoSdk)        │
│  RoomMemoryStore (implements MemoryStore)                   │
├─────────────────────────────────────────────────────────────┤
│  Aivo SDK Infrastructure                                    │
│  AivoSdk · Providers (Gemini / Ollama / OpenRouter)         │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. SDK Initialization Strategies

### Strategy A: Rapid One-Line Setup (`AivoSdk.create`)
Best for single-agent apps, prototypes, and simple assistants:

```kotlin
import com.aivo.sdk.AivoSdk
import com.aivo.sdk.AivoProvider
import com.aivo.sdk.provider.gemini.GeminiModel
import com.aivo.sdk.core.model.ConversationId

val aivo = AivoSdk.create(
    model = GeminiModel.GEMINI_3_8_FLASH,
    apiKey = BuildConfig.GEMINI_API_KEY,
    systemPrompt = "You are a helpful Android assistant.",
)
```

### Strategy B: Full Production Builder DSL (`AivoSdk { ... }`)
Recommended for production apps requiring tools, memory policies, guardrails, and telemetry:

```kotlin
import com.aivo.sdk.AivoSdk
import com.aivo.sdk.AivoProvider
import com.aivo.sdk.provider.ollama.OllamaModel

val aivo = AivoSdk {
    providers {
        ollama(AivoProvider.OLLAMA.id) {
            apiKey(BuildConfig.OLLAMA_API_KEY)
        }
    }
    defaultModel(OllamaModel.GPT_OSS_120B)

    memory {
        slidingWindow(maxMessages = 25)
    }

    resilience {
        retry {
            maxAttempts = 3
            baseDelayMs = 1_000L
            maxDelayMs = 10_000L
            jitter = true
        }
        timeouts {
            requestTimeoutMs = 60_000L
            streamIdleTimeoutMs = 20_000L
        }
    }

    runtime {
        maxSteps = 10
        toolTimeoutMs = 15_000L
        parallelToolExecution = true
    }

    agents {
        define("assistant") {
            name = "Android Assistant"
            systemPrompt = "You are a professional Android assistant."
        }
    }
    entryAgent("assistant")
}
```

---

## 5. Providers & Model Catalogs

Aivo SDK provides strongly-typed models with compile-time safety and automatic free/paid metadata:

### 1. Google Gemini (`AivoProvider.GEMINI`)
- Default: `GeminiModel.GEMINI_3_8_FLASH` (`gemini-3.8-flash` - Free tier)
- Other options: `GEMINI_3_7_FLASH`, `GEMINI_3_6_FLASH`, `GEMINI_3_1_PRO_PREVIEW`

```kotlin
providers {
    gemini(AivoProvider.GEMINI.id) {
        apiKey(BuildConfig.GEMINI_API_KEY)
    }
}
defaultModel(GeminiModel.GEMINI_3_8_FLASH)
```

### 2. Ollama Cloud / Local (`AivoProvider.OLLAMA`)
- Default: `OllamaModel.GPT_OSS_120B` (`gpt-oss:120b` - Free tier)
- Lightweight: `OllamaModel.GPT_OSS_20B` (`gpt-oss:20b` - Free tier)
- Reasoning: `OllamaModel.NEMOTRON_3_SUPER`
- Local Instance Override: `baseUrl("http://10.0.2.2:11434")` (Android Emulator loopback)

```kotlin
providers {
    ollama(AivoProvider.OLLAMA.id) {
        // For Android Emulator targeting local machine Ollama:
        // baseUrl("http://10.0.2.2:11434")
        apiKey(BuildConfig.OLLAMA_API_KEY)
    }
}
defaultModel(OllamaModel.GPT_OSS_120B)
```

### 3. OpenRouter (`AivoProvider.OPEN_ROUTER`)
- Default: `OpenRouterModel.LING_3_0_FLASH_SANTE_FREE` (Free tier)
- Other free models: `QWEN3_8_27B_FREE`, `NEMOTRON_3_5_LIGHTNING_FREE`, `LFM_2_5_2_6B_FREE`

```kotlin
providers {
    openRouter(AivoProvider.OPEN_ROUTER.id) {
        apiKey(BuildConfig.OPENROUTER_API_KEY)
        referer("https://myapp.com")
        title("Android Assistant")
    }
}
defaultModel(OpenRouterModel.LING_3_0_FLASH_SANTE_FREE)
```

### 4. Custom Self-Hosted / vLLM / OpenAI-Compatible
```kotlin
providers {
    openAiCompatible("internal-vllm") {
        baseUrl("https://ai.company.internal/v1")
        apiKey("internal-token")
    }
}
defaultModel("internal-vllm:custom-llama-3.3-70b")
```

---

## 6. Building Custom Tools & Human-in-the-Loop

Define tools using the type-safe `tool` DSL.

### Device State Tool (Low Risk)
```kotlin
import com.aivo.sdk.core.model.ParamType
import com.aivo.sdk.core.model.ToolRisk
import com.aivo.sdk.core.port.tool

val getDeviceInfoTool = tool("get_device_info", "Fetches Android device manufacture and model") {
    risk(ToolRisk.LOW)
    execute { _ ->
        val manufacturer = android.os.Build.MANUFACTURER
        val model = android.os.Build.MODEL
        val version = android.os.Build.VERSION.SDK_INT
        "Device: $manufacturer $model (Android SDK $version)"
    }
}
```

### High-Risk Tool Requiring Human Confirmation
```kotlin
val clearAppCacheTool = tool("clear_app_cache", "Clears temporary application cache files") {
    risk(ToolRisk.HIGH)
    requiresConfirmation(true)
    param("target_dir", "Directory name to clear", type = ParamType.String, required = true)

    execute { args ->
        val dir = args.string("target_dir")
        // Executed only after ConfirmationHandler approves
        "Cleared cache for $dir successfully"
    }
}
```

### Integrating Android Confirmation Dialog
Wire the confirmation handler into the SDK:
```kotlin
val aivo = AivoSdk {
    // ...
    tools {
        +getDeviceInfoTool
        +clearAppCacheTool
    }
    security {
        confirmationHandler = ConfirmationHandler { toolName, args ->
            // Suspend until user clicks 'Allow' or 'Deny' on a Compose dialog
            dialogManager.requestApproval(toolName, args.toString())
        }
    }
}
```

---

## 7. Jetpack Compose Streaming Chat

Aivo SDK emits granular events via `Flow<AgentEvent>`:

### Handling `AgentEvent` in ViewModel
```kotlin
class ChatViewModel(private val repository: ChatRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun sendMessage(text: String, conversationId: ConversationId = ConversationId("main")) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, error = null) }
            repository.streamResponse(conversationId, text)
                .collect { event ->
                    when (event) {
                        is AgentEvent.RunStarted -> { /* Update status */ }
                        is AgentEvent.AgentEntered -> { /* Show active agent name */ }
                        is AgentEvent.TextDelta -> {
                            _uiState.update { it.appendStreamingText(event.text) }
                        }
                        is AgentEvent.ReasoningDelta -> {
                            _uiState.update { it.appendThinkingText(event.text) }
                        }
                        is AgentEvent.ToolCallStarted -> {
                            _uiState.update { it.copy(toolStatus = "Calling ${event.toolName}...") }
                        }
                        is AgentEvent.ToolCallExecuted -> {
                            _uiState.update { it.copy(toolStatus = null) }
                        }
                        is AgentEvent.RunCompleted -> {
                            _uiState.update { it.finalizeMessage() }
                        }
                        is AgentEvent.RunFailed -> {
                            _uiState.update { it.copy(isGenerating = false, error = event.error.message) }
                        }
                        else -> Unit
                    }
                }
        }
    }
}
```

See [examples/compose-chat-screen.kt](./examples/compose-chat-screen.kt) for complete screen UI.

---

## 8. Room Database Conversation Memory

To persist conversation history across Android app restarts:

```kotlin
import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.core.model.Message
import com.aivo.sdk.runtime.memory.MemoryStore

class RoomMemoryStore(private val dao: ChatMessageDao) : MemoryStore {
    override suspend fun load(conversationId: ConversationId): List<Message> {
        return dao.getMessages(conversationId.value).map { it.toDomainMessage() }
    }

    override suspend fun append(conversationId: ConversationId, messages: List<Message>) {
        dao.insertAll(messages.map { it.toEntity(conversationId.value) })
    }

    override suspend fun clear(conversationId: ConversationId) {
        dao.deleteConversation(conversationId.value)
    }
}
```

Plug it into the SDK:
```kotlin
val aivo = AivoSdk {
    // ...
    memory {
        store = RoomMemoryStore(database.chatMessageDao())
        slidingWindow(maxMessages = 40)
    }
}
```

See [examples/room-memory-store.kt](./examples/room-memory-store.kt) for the full entity, DAO, and store implementation.

---

## 9. Multi-Agent Teams & Supervisor Pattern

Decompose complex tasks into specialized agents with a supervisor orchestrator:

```kotlin
val aivo = AivoSdk {
    providers {
        gemini(AivoProvider.GEMINI.id) { apiKey(BuildConfig.GEMINI_API_KEY) }
    }
    defaultModel(GeminiModel.GEMINI_3_8_FLASH)

    tools {
        +getDeviceInfoTool
        +queryDatabaseTool
    }

    agents {
        define("supervisor") {
            name = "Support Lead"
            systemPrompt = """
                You are the Support Lead. Analyze user requests.
                Delegate technical diagnostics to the 'diagnostics_agent'.
                Delegate data lookups to the 'data_agent'.
                Synthesize final concise answers for the customer.
            """.trimIndent()
            delegates("diagnostics_agent", "data_agent")
        }

        define("diagnostics_agent") {
            name = "Diagnostics Specialist"
            systemPrompt = "You analyze device hardware and status using available tools."
            tools("get_device_info")
        }

        define("data_agent") {
            name = "Data Specialist"
            systemPrompt = "You query user records accurately."
            tools("query_database")
        }
    }
    entryAgent("supervisor")
}
```

The runtime automatically detects delegation cycles and limits nesting depth (default: 3).

---

## 10. Error Handling & Production Guardrails

Catch specific `SdkException` instances to present helpful UI feedback:

```kotlin
import com.aivo.sdk.core.error.*

try {
    val result = aivo.chat(ConversationId("session-1"), "Hello")
} catch (e: AuthenticationException) {
    // HTTP 401/403 - bad API key. Never retried.
    showSnackbar("Invalid API key for ${e.providerId.value}")
} catch (e: RateLimitException) {
    // HTTP 429 - rate limit exceeded.
    val waitSec = (e.retryAfterMs ?: 5000L) / 1000
    showSnackbar("Rate limit reached. Retrying in ${waitSec}s...")
} catch (e: MaxStepsExceededException) {
    showSnackbar("Agent exceeded step limit (${e.maxSteps}). Simplifying request.")
} catch (e: RunTimeoutException) {
    showSnackbar("Agent timed out after ${e.timeoutMs / 1000}s.")
} catch (e: NetworkException) {
    showSnackbar("Network connection lost. Please check your internet.")
} catch (e: SdkException) {
    showSnackbar("Error: ${e.message}")
}
```

---

## 11. Unit Testing with FakeLlmProvider

Test ViewModels and UseCases completely offline without network calls or API costs:

```kotlin
import com.aivo.sdk.AivoSdk
import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.core.model.Message
import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.testing.FakeLlmProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatUseCaseTest {

    @Test
    fun `use case emits scripted assistant response`() = runTest {
        // Arrange
        val fake = FakeLlmProvider.scripted(
            id = ProviderId("fake"),
            response = Message.assistant("Mocked assistant response from Aivo SDK")
        )

        val sdk = AivoSdk {
            providers { register(fake) }
            defaultModel("fake:test-model")
            agents {
                define("assistant") { systemPrompt = "Test prompt" }
            }
            entryAgent("assistant")
        }

        // Act
        val result = sdk.chat(ConversationId("test-convo"), "Ping")

        // Assert
        assertEquals("Mocked assistant response from Aivo SDK", result.text)
    }
}
```

---

## 12. Reference Links & Deep Dives

- [API Reference](./references/api-reference.md)
- [Providers & Models Catalog](./references/providers-and-models.md)
- [Android Architecture Patterns](./references/architecture-patterns.md)
- [Compose Chat Screen Recipe](./examples/compose-chat-screen.kt)
- [Room Memory Store Implementation](./examples/room-memory-store.kt)
- [Hilt Dependency Injection Module](./examples/hilt-di-module.kt)
- [Unit Testing with Fake Doubles](./examples/unit-tests-fake-llm.kt)
- [Gradle Build Template](./templates/build.gradle.kts.template)
