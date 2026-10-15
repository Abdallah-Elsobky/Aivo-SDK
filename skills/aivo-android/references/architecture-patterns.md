# Aivo SDK Android — Architecture & Best Practices

This guide explains how to integrate the **Aivo SDK** into production Android applications following **Clean Architecture**, **MVVM / MVI**, and **Dependency Injection** best practices.

---

## 1. Clean Hexagonal Architecture Rule

Never leak LLM infrastructure types or HTTP transport details into your UI or Presentation layer.

```
┌─────────────────────────────────────────────────────────────┐
│ PRESENTATION LAYER                                          │
│ - ChatScreen (Jetpack Compose)                              │
│ - ChatViewModel (StateFlow<ChatUiState>)                    │
└──────────────────────────────┬──────────────────────────────┘
                               │ Observes UI state / Dispatches user intents
┌──────────────────────────────▼──────────────────────────────┐
│ DOMAIN LAYER                                                │
│ - SendMessageUseCase / StreamResponseUseCase                │
│ - Domain Models (ChatMessage, UserIntent, ToolApproval)     │
└──────────────────────────────┬──────────────────────────────┘
                               │ Invokes business operations
┌──────────────────────────────▼──────────────────────────────┐
│ DATA / REPOSITORY LAYER                                     │
│ - ChatRepositoryImpl                                        │
│ - AivoClientFactory (caches AivoSdk instance)               │
│ - RoomMemoryStore (implements Aivo MemoryStore port)        │
└──────────────────────────────┬──────────────────────────────┘
                               │ Interacts with SDK facade
┌──────────────────────────────▼──────────────────────────────┐
│ AIVO SDK INFRASTRUCTURE LAYER                               │
│ - AivoSdk, Providers (Gemini/Ollama), Ktor Transport Engine │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Repository Instance Caching (`AivoClientFactory`)

Creating an `AivoSdk` instance involves setting up Ktor HTTP client engines, connection pools, and agent loop registries. You should **cache** the SDK instance and only reconstruct it if runtime settings (e.g. active provider, model, or API key) change:

```kotlin
class ChatRepositoryImpl(
    private val clientFactory: (AivoProvider, String, String) -> AivoSdk = { p, m, k ->
        AivoClientFactory.create(p, m, k)
    }
) : ChatRepository {

    private data class SdkKey(val provider: AivoProvider, val model: String, val apiKey: String)

    @Volatile private var cachedKey: SdkKey? = null
    @Volatile private var cachedSdk: AivoSdk? = null

    @Synchronized
    private fun getSdk(provider: AivoProvider, model: String, apiKey: String): AivoSdk {
        val key = SdkKey(provider, model, apiKey)
        if (cachedKey != key || cachedSdk == null) {
            cachedKey = key
            cachedSdk = clientFactory(provider, model, apiKey)
        }
        return cachedSdk!!
    }

    override fun streamMessage(
        provider: AivoProvider,
        model: String,
        apiKey: String,
        conversationId: ConversationId,
        userMessage: String
    ): Flow<AgentEvent> {
        val sdk = getSdk(provider, model, apiKey)
        return sdk.stream(conversationId, userMessage)
    }
}
```

---

## 3. ViewModel & Compose State Management

Use unidirectional data flow (UDF). The ViewModel exposes a single immutable `StateFlow<ChatUiState>`:

```kotlin
data class ChatUiState(
    val messages: List<UiMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val activeAgent: String? = null,
    val currentThinking: String? = null,
    val activeToolName: String? = null,
    val errorMessage: String? = null,
    val pendingConfirmation: PendingConfirmation? = null,
)

data class PendingConfirmation(
    val toolName: String,
    val argumentsJson: String,
    val onConfirm: (Boolean) -> Unit
)
```

### Coroutine Cancellation & Scopes
Always launch streaming within `viewModelScope`. When the user navigates away or clears the conversation, `viewModelScope` automatically cancels the underlying Kotlin `Flow` and aborts the ongoing network request in the Ktor transport layer without leaking memory or battery.

---

## 4. Dependency Injection with Hilt

Configure Aivo SDK in a Hilt `@Module`:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AivoModule {

    @Provides
    @Singleton
    fun provideRoomMemoryStore(db: AppDatabase): MemoryStore {
        return RoomMemoryStore(db.chatDao())
    }

    @Provides
    @Singleton
    fun provideChatRepository(memoryStore: MemoryStore): ChatRepository {
        return ChatRepositoryImpl(memoryStore = memoryStore)
    }
}
```
