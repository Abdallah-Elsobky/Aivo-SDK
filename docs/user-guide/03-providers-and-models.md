# Providers & Models

Aivo SDK is completely provider-agnostic. You can switch between cloud APIs, local LLMs, and private endpoints with a single line of code.

---

## 🔌 Supported Providers

### 1. Ollama *(Default)*

[Ollama](https://ollama.com) hosts high-performance open models. By default, the SDK points directly to the cloud endpoint (`https://ollama.com/api/chat`).

```kotlin
import com.aivo.sdk.AivoSdk
import com.aivo.sdk.AivoProvider
import com.aivo.sdk.provider.ollama.OllamaModel

val aivo = AivoSdk {
    providers {
        ollama(AivoProvider.OLLAMA.id) {
            apiKey(BuildConfig.OLLAMA_API_KEY)
            // Optional: override baseUrl if connecting to a self-hosted instance:
            // baseUrl("http://192.168.1.50:11434")
        }
    }
    defaultModel(OllamaModel.GPT_OSS_120B)
}
```

**Common Strongly-Typed Ollama Models (`OllamaModel`):**

| Constant | Model ID (`modelId`) | Tier | Description |
|---|---|---|---|
| `OllamaModel.GPT_OSS_120B` | `gpt-oss:120b` | Free | Default high-capability 120B model |
| `OllamaModel.GPT_OSS_20B` | `gpt-oss:20b` | Free | Fast and lightweight 20B model |
| `OllamaModel.GEMMA4_31B` | `gemma4:31b` | Free | Gemma 4 31B instruction model |
| `OllamaModel.NEMOTRON_3_SUPER` | `nemotron-3-super` | Free | Nemotron 3 Super reasoning model |
| `OllamaModel.DEEPSEEK_V4_1_FLASH` | `deepseek-v4.1-flash` | Paid | DeepSeek v4.1 Flash |
| `OllamaModel.MISTRAL_LARGE_3` | `mistral-large-3` | Paid | Mistral Large 3 enterprise model |

---

### 2. Google Gemini

Connects to Google's modern Gemini API with stateful sessions and function calling.

```kotlin
import com.aivo.sdk.AivoSdk
import com.aivo.sdk.AivoProvider
import com.aivo.sdk.provider.gemini.GeminiModel

val aivo = AivoSdk {
    providers {
        gemini(AivoProvider.GEMINI.id) {
            apiKey(BuildConfig.GEMINI_API_KEY)
            // Optional: override base URL (e.g. for Vertex AI, custom proxy, or gateway)
            // baseUrl("https://custom-proxy.internal.com")
        }
    }
    defaultModel(GeminiModel.GEMINI_3_8_FLASH)
}
```

**Common Strongly-Typed Gemini Models (`GeminiModel`):**

| Constant | Model ID (`modelId`) | Tier |
|---|---|---|
| `GeminiModel.GEMINI_3_8_FLASH` | `gemini-3.8-flash` | Free |
| `GeminiModel.GEMINI_3_7_FLASH` | `gemini-3.7-flash` | Free |
| `GeminiModel.GEMINI_3_6_FLASH` | `gemini-3.6-flash` | Free |
| `GeminiModel.GEMINI_3_5_FLASH` | `gemini-3.5-flash` | Free |
| `GeminiModel.GEMINI_3_1_PRO_PREVIEW` | `gemini-3.1-pro-preview` | Paid |

---

### 3. OpenRouter

[OpenRouter](https://openrouter.ai) provides access to 200+ models from Meta, Mistral, Anthropic, and Cohere through a unified API.

```kotlin
import com.aivo.sdk.AivoSdk
import com.aivo.sdk.AivoProvider
import com.aivo.sdk.provider.openai.OpenRouterModel

val aivo = AivoSdk {
    providers {
        openRouter(AivoProvider.OPEN_ROUTER.id) {
            apiKey(BuildConfig.OPENROUTER_API_KEY)
            referer("https://myapp.com")  // optional
            title("My App")               // optional
        }
    }
    defaultModel(OpenRouterModel.LING_3_0_FLASH_SANTE_FREE)
}
```

**Common Strongly-Typed OpenRouter Models (`OpenRouterModel`):**

| Constant | Model ID (`modelId`) | Tier |
|---|---|---|
| `OpenRouterModel.LING_3_0_FLASH_SANTE_FREE` | `inclusionai/ling-3.0-flash-sante:free` | Free |
| `OpenRouterModel.QWEN3_8_27B_FREE` | `qwen/qwen3.8-27b:free` | Free |
| `OpenRouterModel.NEMOTRON_3_5_LIGHTNING_FREE` | `nvidia/nemotron-3.5-lightning:free` | Free |
| `OpenRouterModel.LFM_2_5_2_6B_FREE` | `liquid/lfm-2.5-2.6b:free` | Free |

---

### 4. OpenAI-Compatible & Custom Endpoints

Connect to any service implementing the OpenAI chat completions format (Together AI, Fireworks AI, Groq, Perplexity, LiteLLM, vLLM, self-hosted LLMs):

```kotlin
val aivo = AivoSdk {
    providers {
        openAiCompatible("custom-provider") {
            baseUrl("https://api.myservice.com/v1") // Custom endpoint URL as String
            apiKey(BuildConfig.MY_API_KEY)
            header("X-Custom-Header", "value")      // Optional custom headers
        }
    }
    defaultModel("custom-provider:my-custom-model") // Custom model identifier as String
}
```

---

## 🏷️ Model Reference Syntax

When referencing models as raw strings, the format is always:
`"providerId:modelName"`

Examples:
* `"ollama:gpt-oss:120b"`
* `"gemini:gemini-3.8-flash"`
* `"openrouter:inclusionai/ling-3.0-flash-sante:free"`
* `"custom-provider:mistral-7b-instruct"`
