# Aivo SDK KMP — iOS Swift Interoperability Guide

This guide explains how to package your KMP module and consume the **Aivo SDK** from native iOS projects using **Swift** and **SwiftUI**.

---

## 1. Exporting Aivo SDK in `shared/build.gradle.kts`

To ensure Swift can see Aivo SDK types (such as `AivoProvider`, `GeminiModel`, `AgentEvent`, and `ConversationId`), declare `api` in `commonMain` and `export` in your framework binary config:

```kotlin
kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SharedAiKit"
            isStatic = true
            export("io.github.abdallah-elsobky:aivo-sdk:1.0.1")
        }
    }

    sourceSets {
        commonMain.dependencies {
            api("io.github.abdallah-elsobky:aivo-sdk:1.0.1")
        }
    }
}
```

---

## 2. Consuming Streaming in Swift (`FlowAdapter`)

Kotlin `Flow` cannot be directly iterated in Swift without an adapter. Aivo SDK ships with `FlowAdapter` which maps seamlessly to Swift closures:

```swift
import SharedAiKit

class AiChatManager {
    private let aivo: AivoSdk
    private var activeJob: CancellableJob? = nil

    init(apiKey: String) {
        self.aivo = AivoSdkKt.create(
            provider: AivoProvider.gemini,
            model: GeminiModel.gemini38Flash.modelId,
            apiKey: apiKey
        )
    }

    func stream(prompt: String, onToken: @escaping (String) -> Void, onDone: @escaping () -> Void) {
        // Cancel existing stream if running
        activeJob?.cancel()

        activeJob = aivo.stream(
            conversationId: ConversationId(value: UUID().uuidString),
            message: prompt
        ).subscribe(
            onEach: { event in
                if let textDelta = event as? AgentEvent.TextDelta {
                    onToken(textDelta.text)
                }
            },
            onError: { error in
                print("Stream encountered error: \(error.localizedDescription)")
                onDone()
            },
            onComplete: {
                onDone()
            }
        )
    }

    func stop() {
        activeJob?.cancel()
        activeJob = nil
    }
}
```

---

## 3. Handling SwiftUI View Lifecycle
Always cancel `activeJob` when the view disappears:

```swift
.onDisappear {
    viewModel.cancel()
}
```
