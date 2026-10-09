# Tools & Capabilities

Tools give your agents the power to interact with external APIs, databases, native mobile hardware, and local files.

---

## 1. Defining Tools

Use the type-safe `tool { }` DSL to declare tool names, descriptions, parameter schemas, and execution logic.

```kotlin
import com.aivo.sdk.core.model.ParamType
import com.aivo.sdk.runtime.tool.tool

val getWeatherTool = tool("get_weather", "Get current weather for a city") {
    param("city", "The city name", type = ParamType.String, required = true)
    param(
        name        = "unit",
        description = "Temperature unit",
        type        = ParamType.String,
        required    = false,
        enum        = listOf("celsius", "fahrenheit")
    )

    execute { args ->
        val city = args.string("city")
        val unit = args.stringOrNull("unit") ?: "celsius"
        // Invoke your real weather service
        "Weather in $city: 22°${if (unit == "celsius") "C" else "F"}, sunny"
    }
}
```

---

## 2. Advanced Parameter Schemas

You can define rich parameters with validation constraints (`minLength`, `minimum`, `maximum`, `enum`):

```kotlin
val createUserTool = tool("create_user", "Creates a new user account") {
    parameters {
        string("username",    "Unique username",     required = true,  minLength = 3, maxLength = 30)
        string("email",       "Email address",        required = true)
        integer("age",        "User age",             required = false, minimum = 13, maximum = 120)
        boolean("newsletter", "Opt-in to newsletter", required = false)
        string("role",        "Assigned role",        required = true,
               enum = listOf("user", "admin", "moderator"))
    }

    execute { args ->
        val username = args.string("username")
        val email    = args.string("email")
        val role     = args.string("role")
        // Return string or JSON string result
        """{"id": "usr_99", "username": "$username", "email": "$email", "role": "$role"}"""
    }
}
```

**Supported Parameter Types:** `String` · `Int` · `Long` · `Double` · `Boolean` · `Array` · `Object`

---

## 3. Risk Levels & Human Confirmation Gates

Actions with high blast radius (such as deleting files, transferring funds, or changing passwords) can be gated behind human approval.

### Step 1: Assign Risk & Require Confirmation

```kotlin
val deleteFileTool = tool("delete_file", "Permanently deletes a file from disk") {
    param("path", "Absolute file path", type = ParamType.String, required = true)

    risk(ToolRisk.HIGH)
    requiresConfirmation(true) // Pauses the agent until user explicitly approves

    execute { args ->
        val path = args.string("path")
        File(path).delete()
        "File successfully deleted: $path"
    }
}
```

### Step 2: Implement `ConfirmationHandler`

Provide a handler in the `security { }` configuration to display an Android dialog, alert prompt, or biometric prompt:

```kotlin
val aivo = AivoSdk {
    // ...
    tools { +deleteFileTool }

    security {
        confirmationHandler = ConfirmationHandler { toolName, args ->
            // Suspend and prompt user: return true (allow) or false (deny)
            showUserApprovalDialog("Allow tool '$toolName'?", args.toString())
        }
    }
}
```

If the user denies the prompt, the agent receives a `ToolResult.Failure(DENIED)` and automatically adjusts its response without crashing.

---

## 4. Execution Pipeline & Safety Guarantees

Every tool call traverses a 6-stage pipeline:
1. **Registry Lookup:** Verifies the tool is registered and authorized for the active agent.
2. **Schema Validation:** Verifies argument types against JSON Schema before running tool code.
3. **Policy Gate:** Checks tool policy and triggers `confirmationHandler` if required.
4. **Timeout Enforcement:** Cancels execution if a tool hangs beyond `toolTimeoutMs` (default: 30s).
5. **Exception Boundary:** Tool implementations cannot crash the agent runtime; unhandled exceptions are caught and returned as structured failure messages.
6. **Token Truncation:** Large return values exceeding `maxToolResultChars` are truncated to protect your token budget.
