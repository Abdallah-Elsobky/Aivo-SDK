package com.aivo.sdk.core.model

import kotlinx.serialization.Serializable

/**
 * Standard sections in a structured system prompt, composed in priority order.
 */
public enum class PromptSection {
    ROLE,
    GOAL,
    RESPONSIBILITIES,
    INSTRUCTIONS,
    RULES,
    STYLE,
    TEAM,
    OUTPUT_FORMAT,
    CONTEXT,
}

/**
 * Reusable style presets defining tone and brevity for agent responses.
 */
public enum class StylePreset(public val directive: String) {
    CONCISE("Be direct, concise, and avoid unnecessary conversational filler or preambles."),
    TECHNICAL("Use precise technical terminology. Include clear code snippets, typing, and architectural context."),
    EMPATHETIC("Communicate with warmth, active listening, and professional empathy."),
    ACADEMIC("Maintain objective, scholarly prose with analytical rigor and explicit reasoning."),
    EXECUTIVE("Present high-level executive summaries first, followed by key metrics and bulleted action items."),
    CASUAL("Keep the tone friendly, conversational, approachable, and easy to understand.");

    override fun toString(): String = directive
}

/**
 * Reusable safety and operational rules for agent reasoning.
 */
public enum class SafetyRule(public val directive: String) {
    NEVER_HALLUCINATE_SOURCES("Never invent or guess a source, fact, metric, or URL. If information is unavailable or unverified, state so explicitly."),
    CONSTRAIN_TO_STATE("Only rely on facts provided in the conversation context or returned by tools; do not extrapolate external assumptions."),
    CONFIRM_HIGH_RISK_ACTIONS("Always request explicit user confirmation before executing destructive, financial, or irreversible actions."),
    STRICT_SCHEMA_ADHERENCE("When producing structured output or invoking tools, strictly conform to the expected format without extra text or markdown wrappers."),
    PRESERVE_PRIVACY("Never output sensitive personal identifiers, authentication tokens, or internal secrets.");

    override fun toString(): String = directive
}

/**
 * Standard output format constraints guiding model generation.
 */
@Serializable
public sealed interface OutputFormat {
    public val description: String

    @Serializable
    public object FreeText : OutputFormat {
        override val description: String = "Natural conversational text."
    }

    @Serializable
    public object Markdown : OutputFormat {
        override val description: String = "Structured Markdown with clear headers, bullet points, and code blocks where appropriate."
    }

    @Serializable
    public data class Json(public val schemaDescription: String) : OutputFormat {
        override val description: String = "Valid JSON strictly conforming to: $schemaDescription. Do not wrap with conversational commentary."
    }

    @Serializable
    public object ApprovalVerdict : OutputFormat {
        override val description: String = "The very first line must be either 'APPROVED' or 'CHANGES_NEEDED'. Following lines must contain concise, itemized bullet points."
    }

    @Serializable
    public data class Custom(override val description: String) : OutputFormat
}
