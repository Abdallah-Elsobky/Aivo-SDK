# Agents & Multi-Agent Teams

Agents are the central abstraction in Aivo SDK. An agent defines a persona, instructions, tools, and optional sub-agents it can delegate to.

---

## 1. Defining Agents in Kotlin DSL

```kotlin
import com.aivo.sdk.AivoSdk
import com.aivo.sdk.AivoProvider
import com.aivo.sdk.provider.ollama.OllamaModel

val aivo = AivoSdk {
    providers {
        ollama(AivoProvider.OLLAMA.id) { apiKey(BuildConfig.OLLAMA_API_KEY) }
    }
    defaultModel(OllamaModel.GPT_OSS_120B)

    tools {
        +getWeatherTool
        +searchWebTool
    }

    agents {
        define("assistant") {
            name         = "Personal Assistant"
            systemPrompt = """
                You are a helpful assistant with access to real-time tools.
                Current customer name: {{user_name}}.
                Always be concise and accurate.
            """.trimIndent()
            
            // Allow this agent to invoke specific registered tools
            tools("get_weather", "search_web")

            // Optional: override model for this specific agent
            model(OllamaModel.GPT_OSS_120B)
            maxSteps = 15
        }
    }
    entryAgent("assistant")
}

// Invoke with prompt variables
val result = aivo.chat(
    conversationId = ConversationId("session-1"),
    message        = "What's the weather today in Cairo?",
    variables      = mapOf("user_name" to "Abdallah")
)
println(result.text)
```

---

## 2. Declarative Agents (Markdown & JSON)

Declarative definitions decouple prompt engineering from Kotlin code, allowing product teams to tweak agent instructions without app re-compilation.

### Markdown with YAML Frontmatter

```markdown
<!-- assets/agents/researcher.md -->
---
id: researcher
name: Research Specialist
model: ollama:gpt-oss:120b
tools:
  - search_web
  - get_weather
maxSteps: 20
---
You are a meticulous research specialist. Use the provided tools to gather
accurate, up-to-date information. Always cite your sources.
Never fabricate data or statistics.
```

Load markdown agents:
```kotlin
agents {
    // Android: loads from assets/agents/researcher.md
    // JVM: loads from classpath or filesystem path
    fromMarkdownPath("agents/researcher.md")
}
entryAgent("researcher")
```

### JSON Definition

```json
{
  "id": "coding-assistant",
  "name": "Coding Assistant",
  "model": "ollama:gpt-oss:120b",
  "systemPrompt": "You are an expert Kotlin developer. Write clean, idiomatic code.",
  "tools": ["run_code", "search_docs"],
  "maxSteps": 10
}
```

```kotlin
agents {
    fromJsonPath("agents/coding-assistant.json")
    // Or load from raw string:
    fromJson("""{ "id": "bot", "systemPrompt": "You are a helpful bot." }""")
}
```

---

## 3. Multi-Agent Teams & Supervisor Delegation

Compose specialized agents into a collaborative team. The supervisor dynamically delegates tasks to sub-agents using model reasoning.

```kotlin
val aivo = AivoSdk {
    providers {
        ollama(AivoProvider.OLLAMA.id) { apiKey(BuildConfig.OLLAMA_API_KEY) }
    }
    defaultModel(OllamaModel.GPT_OSS_120B)

    tools {
        +searchWebTool
        +writeFileTool
        +readFileTool
    }

    agents {
        // Supervisor — orchestrates the team
        define("supervisor") {
            name         = "Team Supervisor"
            systemPrompt = """
                You coordinate a research and writing team.
                Delegate research tasks to the 'researcher' agent.
                Delegate writing tasks to the 'writer' agent.
                Synthesize results into a final answer.
            """.trimIndent()
            delegates("researcher", "writer")
        }

        // Sub-agent 1 — Research Specialist
        define("researcher") {
            name         = "Research Specialist"
            systemPrompt = "You gather accurate, up-to-date information using tools."
            tools("search_web")
        }

        // Sub-agent 2 — Writing Specialist
        define("writer") {
            name         = "Writing Specialist"
            systemPrompt = "You write polished, professional content based on research notes."
            tools("write_file", "read_file")
        }
    }
    entryAgent("supervisor")
}

// The supervisor automatically delegates to researcher and writer
val result = aivo.chat(
    ConversationId("project-1"),
    "Research the history of Kotlin and write a 500-word blog post."
)
println(result.text)
```

### Safety & Guardrails Built-In:
* **Cycle Detection:** If Agent A delegates to Agent B and Agent B attempts to delegate back to Agent A, the runtime catches the cycle and throws `DelegationCycleException`.
* **Depth Limits:** Supervisor nesting is capped at 3 levels by default (configurable via `runtime { maxDelegationDepth = 4 }`).
* **Context Isolation:** Sub-agents execute within isolated child conversation scopes so large tool payloads do not pollute the supervisor's context window.
