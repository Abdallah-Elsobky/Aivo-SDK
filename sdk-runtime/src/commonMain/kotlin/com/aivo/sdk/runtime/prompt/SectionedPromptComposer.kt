package com.aivo.sdk.runtime.prompt

import com.aivo.sdk.core.model.PromptSection
import com.aivo.sdk.core.port.PromptComposeContext
import com.aivo.sdk.core.port.PromptComposer

/**
 * Standard implementation of [PromptComposer] that renders structured prompt sections
 * in a deterministic order with Markdown headers.
 */
public class SectionedPromptComposer(
    public val sectionOrder: List<PromptSection> = listOf(
        PromptSection.ROLE,
        PromptSection.GOAL,
        PromptSection.RESPONSIBILITIES,
        PromptSection.INSTRUCTIONS,
        PromptSection.RULES,
        PromptSection.STYLE,
        PromptSection.TEAM,
        PromptSection.OUTPUT_FORMAT,
        PromptSection.CONTEXT,
    ),
    public val strictVariables: Boolean = false,
) : PromptComposer {

    override fun compose(context: PromptComposeContext): String {
        val blocks = mutableListOf<String>()

        // If raw system prompt is present, prepend it as foundation
        context.rawSystemPrompt?.takeIf { it.isNotBlank() }?.let { raw ->
            blocks.add(raw.trim())
        }

        for (section in sectionOrder) {
            when (section) {
                PromptSection.ROLE -> {
                    context.role?.let { role ->
                        val text = buildString {
                            appendLine("### Role")
                            append("You are ${role.title}.")
                            if (role.defaultResponsibilities.isNotEmpty()) {
                                appendLine()
                                appendLine("Core responsibilities:")
                                for (resp in role.defaultResponsibilities) {
                                    appendLine("- $resp")
                                }
                            }
                        }.trim()
                        blocks.add(text)
                    }
                }
                PromptSection.GOAL -> {
                    context.goal?.takeIf { it.isNotBlank() }?.let { goal ->
                        blocks.add("### Goal\n${goal.trim()}")
                    }
                }
                PromptSection.RESPONSIBILITIES -> {
                    if (context.responsibilities.isNotEmpty()) {
                        val text = buildString {
                            appendLine("### Responsibilities")
                            for (resp in context.responsibilities) {
                                appendLine("- $resp")
                            }
                        }.trim()
                        blocks.add(text)
                    }
                }
                PromptSection.INSTRUCTIONS -> {
                    if (context.instructions.isNotEmpty()) {
                        val text = buildString {
                            appendLine("### Instructions")
                            for (inst in context.instructions) {
                                appendLine("- $inst")
                            }
                        }.trim()
                        blocks.add(text)
                    }
                }
                PromptSection.RULES -> {
                    if (context.rules.isNotEmpty()) {
                        val text = buildString {
                            appendLine("### Rules & Constraints")
                            for (rule in context.rules) {
                                appendLine("- $rule")
                            }
                        }.trim()
                        blocks.add(text)
                    }
                }
                PromptSection.STYLE -> {
                    context.style?.takeIf { it.isNotBlank() }?.let { style ->
                        blocks.add("### Style & Tone\n${style.trim()}")
                    }
                }
                PromptSection.TEAM -> {
                    if (context.teamMembers.isNotEmpty()) {
                        val text = buildString {
                            appendLine("### Team Members")
                            appendLine("You can delegate tasks to the following specialized agents:")
                            for (member in context.teamMembers) {
                                val rolePart = member.role?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                                appendLine("- **${member.id}**$rolePart: ${member.description}")
                            }
                        }.trim()
                        blocks.add(text)
                    }
                }
                PromptSection.OUTPUT_FORMAT -> {
                    context.outputFormat?.let { format ->
                        blocks.add("### Output Format\n${format.description.trim()}")
                    }
                }
                PromptSection.CONTEXT -> {
                    context.customContext?.takeIf { it.isNotBlank() }?.let { ctx ->
                        blocks.add("### Context\n${ctx.trim()}")
                    }
                }
            }
        }

        val rawMerged = blocks.joinToString("\n\n")
        return PromptRenderer.render(
            template = rawMerged,
            variables = context.variables,
            strictMode = strictVariables,
        )
    }

    public companion object {
        public val Default: SectionedPromptComposer = SectionedPromptComposer()
    }
}
