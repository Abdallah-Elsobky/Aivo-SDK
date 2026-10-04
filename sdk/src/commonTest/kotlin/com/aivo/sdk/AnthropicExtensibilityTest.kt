package com.aivo.sdk

import com.aivo.sdk.core.model.ContentPart
import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.core.model.FinishReason
import com.aivo.sdk.core.model.LlmRequest
import com.aivo.sdk.core.model.LlmResponse
import com.aivo.sdk.core.model.LlmStreamEvent
import com.aivo.sdk.core.model.Message
import com.aivo.sdk.core.model.ProviderCapabilities
import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.core.model.Usage
import com.aivo.sdk.core.port.LlmProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Phase 10.6: Demonstrates that a new 4th provider (e.g. Anthropic) can be created
 * and plugged into AivoSdk with ZERO changes to sdk-core, sdk-runtime, or existing providers.
 */
class AnthropicExtensibilityTest {

    class AnthropicProvider(override val id: ProviderId = ProviderId("anthropic")) : LlmProvider {
        override val capabilities: ProviderCapabilities = ProviderCapabilities(
            streaming = true,
            toolCalling = true,
            parallelToolCalls = true,
            reasoning = true,
            structuredOutput = false,
            statefulConversation = false,
        )

        override suspend fun generate(request: LlmRequest): LlmResponse {
            return LlmResponse(
                message = Message.Assistant(listOf(ContentPart.Text("Response from Anthropic Claude"))),
                finishReason = FinishReason.STOP,
                usage = Usage(inputTokens = 12, outputTokens = 8),
            )
        }

        override fun stream(request: LlmRequest): Flow<LlmStreamEvent> = flow {
            emit(LlmStreamEvent.TextDelta("Response from Anthropic Claude"))
            emit(
                LlmStreamEvent.Completed(
                    LlmResponse(
                        message = Message.Assistant(listOf(ContentPart.Text("Response from Anthropic Claude"))),
                        finishReason = FinishReason.STOP,
                        usage = Usage(inputTokens = 12, outputTokens = 8),
                    )
                )
            )
        }
    }

    @Test
    fun custom_anthropic_provider_works_seamlessly_in_aivo_sdk() = runTest {
        val anthropic = AnthropicProvider()

        val ai = AivoSdk {
            providers {
                register(anthropic)
            }
            defaultModel("anthropic:claude-3-5-sonnet")

            agents {
                define("claude_agent") {
                    systemPrompt = "You are Claude."
                }
            }
            entryAgent("claude_agent")
        }

        val result = ai.chat(ConversationId("anthropic-conv"), "Hello Claude!")
        assertEquals("Response from Anthropic Claude", result.text)
        assertEquals(1, result.steps)
    }
}
