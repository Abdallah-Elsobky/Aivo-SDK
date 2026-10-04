# Contributing to Aivo SDK

Thank you for your interest in contributing to Aivo SDK!

## Principles
1. **Clean Architecture & SOLID:** Never violate the inward dependency rule. `sdk-core` must never know about Ktor, HTTP, or JSON wire formats.
2. **Multiplatform First:** All shared logic must reside in `commonMain`. Platform-specific code is restricted to minimal `expect/actual` ports (`PlatformLogger`, `ResourceReader`).
3. **No Reflection:** Do not use JVM reflection or JVM-only APIs in shared code.
4. **Testability:** All behaviors must be covered by automated tests that execute without network access using `MockEngine` and `FakeLlmProvider`.
5. **No Secret Leakage:** Never log raw headers, authorization tokens, or sensitive payload data.

## Conventional Commits
Please format commit messages following the Conventional Commits specification:
- `feat: add mistral provider`
- `fix: resolve sse crlf frame splitting`
- `docs: update quick start guide`
- `test: add contract test for gemini streaming`

## Architecture Decisions (ADR)
When making significant design decisions or introducing new paradigms, create a new record in `docs/adr/`.
