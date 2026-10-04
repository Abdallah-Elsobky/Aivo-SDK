package com.aivo.sdk.runtime

import com.aivo.sdk.core.error.ConfigurationException
import com.aivo.sdk.runtime.prompt.PromptRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PromptRendererTest {

    @Test
    fun render_replaces_variables() {
        val template = "Hello {{user_name}}, welcome to {{bank_name}}!"
        val variables = mapOf("user_name" to "Ahmed", "bank_name" to "AivoBank")
        val rendered = PromptRenderer.render(template, variables)
        assertEquals("Hello Ahmed, welcome to AivoBank!", rendered)
    }

    @Test
    fun render_in_strict_mode_throws_on_missing_variable() {
        val template = "Welcome {{user_name}} to {{bank_name}}!"
        val variables = mapOf("user_name" to "Ahmed")

        assertFailsWith<ConfigurationException> {
            PromptRenderer.render(template, variables, strictMode = true)
        }
    }

    @Test
    fun render_in_lenient_mode_leaves_placeholder() {
        val template = "Welcome {{user_name}} to {{bank_name}}!"
        val variables = mapOf("user_name" to "Ahmed")
        val rendered = PromptRenderer.render(template, variables, strictMode = false)
        assertEquals("Welcome Ahmed to {{bank_name}}!", rendered)
    }
}
