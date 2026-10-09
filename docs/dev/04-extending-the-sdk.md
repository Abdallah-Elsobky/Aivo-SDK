# Extending the Aivo SDK

This guide explains how to extend the Aivo SDK internals with new LLM providers, transport adapters, memory stores, and reasoning loops **without modifying existing core code** (adhering strictly to the Open-Closed Principle).

---

## 1. Adding a New LLM Provider Module

To add support for a new LLM provider (e.g. Anthropic, Mistral, Groq, Cohere) as a first-class module:

### Step 1: Create the Gradle Subproject
1. In `settings.gradle.kts`:
   ```kotlin
   include(":sdk-provider-anthropic")
   ```
2. In `sdk-provider-anthropic/build.gradle.kts`:
   ```kotlin
   kotlin {
       sourceSets {
           commonMain.dependencies {
               implementation(project(":sdk-core"))
               implementation(project(":sdk-transport"))
               implementation(libs.kotlinx.serialization.json)
           }
       }
   }
   ```

### Step 2: Implement `WireProtocol`
The `WireProtocol` handles mapping between canonical domain types and vendor-specific REST schemas:

```kotlin
package com.aivo.sdk.provider.anthropic

import com.aivo.sdk.core.model.*
import com.aivo.sdk.core.error.*
import com.aivo.sdk.transport.protocol.*

public class AnthropicWireProtocol(
    private val apiVersion: String = "2023-06-01"
) : WireProtocol {

    override fun endpoint(request: LlmRequest, stream: Boolean): String = "/v1/messages"

    override fun encode(request: LlmRequest, stream: Boolean): String {
        // Build Anthropic request JSON body
        // Map Message.System -> system property
        // Map Message.User & Message.Assistant -> messages array
        // Map Tool -> tools array
    }

    override fun decode(responseBody: String): LlmResponse {
        // Parse non-streaming response body into domain LlmResponse
    }

    override fun decodeStream(frames: Flow<RawFrame>): Flow<LlmStreamEvent> = flow {
        // Parse SSE events: message_start, content_block_delta, message_delta, message_stop
        // Emit TextDelta, ToolCallStarted, ToolCallArgumentsDelta
    }

    override fun mapError(statusCode: Int, responseBody: String, headers: Map<String, List<String>>): SdkException {
        return when (statusCode) {
            400 -> InvalidRequestException(ProviderId("anthropic"), responseBody)
            401 -> AuthenticationException(ProviderId("anthropic"), responseBody)
            429 -> RateLimitException(ProviderId("anthropic"), responseBody, parseRetryAfter(headers))
            500, 502, 503, 504 -> ServerErrorException(ProviderId("anthropic"), statusCode, responseBody, retryable = true)
            else -> ProviderException(ProviderId("anthropic"), responseBody)
        }
    }
}
```

### Step 3: Implement the Provider Factory
Create an entry point returning a configured [`HttpLlmProvider`](file:///d:/Projects/AivoSdk/sdk-transport/src/commonMain/kotlin/com/aivo/sdk/transport/HttpLlmProvider.kt):

```kotlin
public class AnthropicProvider(
    config: AnthropicConfig
) : LlmProvider by HttpLlmProvider(
    id = ProviderId(config.id),
    baseUrl = config.baseUrl,
    protocol = AnthropicWireProtocol(config.apiVersion),
    authStrategy = HeaderAuthStrategy(
        headerName = "x-api-key",
        credentials = config.credentials,
        extraHeaders = mapOf("anthropic-version" to config.apiVersion)
    ),
    transport = config.transport
)
```

### Step 4: Expose in SDK Providers DSL
In `:sdk`, add the DSL extension:

```kotlin
public fun ProvidersDsl.anthropic(
    id: String = "anthropic",
    block: AnthropicDsl.() -> Unit
) {
    val dsl = AnthropicDsl(id).apply(block)
    factories.add { transport ->
        val creds = dsl.credentials ?: throw ConfigurationException(listOf("Anthropic requires credentials"))
        ProviderId(dsl.id) to AnthropicProvider(AnthropicConfig(dsl.id, dsl.baseUrl, creds, transport))
    }
}
```

---

## 2. Implementing a Custom Memory Backend

To persist conversation history into a relational database (Room, SQLDelight, SQLite) or key-value store:

Implement the [`MemoryStore`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/port/Ports.kt#L114) interface:

```kotlin
class RoomMemoryStore(private val dao: ConversationDao) : MemoryStore {
    
    override suspend fun load(conversationId: ConversationId): List<Message> {
        return dao.getMessages(conversationId.value).map { it.toDomainMessage() }
    }

    override suspend fun append(conversationId: ConversationId, messages: List<Message>) {
        // Must insert messages atomically in chronological order
        dao.insertMessages(messages.map { it.toEntity(conversationId.value) })
    }

    override suspend fun clear(conversationId: ConversationId) {
        dao.deleteConversation(conversationId.value)
    }
}
```

---

## 3. Creating a Custom Reasoning Loop

If an agent needs custom decision logic beyond standard ReAct (e.g. Plan-and-Execute, Tree-of-Thought, Actor-Critic):

Implement [`AgentLoop`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/port/AgentLoop.kt#L67):

```kotlin
class CriticEvaluationLoop(
    private val maxRounds: Int = 3
) : AgentLoop {

    override suspend fun run(context: LoopContext): LoopOutcome {
        var round = 0
        while (round < maxRounds) {
            round++
            // 1. Generate candidate solution
            val turn = context.think()
            val text = when (turn) {
                is Turn.Text -> turn.text
                is Turn.Call -> {
                    // Execute tools if needed
                    val results = context.act(turn.calls)
                    continue
                }
            }

            // 2. Evaluate solution
            val isAcceptable = evaluateQuality(text)
            if (isAcceptable) {
                return context.finish(text)
            }
        }
        return context.finish("Failed to reach quality bar within $maxRounds rounds.")
    }
}
```

---

## 4. Architectural Rules for Contributors

1. **Zero External Types in `sdk-core`:** Domain models must never import Ktor, OkHttp, kotlinx.datetime, or Android SDK classes.
2. **Deterministic Tests:** Never write tests making live HTTP calls. Use `FakeLlmProvider` and `MockEngine`.
3. **Immutability:** All domain objects (`Message`, `LlmRequest`, `LlmResponse`, `AgentDefinition`) are immutable data classes. State mutations only occur inside isolated runtime session scopes.
