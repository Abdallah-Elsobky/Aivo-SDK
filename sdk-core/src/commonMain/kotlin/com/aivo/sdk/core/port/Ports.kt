package com.aivo.sdk.core.port

import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.core.model.LlmRequest
import com.aivo.sdk.core.model.LlmResponse
import com.aivo.sdk.core.model.LlmStreamEvent
import com.aivo.sdk.core.model.Message
import com.aivo.sdk.core.model.ProviderCapabilities
import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.core.model.ToolCall
import com.aivo.sdk.core.model.ToolResult
import com.aivo.sdk.core.model.ToolSpec
import com.aivo.sdk.core.util.SecretString
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────────────────────────────────────────────────────
// LlmProvider
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The central port of the SDK. Represents a single LLM provider.
 *
 * **Contract every implementation must honour (Liskov Substitution):**
 * - [generate] and [stream] never leak Ktor or serialization exceptions — only [com.aivo.sdk.core.error.SdkException] subclasses.
 * - [stream] ends with exactly one [LlmStreamEvent.Completed] or throws.
 * - [stream] is cold; cancelling the collector cancels the HTTP call.
 * - If a capability is absent, throw [com.aivo.sdk.core.error.UnsupportedCapabilityException] instead of silently ignoring.
 *
 * The `sdk-runtime` module depends **only** on this interface — it never imports a concrete provider.
 */
public interface LlmProvider {
    /** Stable identifier used for routing and telemetry. */
    public val id: ProviderId

    /** Declares what this provider supports. The runtime uses this to fail-fast. */
    public val capabilities: ProviderCapabilities

    /**
     * Sends a request and returns the complete response (blocking until done).
     *
     * @throws com.aivo.sdk.core.error.SdkException on any error.
     */
    public suspend fun generate(request: LlmRequest): LlmResponse

    /**
     * Sends a request and streams incremental events. The last event is always
     * [LlmStreamEvent.Completed] on success.
     *
     * The returned [Flow] is **cold** — no network call is made until collection begins.
     * Cancelling the collector cancels the HTTP body reader.
     */
    public fun stream(request: LlmRequest): Flow<LlmStreamEvent>
}

// ─────────────────────────────────────────────────────────────────────────────
// Tool
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Represents a callable function that an agent can invoke.
 *
 * The SDK supplies [spec] to the LLM as a function declaration.
 * When the LLM requests a call, the runtime invokes [execute].
 *
 * Implementations must:
 * - Never throw unchecked exceptions (catch and return [ToolResult.Failure] instead).
 * - Never rethrow [kotlinx.coroutines.CancellationException] as anything else.
 * - Never include stack traces or sensitive data in [ToolResult.Failure] messages.
 */
public interface Tool {
    /** The function declaration sent to the LLM. */
    public val spec: ToolSpec

    /**
     * Executes the tool for [call].
     *
     * @param call    The invocation requested by the model, including parsed arguments.
     * @param context Runtime context: conversation id, agent id, run id, variables, logger.
     * @return [ToolResult.Success] or [ToolResult.Failure] — never throws (except [kotlinx.coroutines.CancellationException]).
     */
    public suspend fun execute(call: ToolCall, context: ToolContext): ToolResult
}

/**
 * Runtime context passed to every [Tool.execute] invocation.
 *
 * @param conversationId  The active conversation.
 * @param agentId         The agent that requested this tool call.
 * @param runId           The current agent run. Useful for telemetry correlation.
 * @param callId          The specific tool call id (from [ToolCall.id]).
 * @param variables       Template variables from the current [RunContext].
 * @param logger          A scoped logger; messages are tagged with agent and run information.
 */
public data class ToolContext(
    public val conversationId: ConversationId,
    public val agentId: String,
    public val runId: String,
    public val callId: String,
    public val variables: Map<String, String>,
    public val logger: Logger,
)

// ─────────────────────────────────────────────────────────────────────────────
// MemoryStore
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Stores and retrieves conversation history keyed by [ConversationId].
 *
 * **Thread-safety contract:** implementations must be safe for concurrent access
 * from multiple coroutines. The runtime uses per-conversation mutexes to serialise
 * writes, but multiple conversations may be active simultaneously.
 */
public interface MemoryStore {
    /**
     * Returns all messages for [id] in chronological order.
     * Returns an empty list if the conversation has no history.
     */
    public suspend fun load(id: ConversationId): List<Message>

    /**
     * Appends [messages] to the conversation [id] atomically.
     * The runtime persists an assistant message and all its tool results together
     * in a single call to prevent orphaned tool calls on crash.
     */
    public suspend fun append(id: ConversationId, messages: List<Message>)

    /** Clears all history for [id]. */
    public suspend fun clear(id: ConversationId)
}

// ─────────────────────────────────────────────────────────────────────────────
// Logger
// ─────────────────────────────────────────────────────────────────────────────

