package com.aivo.sdk.agent.config

import com.aivo.sdk.core.error.ConfigurationException
import com.aivo.sdk.core.model.ProviderId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MarkdownAgentLoaderTest {

    @Test
    fun parse_valid_markdown_agent() {
        val markdown = """
            ---
            id: support_agent
            name: Customer Support
            description: Answers banking questions
            model: ollama:gemma4:31b
            tools: [get_balance, transfer_money]
            delegates: [billing_specialist]
            instructions: [Be polite, Be concise]
            maxSteps: 5
            temperature: 0.2
            ---
            You are a helpful customer support agent for Aivo Bank.
        """.trimIndent()

        val agent = MarkdownAgentLoader.parse(markdown)
        assertEquals("support_agent", agent.id)
        assertEquals("Customer Support", agent.name)
        assertEquals("Answers banking questions", agent.description)
        assertNotNull(agent.model)
        assertEquals(ProviderId("ollama"), agent.model!!.provider)
        assertEquals("gemma4:31b", agent.model!!.model)
        assertEquals(listOf("get_balance", "transfer_money"), agent.tools)
        assertEquals(listOf("billing_specialist"), agent.delegates)
        assertEquals(listOf("Be polite", "Be concise"), agent.instructions)
        assertEquals(5, agent.limits.maxSteps)
        assertEquals(0.2, agent.options.temperature)
        assertTrue(agent.systemPrompt.contains("You are a helpful customer support agent"))
        assertTrue(agent.systemPrompt.contains("Instructions:"))
    }

    @Test
    fun parse_missing_frontmatter_delimiters_throws_configuration_exception() {
        val markdown = "Just body without frontmatter"
        assertFailsWith<ConfigurationException> {
            MarkdownAgentLoader.parse(markdown)
        }
    }

    @Test
    fun parse_aggregates_validation_errors() {
        val markdown = """
            ---
            name: Missing ID and bad model
            model: invalid_model_no_colon
            ---
            System prompt
        """.trimIndent()

        val ex = assertFailsWith<ConfigurationException> {
            MarkdownAgentLoader.parse(markdown)
        }
        assertTrue(ex.problems.any { it.contains("Missing or blank 'id'") })
        assertTrue(ex.problems.any { it.contains("Invalid 'model' format") })
    }
}
