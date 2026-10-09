# Getting Started & Installation

This guide walks through adding Aivo SDK to your project, obtaining LLM provider credentials, and storing API keys securely.

---

## 1. Installation

### Step 1: Add Maven Central Repository
In your root `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
```

### Step 2: Add the Dependency

#### Android (Pure Android Project)
In `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.abdallah-elsobky:aivo-sdk:1.0.1")
}
```

#### Kotlin Multiplatform (KMP)
Add the single cross-platform dependency in your shared module's `commonMain` source set:

```kotlin
// shared/build.gradle.kts
kotlin {
    androidTarget()
    iosArm64()
    iosSimulatorArm64()
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation("io.github.abdallah-elsobky:aivo-sdk:1.0.1")
        }
    }
}
```

> **No platform-specific artifacts needed.** A single dependency provides complete multiplatform support across Android (API 24+), JVM 11+, and iOS (Apple Silicon & Simulator).

---

## 2. Obtaining API Keys

### Ollama *(Default — Cloud & Local)*
1. Visit [ollama.com](https://ollama.com) and create an account.
2. Navigate to **Settings → API Keys** ([ollama.com/settings/keys](https://ollama.com/settings/keys)) and generate a key.
3. *Note:* If running Ollama locally via Docker or desktop binary, no API key is required; simply provide `baseUrl = "http://localhost:11434"`.

### Google Gemini *(Free Tier Available)*
1. Go to [Google AI Studio](https://aistudio.google.com/app/apikey).
2. Click **"Create API key"** and select or create a project.
3. Copy your key (starts with `AIza...`).

### OpenRouter *(Free Models Available)*
1. Sign up at [openrouter.ai](https://openrouter.ai).
2. Go to **Keys → Create Key**.
3. Copy your key (starts with `sk-or-...`).

---

## 3. Storing API Keys Securely

> [!CAUTION]
> **Never hardcode API keys in source files or commit them to version control.**

### Pattern 1: Android — `local.properties` → `BuildConfig`

`local.properties` is ignored by Git by default in Android Studio projects.

1. Add your keys to `local.properties` (in project root):
   ```properties
   # local.properties (never committed)
   OLLAMA_API_KEY=your_ollama_api_key_here
   GEMINI_API_KEY=AIzaSy...
   OPENROUTER_API_KEY=sk-or-...
   ```

2. Expose the keys to `BuildConfig` in `app/build.gradle.kts`:
   ```kotlin
   android {
       buildFeatures { buildConfig = true }

       defaultConfig {
           val localProps = java.util.Properties().apply {
               val file = rootProject.file("local.properties")
               if (file.exists()) load(file.inputStream())
           }

           buildConfigField(
               "String", "OLLAMA_API_KEY",
               "\"${localProps["OLLAMA_API_KEY"] ?: ""}\""
           )
           buildConfigField(
               "String", "GEMINI_API_KEY",
               "\"${localProps["GEMINI_API_KEY"] ?: ""}\""
           )
       }
   }
   ```

3. Read them at runtime:
   ```kotlin
   import com.example.app.BuildConfig
   import com.aivo.sdk.AivoSdk
   import com.aivo.sdk.AivoProvider
   import com.aivo.sdk.provider.ollama.OllamaModel

   private val aivo = AivoSdk.create(
       provider = AivoProvider.OLLAMA,
       model    = OllamaModel.GPT_OSS_120B.modelId,
       apiKey   = BuildConfig.OLLAMA_API_KEY,
   )
   ```

---

### Pattern 2: KMP Shared Module Architecture

Inject the API key from platform code into your shared business logic layer:

```kotlin
// shared/commonMain
class ChatRepository(private val apiKey: String) {
    private val aivo = AivoSdk.create(
        provider = AivoProvider.OLLAMA,
        model    = OllamaModel.GPT_OSS_120B.modelId,
        apiKey   = apiKey,
    )

    suspend fun chat(conversationId: ConversationId, prompt: String): String =
        aivo.chat(conversationId, prompt).text
}
```

Platform injections:
```kotlin
// androidMain
val repository = ChatRepository(apiKey = BuildConfig.OLLAMA_API_KEY)

// iosMain / jvmMain
val repository = ChatRepository(
    apiKey = NSProcessInfo.processInfo.environment["OLLAMA_API_KEY"] as? String ?: ""
)
```

---

### Pattern 3: Production Mobile Gateway (Best Practice)

In enterprise mobile applications, shipping master API keys inside the APK/IPA is discouraged because keys can be decompiled. 

The recommended approach is to connect Aivo SDK to an authenticated company proxy using the custom endpoint capability:

```kotlin
val aivo = AivoSdk.create(
    provider = AivoProvider.OLLAMA,
    model    = OllamaModel.GPT_OSS_120B.modelId,
    baseUrl  = "https://api.mycompany.com/v1/ai-proxy", // Internal mobile gateway
    apiKey   = userSessionToken,                        // Short-lived user auth token
)
```
