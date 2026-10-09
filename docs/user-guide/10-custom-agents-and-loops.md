# Custom Agents & Reasoning Loops

This guide explains how to build custom agents, define custom reasoning loops, create reusable tools, and orchestrate multi-agent workflows using the **Aivo SDK**.

---

## 1. The Three Fundamental Nouns

Every concept in the SDK reduces to one of three foundational nouns:

| Noun | Role | Key Responsibilities |
|---|---|---|
| **Agent** | *Identity & Capabilities* | Archetype/Role, System Prompt Composer, Scoped Tools, Limits, Model. |
| **Tool** | *Reusable Action* | Typed parameter schema, Risk policy, Execution logic (fetching APIs, databases). |
| **Loop** | *Reasoning Algorithm* | The control flow algorithm (`ToolCallingLoop`, `SequenceLoop`, `RouterLoop`, or your custom loop). |

---

## 2. Type-Safe Catalogs: Zero Magic Strings

The SDK eliminates magic strings across agent declarations.

### Type-Safe Roles (`AgentRole`)

```kotlin
import com.aivo.sdk.core.model.AgentRole

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

### Strongly-Typed Blackboard State (`StateKey<T>`)

Instead of passing untyped strings between agents in multi-agent workflows:

```kotlin
import com.aivo.sdk.core.model.StateKey

object AppKeys {
    val ResearchNotes = StateKey.string("research_notes")
    val DraftArticle = StateKey.string("draft_article")
    val RetryCount = StateKey.int("retry_count")
}
```

---

## 3. Custom Agents with Fluent DSL

```kotlin
import com.aivo.sdk.core.model.AgentRole
import com.aivo.sdk.runtime.agent.agent

val supportAgent = agent("customer_support") {
    name = "Billing Support Specialist"
    role = AgentRole.SUPPORT
    goal = "Resolve billing questions swiftly and accurately."
    instructions(
        "Look up invoice history before answering payment queries.",
        "If a refund exceeds $100, escalate to a supervisor."
    )
    tools(weatherTool, billingToolbox)
}
```

---

## 4. Supervisor & Workflow Teams

### Model-Driven Supervisor

In this mode, the **Supervisor LLM** analyzes user intent and decides which specialist to delegate to:

```kotlin
import com.aivo.sdk.core.model.AgentRole
import com.aivo.sdk.runtime.agent.agent
import com.aivo.sdk.runtime.agent.supervisor

val researcher = agent("researcher") {
    role = AgentRole.RESEARCHER
    description = "Searches, verifies, and bullet-points facts on any topic."
    tools(weatherTool)
}

val writer = agent("writer") {
    role = AgentRole.WRITER
    description = "Drafts clear, engaging responses based on research notes."
}

val teamLead = supervisor("lead") {
    name = "Editorial Lead"
    role = AgentRole.SUPERVISOR
    instructions("Analyze the user query. Delegate research to researcher, then delegate formatting to writer.")
    manages(researcher, writer) // Automatically registers researcher and writer
}
```

### Code-Driven Workflow Orchestrator

When you want a deterministic execution pipeline written in clean Kotlin code:

```kotlin
import com.aivo.sdk.core.model.AgentRole
import com.aivo.sdk.runtime.agent.agent
import com.aivo.sdk.runtime.agent.workflow

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

## 5. Implementing a Custom Reasoning Loop

To build a custom reasoning loop (such as Plan-and-Execute, Tree-of-Thought, or Reflection), implement the [`AgentLoop`](file:///d:/Projects/AivoSdk/sdk-core/src/commonMain/kotlin/com/aivo/sdk/core/port/AgentLoop.kt) interface:

```kotlin
import com.aivo.sdk.core.model.Message
import com.aivo.sdk.core.model.ToolSelection
import com.aivo.sdk.core.port.AgentLoop
import com.aivo.sdk.core.port.LoopContext
import com.aivo.sdk.core.port.LoopOutcome
import com.aivo.sdk.core.port.Turn

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

### Safety Guarantees Inside Primitives:
All safety invariants are enforced **inside `LoopContext` primitives**, not inside the loop:
- `ctx.think()`: Enforces `maxSteps`, `runTimeoutMs`, and token budgets.
- `ctx.act()`: Enforces tool allow-lists (only tools declared on the agent can execute) and timeouts.
- `ctx.delegate()`: Enforces `maxDelegationDepth` and cycle detection.
