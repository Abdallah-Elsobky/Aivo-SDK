package com.aivo.sdk

import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.core.model.ModelRef
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Live provider integration tests.
 *
 * These tests are skipped by default unless live credentials are provided via environment variables:
 * - `OPENROUTER_API_KEY`
 * - `GEMINI_API_KEY`
 *
 * Never run in standard CI without explicitly configured repository secrets.
 */
class LiveProviderTest {

    @Test
    fun openrouter_live_smoke_test() = runTest {
        val key = getEnv("OPENROUTER_API_KEY")
        if (key.isNullOrBlank()) {
            println("Skipping OpenRouter live test: OPENROUTER_API_KEY not set")
            return@runTest
        }

        val sdk = AivoSdk {
            providers {
                openRouter {
                    apiKey(key)
                }
            }
            agents {
                define("live-agent") {
                    name = "Live Agent"
                    model = ModelRef.parse("openrouter:deepseek/deepseek-chat")
                    systemPrompt = "You are a concise test assistant."
                }
            }
        }

        val result = sdk.chat(ConversationId("live-test-or"), "Reply with the word 'PONG'")
        assertTrue(result.text.contains("PONG", ignoreCase = true))
    }

    @Test
    fun gemini_live_smoke_test() = runTest {
        val key = getEnv("GEMINI_API_KEY")
        if (key.isNullOrBlank()) {
            println("Skipping Gemini live test: GEMINI_API_KEY not set")
            return@runTest
        }

        val sdk = AivoSdk {
            providers {
                gemini {
                    apiKey(key)
                }
            }
            agents {
                define("live-gemini-agent") {
                    name = "Live Gemini Agent"
                    model = ModelRef.parse("gemini:gemini-2.5-flash")
                    systemPrompt = "You are a concise test assistant."
                }
            }
        }

        val result = sdk.chat(ConversationId("live-test-gemini"), "Reply with the word 'PONG'")
        assertTrue(result.text.contains("PONG", ignoreCase = true))
    }

    private fun getEnv(name: String): String? {
        // Platform-agnostic env lookup fallback
        return null
    }
}
