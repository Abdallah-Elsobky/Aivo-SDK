# Aivo SDK: Custom Agents, Reasoning Loops & Reusable Tools Guide

This guide explains how to build custom agents, define custom reasoning loops, create reusable tools, and orchestrate multi-agent teams using the **Aivo SDK**.

---

## 1. The Three Fundamental Nouns

Every concept in the SDK reduces to one of three foundational nouns:

| Noun | Role | Key Responsibilities |
|---|---|---|
| **Agent** | *Identity & Capabilities* | Archetype/Role, System Prompt Composer, Scoped Tools, Limits, Model. |
| **Tool** | *Reusable Action* | Typed parameter schema, Risk policy, Execution logic (fetching APIs, databases). |
| **Loop** | *Reasoning Algorithm* | The control flow algorithm (`ToolCallingLoop`, `SequenceLoop`, `RouterLoop`, or your own custom loop). |

---

## 2. Zero Hardcoding: Type-Safe Catalogs

The SDK eliminates magic strings across all agent declarations. You can use standard catalogs out of the box or define custom typed values.

### 2.1 Type-Safe Roles (`AgentRole`)

```kotlin
import com.aivo.sdk.AgentRole

// Prebuilt standard roles
val assistant = AgentRole.ASSISTANT
val researcher = AgentRole.RESEARCHER
val writer = AgentRole.WRITER
val reviewer = AgentRole.REVIEWER
val supervisor = AgentRole.SUPERVISOR
val orchestrator = AgentRole.ORCHESTRATOR
val coder = AgentRole.CODER
val support = AgentRole.SUPPORT
val analyst = AgentRole.ANALYST
val router = AgentRole.ROUTER

// Custom domain role without hardcoded strings in business logic
val travelAdvisor = AgentRole.custom(
    id = "travel_advisor",
    title = "Luxury Travel Advisor",
    defaultResponsibilities = listOf("Recommend hotels and flight itineraries", "Provide local cultural advice")
)
```

### 2.2 Reusable Presets & Rules

```kotlin
import com.aivo.sdk.StylePreset
import com.aivo.sdk.SafetyRule
import com.aivo.sdk.OutputFormat

// Style Presets
val concise = StylePreset.CONCISE       // "Be direct, concise, and avoid unnecessary conversational filler."
val technical = StylePreset.TECHNICAL   // "Use precise technical terminology. Include clear code snippets."
val empathetic = StylePreset.EMPATHETIC // "Communicate with warmth, active listening, and professional empathy."

// Safety Rules
val noHallucinations = SafetyRule.NEVER_HALLUCINATE_SOURCES
val checkState = SafetyRule.CONSTRAIN_TO_STATE
val confirmRisks = SafetyRule.CONFIRM_HIGH_RISK_ACTIONS

// Output Format
val jsonOutput = OutputFormat.Json("""{"status": "OK" | "ERROR", "data": any}""")
val markdown = OutputFormat.Markdown
val approval = OutputFormat.ApprovalVerdict // First line APPROVED or CHANGES_NEEDED
```

### 2.3 Strongly-Typed Blackboard State (`StateKey<T>`)

Instead of passing untyped strings between agents:

```kotlin
import com.aivo.sdk.StateKey

object AppKeys {
    val ResearchNotes = StateKey.string("research_notes")
    val DraftArticle = StateKey.string("draft_article")
    val RetryCount = StateKey.int("retry_count")
}
```

---

## 3. Defining Custom Reusable Tools

Creating a custom tool requires only a few lines with the ergonomic `tool` DSL:

```kotlin
import com.aivo.sdk.tool
import com.aivo.sdk.ParamType

val weatherTool = tool("get_weather", "Fetch current live weather and temperature for a city") {
    param("city", "City name (e.g. Cairo, London, Tokyo)", type = ParamType.String, required = true)
    param("unit", "Temperature unit: C or F", type = ParamType.String, required = false)

    execute { args ->
        val city = args.string("city")
        val unit = args.stringOrNull("unit") ?: "C"

        // Call your actual backend, HTTP API, or database:
        // Any Map, Data Class, primitive, or String is automatically wrapped!
        mapOf(
            "city" to city,
            "temp" to if (unit == "C") 28 else 82,
            "condition" to "Sunny",
            "humidity" to "40%"
        )
    }
}
```

### Grouping Tools with `Toolbox`

```kotlin
import com.aivo.sdk.toolbox

val travelTools = toolbox("travel_toolbox", weatherTool, flightSearchTool, hotelBookTool)
```

---

## 4. Custom Agents

You can define agents using the fluent `agent` DSL:

```kotlin
import com.aivo.sdk.agent

val supportAgent = agent("customer_support") {
    name = "Billing Support Specialist"
    role = AgentRole.SUPPORT
    goal = "Resolve billing questions swiftly and accurately."
    style = StylePreset.EMPATHETIC
    rules(SafetyRule.NEVER_HALLUCINATE_SOURCES, SafetyRule.CONFIRM_HIGH_RISK_ACTIONS)
    instructions(
        "Look up invoice history before answering payment queries.",
        "If a refund exceeds $100, escalate to a supervisor."
    )
    tools(weatherTool, billingToolbox)
}
```

