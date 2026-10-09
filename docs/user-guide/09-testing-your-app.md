# Testing Your App

Aivo SDK ships a dedicated `sdk-testing` artifact containing test doubles, scripted responders, and telemetry recorders. You can unit-test your entire agent application offline without real network calls or paid API keys.

---

## 1. Add Testing Dependency

In `build.gradle.kts`:

```kotlin
dependencies {
    testImplementation("io.github.abdallah-elsobky:aivo-sdk-testing:1.0.1")
}
```

---

## 2. Testing with `FakeLlmProvider`

### Scripting Text Responses

```kotlin
import com.aivo.sdk.AivoSdk
import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.core.model.Message
import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.testing.FakeLlmProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatViewModelTest {

    @Test
    fun `agent returns scripted greeting`() = runTest {
        val fakeProvider = FakeLlmProvider.scripted(
            id       = ProviderId("fake"),
            response = Message.assistant("Hello! How can I help you today?")
        )

        val aivo = AivoSdk {
            providers { register(fakeProvider) }
            defaultModel("fake:model")
            agents {
                define("bot") { systemPrompt = "You are a helpful bot." }
            }
            entryAgent("bot")
        }

        val result = aivo.chat(ConversationId("test-1"), "Hi!")
        assertEquals("Hello! How can I help you today?", result.text)
    }
}
```

---

## 3. Testing Autonomous Tool Execution

Verify that your agent loop correctly executes tools and feeds results back to the model:

```kotlin
@Test
fun `agent executes tool and returns final synthesized response`() = runTest {
    val fakeProvider = FakeLlmProvider(
        responses = listOf(
            // Turn 1: Model decides to call get_weather tool
            FakeResponse.ToolCalls(
                listOf(ToolCall(id = "c1", name = "get_weather", arguments = buildJsonObject { put("city", "Cairo") }))
            ),
            // Turn 2: Model synthesizes answer after receiving tool result
            FakeResponse.Text("The weather in Cairo is 25°C and sunny.")
        )
    )

    val aivo = AivoSdk {
        providers { register(fakeProvider) }
        defaultModel("fake:test-model")
        tools { +getWeatherTool }
        agents {
            define("assistant") { tools("get_weather") }
        }
        entryAgent("assistant")
    }

    val result = aivo.chat(ConversationId("test-conv"), "What's the weather in Cairo?")
    assertEquals("The weather in Cairo is 25°C and sunny.", result.text)
    assertEquals(1, result.toolCalls.size)
}
```

---

## 4. Asserting on Telemetry & Tokens

Use `InMemoryTelemetry` to verify token usage and request latency in tests:

```kotlin
import com.aivo.sdk.testing.InMemoryTelemetry

@Test
fun `telemetry records request metrics`() = runTest {
    val telemetry = InMemoryTelemetry()

    val aivo = AivoSdk {
        // ... (use FakeLlmProvider)
        observability {
            this.telemetry = telemetry
        }
    }

    aivo.chat(ConversationId("test-2"), "Ping")

    assertEquals(1, telemetry.requestEndEvents.size)
    assertEquals("fake", telemetry.requestEndEvents.first().providerId.value)
}
```
