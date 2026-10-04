package com.aivo.sdk.core

import com.aivo.sdk.core.model.ModelRef
import com.aivo.sdk.core.model.ProviderId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ModelRefTest {

    @Test
    fun parse_splits_on_first_colon_only() {
        val ref = ModelRef.parse("ollama:gemma4:31b")
        assertEquals(ProviderId("ollama"), ref.provider)
        assertEquals("gemma4:31b", ref.model)
        assertEquals("ollama:gemma4:31b", ref.toString())
    }

    @Test
    fun parse_handles_slashes_in_model_name() {
        val ref = ModelRef.parse("openrouter:deepseek/deepseek-v4.1-flash")
        assertEquals(ProviderId("openrouter"), ref.provider)
        assertEquals("deepseek/deepseek-v4.1-flash", ref.model)
    }

    @Test
    fun parse_fails_on_missing_colon() {
        assertFailsWith<IllegalArgumentException> {
            ModelRef.parse("gpt-4o")
        }
    }

    @Test
    fun parse_fails_on_empty_provider() {
        assertFailsWith<IllegalArgumentException> {
            ModelRef.parse(":model-name")
        }
    }
}
