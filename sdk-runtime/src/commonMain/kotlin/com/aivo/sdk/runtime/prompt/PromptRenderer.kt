package com.aivo.sdk.runtime.prompt

import com.aivo.sdk.core.error.ConfigurationException

/**
 * Renders prompt templates containing `{{variable}}` placeholders.
 *
 * Does not execute arbitrary code or complex expressions (security by design).
 */
public object PromptRenderer {

    private val PLACEHOLDER_REGEX = Regex("""\{\{\s*([a-zA-Z0-9_.-]+)\s*\}\}""")

    /**
     * Replaces `{{variable}}` patterns with corresponding values from [variables].
     *
     * @param template   The prompt text template.
     * @param variables  Key-value substitution map.
     * @param strictMode When `true`, missing variables cause a [ConfigurationException].
     */
    public fun render(
        template: String,
        variables: Map<String, String> = emptyMap(),
        strictMode: Boolean = true,
    ): String {
        val missingKeys = mutableListOf<String>()

        val rendered = PLACEHOLDER_REGEX.replace(template) { matchResult ->
            val key = matchResult.groupValues[1]
            val value = variables[key]
            if (value != null) {
                value
            } else {
                if (strictMode) {
                    missingKeys.add(key)
                }
                matchResult.value
            }
        }

        if (strictMode && missingKeys.isNotEmpty()) {
            throw ConfigurationException(
                missingKeys.map { "Undefined template variable: '{{$it}}'" }
            )
        }

        return rendered
    }
}
