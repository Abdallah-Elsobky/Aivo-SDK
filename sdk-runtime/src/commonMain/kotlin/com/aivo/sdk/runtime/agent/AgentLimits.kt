package com.aivo.sdk.runtime.agent

import kotlinx.serialization.Serializable

/**
 * Execution boundaries and safety limits for an agent run.
 *
 * @param maxSteps              Maximum number of model <-> tool loop iterations.
 * @param maxDelegationDepth    Maximum depth of child agent invocations.
 * @param runTimeoutMs          Wall-clock timeout for the entire run.
 * @param maxTokens             Total token budget across all steps.
 * @param maxToolResultChars    Maximum length of a single tool result before truncation.
 */
@Serializable
public data class AgentLimits(
    val maxSteps: Int = 10,
    val maxDelegationDepth: Int = 3,
    val runTimeoutMs: Long = 60_000L,
    val maxTokens: Int? = null,
    val maxToolResultChars: Int = 10_000,
)
