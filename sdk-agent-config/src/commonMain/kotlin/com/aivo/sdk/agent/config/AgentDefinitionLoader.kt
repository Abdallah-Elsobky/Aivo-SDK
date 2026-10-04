package com.aivo.sdk.agent.config

import com.aivo.sdk.core.error.ConfigurationException
import com.aivo.sdk.runtime.agent.AgentDefinition

/**
 * Unified loader for agent definitions from files or strings.
 */
public class AgentDefinitionLoader(
    private val resourceReader: ResourceReader = defaultResourceReader(),
) {

    /**
     * Loads an [AgentDefinition] from [resourcePath].
     *
     * Detects format by extension (`.md`, `.markdown` -> [MarkdownAgentLoader], `.json` -> [JsonAgentLoader]).
     */
    public suspend fun loadFromPath(resourcePath: String): AgentDefinition {
        val text = resourceReader.readText(resourcePath)
        return loadFromString(text, resourcePath)
    }

    /**
     * Parses an [AgentDefinition] from [content], using [hintName] for format detection if available.
     */
    public fun loadFromString(content: String, hintName: String = ""): AgentDefinition {
        val lower = hintName.lowercase()
        return when {
            lower.endsWith(".json") || content.trimStart().startsWith("{") -> {
                JsonAgentLoader.parse(content)
            }
            lower.endsWith(".md") || lower.endsWith(".markdown") || content.trimStart().startsWith("---") -> {
                MarkdownAgentLoader.parse(content)
            }
            else -> {
                throw ConfigurationException(
                    listOf("Unable to determine agent format for '$hintName'. Must be Markdown (.md) with YAML frontmatter or JSON (.json).")
                )
            }
        }
    }
}
