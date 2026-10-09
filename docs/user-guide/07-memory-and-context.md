# Memory & Context Window Strategies

Control how conversation history is persisted across sessions and how much context is sent to the LLM on each turn.

---

## 1. Context Window Strategies

LLMs have finite context limits and per-token pricing. Aivo SDK provides three built-in pruning strategies:

### Keep All Messages *(Default)*
Sends the entire conversation history unmodified. Best for short workflows:

```kotlin
val aivo = AivoSdk {
    // ...
    memory {
        keepAll()
    }
}
```

### Sliding Window
Retains only the most recent $N$ messages. The first system prompt is always preserved:

```kotlin
val aivo = AivoSdk {
    // ...
    memory {
        slidingWindow(maxMessages = 20)
    }
}
```

### Token Budget
Calculates estimated token counts backwards from newest to oldest until a threshold is reached:

```kotlin
val aivo = AivoSdk {
    // ...
    memory {
        tokenBudget(
            maxTokens = 8_000,
            estimator = CharCountEstimator // Or provide your own TokenEstimator
        )
    }
}
```

---

## 2. The Atomic Invariant: Never Orphan Tool Messages

A common bug in naive context truncation is dropping a tool execution result message while keeping the assistant message that requested it (or vice-versa). This causes LLM providers like Gemini and OpenAI to reject the request with `Invalid Request: Tool call has no matching tool response`.

**Aivo SDK guarantees this never happens:**
The context window strategies group assistant messages and their corresponding `Message.Tool` results into **atomic units**. They are either retained together or pruned together.

---

## 3. Persistent Memory Backends (Database Integration)

By default, the SDK uses `InMemoryMemoryStore`. For persistent chat history across app restarts, implement the `MemoryStore` interface:

### Room Database Example (Android)

```kotlin
import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.core.model.Message
import com.aivo.sdk.core.port.MemoryStore

class RoomMemoryStore(private val dao: MessageDao) : MemoryStore {

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

Register your store in the SDK builder:

```kotlin
val aivo = AivoSdk {
    // ...
    memory {
        store = RoomMemoryStore(myDatabase.messageDao())
        slidingWindow(maxMessages = 30)
    }
}
```
