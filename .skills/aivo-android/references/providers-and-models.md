# Aivo SDK Android — Providers & Models Catalog

Aivo SDK provides first-class support for 4 major provider ecosystems:
1. **Google Gemini** (`AivoProvider.GEMINI`)
2. **Ollama Cloud / Local** (`AivoProvider.OLLAMA`)
3. **OpenRouter** (`AivoProvider.OPEN_ROUTER`)
4. **OpenAI-Compatible Custom Endpoints** (`openAiCompatible`)

---

## 1. Google Gemini (`AivoProvider.GEMINI`)

Google Gemini offers high speed, multi-modal reasoning, and generous free tiers.

### Recommended Models (`GeminiModel`):
| Constant | Model ID | Tier | Context Window | Description |
|---|---|---|---|---|
| `GeminiModel.GEMINI_3_8_FLASH` | `gemini-3.8-flash` | Free | 1M tokens | Recommended default. Fast, high accuracy. |
| `GeminiModel.GEMINI_3_7_FLASH` | `gemini-3.7-flash` | Free | 1M tokens | High reasoning capability. |
| `GeminiModel.GEMINI_3_6_FLASH` | `gemini-3.6-flash` | Free | 1M tokens | Ultra-low latency mobile use cases. |
| `GeminiModel.GEMINI_3_5_FLASH` | `gemini-3.5-flash` | Free | 1M tokens | Stable release. |
| `GeminiModel.GEMINI_3_1_PRO_PREVIEW`| `gemini-3.1-pro-preview` | Paid | 2M tokens | Deep reasoning and complex tool tasks. |

### Configuration:
```kotlin
val aivo = AivoSdk {
    providers {
        gemini(AivoProvider.GEMINI.id) {
            apiKey(BuildConfig.GEMINI_API_KEY)
            // Optional custom gateway or Vertex AI proxy:
            // baseUrl("https://my-vertex-proxy.internal.com")
        }
    }
    defaultModel(GeminiModel.GEMINI_3_8_FLASH)
}
```

---

## 2. Ollama Cloud & Local (`AivoProvider.OLLAMA`)

Ollama provides open-weights models. By default, Aivo SDK points to `https://ollama.com/api/chat`.

### Recommended Models (`OllamaModel`):
| Constant | Model ID | Tier | Description |
|---|---|---|---|
| `OllamaModel.GPT_OSS_120B` | `gpt-oss:120b` | Free | Flagship open model, superior coding and agent tools. |
| `OllamaModel.GPT_OSS_20B` | `gpt-oss:20b` | Free | Fast and lightweight, ideal for quick mobile chats. |
| `OllamaModel.GEMMA4_31B` | `gemma4:31b` | Free | Google Gemma instruction-tuned model. |
| `OllamaModel.NEMOTRON_3_SUPER` | `nemotron-3-super` | Free | NVIDIA Nemotron reasoning model. |
| `OllamaModel.DEEPSEEK_V4_1_FLASH` | `deepseek-v4.1-flash`| Paid | High-speed DeepSeek v4.1 model. |

### Android Emulator Local Ollama Note:
When testing against an Ollama instance running on your development laptop, Android Emulator cannot reach `http://localhost:11434`. Instead, use the special loopback IP:
```kotlin
providers {
    ollama(AivoProvider.OLLAMA.id) {
        // Android Emulator loopback IP to host machine:
        baseUrl("http://10.0.2.2:11434")
    }
}
```

---

## 3. OpenRouter (`AivoProvider.OPEN_ROUTER`)

OpenRouter aggregates 200+ models with smart routing and automated fallback.

### Recommended Free Models (`OpenRouterModel`):
| Constant | Model ID | Tier |
|---|---|---|
| `OpenRouterModel.LING_3_0_FLASH_SANTE_FREE` | `inclusionai/ling-3.0-flash-sante:free` | Free |
| `OpenRouterModel.QWEN3_8_27B_FREE` | `qwen/qwen3.8-27b:free` | Free |
| `OpenRouterModel.NEMOTRON_3_5_LIGHTNING_FREE` | `nvidia/nemotron-3.5-lightning:free` | Free |
| `OpenRouterModel.LFM_2_5_2_6B_FREE` | `liquid/lfm-2.5-2.6b:free` | Free |

### Configuration:
```kotlin
providers {
    openRouter(AivoProvider.OPEN_ROUTER.id) {
        apiKey(BuildConfig.OPENROUTER_API_KEY)
        referer("https://my-android-app.com")
        title("My Android Assistant")
    }
}
defaultModel(OpenRouterModel.LING_3_0_FLASH_SANTE_FREE)
```

---

## 4. OpenAI-Compatible Custom Endpoints

Any standard OpenAI chat completion API (Groq, Together AI, Perplexity, LiteLLM, vLLM) can be plugged in:

```kotlin
val aivo = AivoSdk {
    providers {
        openAiCompatible("company-llm") {
            baseUrl("https://ai-proxy.company.internal/v1")
            apiKey(BuildConfig.COMPANY_AI_KEY)
            header("X-Client-App", "Android-Enterprise")
        }
    }
    // Qualified "providerId:modelId" format:
    defaultModel("company-llm:llama-3.3-70b-instruct")
}
```
