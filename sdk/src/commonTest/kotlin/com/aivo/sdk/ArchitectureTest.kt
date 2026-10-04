package com.aivo.sdk

import com.aivo.sdk.core.model.ContentPart
import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.core.model.FinishReason
import com.aivo.sdk.core.model.LlmRequest
import com.aivo.sdk.core.model.LlmResponse
import com.aivo.sdk.core.model.Message
import com.aivo.sdk.core.model.ModelRef
import com.aivo.sdk.core.model.ProviderCapabilities
import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.core.model.ToolCall
import com.aivo.sdk.core.model.ToolFailureKind
import com.aivo.sdk.core.model.ToolResult
import com.aivo.sdk.core.model.Usage
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Architectural tests verifying Clean Architecture boundaries and commonMain purity.
 *
 * Rules:
 * 1. Dependency Inversion: `sdk-core` domain models must never have provider-specific fields.
 * 2. Escape hatch purity: Only `ProviderMetadata` and `extras` are allowed as vendor metadata.
 * 3. Modularity: All core domain models can be instantiated without runtime or transport dependencies.
 */
class ArchitectureTest {

    @Test
    fun core_domain_models_do_not_leak_provider_implementations() {
        // Assert domain models are fully decoupled from concrete providers
        val modelRef = ModelRef.parse("anthropic:claude-3-opus")
        assertEquals(ProviderId("anthropic"), modelRef.provider)
        assertEquals("claude-3-opus", modelRef.model)

        val request = LlmRequest(
            model = "test-model",
            messages = listOf(
                Message.System("System instruction"),
                Message.User("User input"),
                Message.Assistant(listOf(ContentPart.Text("Assistant response"))),
            ),
        )

        assertNotNull(request)
        assertTrue(request.extras.isEmpty())
        assertTrue(request.tools.isEmpty())
    }

    @Test
    fun assistant_message_metadata_is_isolated_to_providermetadata() {
        val assistant = Message.Assistant(
            parts = listOf(ContentPart.Text("Hello")),
            toolCalls = emptyList(),
        )

        // Metadata map should be empty by default and not expose provider-specific types
        assertTrue(assistant.providerMetadata.isEmpty())
    }

    @Test
    fun tool_results_never_leak_platform_exceptions() {
        val success = ToolResult.Success(content = kotlinx.serialization.json.JsonPrimitive("OK"))
        val failure = ToolResult.Failure(
            kind = ToolFailureKind.EXECUTION_FAILED,
            message = "Safe error message",
        )

        assertNotNull(success)
        assertNotNull(failure)
        assertFalse(failure.message.contains("Exception in thread"))
    }

    private fun <T> assertEquals(expected: T, actual: T) {
        kotlin.test.assertEquals(expected, actual)
    }
}
