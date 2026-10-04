# Aivo SDK — Security & Threat Model Guide

## 1. Threat Model

| Threat | Impact | Mitigation Strategy |
|---|---|---|
| **API key in client binary** | Attacker extracts provider keys from APK or iOS IPA | Never ship secret master keys in mobile apps. Use the **Mobile Gateway Pattern** where the client calls an authenticated application backend that passes through LLM requests or issues short-lived session tokens. |
| **API key leakage in logs** | Secrets exposed in console or centralized log sinks | `SecretString` overrides `toString()` to `"***"`. Loggers and telemetry sinks never receive raw authorization headers or API keys. |
| **Prompt injection via tool output** | Malicious data in tool results attempts to hijack the agent | Tool results are always enclosed in `Message.Tool` data structures. The runtime treats tool output purely as data and never evaluates instructions contained within it. |
| **Runaway loops / Cost explosion** | Model gets stuck in infinite tool loops | Bounded execution limits: `maxSteps` (default 10), `runTimeoutMs`, token budget limits, and delegation depth bounds. |
| **Unauthorized tool actions** | Model attempts risky action (e.g. fund transfer) | `ToolRisk.HIGH` tools require explicit human confirmation via `ConfirmationHandler` before the tool logic executes. |
| **Insecure Transport** | Man-in-the-middle attacks on LLM prompts | HTTPS is enforced by default. Plaintext HTTP requires explicit opt-in via `allowInsecure = true`. |

## 2. Redactor
The `Redactor` interface sanitizes text before any message payload is logged (even when `logPayloads = true` is opted in):
- Filters `Bearer` tokens
- Filters `x-goog-api-key`
- Supports custom PII masking patterns
