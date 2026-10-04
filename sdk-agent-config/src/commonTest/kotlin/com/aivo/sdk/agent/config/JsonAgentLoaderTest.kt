package com.aivo.sdk.agent.config

import com.aivo.sdk.core.model.ProviderId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JsonAgentLoaderTest {

    @Test
    fun parse_valid_json_agent() {
        val json = """
            {
              "id": "json_agent",
              "name": "JSON Agent",
              "description": "Agent loaded from JSON",
              "model": {
                "provider": "ollama",
                "model": "gemma4:31b"
              },
              "systemPrompt": "You are a JSON configured agent.",
              "tools": ["tool_1"]
            }
        """.trimIndent()

        val agent = JsonAgentLoader.parse(json)
        assertEquals("json_agent", agent.id)
        assertEquals("JSON Agent", agent.name)
        assertNotNull(agent.model)
        assertEquals(ProviderId("ollama"), agent.model!!.provider)
        assertEquals("gemma4:31b", agent.model!!.model)
        assertEquals(listOf("tool_1"), agent.tools)
    }
}
