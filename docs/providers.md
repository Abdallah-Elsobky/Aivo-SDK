# Aivo SDK — LLM Providers Guide

Aivo SDK provides native support for multiple LLM backends through a unified domain model.

## 1. Supported Providers

### Ollama
Ideal for local model execution (Gemma, Llama, Mistral, DeepSeek) and self-hosted cloud endpoints.
- **Protocol:** HTTP POST to `/api/chat`
- **Streaming:** NDJSON (Newline Delimited JSON)
- **Tool Calling:** Native JSON schema parameters inside `tools`

```kotlin
AivoSdk {
    providers {
        ollama("ollama") {
            baseUrl("http://localhost:11434")
            // apiKey("...") // for Ollama cloud if applicable
        }
    }
    defaultModel("ollama:gemma4:31b")
}
```

### OpenRouter & OpenAI-Compatible
Supports OpenAI, OpenRouter, Groq, Together AI, vLLM, and any OpenAI API-compatible proxy.
- **Protocol:** HTTP POST to `/v1/chat/completions`
- **Streaming:** SSE (Server-Sent Events) with `data: [DONE]`
- **Headers:** Optional `HTTP-Referer` and `X-Title` for OpenRouter rankings

```kotlin
AivoSdk {
    providers {
        openRouter("openrouter") {
            apiKey(System.getenv("OPENROUTER_API_KEY") ?: "")
            referer("https://mybank.com")
            title("Aivo Bank Assistant")
        }
    }
    defaultModel("openrouter:deepseek/deepseek-v4.1-flash")
}
```

### Google Gemini (Interactions API)
Uses Google's Gemini Interactions API (`/v1beta/interactions`) supporting thought signatures and stateful sessions.
- **Protocol:** HTTP POST
- **Streaming:** Typed SSE events (`interaction.created`, `step.start`, `step.delta`, `interaction.completed`)
- **Auth:** `x-goog-api-key` header

```kotlin
AivoSdk {
    providers {
        gemini("gemini") {
            apiKey(System.getenv("GEMINI_API_KEY") ?: "")
        }
    }
    defaultModel("gemini:gemini-2.5-flash")
}
```

## 2. Model References
All models are identified by a string format:
`providerId:modelName`

Examples:
- `ollama:gemma4:31b`
- `openrouter:anthropic/claude-3.5-sonnet`
- `gemini:gemini-2.5-flash`

The first colon splits the provider routing key from the vendor model identifier. Slashes or colons in model names are preserved.
