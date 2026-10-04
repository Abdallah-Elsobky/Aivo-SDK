# Aivo SDK — Extension Guide

This guide walks through extending Aivo SDK with new providers, tools, memory backends, and context strategies **without modifying existing core code**.

## 1. Adding a New LLM Provider
To add a new provider (e.g. Anthropic, Mistral, or a custom internal gateway):

1. Create a class implementing `LlmProvider`:
```kotlin
public class MyCustomProvider(override val id: ProviderId = ProviderId("my-provider")) : LlmProvider {
    override val capabilities: ProviderCapabilities = ProviderCapabilities(
        streaming = true,
        toolCalling = true,
        parallelToolCalls = true,
        reasoning = false,
        structuredOutput = false,
        statefulConversation = false,
    )

    override suspend fun generate(request: LlmRequest): LlmResponse {
        // Encode domain LlmRequest to vendor format, call HTTP client, decode response
    }

    override fun stream(request: LlmRequest): Flow<LlmStreamEvent> = flow {
        // Stream text deltas, tool call deltas, ending with Completed(LlmResponse)
    }
}
```

2. Register it in `AivoSdk`:
```kotlin
val ai = AivoSdk {
    providers {
        register(MyCustomProvider())
    }
    defaultModel("my-provider:model-v1")
}
```

## 2. Adding a New Tool
Implement `Tool` or use the `tool` DSL:
```kotlin
val myTool = tool("calculate_interest") {
    parameters {
        number("principal", required = true)
        number("rate", required = true)
    }
    execute { call, _ ->
        val principal = call.arguments["principal"]?.jsonPrimitive?.double ?: 0.0
        val rate = call.arguments["rate"]?.jsonPrimitive?.double ?: 0.0
        ToolResult.Success(JsonPrimitive(principal * rate))
    }
}

ai = AivoSdk {
    tools { register(myTool) }
}
```

## 3. Adding a Custom Memory Backend
Implement `MemoryStore`:
```kotlin
class SqlDelightMemoryStore(private val db: MyDatabase) : MemoryStore {
    override suspend fun load(id: ConversationId): List<Message> { ... }
    override suspend fun append(id: ConversationId, messages: List<Message>) { ... }
    override suspend fun clear(id: ConversationId) { ... }
}
```

## 4. Adding a Custom Context Window Strategy
Implement `ContextWindowStrategy`:
```kotlin
class SummarizingContextStrategy : ContextWindowStrategy {
    override fun select(messages: List<Message>): List<Message> {
        // Custom message condensation logic preserving tool call groupings
    }
}
```
