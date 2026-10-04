# ADR 0001: Record Architecture Decisions

## Status
Accepted

## Context
Aivo SDK is a multiplatform Agentic AI SDK designed to run identically on JVM, Android, and iOS. As the project evolves, architectural consistency, clean separation of concerns, and adherence to SOLID principles must be preserved across all modules and contributors.

## Decision
We adopt Architecture Decision Records (ADRs) to document significant architectural choices.
All ADRs will be stored in `docs/adr/` in Markdown format, numbered sequentially.

## Consequences
- Every non-trivial architectural decision will be recorded, reviewable, and tracked in git.
- Future contributors understand the rationale behind design choices (e.g. why no Ktor SSE plugin was used, why KMP serialization handles wire mapping).
