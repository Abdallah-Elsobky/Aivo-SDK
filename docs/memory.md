# Aivo SDK — Memory & Context Window Guide

## 1. Memory Stores
The `MemoryStore` interface persists conversation messages keyed by `ConversationId`.

### Implementations
- `InMemoryMemoryStore`: Default thread-safe, in-memory store.
- Custom stores: Implement `MemoryStore` for Room, SQLDelight, or remote backends.

```kotlin
AivoSdk {
    memory {
        store = InMemoryMemoryStore()
    }
}
```

## 2. Context Window Strategies
LLMs have a finite context window. The `ContextWindowStrategy` governs which messages from the conversation history are sent to the LLM.

### Built-in Strategies
- `KeepAllStrategy`: Keeps all messages in history without trimming.
- `SlidingWindowStrategy(maxHistoryUnits)`: Keeps the system prompt, latest user message, and the most recent N turns.
- `TokenBudgetStrategy(maxTokens, estimator)`: Trims older messages when estimated tokens exceed the budget.

### Atomic Invariant (Never Orphan Tool Messages)
A tool call in an assistant message must **never** be sent without all of its corresponding `Message.Tool` results. Trimming algorithms in Aivo SDK group assistant tool calls and their results into atomic units so that they are dropped or kept together.
