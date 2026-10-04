# Aivo SDK — Tool System Guide

Tools are functions and capabilities that an LLM can invoke during an agent run.

## 1. Defining a Tool

Use the `tool` DSL:

```kotlin
val getBalanceTool = tool(
    name = "get_balance",
    description = "Retrieves the current account balance for a given customer account ID."
) {
    parameters {
        string("accountId", description = "The account ID to query", required = true)
    }
    execute { call, context ->
        val accountId = call.arguments["accountId"]?.jsonPrimitive?.content
            ?: return@execute ToolResult.Failure(
                ToolFailureKind.INVALID_ARGUMENTS,
                "Missing accountId"
            )

        val balance = backend.getBalance(accountId)
        ToolResult.Success(
            buildJsonObject {
                put("accountId", accountId)
                put("balance", balance)
            }
        )
    }
}
```

## 2. Safety & Risk Levels

Tools can be assigned risk levels:
- `ToolRisk.LOW` (default) — Read-only actions (e.g. searching FAQs, fetching rates)
- `ToolRisk.MEDIUM` — State changes with low blast radius
- `ToolRisk.HIGH` — Financial transfers, deletions, account modifications

High-risk tools can require explicit user confirmation before executing:

```kotlin
val transferTool = tool("transfer_money") {
    risk(ToolRisk.HIGH)
    requiresConfirmation(true)
    // ...
}
```

In the SDK builder, provide a `confirmationHandler`:
```kotlin
AivoSdk {
    security {
        confirmationHandler = { call, spec ->
            // Suspend until user approves in native UI dialog
            uiDialog.showConfirmationPrompt(call, spec)
        }
    }
}
```

## 3. Tool Execution Pipeline
Each tool call requested by an LLM traverses the pipeline:
1. **Registry Lookup** — check if tool is registered
2. **Schema Validation** — validate arguments against JSON Schema
3. **Policy Gate** — check `ToolPolicy` & trigger `confirmationHandler` if required
4. **Timeout Enforcement** — cancel with timeout if execution takes too long
5. **Execution** — run the tool `execute` block safely
6. **Result Truncation** — truncate excessive string outputs to preserve token budget
