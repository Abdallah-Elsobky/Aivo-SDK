package com.aivo.sdk.runtime.tool

import com.aivo.sdk.core.model.ToolCall
import com.aivo.sdk.core.model.ToolFailureKind
import com.aivo.sdk.core.model.ToolResult
import com.aivo.sdk.core.port.AlwaysAllow
import com.aivo.sdk.core.port.ConfirmationHandler
import com.aivo.sdk.core.port.DefaultToolPolicy
import com.aivo.sdk.core.port.PolicyDecision
import com.aivo.sdk.core.port.Telemetry
import com.aivo.sdk.core.port.TelemetryEvent
import com.aivo.sdk.core.port.Tool
import com.aivo.sdk.core.port.ToolContext
import com.aivo.sdk.core.port.ToolPolicy
import com.aivo.sdk.core.util.Clock
import com.aivo.sdk.core.util.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonPrimitive

/**
 * Orchestrates the validation, confirmation policy, timeout, execution, and truncation of tools.
 */
public class ToolExecutor(
    private val registry: ToolRegistry,
    private val policy: ToolPolicy = DefaultToolPolicy,
    private val confirmationHandler: ConfirmationHandler = AlwaysAllow,
    private val defaultTimeoutMs: Long = 30_000L,
    private val maxToolResultChars: Int = 10_000,
    private val telemetry: Telemetry? = null,
    private val clock: Clock = SystemClock,
) {

    /**
     * Executes an explicit [tool] instance for [call] through the complete validation and execution pipeline.
     */
    public suspend fun execute(tool: Tool, call: ToolCall, context: ToolContext): ToolResult {
        val startMs = clock.nowMillis()

        // 1. Validate arguments against JSON Schema
        val validation = ToolValidator.validate(tool.spec.parameters, call.arguments)
        if (validation is ValidationResult.Invalid) {
            val failure = ToolResult.Failure(
                kind = ToolFailureKind.INVALID_ARGUMENTS,
                message = "Invalid arguments for tool '${call.name}': ${validation.errorMessage()}",
            )
            recordTelemetry(call, context, startMs, failure)
            return failure
        }

        // 2. Policy check
        when (val decision = policy.decide(tool.spec, call, context)) {
            is PolicyDecision.Deny -> {
                val failure = ToolResult.Failure(
                    kind = ToolFailureKind.DENIED,
                    message = "Execution denied by policy: ${decision.reason}",
                )
                recordTelemetry(call, context, startMs, failure)
                return failure
            }
            is PolicyDecision.RequireConfirmation -> {
                val confirmed = try {
                    confirmationHandler.confirm(call, tool.spec)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    false
                }
                if (!confirmed) {
                    val failure = ToolResult.Failure(
                        kind = ToolFailureKind.DENIED,
                        message = "Tool execution rejected by user confirmation.",
                    )
                    recordTelemetry(call, context, startMs, failure)
                    return failure
                }
            }
            is PolicyDecision.Allow -> { /* proceed */ }
        }

        // 3. Execution with timeout
        val result: ToolResult = try {
            withTimeout(defaultTimeoutMs) {
                tool.execute(call, context)
            }
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                ToolResult.Failure(
                    kind = ToolFailureKind.TIMEOUT,
                    message = "Tool execution timed out after ${defaultTimeoutMs}ms.",
                )
            } else {
                throw e
            }
        } catch (e: Throwable) {
            ToolResult.Failure(
                kind = ToolFailureKind.EXECUTION_FAILED,
                message = "Tool execution failed: ${e.message ?: "Unknown error"}",
            )
        }

        // 4. Truncate oversized results
        val finalResult = truncateIfNeeded(result)
        recordTelemetry(call, context, startMs, finalResult)
        return finalResult
    }

    /**
     * Executes a single tool call through the complete validation and execution pipeline,
     * looking up the tool in the registry by name.
     */
    public suspend fun execute(call: ToolCall, context: ToolContext): ToolResult {
        val tool = registry.get(call.name)
        if (tool == null) {
            val failure = ToolResult.Failure(
                kind = ToolFailureKind.UNKNOWN_TOOL,
                message = "Tool '${call.name}' is not registered.",
            )
            recordTelemetry(call, context, clock.nowMillis(), failure)
            return failure
        }
        return execute(tool, call, context)
    }

    /**
     * Executes multiple tool calls, optionally in parallel, returning the results in
     * the **exact same order** as the input calls.
     */
    public suspend fun executeAll(
        calls: List<ToolCall>,
        contextFactory: (ToolCall) -> ToolContext,
        parallel: Boolean = true,
        maxConcurrency: Int = 4,
    ): List<ToolResult> = coroutineScope {
        if (!parallel || calls.size <= 1) {
            calls.map { call -> execute(call, contextFactory(call)) }
        } else {
            val semaphore = Semaphore(maxConcurrency.coerceAtLeast(1))
            val deferreds = calls.map { call ->
                async {
                    semaphore.withPermit {
                        execute(call, contextFactory(call))
                    }
                }
            }
            deferreds.awaitAll()
        }
    }

    private fun truncateIfNeeded(result: ToolResult): ToolResult {
        if (result !is ToolResult.Success) return result
        val contentStr = result.content.toString()
        if (contentStr.length <= maxToolResultChars) return result

        val truncated = contentStr.take(maxToolResultChars) + "… [truncated]"
        return ToolResult.Success(JsonPrimitive(truncated))
    }

    private fun recordTelemetry(
        call: ToolCall,
        context: ToolContext,
        startMs: Long,
        result: ToolResult,
    ) {
        val durationMs = clock.nowMillis() - startMs
        telemetry?.record(
            TelemetryEvent.ToolExecuted(
                runId = context.runId,
                conversationId = context.conversationId,
                agentId = context.agentId,
                toolName = call.name,
                callId = call.id,
                durationMs = durationMs,
                success = result is ToolResult.Success,
                failureKind = (result as? ToolResult.Failure)?.kind?.name,
            )
        )
    }
}