/** Severity levels used by [Logger]. */
public enum class LogLevel { VERBOSE, DEBUG, INFO, WARN, ERROR }

/**
 * Logging port. The default implementations delegate to Logcat (Android),
 * `os_log` (iOS), and `println` (JVM).
 *
 * **Security guarantee:** the SDK never passes API keys, full prompts, or
 * response bodies to [log] unless `logPayloads = true` AND a [Redactor] is configured.
 *
 * @param message Lazy lambda — only evaluated if [level] is active, avoiding string allocation.
 */
public interface Logger {
    public fun log(
        level: LogLevel,
        tag: String,
        message: () -> String,
        throwable: Throwable? = null,
    )

    // Convenience helpers — avoids boilerplate at call sites.
    public fun verbose(tag: String, message: () -> String): Unit = log(LogLevel.VERBOSE, tag, message)
    public fun debug(tag: String, message: () -> String): Unit = log(LogLevel.DEBUG, tag, message)
    public fun info(tag: String, message: () -> String): Unit = log(LogLevel.INFO, tag, message)
    public fun warn(tag: String, message: () -> String, throwable: Throwable? = null): Unit =
        log(LogLevel.WARN, tag, message, throwable)
    public fun error(tag: String, message: () -> String, throwable: Throwable? = null): Unit =
        log(LogLevel.ERROR, tag, message, throwable)
}

/** A [Logger] that discards all messages. Useful in tests and as a default. */
public object NoOpLogger : Logger {
    override fun log(level: LogLevel, tag: String, message: () -> String, throwable: Throwable?): Unit = Unit
}

// ─────────────────────────────────────────────────────────────────────────────
// Telemetry
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Receives structured telemetry events from the SDK.
 *
 * Events carry identifiers (run, conversation, agent, step) and metrics (tokens, cost, latency)
 * but **no message content** by default.
 *
 * Design intent: a future OpenTelemetry bridge can implement this interface and translate
 * events to OTel spans without any core changes.
 */
public interface Telemetry {
    public fun record(event: TelemetryEvent)
}

/** A [Telemetry] that discards all events. Default when the user provides none. */
public object NoOpTelemetry : Telemetry {
    override fun record(event: TelemetryEvent): Unit = Unit
}

// ─────────────────────────────────────────────────────────────────────────────
// TelemetryEvent
// ─────────────────────────────────────────────────────────────────────────────

/** Base type for all structured telemetry events. */
public sealed interface TelemetryEvent {
    public val runId: String
    public val conversationId: ConversationId
    public val agentId: String

    // ── LLM call events ──────────────────────────────────────────────────────

    public data class LlmCallStarted(
        override val runId: String,
        override val conversationId: ConversationId,
        override val agentId: String,
        val providerId: ProviderId,
        val model: String,
        val stepIndex: Int,
        val startedAtMs: Long,
    ) : TelemetryEvent

    public data class LlmCallCompleted(
        override val runId: String,
        override val conversationId: ConversationId,
        override val agentId: String,
        val providerId: ProviderId,
        val model: String,
        val stepIndex: Int,
        val durationMs: Long,
        val inputTokens: Int?,
        val outputTokens: Int?,
        val reasoningTokens: Int?,
        val costUsd: Double?,
        val retryCount: Int,
    ) : TelemetryEvent

    public data class LlmCallFailed(
        override val runId: String,
        override val conversationId: ConversationId,
        override val agentId: String,
        val providerId: ProviderId,
        val model: String,
        val stepIndex: Int,
        val durationMs: Long,
        val errorType: String,
        val retryCount: Int,
    ) : TelemetryEvent

    // ── Tool events ───────────────────────────────────────────────────────────

    public data class ToolExecuted(
        override val runId: String,
        override val conversationId: ConversationId,
        override val agentId: String,
        val toolName: String,
        val callId: String,
        val durationMs: Long,
        val success: Boolean,
        val failureKind: String? = null,
    ) : TelemetryEvent

    // ── Agent run events ──────────────────────────────────────────────────────

    public data class AgentRunStarted(
        override val runId: String,
        override val conversationId: ConversationId,
        override val agentId: String,
        val startedAtMs: Long,
    ) : TelemetryEvent

    public data class AgentRunCompleted(
        override val runId: String,
        override val conversationId: ConversationId,
        override val agentId: String,
        val durationMs: Long,
        val steps: Int,
        val totalInputTokens: Int?,
        val totalOutputTokens: Int?,
        val totalCostUsd: Double?,
    ) : TelemetryEvent

    public data class AgentRunFailed(
        override val runId: String,
        override val conversationId: ConversationId,
        override val agentId: String,
        val durationMs: Long,
        val steps: Int,
        val errorType: String,
    ) : TelemetryEvent

    // ── Delegation events ─────────────────────────────────────────────────────

