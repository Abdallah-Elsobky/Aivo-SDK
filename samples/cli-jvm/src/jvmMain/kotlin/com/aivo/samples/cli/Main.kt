package com.aivo.samples.cli

import com.aivo.sdk.AivoSdk
import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.core.model.ToolResult
import com.aivo.sdk.core.model.ToolRisk
import com.aivo.sdk.runtime.agent.AgentEvent
import com.aivo.sdk.runtime.tool.tool
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * JVM CLI sample application demonstrating multi-agent supervisor patterns with the Aivo SDK.
 *
 * This sample shows a banking assistant use case with:
 * - A supervisor agent that delegates to specialist sub-agents
 * - Custom tools with high-risk confirmation gates
 * - Real-time streaming of agent events
 *
 * ## Configuration
 *
 * Before running, set your preferred provider's API key as an environment variable.
 * The sample will automatically detect and use the first available provider.
 *
 * | Provider    | Environment Variable     | Notes                              |
 * |-------------|--------------------------|------------------------------------|
 * | OpenRouter  | `OPENROUTER_API_KEY`     | Free tier available at openrouter.ai |
 * | Gemini      | `GEMINI_API_KEY`         | Free tier available at ai.google.dev |
 * | Ollama      | _(no key required)_      | Start Ollama locally first         |
 *
 * ### Running
 * ```bash
 * # Using OpenRouter
 * export OPENROUTER_API_KEY="sk-or-..."
 *
 * # Using Gemini
 * export GEMINI_API_KEY="AIza..."
 *
 * # Using Ollama (local — no key needed)
 * ollama pull llama3.2
 *
 * ./gradlew :samples:cli-jvm:run
 * ```
 */
