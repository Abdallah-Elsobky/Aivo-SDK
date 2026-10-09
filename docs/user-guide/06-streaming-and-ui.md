# Real-Time Streaming & UI

Aivo SDK provides end-to-end token and event streaming powered by Kotlin Coroutines `Flow`.

---

## 1. Consuming `Flow<AgentEvent>`

Every streaming run emits typed events with the exact `agentPath` so you always know which agent in a team is active.

```kotlin
import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.runtime.agent.AgentEvent

aivo.stream(ConversationId("stream-1"), "Write a short poem about Kotlin.")
    .collect { event ->
        when (event) {
            is AgentEvent.RunStarted         -> println("▶ Run started")
            is AgentEvent.AgentEntered       -> println("→ Active Agent: ${event.agentId}")
            is AgentEvent.StepStarted        -> println("\n--- Step ${event.stepIndex} ---")
            is AgentEvent.TextDelta          -> print(event.text)                 // Live assistant tokens
            is AgentEvent.ReasoningDelta     -> print("[thinking: ${event.text}]") // Chain-of-thought
            is AgentEvent.ToolCallStarted    -> println("\n⚙ Tool Invoking: ${event.toolName}")
            is AgentEvent.ToolCallExecuted   -> println("✓ Tool Execution Complete")
            is AgentEvent.StepCompleted      -> {}
            is AgentEvent.RunCompleted       -> println("\n\n✅ Finished in ${event.result.steps} steps")
            is AgentEvent.RunFailed          -> println("\n❌ Error: ${event.error.message}")
        }
    }
```

---

## 2. Jetpack Compose Integration (Android)

### ViewModel

```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aivo.sdk.AivoSdk
import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.runtime.agent.AgentEvent
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(private val aivo: AivoSdk) : ViewModel() {

    private val _streamedText = MutableStateFlow("")
    val streamedText: StateFlow<String> = _streamedText.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    fun sendMessage(prompt: String, conversationId: ConversationId) {
        viewModelScope.launch {
            _streamedText.value = ""
            _isThinking.value = true

            aivo.stream(conversationId, prompt).collect { event ->
                when (event) {
                    is AgentEvent.TextDelta -> {
                        _isThinking.value = false
                        _streamedText.update { it + event.text }
                    }
                    is AgentEvent.ReasoningDelta -> {
                        // Display chain-of-thought accordion if desired
                    }
                    is AgentEvent.RunCompleted -> {
                        _isThinking.value = false
                    }
                    is AgentEvent.RunFailed -> {
                        _isThinking.value = false
                        _streamedText.value = "Error: ${event.error.message}"
                    }
                    else -> Unit
                }
            }
        }
    }
}
```

### Composable Screen

```kotlin
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val streamedText by viewModel.streamedText.collectAsState()
    val isThinking by viewModel.isThinking.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        if (isThinking) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Text(
            text = streamedText.ifEmpty { "Ask a question..." },
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { viewModel.sendMessage("Explain coroutines", ConversationId("conv-1")) }) {
            Text("Send")
        }
    }
}
```

---

## 3. iOS Swift Integration

The SDK ships an iOS interop shim (`FlowAdapter` + `CancellableJob`) to bridge Kotlin `Flow` directly to Swift:

```swift
import Foundation
import Shared

// 1. Initialize SDK
let aivo = AivoSdkKt.create(
    provider: AivoProvider.ollama,
    model: OllamaModel.gptOss120b.modelId,
    apiKey: ProcessInfo.processInfo.environment["OLLAMA_API_KEY"] ?? ""
)

// 2. Stream into Swift UI / ViewModel
let job = aivo.stream(
    conversationId: ConversationId(value: "ios-session-1"),
    message: "Hello from iOS!"
).subscribe(
    onEach: { event in
        if let textDelta = event as? AgentEvent.TextDelta {
            DispatchQueue.main.async {
                self.output += textDelta.text
            }
        }
    },
    onError: { error in
        print("Stream error: \(error.localizedDescription)")
    },
    onComplete: {
        print("Stream finished successfully")
    }
)

// 3. Cancel when the view dismisses
// job.cancel()
```