    public data class DelegationStarted(
        override val runId: String,
        override val conversationId: ConversationId,
        override val agentId: String,
        val fromAgentId: String,
        val toAgentId: String,
        val task: String,
    ) : TelemetryEvent

    public data class DelegationCompleted(
        override val runId: String,
        override val conversationId: ConversationId,
        override val agentId: String,
        val fromAgentId: String,
        val toAgentId: String,
        val durationMs: Long,
        val success: Boolean,
    ) : TelemetryEvent
}

// ─────────────────────────────────────────────────────────────────────────────
// CredentialsProvider
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Provides authentication credentials per request.
 *
 * Implemented as `suspend` so short-lived tokens (OAuth, JWT) can be fetched or
 * refreshed from a backend without blocking the calling thread.
 *
 * **Security:** credentials are returned as [SecretString] and must never appear
 * in logs, exceptions, or telemetry.
 */
public fun interface CredentialsProvider {
    public suspend fun credentials(): SecretString
}

/** A static [CredentialsProvider] that always returns the same [SecretString]. */
public fun staticCredentials(secret: SecretString): CredentialsProvider =
    CredentialsProvider { secret }

// ─────────────────────────────────────────────────────────────────────────────
// TokenEstimator
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Estimates the token count of a string without running a full tokenizer.
 * Used by [com.aivo.sdk.runtime.memory.context.TokenBudgetStrategy].
 */
public fun interface TokenEstimator {
    public fun estimate(text: String): Int
}

/** Default estimator: `ceil(characters / 4)`. Good enough for English; pluggable. */
public object CharCountEstimator : TokenEstimator {
    override fun estimate(text: String): Int = (text.length + 3) / 4
}

// ─────────────────────────────────────────────────────────────────────────────
// ConfirmationHandler
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A suspend callback the application provides to confirm high-risk tool calls.
 *
 * Return `true` to allow; `false` to deny.
 * The implementation should show a native confirmation dialog and suspend until
 * the user responds. If it suspends forever, the tool executor will time out
 * and deny the call.
 */
public fun interface ConfirmationHandler {
    public suspend fun confirm(call: ToolCall, spec: ToolSpec): Boolean
}

/** Always allows — safe only for low-risk tools in tests. */
public object AlwaysAllow : ConfirmationHandler {
    override suspend fun confirm(call: ToolCall, spec: ToolSpec): Boolean = true
}

/** Always denies — useful as a safe default. */
public object AlwaysDeny : ConfirmationHandler {
    override suspend fun confirm(call: ToolCall, spec: ToolSpec): Boolean = false
}

// ─────────────────────────────────────────────────────────────────────────────
// ToolPolicy
// ─────────────────────────────────────────────────────────────────────────────

/** The result of a [ToolPolicy] decision. */
public sealed interface PolicyDecision {
    public object Allow : PolicyDecision
    public data class Deny(val reason: String) : PolicyDecision
    public object RequireConfirmation : PolicyDecision
}

/**
 * Decides whether a tool call is allowed to proceed.
 *
 * Implementations can enforce role-based access, rate limits, blacklists,
 * or automatic confirmation requirements for HIGH-risk tools.
 */
public fun interface ToolPolicy {
    public fun decide(spec: ToolSpec, call: ToolCall, context: ToolContext): PolicyDecision
}

/** Default policy: require confirmation for [com.aivo.sdk.core.model.ToolRisk.HIGH] tools; allow others. */
public object DefaultToolPolicy : ToolPolicy {
    override fun decide(spec: ToolSpec, call: ToolCall, context: ToolContext): PolicyDecision =
        when {
            spec.requiresConfirmation -> PolicyDecision.RequireConfirmation
            else -> PolicyDecision.Allow
        }
}

// ─────────────────────────────────────────────────────────────────────────────
// Redactor
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Sanitises strings before they appear in logs, telemetry, or exceptions.
 *
 * The default implementation redacts `Authorization` header values and `x-goog-api-key` values.
 * Applications can provide a custom implementation for PII masking.
 */
public fun interface Redactor {
    public fun redact(input: String): String
}

/** Redacts common auth patterns and can be extended with custom patterns. */
public class DefaultRedactor(
    private val additionalPatterns: List<Regex> = emptyList(),
) : Redactor {
    private val builtInPatterns = listOf(
        Regex("""(Authorization:\s*Bearer\s+)\S+""", RegexOption.IGNORE_CASE),
        Regex("""(x-goog-api-key:\s*)\S+""", RegexOption.IGNORE_CASE),
        Regex("""("api_key"\s*:\s*)"[^"]*""""),
        Regex("""("Authorization"\s*:\s*)"[^"]*""""),
    )

    override fun redact(input: String): String {
        var result = input
        for (pattern in builtInPatterns + additionalPatterns) {
            result = pattern.replace(result) { match ->
                val groups = match.groupValues
                if (groups.size > 1) groups[1] + "***" else "***"
            }
        }
        return result
    }
}
