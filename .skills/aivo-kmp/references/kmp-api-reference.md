# Aivo SDK KMP — Multiplatform API Reference

Detailed reference for multiplatform targets, threading rules, and memory behavior in KMP.

---

## 1. Supported Targets & Platform Matrix

| Target | Kotlin Target DSL | Min Platform Version | Engine / Transport |
|---|---|---|---|
| Android | `androidTarget()` | Android API 24+ | Ktor OkHttp / Android Engine |
| iOS 64-bit | `iosArm64()` | iOS 14.0+ | Ktor Darwin Engine |
| iOS Simulator | `iosSimulatorArm64()`, `iosX64()` | iOS 14.0+ | Ktor Darwin Engine |
| JVM / Desktop | `jvm()` | Java 11+ | Ktor CIO / Java Engine |

---

## 2. Kotlin Coroutines & Threading Rules

- **Pure Coroutines**: All network I/O and streaming SSE decoders execute on non-blocking coroutines without thread locks.
- **New Memory Model**: Fully compatible with Kotlin's new memory model — no manual freezing or worker detachment required.
- **Main Thread Safe**: Calling `aivo.chat` or collecting `aivo.stream` is safe to initiate from any dispatcher (`Dispatchers.Main`, `Dispatchers.Default`, or `Dispatchers.IO`).

---

## 3. Streaming Interop Types (`com.aivo.sdk.ios.*`)

For Swift interoperability, the SDK exposes wrapper primitives:

```kotlin
// Bridges Kotlin Flow<AgentEvent> to Swift closure callbacks
class FlowAdapter<T : Any>(private val flow: Flow<T>) {
    fun subscribe(
        onEach: (T) -> Unit,
        onError: (Throwable) -> Unit,
        onComplete: () -> Unit
    ): CancellableJob
}

// Allows Swift callers to cleanly cancel ongoing streams
interface CancellableJob {
    fun cancel()
}
```

---

## 4. Multiplatform Error Mapping

All exceptions inherit from `SdkException` (`commonMain`):
- `AuthenticationException`: HTTP 401/403.
- `RateLimitException`: HTTP 429.
- `NetworkException`: Socket or DNS failures across platforms.
- `TimeoutException`: Connect or read timeout across platforms.
- `MaxStepsExceededException`: Orchestration loop step limit.