---

## 5. Supervisor & Orchestrator Teams

### 5.1 Model-Driven Supervisor

In this mode, the **Supervisor LLM** analyzes the user prompt and decides which specialist to delegate tasks to:

```kotlin
import com.aivo.sdk.supervisor

val researcher = agent("researcher") {
    role = AgentRole.RESEARCHER
    description = "Searches, verifies, and bullet-points facts on any topic."
    tools(weatherTool)
}

val writer = agent("writer") {
    role = AgentRole.WRITER
    description = "Drafts clear, engaging responses based on research notes."
    style = StylePreset.TECHNICAL
}

val teamLead = supervisor("lead") {
    name = "Editorial Lead"
    role = AgentRole.SUPERVISOR
    instructions("Analyze the user query. Delegate research to researcher, then delegate formatting to writer.")
    manages(researcher, writer) // Automatically auto-registers researcher and writer!
}
```

### 5.2 Code-Driven Workflow Orchestrator

When you want a deterministic execution pipeline written in clean Kotlin code:

```kotlin
import com.aivo.sdk.workflow

val articlePipeline = agent("article_pipeline") {
    role = AgentRole.ORCHESTRATOR
    description = "Deterministic research -> write -> review pipeline."

    loop = workflow(researcher, writer, reviewer) {
        // Step 1: Run researcher
        val notes = run(researcher, input)

        // Step 2: Run writer
        var draft = run(writer, "Draft an article from research notes:\n$notes")

        // Step 3: Run review loop (up to 2 iterations)
        repeat(2) {
            val review = run(reviewer, draft)
            if (review.startsWith("APPROVED")) {
                return@workflow finish(draft)
            }
            draft = run(writer, "Revise draft based on fixes:\n$review\n\nDraft:\n$draft")
        }

        finish(draft)
    }
}
```

---

## 6. Creating a Custom Agent Reasoning Loop

If you want complete control over the reasoning loop, implement the `AgentLoop` interface:

```kotlin
import com.aivo.sdk.AgentLoop
import com.aivo.sdk.LoopContext
import com.aivo.sdk.LoopOutcome
import com.aivo.sdk.Turn

class PlanAndExecuteLoop : AgentLoop {
    override val name: String = "plan_and_execute"

    override suspend fun run(context: LoopContext): LoopOutcome {
        // Step 1: Generate plan (disable tools for this turn)
        val planTurn = context.think(
            tools = ToolSelection.None,
            promptOverride = "Produce a step-by-step numbered plan to answer: ${context.input}"
        )
        val planText = (planTurn as Turn.Text).text

        // Step 2: Execute plan with tools enabled
        context.append(Message.System("Execution Plan:\n$planText"))
        val execTurn = context.think(tools = ToolSelection.Auto)

        if (execTurn is Turn.Call) {
            // Execute requested tools safely
            context.act(execTurn.calls)
            // Final synthesis turn
            val finalTurn = context.think(tools = ToolSelection.None)
            return context.finish((finalTurn as Turn.Text).text)
        }

        return context.finish((execTurn as Turn.Text).text)
    }
}
```

### Safety Guarantees Inside Primitives

All safety invariants are enforced **inside `LoopContext` primitives**, not inside the loop:
- `ctx.think()`: Enforces `maxSteps`, `runTimeoutMs`, and `maxTokens`.
- `ctx.act()`: Enforces tool allow-list (only tools declared on the agent can execute) and timeouts.
- `ctx.delegate()`: Enforces `maxDelegationDepth` and cycle detection.

---

## 7. Initializing the SDK Facade

```kotlin
import com.aivo.sdk.AivoSdk
import com.aivo.sdk.core.model.OpenRouterModel

val ai = AivoSdk {
    providers {
        openRouter { apiKey(System.getenv("OPENROUTER_API_KEY")) }
    }
    defaultModel(OpenRouterModel.Free.QWEN_3_8_27B)

    agents {
        +teamLead        // Automatically registers teamLead, researcher, and writer!
        +articlePipeline
    }

    entryAgent("lead")
}

// Inspect the entire agent graph at runtime
println(ai.describe())

// Run the supervisor
val reply = ai.agent("lead").run("Research and summarize recent developments in fusion energy.")
println(reply.text)

// Stream the workflow pipeline
ai.agent("article_pipeline").stream("Write an article about quantum computing")
    .collect { event ->
        when (event) {
            is AgentEvent.AgentEntered -> println("[Agent: ${event.agentId}]")
            is AgentEvent.ToolCallStarted -> println("[Tool: ${event.toolName}]")
            is AgentEvent.TextDelta -> print(event.text)
            is AgentEvent.RunCompleted -> println("\n[Completed in ${event.result.steps} steps]")
            else -> {}
        }
    }
```