fun main(): Unit = runBlocking {
    println("==================================================")
    println("    Aivo SDK — Banking Multi-Agent Supervisor     ")
    println("==================================================")

    // ── 1. Detect which provider is available ──────────────────────────────────
    val openRouterKey = System.getenv("OPENROUTER_API_KEY").orEmpty()
    val geminiKey     = System.getenv("GEMINI_API_KEY").orEmpty()
    val ollamaUrl     = System.getenv("OLLAMA_BASE_URL") ?: "http://localhost:11434"

    val (providerName, model) = when {
        openRouterKey.isNotBlank() -> {
            println("ℹ️  Using OpenRouter (OPENROUTER_API_KEY found)")
            "openrouter" to "openrouter:deepseek/deepseek-r1-0528-qwen3-8b:free"
        }
        geminiKey.isNotBlank() -> {
            println("ℹ️  Using Google Gemini (GEMINI_API_KEY found)")
            "gemini" to "gemini:gemini-2.0-flash"
        }
        else -> {
            println("ℹ️  No cloud API key found — using Ollama at $ollamaUrl")
            println("     Set OPENROUTER_API_KEY or GEMINI_API_KEY for cloud providers.")
            println("     Ensure 'ollama pull llama3.2' has been run.")
            "ollama" to "ollama:llama3.2"
        }
    }

    // ── 2. Define Tools ────────────────────────────────────────────────────────
    //
    // Tools are pure Kotlin lambdas registered with the SDK via the `tool { }` DSL.
    // In a production app, the execute block would call your real APIs.

    val getBalanceTool = tool(
        name = "get_balance",
        description = "Retrieves the current account balance for a customer account.",
    ) {
        parameters {
            string("accountId", description = "The account ID to query", required = true)
        }
        execute { call, _ ->
            val accountId = call.arguments["accountId"]?.jsonPrimitive?.content ?: "UNKNOWN"
            println("   [Tool] get_balance(accountId=$accountId)")
            // TODO: Replace with a real database / banking API call.
            ToolResult.Success(
                buildJsonObject {
                    put("accountId", accountId)
                    put("balance", 2_450.75)
                    put("currency", "USD")
                }
            )
        }
    }

    val transferMoneyTool = tool(
        name = "transfer_money",
        description = "Transfers money from the user's account to a recipient. Requires human confirmation.",
    ) {
        risk(ToolRisk.HIGH)
        requiresConfirmation(true)
        parameters {
            string("fromAccountId", description = "Sender account ID", required = true)
            string("toRecipient",   description = "Recipient name or account", required = true)
            number("amount",        description = "Amount to transfer in USD", required = true)
        }
        execute { call, _ ->
            val to     = call.arguments["toRecipient"]?.jsonPrimitive?.content ?: "Unknown"
            val amount = call.arguments["amount"]?.jsonPrimitive?.content ?: "0"
            println("   [Tool] transfer_money(to=$to, amount=$$amount)")
            // TODO: Replace with your secure payment gateway call.
            ToolResult.Success(
                buildJsonObject {
                    put("status", "SUCCESS")
                    put("transactionId", "TXN-98412")
                    put("message", "Successfully transferred $$amount to $to")
                }
            )
        }
    }

    val getProductInfoTool = tool(
        name = "get_product_information",
        description = "Provides details about available banking products, interest rates, and loan terms.",
    ) {
        parameters {
            string("productType", description = "Product category: savings, credit, loan", required = true)
        }
        execute { call, _ ->
            val type = call.arguments["productType"]?.jsonPrimitive?.content ?: "general"
            println("   [Tool] get_product_information(productType=$type)")
            ToolResult.Success(
                buildJsonObject {
                    put("product", type)
                    put("interestRateAnnual", "4.25% APY")
                    put("monthlyFee", "\$0.00")
                }
            )
        }
    }

    // ── 3. Configure AivoSdk ──────────────────────────────────────────────────
    //
    // The entire SDK is wired here in a single builder block.
    // Note: API keys come from environment variables — never hardcoded.

    val ai = AivoSdk {
        providers {
            when (providerName) {
                "openrouter" -> openRouter("openrouter") {
                    apiKey(openRouterKey)
                }
                "gemini" -> gemini("gemini") {
                    apiKey(geminiKey)
                }
                else -> ollama("ollama") {
                    baseUrl(ollamaUrl)
                }
            }
        }
        defaultModel(model)

        tools {
            register(getBalanceTool)
            register(transferMoneyTool)
            register(getProductInfoTool)
        }

        agents {
            // Supervisor: routes user requests to specialist agents
            define("supervisor") {
                name = "Banking Supervisor"
                description = "Coordinates all customer inquiries and delegates specialised tasks."
                systemPrompt = """
                    You are the primary Banking Assistant supervisor.
                    Understand the user's intent and delegate to the right specialist:
                    - 'payments' for balance inquiries and money transfers
                    - 'customer_service' for product information and general questions
                """.trimIndent()
                delegates("payments", "customer_service")
            }

            // Payments specialist: handles financial operations
            define("payments") {
                name = "Payments Specialist"
                description = "Handles account balance lookups and secure fund transfers."
                systemPrompt = "You are a secure payments agent. Execute financial operations accurately and safely."
                tools("get_balance", "transfer_money")
            }

            // Customer service: answers general questions
            define("customer_service") {
                name = "Customer Service Specialist"
                description = "Answers general questions about banking products and interest rates."
                systemPrompt = "You are a helpful customer service advisor. Provide clear product explanations."
                tools("get_product_information")
            }
        }

        entryAgent("supervisor")

        // Security: human-in-the-loop confirmation for high-risk tools
        security {
            confirmationHandler = { call, spec ->
                println("\n>>> [SECURITY GATE] High-risk tool requires confirmation: ${spec.name}")
                println(">>> Arguments: ${call.arguments}")
                print(">>> Approve? (y/n): ")
                // In this CLI demo we auto-approve. In a real app, prompt the user.
                println("y (auto-approved in demo)")
                true
            }
        }

        // Resilience: exponential backoff with jitter
        resilience {
            retry {
                maxAttempts = 3
                baseDelayMs = 1_000L
            }
        }
    }

    println("\nSDK Configuration:")
    println(ai.describe())

    // ── 4. Run the multi-agent streaming conversation ──────────────────────────
    println("\nUser: 'Please transfer \$50 to Ahmed from my account ACC-101.'\n")
    val conversationId = ConversationId("demo-session-001")

    ai.stream(conversationId, "Please transfer \$50 to Ahmed from my account ACC-101.").collect { event ->
        when (event) {
            is AgentEvent.AgentEntered -> {
                println("[Event] Entered Agent: '${event.agentId}' (Path: ${event.agentPath.joinToString(" → ")})")
            }
            is AgentEvent.StepStarted -> {
                println("[Event] Step ${event.stepIndex} started for '${event.agentPath.last()}'")
            }
            is AgentEvent.ToolCallStarted -> {
                println("[Event] Calling Tool: '${event.toolName}' [ID: ${event.callId}]")
            }
            is AgentEvent.ToolCallExecuted -> {
                println("[Event] Tool '${event.toolName}' returned: ${event.result::class.simpleName}")
            }
            is AgentEvent.TextDelta -> {
                print(event.text)
            }
            is AgentEvent.RunCompleted -> {
                println("\n\n[Event] Run completed!")
                println("Final Answer: ${event.result.text}")
                println("Total Steps : ${event.result.steps}")
            }
            is AgentEvent.RunFailed -> {
                System.err.println("\n[Event] Run failed: ${event.error.message}")
            }
            else -> {}
        }
    }

    println("\n==================================================")
    println("              CLI Sample Finished                 ")
    println("==================================================")
}
