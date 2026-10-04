package com.aivo.sdk.provider.openai

import com.aivo.sdk.core.SdkJson
import com.aivo.sdk.core.error.AuthenticationException
import com.aivo.sdk.core.error.ContentFilteredException
import com.aivo.sdk.core.error.InvalidRequestException
import com.aivo.sdk.core.error.ModelNotFoundException
import com.aivo.sdk.core.error.NetworkException
import com.aivo.sdk.core.error.ProtocolException
import com.aivo.sdk.core.error.ProviderException
import com.aivo.sdk.core.error.RateLimitException
import com.aivo.sdk.core.error.ServerException
import com.aivo.sdk.core.model.ContentPart
import com.aivo.sdk.core.model.FinishReason
import com.aivo.sdk.core.model.LlmRequest
import com.aivo.sdk.core.model.LlmResponse
import com.aivo.sdk.core.model.LlmStreamEvent
import com.aivo.sdk.core.model.Message
import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.core.model.ProviderMetadata
import com.aivo.sdk.core.model.ToolCall
import com.aivo.sdk.core.model.ToolChoice
import com.aivo.sdk.core.model.Usage
import com.aivo.sdk.core.model.renderedText
import com.aivo.sdk.transport.protocol.Endpoint
import com.aivo.sdk.transport.protocol.RawFrame
import com.aivo.sdk.transport.protocol.StreamFraming
import com.aivo.sdk.transport.protocol.WireProtocol
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Adapts the OpenAI Chat Completions API wire format to the SDK domain model.
 *
 * Compatible with: **OpenAI**, **OpenRouter**, **Groq**, **Together AI**, **vLLM**, **LM Studio**.
 *
 * Wire contract:
 * - Endpoint: `POST {base}/chat/completions`
 * - Streaming: **SSE** (`stream: true`; frames end with `data: [DONE]`)
 * - Tool calls: `choices[0].message.tool_calls[].{id, type, function{name, arguments: string}}`
 * - Reasoning: `choices[0].message.reasoning_content` (DeepSeek) or
 *   `choices[0].message.content` first block with `type: "thinking"` (Anthropic-style, future)
 *
 * @param providerId Used in error messages only — the adapter itself is provider-agnostic.
 *
 * All methods are pure functions over JSON — no Ktor dependency.
 */
