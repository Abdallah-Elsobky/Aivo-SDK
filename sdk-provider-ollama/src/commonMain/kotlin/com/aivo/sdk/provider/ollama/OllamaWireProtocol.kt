package com.aivo.sdk.provider.ollama

import com.aivo.sdk.core.SdkJson
import com.aivo.sdk.core.error.AuthenticationException
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
import com.aivo.sdk.core.stream.StreamAssembler
import com.aivo.sdk.core.util.IdGenerator
import com.aivo.sdk.core.util.UuidIdGenerator
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
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
 * Adapts the Ollama `/api/chat` wire format to the SDK domain model.
 *
 * Wire contract (from recorded fixtures in Appendix A / prompt.txt):
 * - Endpoint: `POST {base}/api/chat`
 * - Streaming: **NDJSON** (`stream: true`)
 * - Tool calls: `message.tool_calls[].function{name, arguments: object}` (no id — we generate one)
 * - Reasoning: `message.thinking` field (when `think` is enabled in options)
 *
 * All methods are **pure functions over JSON** — no Ktor dependency.
 * @see [WireProtocol]
 */
public class OllamaWireProtocol(
    private val idGenerator: IdGenerator = UuidIdGenerator,
) : WireProtocol {

    override val framing: StreamFraming = StreamFraming.NDJSON

    override fun endpoint(request: LlmRequest, stream: Boolean): Endpoint =
        Endpoint(method = HttpMethod.Post, path = "/api/chat")

    // ─── encode ──────────────────────────────────────────────────────────────

    override fun encode(request: LlmRequest, stream: Boolean): JsonObject = buildJsonObject {
        put("model", request.model)
        put("stream", stream)

        val hasTools = request.tools.isNotEmpty()

        putJsonArray("messages") {
            for (msg in request.messages) {
                add(encodeMessage(msg, hasTools))
            }
        }

        // Generation options → Ollama's `options` object
        with(request.options) {
            if (temperature != null || topP != null || maxOutputTokens != null ||
                stop.isNotEmpty() || seed != null
            ) {
                putJsonObject("options") {
                    temperature?.let { put("temperature", it) }
                    topP?.let { put("top_p", it) }
                    maxOutputTokens?.let { put("num_predict", it) }
                    if (stop.isNotEmpty()) put("stop", JsonArray(stop.map { JsonPrimitive(it) }))
                    seed?.let { put("seed", it) }
                }
            }
            if (reasoning?.enabled == true) put("think", true)
        }

        // Tool declarations
        if (hasTools) {
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
        }

        // Merge extras (caller override — must be last, excluding internal SDK metadata)
        for ((k, v) in request.extras) {
            if (!k.startsWith("_")) {
                put(k, v)
            }
        }
    }

    private fun encodeMessage(msg: Message, hasTools: Boolean = true): JsonElement = when (msg) {
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
            if (hasTools) {
                put("content", text)
                if (msg.toolCalls.isNotEmpty()) {
                    putJsonArray("tool_calls") {
                        for (tc in msg.toolCalls) {
                            add(buildJsonObject {
                                putJsonObject("function") {
                                    put("name", tc.name)
                                    put("arguments", tc.arguments)
                                }
                            })
                        }
                    }
                }
            } else {
                val toolSummary = if (msg.toolCalls.isNotEmpty()) {
                    val calls = msg.toolCalls.joinToString(", ") { "${it.name}(${it.arguments})" }
                    if (text.isNotBlank()) "\n[Called tool: $calls]" else "[Called tool: $calls]"
                } else ""
                put("content", text + toolSummary)
            }
        }

        is Message.Tool -> buildJsonObject {
            if (hasTools) {
                put("role", "tool")
                put("content", msg.result.renderedText)
            } else {
                put("role", "user")
                put("content", "[Tool Result (${msg.toolName})]: ${msg.result.renderedText}")
            }
        }
    }

    // ─── decode (non-streaming) ───────────────────────────────────────────────

    override fun decode(body: String): LlmResponse {
        val json = runCatching { SdkJson.parseToJsonElement(body).jsonObject }
            .getOrElse { throw ProtocolException(ProviderId("ollama"), "Cannot parse response JSON: ${body.take(200)}", it) }

        return parseResponse(json)
    }

    private fun parseResponse(root: JsonObject): LlmResponse {
        val message = root["message"]?.jsonObject
            ?: throw ProtocolException(ProviderId("ollama"), "Missing 'message' field in Ollama response")

        val content = message["content"]?.jsonPrimitive?.content ?: ""
        val reasoning = message["thinking"]?.jsonPrimitive?.content

        val toolCalls: List<ToolCall> = message["tool_calls"]?.jsonArray?.map { elem ->
            val fn = elem.jsonObject["function"]?.jsonObject
                ?: return@map null
            val name = fn["name"]?.jsonPrimitive?.content ?: return@map null
            val arguments = fn["arguments"]?.jsonObject ?: JsonObject(emptyMap())
            ToolCall(
                id = idGenerator.next(),   // Ollama has no id — generate one
                name = name,
                arguments = arguments,
            )
        }?.filterNotNull() ?: emptyList()

        val doneReason = root["done_reason"]?.jsonPrimitive?.content
        val done = root["done"]?.jsonPrimitive?.boolean ?: false
        val finishReason = when {
            toolCalls.isNotEmpty() -> FinishReason.TOOL_CALLS
            doneReason == "stop"   -> FinishReason.STOP
            doneReason == "length" -> FinishReason.LENGTH
            root["error"] != null  -> FinishReason.ERROR
            done                   -> FinishReason.STOP
            else                   -> FinishReason.OTHER
        }

        val usage = Usage(
            inputTokens  = root["prompt_eval_count"]?.jsonPrimitive?.int,
            outputTokens = root["eval_count"]?.jsonPrimitive?.int,
            cachedInputTokens = root["prompt_eval_cached_count"]?.jsonPrimitive?.int,
        )

        val parts = if (content.isNotBlank()) {
            listOf(ContentPart.Text(content))
        } else if (!reasoning.isNullOrBlank()) {
            listOf(ContentPart.Text(reasoning))
        } else {
            emptyList()
        }

        return LlmResponse(
            message = Message.Assistant(
                parts = parts,
                toolCalls = toolCalls,
                reasoning = reasoning,
            ),
            finishReason = finishReason,
            usage = usage,
            model = root["model"]?.jsonPrimitive?.content,
        )
    }

    // ─── decodeStream ─────────────────────────────────────────────────────────

    override fun decodeStream(frames: Flow<RawFrame>): Flow<LlmStreamEvent> = flow {
        val accumulatedContent = StringBuilder()
        val accumulatedThinking = StringBuilder()
        val accumulatedToolCalls = mutableListOf<ToolCall>()
        var finalUsage: Usage? = null
        var finalModel: String? = null
        var finalDoneReason: String? = null
        var completedResponse: LlmResponse? = null

        frames.collect { frame ->
            when (frame) {
                is RawFrame.Error -> {
                    // Ollama may send {"error": "…"} mid-stream.
                    val root = runCatching { SdkJson.parseToJsonElement(frame.body).jsonObject }.getOrNull()
                    val errorMsg = root?.get("error")?.jsonPrimitive?.content ?: frame.body
                    throw ProtocolException(ProviderId("ollama"), "Mid-stream error: $errorMsg")
                }

                is RawFrame.Done -> return@collect

                is RawFrame.Data -> {
                    val root = runCatching { SdkJson.parseToJsonElement(frame.data).jsonObject }
                        .getOrElse { return@collect }

                    val done = root["done"]?.jsonPrimitive?.boolean ?: false
                    val message = root["message"]?.jsonObject

                    if (root["model"] != null) {
                        finalModel = root["model"]?.jsonPrimitive?.content
                    }
                    if (root["done_reason"] != null) {
                        finalDoneReason = root["done_reason"]?.jsonPrimitive?.content
                    }
                    if (root["prompt_eval_count"] != null || root["eval_count"] != null) {
                        finalUsage = Usage(
                            inputTokens = root["prompt_eval_count"]?.jsonPrimitive?.int,
                            outputTokens = root["eval_count"]?.jsonPrimitive?.int,
                            cachedInputTokens = root["prompt_eval_cached_count"]?.jsonPrimitive?.int,
                        )
                    }

                    if (message != null) {
                        val textDelta = message["content"]?.jsonPrimitive?.content
                        if (!textDelta.isNullOrEmpty()) {
                            accumulatedContent.append(textDelta)
                            emit(LlmStreamEvent.TextDelta(textDelta))
                        }

                        val thinkingDelta = message["thinking"]?.jsonPrimitive?.content
                        if (!thinkingDelta.isNullOrEmpty()) {
                            accumulatedThinking.append(thinkingDelta)
                            emit(LlmStreamEvent.ReasoningDelta(thinkingDelta))
                        }

                        val toolCallsJson = message["tool_calls"]?.jsonArray
                        if (toolCallsJson != null && toolCallsJson.isNotEmpty()) {
                            for (elem in toolCallsJson) {
                                val fn = elem.jsonObject["function"]?.jsonObject ?: continue
                                val name = fn["name"]?.jsonPrimitive?.content ?: continue
                                val args = fn["arguments"]?.jsonObject ?: JsonObject(emptyMap())
                                val callId = idGenerator.next()
                                val call = ToolCall(id = callId, name = name, arguments = args)
                                accumulatedToolCalls.add(call)

                                val index = accumulatedToolCalls.size - 1
                                emit(LlmStreamEvent.ToolCallStarted(index = index, id = callId, name = name))
                                emit(LlmStreamEvent.ToolCallArgumentsDelta(
                                    index = index,
                                    jsonFragment = SdkJson.encodeToString(JsonObject.serializer(), args)
                                ))
                            }
                        }
                    }

                    if (done) {
                        val finishReason = when {
                            accumulatedToolCalls.isNotEmpty() -> FinishReason.TOOL_CALLS
                            finalDoneReason == "stop" -> FinishReason.STOP
                            finalDoneReason == "length" -> FinishReason.LENGTH
                            root["error"] != null -> FinishReason.ERROR
                            else -> FinishReason.STOP
                        }

                        val parts = if (accumulatedContent.isNotEmpty()) {
                            listOf(ContentPart.Text(accumulatedContent.toString()))
                        } else if (accumulatedThinking.isNotEmpty()) {
                            listOf(ContentPart.Text(accumulatedThinking.toString()))
                        } else {
                            emptyList()
                        }

                        completedResponse = LlmResponse(
                            message = Message.Assistant(
                                parts = parts,
                                toolCalls = accumulatedToolCalls.toList(),
                                reasoning = accumulatedThinking.takeIf { it.isNotEmpty() }?.toString(),
                            ),
                            finishReason = finishReason,
                            usage = finalUsage,
                            model = finalModel,
                        )
                    }
                }
            }
        }

        if (completedResponse == null && (accumulatedContent.isNotEmpty() || accumulatedToolCalls.isNotEmpty() || accumulatedThinking.isNotEmpty())) {
            val finishReason = if (accumulatedToolCalls.isNotEmpty()) FinishReason.TOOL_CALLS else FinishReason.STOP
            val parts = if (accumulatedContent.isNotEmpty()) {
                listOf(ContentPart.Text(accumulatedContent.toString()))
            } else if (accumulatedThinking.isNotEmpty()) {
                listOf(ContentPart.Text(accumulatedThinking.toString()))
            } else {
                emptyList()
            }
            completedResponse = LlmResponse(
                message = Message.Assistant(
                    parts = parts,
                    toolCalls = accumulatedToolCalls.toList(),
                    reasoning = accumulatedThinking.takeIf { it.isNotEmpty() }?.toString(),
                ),
                finishReason = finishReason,
                usage = finalUsage,
                model = finalModel,
            )
        }

        completedResponse?.let { emit(LlmStreamEvent.Completed(it)) }
    }

    // ─── mapError ─────────────────────────────────────────────────────────────

    override fun mapError(status: Int, body: String, headers: Headers): ProviderException {
        val pid = ProviderId("ollama")
        val message = extractErrorMessage(body)
        return when (status) {
            401, 403 -> AuthenticationException(pid, status, message)
            404      -> ModelNotFoundException(pid, modelName = "", message)
            429      -> RateLimitException(pid, retryAfterMs = headers["Retry-After"]?.toLongOrNull()?.times(1000), message)
            in 400..499 -> InvalidRequestException(pid, status, message)
            in 500..599 -> ServerException(pid, status, message)
            else        -> NetworkException(pid, "Unexpected status $status: $message")
        }
    }

    private fun extractErrorMessage(body: String): String {
        val root = runCatching { SdkJson.parseToJsonElement(body).jsonObject }.getOrNull()
        return root?.get("error")?.jsonPrimitive?.content ?: body.take(200)
    }
}
