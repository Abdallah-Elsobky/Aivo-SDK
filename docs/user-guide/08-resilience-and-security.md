# Resilience & Security

Aivo SDK is built with production guardrails to handle real-world mobile network dropouts, rate limits, and secret leak prevention.

---

## 1. Retry & Exponential Backoff

Configure automatic retries for transient HTTP errors (429 Rate Limit, 502/503/504 Bad Gateway, socket disconnects):

```kotlin
val aivo = AivoSdk {
    // ...
    resilience {
        retry {
            maxAttempts = 5
            baseDelayMs = 1_000L   // 1s initial backoff
            maxDelayMs  = 30_000L  // Cap backoff at 30s
            jitter      = true     // ±20% randomization to prevent thundering-herd spikes
        }

        timeouts {
            connectTimeoutMs    = 10_000L // 10s socket connect timeout
            requestTimeoutMs    = 60_000L // 60s total request timeout
            streamIdleTimeoutMs = 30_000L // 30s between incoming streaming chunks
        }
    }
}
```

> **The Pre-First-Event Rule:** Retries on streaming requests are only performed **before** the first text token has reached your UI flow. Once a token is delivered to the screen, retries are aborted to prevent duplicate text output.

---

## 2. Runtime Execution Bounds

Prevent runaway tool loops and unbounded cost:

```kotlin
val aivo = AivoSdk {
    // ...
    runtime {
        maxSteps              = 15      // Max autonomous reasoning turns
        maxDelegationDepth    = 3       // Max supervisor delegation nesting
        toolTimeoutMs         = 15_000L // Timeout per individual tool execution
        runTimeoutMs          = 90_000L // Wall-clock timeout for the entire run
        parallelToolExecution = true    // Execute multiple independent tools concurrently
    }
}
```

---

## 3. Concurrency Policies

Control what happens if a new message arrives while a conversation run is still in-flight:

```kotlin
val aivo = AivoSdk {
    // ...
    runtime {
        concurrencyPolicy = ConcurrencyPolicy.QUEUE  // Queue new requests in FIFO order (default)
        // concurrencyPolicy = ConcurrencyPolicy.REJECT // Throw ConcurrentRunException immediately
        // concurrencyPolicy = ConcurrencyPolicy.CANCEL // Cancel in-flight run and start new one
    }
}
```

---

## 4. Secret Masking & Data Redaction

### `SecretString`
All API keys and tokens are stored in `SecretString`. Calling `toString()` always outputs `"[REDACTED]"`, making it impossible for keys to leak into Logcat, crash logs, or analytics:

```kotlin
val key = SecretString("AIzaSy-secret-token")
println(key)          // Output: [REDACTED]
println(key.reveal()) // Output: AIzaSy-secret-token (use only when sending over HTTPS)
```

### Automatic Payload Redaction
Configure observability loggers to sanitize headers and JSON payloads:

```kotlin
val aivo = AivoSdk {
    observability {
        // Safe to enable: Authorization and Bearer tokens are automatically masked
        logPayloads = BuildConfig.DEBUG
    }
}
```