public class OpenAiCompatibleWireProtocol(
    private val providerId: ProviderId = ProviderId("openai"),
) : WireProtocol {

    override val framing: StreamFraming = StreamFraming.SSE

    override fun endpoint(request: LlmRequest, stream: Boolean): Endpoint =
        Endpoint(method = HttpMethod.Post, path = "/chat/completions")

    // ─── encode ──────────────────────────────────────────────────────────────

    override fun encode(request: LlmRequest, stream: Boolean): JsonObject = buildJsonObject {
        put("model", request.model)
        put("stream", stream)
        if (stream) {
            // Include usage in the final [DONE] chunk for accounting.
            putJsonObject("stream_options") { put("include_usage", true) }
        }

        putJsonArray("messages") {
            for (msg in request.messages) {
                add(encodeMessage(msg))
            }
        }

        with(request.options) {
            temperature?.let { put("temperature", it) }
            topP?.let { put("top_p", it) }
            maxOutputTokens?.let { put("max_tokens", it) }
            if (stop.isNotEmpty()) put("stop", JsonArray(stop.map { JsonPrimitive(it) }))
            seed?.let { put("seed", it) }
        }

        if (request.tools.isNotEmpty()) {
            putJsonArray("tools") {
                for (tool in request.tools) {
                    add(buildJsonObject {
                        put("type", "function")
                        putJsonObject("function") {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("parameters", tool.parameters)
                        }
                    })
                }
            }
            put("tool_choice", encodeToolChoice(request.toolChoice))
        }

        for ((k, v) in request.extras) {
            if (!k.startsWith("_")) put(k, v)
        }
    }

    private fun encodeMessage(msg: Message): JsonElement = when (msg) {
        is Message.System -> buildJsonObject {
            put("role", "system")
            put("content", msg.text)
        }

        is Message.User -> buildJsonObject {
            put("role", "user")
            put("content", msg.parts.joinToString("") {
                if (it is ContentPart.Text) it.text else ""
            })
        }

        is Message.Assistant -> buildJsonObject {
            put("role", "assistant")
            val text = msg.parts.joinToString("") {
                if (it is ContentPart.Text) it.text else ""
            }
            if (text.isNotEmpty()) put("content", text) else put("content", JsonNull)
            if (msg.toolCalls.isNotEmpty()) {
                putJsonArray("tool_calls") {
                    for (tc in msg.toolCalls) {
                        add(buildJsonObject {
                            put("id", tc.id)
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", tc.name)
                                put("arguments", SdkJson.encodeToString(JsonObject.serializer(), tc.arguments))
                            }
                        })
                    }
                }
            }
        }

        is Message.Tool -> buildJsonObject {
            put("role", "tool")
            put("tool_call_id", msg.callId)
            put("content", msg.result.renderedText)
        }
    }

    private fun encodeToolChoice(choice: ToolChoice): JsonElement = when (choice) {
        ToolChoice.Auto -> JsonPrimitive("auto")
        ToolChoice.Required -> JsonPrimitive("required")
        ToolChoice.None -> JsonPrimitive("none")
        is ToolChoice.Named -> buildJsonObject {
            put("type", "function")
            putJsonObject("function") { put("name", choice.name) }
        }
    }

    // ─── decode (non-streaming) ───────────────────────────────────────────────

    override fun decode(body: String): LlmResponse {
        val root = runCatching { SdkJson.parseToJsonElement(body).jsonObject }
            .getOrElse { throw ProtocolException(providerId, "Cannot parse response JSON: ${body.take(200)}", it) }
        return parseResponse(root)
    }

    private fun parseResponse(root: JsonObject): LlmResponse {
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw ProtocolException(providerId, "Missing 'choices' in response")

        val message = choice["message"]?.jsonObject
            ?: throw ProtocolException(providerId, "Missing 'choices[0].message'")

        val content = message["content"]?.let {
            if (it is JsonPrimitive && !it.isString) null
            else it.jsonPrimitive.content.takeIf { s -> s.isNotBlank() }
        }

        // DeepSeek reasoning_content field.
        val reasoning = message["reasoning_content"]?.jsonPrimitive?.content

        val toolCalls: List<ToolCall> = message["tool_calls"]?.jsonArray?.mapNotNull { elem ->
            val obj = elem.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val fn = obj["function"]?.jsonObject ?: return@mapNotNull null
            val name = fn["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val argsStr = fn["arguments"]?.jsonPrimitive?.content ?: "{}"
            val arguments = runCatching { SdkJson.parseToJsonElement(argsStr).jsonObject }.getOrDefault(JsonObject(emptyMap()))
            ToolCall(id = id, name = name, arguments = arguments)
        } ?: emptyList()

        val finishReasonStr = choice["finish_reason"]?.jsonPrimitive?.content
        val finishReason = when (finishReasonStr) {
            "stop"          -> FinishReason.STOP
            "length"        -> FinishReason.LENGTH
            "tool_calls"    -> FinishReason.TOOL_CALLS
            "content_filter" -> FinishReason.CONTENT_FILTER
            else            -> FinishReason.OTHER
        }

        val usageObj = root["usage"]?.jsonObject
        val usage = if (usageObj != null) Usage(
            inputTokens  = usageObj["prompt_tokens"]?.jsonPrimitive?.int,
            outputTokens = usageObj["completion_tokens"]?.jsonPrimitive?.int,
            reasoningTokens = usageObj["completion_tokens_details"]?.jsonObject
                ?.get("reasoning_tokens")?.jsonPrimitive?.int,
            cachedInputTokens = usageObj["prompt_tokens_details"]?.jsonObject
                ?.get("cached_tokens")?.jsonPrimitive?.int,
        ) else null

        val parts = if (content != null) listOf(ContentPart.Text(content)) else emptyList()

        return LlmResponse(
            message = Message.Assistant(
                parts = parts,
                toolCalls = toolCalls,
                reasoning = reasoning,
            ),
            finishReason = finishReason,
            usage = usage,
            model = root["model"]?.jsonPrimitive?.content,
            responseId = root["id"]?.jsonPrimitive?.content,
        )
    }

    // ─── decodeStream ─────────────────────────────────────────────────────────

    override fun decodeStream(frames: Flow<RawFrame>): Flow<LlmStreamEvent> = flow {
        // Tracks in-progress tool call accumulation by index.
        val toolCallMeta = mutableMapOf<Int, Pair<String, String>>() // index → (id, name)

        frames.collect { frame ->
            when (frame) {
                is RawFrame.Done  -> return@collect
                is RawFrame.Error -> {
                    val body = frame.body
                    val root = runCatching { SdkJson.parseToJsonElement(body).jsonObject }.getOrNull()
                    val msg = root?.get("error")?.jsonObject?.get("message")?.jsonPrimitive?.content ?: body
                    throw ProtocolException(providerId, "Mid-stream error: $msg")
                }
                is RawFrame.Data  -> {
                    val root = runCatching { SdkJson.parseToJsonElement(frame.data).jsonObject }
                        .getOrElse { return@collect }

                    val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?: return@collect

                    val delta = choice["delta"]?.jsonObject ?: return@collect

                    // Text delta
                    val textDelta = delta["content"]?.jsonPrimitive?.content
                    if (!textDelta.isNullOrEmpty()) emit(LlmStreamEvent.TextDelta(textDelta))

                    // Reasoning delta (DeepSeek)
                    val reasoningDelta = delta["reasoning_content"]?.jsonPrimitive?.content
                    if (!reasoningDelta.isNullOrEmpty()) emit(LlmStreamEvent.ReasoningDelta(reasoningDelta))

                    // Tool call deltas
                    delta["tool_calls"]?.jsonArray?.forEach { tcElem ->
                        val tc = tcElem.jsonObject
                        val index = tc["index"]?.jsonPrimitive?.int ?: return@forEach

                        val id = tc["id"]?.jsonPrimitive?.content
                        val fn = tc["function"]?.jsonObject
                        val name = fn?.get("name")?.jsonPrimitive?.content

                        if (id != null && name != null) {
                            toolCallMeta[index] = Pair(id, name)
                            emit(LlmStreamEvent.ToolCallStarted(index = index, id = id, name = name))
                        }

                        val argFragment = fn?.get("arguments")?.jsonPrimitive?.content
                        if (!argFragment.isNullOrEmpty()) {
                            emit(LlmStreamEvent.ToolCallArgumentsDelta(index = index, jsonFragment = argFragment))
                        }
                    }

                    // Usage from final SSE chunk (with stream_options.include_usage)
                    // StreamAssembler will capture the final Completed event.
                }
            }
        }
    }

    // ─── mapError ─────────────────────────────────────────────────────────────

    override fun mapError(status: Int, body: String, headers: Headers): ProviderException {
        val message = extractErrorMessage(body)
        val retryAfterMs = headers["Retry-After"]?.toLongOrNull()?.times(1000)
        return when (status) {
            401, 403 -> AuthenticationException(providerId, status, message)
            404      -> ModelNotFoundException(providerId, modelName = "", message)
            429      -> RateLimitException(providerId, retryAfterMs, message)
            in 400..499 -> InvalidRequestException(providerId, status, message)
            in 500..599 -> ServerException(providerId, status, message)
            else        -> NetworkException(providerId, "Unexpected HTTP $status: $message")
        }
    }

    private fun extractErrorMessage(body: String): String {
        val root = runCatching { SdkJson.parseToJsonElement(body).jsonObject }.getOrNull()
        return root?.get("error")?.let {
            when (it) {
                is JsonObject -> it["message"]?.jsonPrimitive?.content
                is JsonPrimitive -> it.content
                else -> null
            }
        } ?: body.take(200)
    }
}
