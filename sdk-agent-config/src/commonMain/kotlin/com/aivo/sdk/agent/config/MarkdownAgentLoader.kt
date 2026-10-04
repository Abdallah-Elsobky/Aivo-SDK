package com.aivo.sdk.agent.config

import com.aivo.sdk.core.error.ConfigurationException
import com.aivo.sdk.core.model.GenerationOptions
import com.aivo.sdk.core.model.ModelRef
import com.aivo.sdk.runtime.agent.AgentDefinition
import com.aivo.sdk.runtime.agent.AgentLimits

/**
 * Parses Markdown files with YAML frontmatter into [AgentDefinition] instances.
 *
 * Example:
 * ```markdown
 * ---
 * id: supervisor
 * name: Customer Supervisor
 * description: Coordinates customer service requests.
 * model: ollama:gemma4:31b
 * tools: [search_kb]
 * subAgents: [billing, tech_support]
 * maxSteps: 8
 * temperature: 0.3
 * ---
 * You are the primary supervisor for customer support.
 * Direct questions to the appropriate specialist agents.
 * ```
 */
public object MarkdownAgentLoader {

    public fun parse(content: String): AgentDefinition {
        val lines = content.lines()
        val frontmatterIndices = mutableListOf<Int>()

        for (i in lines.indices) {
            if (lines[i].trim() == "---") {
                frontmatterIndices.add(i)
                if (frontmatterIndices.size == 2) break
            }
        }

        if (frontmatterIndices.size < 2) {
            throw ConfigurationException(
                listOf("Markdown agent definition must start with '---' YAML frontmatter and close with '---'")
            )
        }

        val frontmatterLines = lines.subList(frontmatterIndices[0] + 1, frontmatterIndices[1])
        val bodyLines = lines.subList(frontmatterIndices[1] + 1, lines.size)
        val systemPrompt = bodyLines.joinToString("\n").trim()

        val kv = parseSimpleYaml(frontmatterLines)
        val errors = mutableListOf<String>()

        val id = kv["id"]
        if (id.isNullOrBlank()) {
            errors.add("Missing or blank 'id' in agent frontmatter")
        }

        val name = kv["name"] ?: (id ?: "")
        val description = kv["description"]

        val modelString = kv["model"]
        val model = if (modelString != null) {
            try {
                ModelRef.parse(modelString)
            } catch (e: Exception) {
                errors.add("Invalid 'model' format '$modelString' in frontmatter: ${e.message}")
                null
            }
        } else {
            null
        }

        val tools = parseList(kv["tools"])
        val delegates = parseList(kv["delegates"] ?: kv["subAgents"])
        val instructions = parseList(kv["instructions"])

        val maxSteps = kv["maxSteps"]?.toIntOrNull() ?: 10
        val maxDepth = kv["maxDelegationDepth"]?.toIntOrNull() ?: 3
        val timeoutMs = kv["timeoutMs"]?.toLongOrNull() ?: 60_000L
        val maxTokens = kv["maxTokens"]?.toIntOrNull()

        val temperature = kv["temperature"]?.toDoubleOrNull()
        val topP = kv["topP"]?.toDoubleOrNull()

        if (errors.isNotEmpty()) {
            throw ConfigurationException(errors)
        }

        val fullSystemPrompt = if (instructions.isNotEmpty()) {
            buildString {
                if (systemPrompt.isNotEmpty()) {
                    append(systemPrompt)
                    append("\n\n")
                }
                append("Instructions:\n")
                instructions.forEach { append("- ").append(it).append("\n") }
            }.trim()
        } else {
            systemPrompt
        }

        return AgentDefinition(
            id = id!!,
            name = name,
            description = description,
            model = model,
            systemPrompt = fullSystemPrompt,
            instructions = instructions,
            tools = tools,
            subAgents = delegates,
            limits = AgentLimits(
                maxSteps = maxSteps,
                maxDelegationDepth = maxDepth,
                runTimeoutMs = timeoutMs,
                maxTokens = maxTokens,
            ),
            options = GenerationOptions(
                temperature = temperature,
                topP = topP,
            ),
        )
    }

    private fun parseSimpleYaml(lines: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val colonIdx = trimmed.indexOf(':')
            if (colonIdx > 0) {
                val key = trimmed.substring(0, colonIdx).trim()
                var value = trimmed.substring(colonIdx + 1).trim()
                if ((value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'"))
                ) {
                    value = value.substring(1, value.length - 1)
                }
                result[key] = value
            }
        }
        return result
    }

    private fun parseList(value: String?): List<String> {
        if (value == null) return emptyList()
        val trimmed = value.trim()
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            val inner = trimmed.substring(1, trimmed.length - 1).trim()
            if (inner.isEmpty()) return emptyList()
            return inner.split(",").map { item ->
                item.trim().removeSurrounding("\"").removeSurrounding("'")
            }.filter { it.isNotEmpty() }
        }
        return listOf(trimmed)
    }
}
