# Quick Start

Get from zero to running AI agents in your Kotlin project in under 2 minutes.

---

## 🚀 Option A — One-Line Setup *(Fastest)*

The `AivoSdk.create(...)` factory auto-configures providers, agents, system prompts, and routing in a single call.

```kotlin
import com.aivo.sdk.AivoSdk
import com.aivo.sdk.AivoProvider
import com.aivo.sdk.provider.ollama.OllamaModel
import com.aivo.sdk.core.model.ConversationId

// Initialize SDK instance
private val aivo = AivoSdk.create(
    provider = AivoProvider.OLLAMA,
    model    = OllamaModel.GPT_OSS_120B.modelId,
    apiKey   = BuildConfig.OLLAMA_API_KEY,  // Android
    // apiKey = System.getenv("OLLAMA_API_KEY"),  // JVM
)

// Execute a chat turn
val result = aivo.chat(ConversationId("session-1"), "What is Kotlin Multiplatform?")
println(result.text)
```

> 💡 **Type-Safe Model Shortcut:** You can also pass the model object directly:
> ```kotlin
> private val aivo = AivoSdk.create(
>     model  = OllamaModel.GPT_OSS_120B,
>     apiKey = BuildConfig.OLLAMA_API_KEY,
> )
> ```

---

## 🛠️ Option B — Full Builder DSL *(Recommended for Production)*

Use the declarative builder to configure multiple providers, register custom tools, define supervisor agents, and configure resilience and memory policies.

```kotlin
import com.aivo.sdk.AivoSdk
import com.aivo.sdk.AivoProvider
import com.aivo.sdk.provider.ollama.OllamaModel
import com.aivo.sdk.core.model.ConversationId

val aivo = AivoSdk {
    providers {
        ollama(AivoProvider.OLLAMA.id) { 
            apiKey(BuildConfig.OLLAMA_API_KEY) 
        }
    }
    defaultModel(OllamaModel.GPT_OSS_120B)

    agents {
        define("assistant") {
            name         = "AI Assistant"
            systemPrompt = "You are a concise, helpful assistant."
        }
    }
    entryAgent("assistant")
}

val result = aivo.chat(ConversationId("chat-1"), "Explain Kotlin coroutines in one sentence.")
println(result.text)
```

---

## ⚡ Option C — Raw LLM Client *(No Agent Loop)*

If you only need raw text generation or embeddings without agent orchestration, tool execution, or memory overhead:

```kotlin
import com.aivo.sdk.AivoSdk
import com.aivo.sdk.AivoProvider
import com.aivo.sdk.provider.ollama.OllamaModel

val aivo = AivoSdk {
    providers {
        ollama(AivoProvider.OLLAMA.id) { apiKey(BuildConfig.OLLAMA_API_KEY) }
    }
    defaultModel(OllamaModel.GPT_OSS_120B)
}

// Direct model call
val response = aivo.llm(OllamaModel.GPT_OSS_120B.toModelRef()).generate("What is 2 + 2?")
println(response.message.content())
```

---

## 🧩 Typed Catalogs vs. Custom Model Strings

Aivo SDK provides the best developer experience by offering **both** strongly-typed enums and **full custom string flexibility**:

1. **Strongly-Typed Enums (`Object.name` / `modelId`):**
   - Built-in catalogs: `AivoProvider`, `OllamaModel`, `GeminiModel`, and `OpenRouterModel`.
   - Advantages: Instant IDE autocomplete, compile-time validation against typos, and built-in tier metadata (`isFree`, `displayName`).

2. **Custom Strings & Endpoints:**
   - If you run self-hosted LLMs, fine-tuned models, local proxies, or custom OpenAI-compatible endpoints, you are **never limited** to pre-defined enums.
   - Any model name, provider name, or base URL can be passed as a plain `String`.

```kotlin
// Example 1: Custom model name & custom base URL with AivoSdk.create(...)
val customAivo = AivoSdk.create(
    provider = AivoProvider.OLLAMA,
    model    = "my-custom-fine-tuned-model:v2",        // Custom string model
    baseUrl  = "https://ollama.internal.company.com/api/chat", // Custom base URL string
    apiKey   = BuildConfig.OLLAMA_API_KEY,
)

// Example 2: Fully custom self-hosted OpenAI-compatible endpoint with Builder DSL
val customLlmSdk = AivoSdk {
    providers {
        openAiCompatible("internal-vllm") {            // Custom provider ID string
            baseUrl("https://vllm.internal.corp/v1")    // Custom host URL string
            apiKey(BuildConfig.INTERNAL_API_KEY)
            header("X-Department", "Mobile-AI")
        }
    }
    defaultModel("internal-vllm:custom-llama-3.3-70b")  // Custom "provider:model" string
}
```
