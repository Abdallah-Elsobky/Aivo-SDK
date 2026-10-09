# ADR 0003: Gemini Interactions API Support & Statefulness

## Status
Accepted

## Context
Google Gemini provides the modern Interactions API (`/v1beta/interactions`) which supports stateful interaction sessions (`previous_interaction_id`), thought signatures, function calling, and typed SSE events (`interaction.created`, `step.start`, `step.delta`, `step.stop`, `interaction.completed`).

## Decision
1. Implement `GeminiWireProtocol` to map domain `LlmRequest` to Gemini Interactions request shapes, and decode responses/SSE frames into `LlmResponse` and `LlmStreamEvent`.
2. Provider-specific state such as `previous_interaction_id` and thought signatures are encapsulated inside `ProviderMetadata` under the `gemini.*` namespace.
3. The domain model remains completely agnostic of Gemini-specific constructs.

## Consequences
- Clean separation between core domain types and provider specifics.
- Full compatibility with thinking models, thought tokens, and multi-turn tool interaction loops.
