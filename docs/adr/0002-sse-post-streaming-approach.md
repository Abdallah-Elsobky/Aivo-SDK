# ADR 0002: Custom SSE Decoder for POST Request Streaming

## Status
Accepted

## Context
LLM streaming APIs (OpenAI-compatible, OpenRouter, Google Gemini Interactions API) stream responses over HTTP POST requests using Server-Sent Events (SSE). The official Ktor SSE client plugin is tailored for GET-based EventSource connections (browser standard) and does not cleanly support POST payloads with arbitrary JSON bodies across all multiplatform engines (OkHttp, Darwin).

## Decision
We implement a lightweight, zero-dependency SSE line decoder (`SseDecoder`) directly in `sdk-transport` in `commonMain`.
The HTTP response body is read as a raw byte channel using Ktor's `readUTF8Line()`, stripped of CRLF, buffered, and parsed into `RawFrame.Data` and `RawFrame.Done`.

## Consequences
- Full control over comment lines (`: keepalive`), multi-line `data:` blocks, and custom event names (`event: interaction.created`).
- Works universally across Android, JVM, and iOS without engine-specific quirks or limitations.
- Hostile chunking and split frames are easily tested in pure unit tests without network access.
