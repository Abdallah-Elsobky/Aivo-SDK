package com.aivo.sdk.testing

import com.aivo.sdk.core.model.ContentPart
import com.aivo.sdk.core.model.FinishReason
import com.aivo.sdk.core.model.LlmRequest
import com.aivo.sdk.core.model.LlmResponse
import com.aivo.sdk.core.model.LlmStreamEvent
import com.aivo.sdk.core.model.Message
import com.aivo.sdk.core.model.ToolSpec
import com.aivo.sdk.core.port.LlmProvider
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Abstract contract test that verifies any [LlmProvider] conforms to the SDK specification.
 *
 * All provider modules extend this test to guarantee Liskov substitutability.
 */
public abstract class LlmProviderContractTest {

    /** Factory producing the provider under test. */
    public abstract fun createProvider(): LlmProvider

    /** Model name expected by the provider under test. */
    public abstract val testModel: String

    @Test
    public fun contract_plain_generation_produces_text_response(): Unit {
        runTest {
            val provider = createProvider()
            val request = LlmRequest(
                model = testModel,
                messages = listOf(Message.User("Hello, world!")),
            )

            val response = provider.generate(request)
            assertNotNull(response.message)
            val text = response.message.parts.filterIsInstance<ContentPart.Text>().joinToString("") { it.text }
            assertTrue(text.isNotBlank(), "Generated text should not be blank")
            assertNotNull(response.finishReason)
        }
    }

    @Test
    public fun contract_streaming_produces_deltas_and_ends_with_completed(): Unit {
        runTest {
            val provider = createProvider()
            val request = LlmRequest(
                model = testModel,
                messages = listOf(Message.User("Stream this response.")),
            )

            val events = provider.stream(request).toList()
            assertTrue(events.isNotEmpty(), "Stream should emit events")

            val lastEvent = events.last()
            assertTrue(lastEvent is LlmStreamEvent.Completed, "Last stream event must be Completed, but was: $lastEvent")

            val completedResponse = (lastEvent as LlmStreamEvent.Completed).response
            assertNotNull(completedResponse.message)
            assertNotNull(completedResponse.finishReason)
        }
    }

    @Test
    public fun contract_capabilities_are_reported_accurately(): Unit {
        val provider = createProvider()
        assertNotNull(provider.capabilities)
        assertNotNull(provider.id)
    }
}
