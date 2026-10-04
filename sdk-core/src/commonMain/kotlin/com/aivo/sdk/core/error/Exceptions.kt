package com.aivo.sdk.core.error

import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.core.model.ToolFailureKind

// ─────────────────────────────────────────────────────────────────────────────
// Root
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Root of the Aivo SDK exception hierarchy.
 *
 * All public suspend functions throw subclasses of [SdkException] rather than
 * raw IO or serialization exceptions. This makes `catch` blocks exhaustive.
 *
 * **Security note:** no exception ever contains API keys, raw prompts, or
 * unredacted HTTP bodies.
 */
public sealed class SdkException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

// ─────────────────────────────────────────────────────────────────────────────
// Configuration
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Thrown at [com.aivo.sdk.AivoSdk] build time when the configuration is invalid.
 *
 * **All** problems are collected before throwing; developers see the full list at once.
 *
 * @param problems Human-readable descriptions of every configuration error found.
 */
public class ConfigurationException(
    public val problems: List<String>,
) : SdkException("SDK configuration has ${problems.size} error(s):\n${problems.joinToString("\n") { "  • $it" }}")

// ─────────────────────────────────────────────────────────────────────────────
// Capability
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Thrown fail-fast when an agent requests a capability the chosen provider does not support
 * (e.g., tool calling on a provider with `toolCalling = false`).
 *
 * Never thrown mid-stream — always caught at run start so the user gets a clear error
 * rather than a mysterious failure after waiting.
 */
public class UnsupportedCapabilityException(
    public val capability: String,
    public val providerId: ProviderId,
) : SdkException("Provider '${providerId.value}' does not support capability: $capability")

// ─────────────────────────────────────────────────────────────────────────────
// Provider errors
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Base class for all errors originating from an LLM provider (HTTP or protocol level).
 *
 * @param providerId  Which provider caused the error.
 * @param httpStatus  The HTTP status code, if applicable.
 * @param retryable   Whether [com.aivo.sdk.middleware.RetryingLlmProvider] should retry.
 */
public sealed class ProviderException(
    public val providerId: ProviderId,
    public val httpStatus: Int?,
    public val retryable: Boolean,
    message: String,
    cause: Throwable? = null,
) : SdkException(message, cause)

/** HTTP 401 or 403 — credentials are missing, expired, or invalid. Never retried. */
public class AuthenticationException(
    providerId: ProviderId,
    httpStatus: Int,
    message: String,
) : ProviderException(providerId, httpStatus, retryable = false, message)

/** HTTP 429 — the provider's rate limit was exceeded. Retried after [retryAfterMs] if provided. */
public class RateLimitException(
    providerId: ProviderId,
    public val retryAfterMs: Long?,
    message: String,
) : ProviderException(providerId, httpStatus = 429, retryable = true, message)

/** HTTP 400 or 422 — the request was malformed. Never retried. */
public class InvalidRequestException(
    providerId: ProviderId,
    httpStatus: Int,
    message: String,
) : ProviderException(providerId, httpStatus, retryable = false, message)

/** HTTP 404 — the requested model or endpoint does not exist. Never retried. */
public class ModelNotFoundException(
    providerId: ProviderId,
    public val modelName: String,
    message: String,
) : ProviderException(providerId, httpStatus = 404, retryable = false, message)

/** The provider's content safety system blocked the response. Never retried. */
public class ContentFilteredException(
    providerId: ProviderId,
    message: String,
) : ProviderException(providerId, httpStatus = null, retryable = false, message)

/** HTTP 5xx — a transient server-side error. Retried. */
public class ServerException(
    providerId: ProviderId,
    httpStatus: Int,
    message: String,
) : ProviderException(providerId, httpStatus, retryable = true, message)

/** A network IO failure (no connection, DNS, socket timeout). Retried. */
public class NetworkException(
    providerId: ProviderId,
    message: String,
    cause: Throwable? = null,
) : ProviderException(providerId, httpStatus = null, retryable = true, message, cause)

/** A connect, request, or stream-idle timeout. Retried. */
public class TimeoutException(
    providerId: ProviderId,
    public val timeoutKind: TimeoutKind,
    message: String,
    cause: Throwable? = null,
) : ProviderException(providerId, httpStatus = null, retryable = true, message, cause)

/** Which kind of timeout fired. */
public enum class TimeoutKind { CONNECT, REQUEST, STREAM_IDLE }

/** The provider returned a response body that could not be decoded. Not retried. */
public class ProtocolException(
    providerId: ProviderId,
    message: String,
    cause: Throwable? = null,
) : ProviderException(providerId, httpStatus = null, retryable = false, message, cause)

// ─────────────────────────────────────────────────────────────────────────────
// Tool errors — thrown by the tool system itself (not by individual tools)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Thrown only if the tool execution infrastructure itself fails catastrophically.
 * Normal tool failures surface as [com.aivo.sdk.core.model.ToolResult.Failure], never as exceptions.
 */
public class ToolException(
    public val kind: ToolFailureKind,
    message: String,
    cause: Throwable? = null,
) : SdkException(message, cause)

// ─────────────────────────────────────────────────────────────────────────────
// Agent runtime errors
// ─────────────────────────────────────────────────────────────────────────────

/** Errors produced by the agent loop. */
public sealed class AgentException(
    message: String,
    cause: Throwable? = null,
) : SdkException(message, cause)

/** Generic failure during agent execution. */
public class AgentRunException(
    message: String,
    cause: Throwable? = null,
) : AgentException(message, cause)

/** The agent loop reached [maxSteps] without producing a final answer. */
public class MaxStepsExceededException(
    public val agentId: String,
    public val maxSteps: Int,
) : AgentException("Agent '$agentId' exceeded max steps ($maxSteps)")

/** The delegation chain exceeded [maxDepth]. */
public class MaxDelegationDepthExceededException(
    public val depth: Int,
) : AgentException("Delegation depth exceeded $depth levels")

/** A delegation cycle was detected in the agent path. */
public class DelegationCycleException(
    public val agentPath: List<String>,
) : AgentException("Delegation cycle detected: ${agentPath.joinToString(" → ")}")

/** The requested agent id is not registered. */
public class UnknownAgentException(
    public val agentId: String,
) : AgentException("Agent '$agentId' is not registered")

/** The run exceeded the configured wall-clock timeout. */
public class RunTimeoutException(
    public val agentId: String,
    public val timeoutMs: Long,
) : AgentException("Agent '$agentId' run timed out after ${timeoutMs}ms")

/** The run exceeded the configured total token budget. */
public class TokenBudgetExceededException(
    public val maxTokens: Int,
) : AgentException("Run exceeded total token budget ($maxTokens tokens)")

/** A concurrent run was rejected for the conversation under REJECT policy. */
public class ConcurrentRunException(
    public val conversationId: com.aivo.sdk.core.model.ConversationId,
) : AgentException("Concurrent run rejected for conversation '${conversationId.value}'")

// ─────────────────────────────────────────────────────────────────────────────
// Memory
// ─────────────────────────────────────────────────────────────────────────────

/** Wraps any storage error from a [com.aivo.sdk.core.port.MemoryStore] implementation. */
public class MemoryException(
    message: String,
    cause: Throwable? = null,
) : SdkException(message, cause)
