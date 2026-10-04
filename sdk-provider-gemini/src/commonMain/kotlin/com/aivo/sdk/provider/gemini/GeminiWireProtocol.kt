package com.aivo.sdk.provider.gemini

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
 * Adapts the Gemini **Live Interactions API** (v1alpha `models/{model}:generateContent`)
 * to the SDK domain model.
 *
 * Wire contract (from recorded fixtures in Appendix B / prompt.txt):
 * - Endpoint: `POST /v1beta/models/{model}:generateContent`
 *   (streaming: `:streamGenerateContent?alt=sse`)
 * - Framing: **SSE** (`event: message`)
 * - Stateful conversation: `previous_interaction_id` in request +
 *   `interaction_id` in response stored in [ProviderMetadata] under `"gemini.interactionId"`.
 * - Thinking (reasoning): `parts[].thought: true` + `parts[].text`
 * - Tool calls: `candidates[0].content.parts[].functionCall{name, args}`
 * - Auth: `x-goog-api-key` header
 *
 * All methods are pure functions over JSON — no Ktor dependency.
 */
public class GeminiWireProtocol(
    private val stateful: Boolean = true,
) : WireProtocol {

    override val framing: StreamFraming = StreamFraming.SSE

    /** Keys under which Gemini state is stored in [ProviderMetadata]. */
    public companion object {
        public const val KEY_INTERACTION_ID: String = "gemini.interactionId"
        public const val KEY_THOUGHT_SIGNATURE: String = "gemini.thoughtSignature"
    }

    override fun endpoint(request: LlmRequest, stream: Boolean): Endpoint {
        val path = if (stream) {
            "/v1beta/models/${request.model}:streamGenerateContent?alt=sse"
        } else {
            "/v1beta/models/${request.model}:generateContent"
        }
        return Endpoint(method = HttpMethod.Post, path = path)
    }

    // ─── encode ──────────────────────────────────────────────────────────────

    override fun encode(request: LlmRequest, stream: Boolean): JsonObject = buildJsonObject {
        // Stateful: include previous_interaction_id if present in last assistant message's metadata.
        if (stateful) {
            val lastAssistant = request.messages.filterIsInstance<Message.Assistant>().lastOrNull()
            val interactionId = lastAssistant?.providerMetadata?.get(KEY_INTERACTION_ID)
                ?.jsonPrimitive?.content
            if (interactionId != null) {
                put("previous_interaction_id", interactionId)
            }
        }

        // System instruction.
        val systemMsg = request.messages.filterIsInstance<Message.System>().firstOrNull()
        if (systemMsg != null) {
            putJsonObject("system_instruction") {
                putJsonArray("parts") {
                    add(buildJsonObject { put("text", systemMsg.text) })
                }
            }
        }

        // Conversation contents (user + model turns only).
        val conversationMessages = request.messages.filter { it !is Message.System }
        putJsonArray("contents") {
            for (msg in conversationMessages) {
                add(encodeMessage(msg))
            }
        }

        // Tools.
        if (request.tools.isNotEmpty()) {
            putJsonArray("tools") {
                add(buildJsonObject {
                    putJsonArray("function_declarations") {
                        for (tool in request.tools) {
                            add(buildJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", tool.parameters)
                            })
                        }
                    }
                })
            }
        }

        // Generation config.
        with(request.options) {
            val reasoningOpt = reasoning
            val hasConfig = temperature != null || topP != null ||
                    maxOutputTokens != null || stop.isNotEmpty() || (reasoningOpt?.enabled == true)
            if (hasConfig) {
                putJsonObject("generationConfig") {
                    temperature?.let { put("temperature", it) }
                    topP?.let { put("topP", it) }
                    maxOutputTokens?.let { put("maxOutputTokens", it) }
                    if (stop.isNotEmpty()) {
                        put("stopSequences", JsonArray(stop.map { JsonPrimitive(it) }))
                    }
                    if (reasoningOpt?.enabled == true) {
                        put("thinkingConfig", buildJsonObject {
                            put("includeThoughts", true)
                            reasoningOpt.maxTokens?.let { put("thinkingBudget", it) }
                        })
                    }
                }
            }
        }

        // Exclude internal SDK metadata keys (prefixed with '_') from API payload
        for ((k, v) in request.extras) {
            if (!k.startsWith("_")) {
                put(k, v)
            }
        }
    }

    private fun encodeMessage(msg: Message): JsonElement = when (msg) {
        is Message.System -> buildJsonObject {} // already handled as system_instruction

        is Message.User -> buildJsonObject {
            put("role", "user")
            putJsonArray("parts") {
                for (part in msg.parts) {
                    if (part is ContentPart.Text) {
                        add(buildJsonObject { put("text", part.text) })
                    }
                }
            }
        }

        is Message.Assistant -> buildJsonObject {
            put("role", "model")
            putJsonArray("parts") {
                for (part in msg.parts) {
                    if (part is ContentPart.Text && part.text.isNotEmpty()) {
                        add(buildJsonObject { put("text", part.text) })
                    }
                }
                // Re-encode thought signature if present.
                val sig = msg.providerMetadata[KEY_THOUGHT_SIGNATURE]
                if (sig != null) {
                    add(buildJsonObject {
                        put("thought", true)
                        put("text", sig.jsonPrimitive.content)
                    })
                }
                for (tc in msg.toolCalls) {
                    add(buildJsonObject {
                        putJsonObject("functionCall") {
                            put("name", tc.name)
                            put("args", tc.arguments)
                        }
                    })
                }
            }
        }

        is Message.Tool -> buildJsonObject {
            put("role", "user")  // Gemini receives tool results as a "user" turn.
            putJsonArray("parts") {
                add(buildJsonObject {
                    putJsonObject("functionResponse") {
                        put("name", msg.toolName)
                        putJsonObject("response") {
                            put("output", msg.result.renderedText)
                        }
                    }
                })
            }
        }
    }

    // ─── decode (non-streaming) ───────────────────────────────────────────────

    override fun decode(body: String): LlmResponse {
        val root = runCatching { SdkJson.parseToJsonElement(body).jsonObject }
            .getOrElse { throw ProtocolException(ProviderId("gemini"), "Cannot parse Gemini JSON", it) }
        return parseResponse(root)
    }

    private fun parseResponse(root: JsonObject): LlmResponse {
        val candidatesArray = root["candidates"]?.jsonArray
        val stepsArray = root["steps"]?.jsonArray

        return when {
            candidatesArray != null -> {
                val candidate = candidatesArray.firstOrNull()?.jsonObject
                    ?: throw ProtocolException(ProviderId("gemini"), "Missing 'candidates' in Gemini response")
                parseCandidatesResponse(root, candidate)
            }
            stepsArray != null -> {
                parseStepsResponse(root, stepsArray)
            }
            else -> {
                throw ProtocolException(ProviderId("gemini"), "Missing 'candidates' or 'steps' in Gemini response")
            }
        }
    }

    private fun parseCandidatesResponse(root: JsonObject, candidate: JsonObject): LlmResponse {
        val rawFinish = candidate["finishReason"]?.jsonPrimitive?.content
        if (rawFinish == "SAFETY") {
            throw ContentFilteredException(ProviderId("gemini"), "Gemini response blocked by content safety filters.")
        }

        val content = candidate["content"]?.jsonObject
        val parts = content?.get("parts")?.jsonArray ?: JsonArray(emptyList())

        val textAccum = StringBuilder()
        val reasoningAccum = StringBuilder()
        var thoughtSignature: String? = null
        val toolCalls = mutableListOf<ToolCall>()

        for (part in parts) {
            val obj = part.jsonObject
            val isThought = obj["thought"]?.jsonPrimitive?.content == "true" ||
                    obj["thought"]?.jsonPrimitive?.let { it.content == "true" } == true

            val text = obj["text"]?.jsonPrimitive?.content
            val fn = obj["functionCall"]?.jsonObject

            when {
                isThought && text != null -> {
                    reasoningAccum.append(text)
                    thoughtSignature = text  // last thought part is the signature
                }
                text != null -> textAccum.append(text)
                fn != null -> {
                    val name = fn["name"]?.jsonPrimitive?.content ?: continue
                    val args = fn["args"]?.jsonObject ?: JsonObject(emptyMap())
                    // Gemini doesn't provide call IDs — generate a stable one from name.
                    toolCalls.add(ToolCall(id = "gemini-call-$name-${toolCalls.size}", name = name, arguments = args))
                }
            }
        }

        val finishReason = when (rawFinish) {
            "STOP"          -> FinishReason.STOP
            "MAX_TOKENS"    -> FinishReason.LENGTH
            "SAFETY"        -> FinishReason.CONTENT_FILTER
            else            -> if (toolCalls.isNotEmpty()) FinishReason.TOOL_CALLS else FinishReason.STOP
        }

        val usageObj = root["usageMetadata"]?.jsonObject
        val usage = usageObj?.let {
            Usage(
                inputTokens       = it["promptTokenCount"]?.jsonPrimitive?.int,
                outputTokens      = it["candidatesTokenCount"]?.jsonPrimitive?.int,
                reasoningTokens   = it["thoughtsTokenCount"]?.jsonPrimitive?.int,
                cachedInputTokens = it["cachedContentTokenCount"]?.jsonPrimitive?.int,
            )
        }

        val interactionId = root["interactionId"]?.jsonPrimitive?.content ?: root["id"]?.jsonPrimitive?.content
        var metadata = ProviderMetadata.Empty
        if (interactionId != null) {
            metadata = metadata.with(KEY_INTERACTION_ID, JsonPrimitive(interactionId))
        }
        if (thoughtSignature != null) {
            metadata = metadata.with(KEY_THOUGHT_SIGNATURE, JsonPrimitive(thoughtSignature))
        }

        val textParts = if (textAccum.isNotEmpty()) listOf(ContentPart.Text(textAccum.toString())) else emptyList()

        return LlmResponse(
            message = Message.Assistant(
                parts = textParts,
                toolCalls = toolCalls,
                reasoning = reasoningAccum.takeIf { it.isNotEmpty() }?.toString(),
                providerMetadata = metadata,
            ),
            finishReason = finishReason,
            usage = usage,
            model = root["model"]?.jsonPrimitive?.content,
            responseId = interactionId,
        )
    }

    private fun parseStepsResponse(root: JsonObject, steps: JsonArray): LlmResponse {
        val textAccum = StringBuilder()
        val reasoningAccum = StringBuilder()
        var thoughtSignature: String? = null
        val toolCalls = mutableListOf<ToolCall>()

        for (stepElement in steps) {
            val step = stepElement.jsonObject
            val stepType = step["type"]?.jsonPrimitive?.content

            when (stepType) {
                "model_output" -> {
                    val contentArr = step["content"]?.jsonArray
                    if (contentArr != null) {
                        for (item in contentArr) {
                            val itemObj = item.jsonObject
                            val text = itemObj["text"]?.jsonPrimitive?.content
                            if (text != null) {
                                textAccum.append(text)
                            }
                        }
                    }
                    val directText = step["text"]?.jsonPrimitive?.content
                    if (directText != null) {
                        textAccum.append(directText)
                    }
                }
                "thought" -> {
                    val sig = step["signature"]?.jsonPrimitive?.content
                    if (sig != null) {
                        thoughtSignature = sig
                    }
                    val text = step["text"]?.jsonPrimitive?.content
                    if (text != null) {
                        reasoningAccum.append(text)
                    }
                }
                "function_call" -> {
                    val id = step["id"]?.jsonPrimitive?.content ?: "gemini-call-${toolCalls.size}"
                    val name = step["name"]?.jsonPrimitive?.content ?: ""
                    val argsElement = step["arguments"]
                    val argsObj = when {
                        argsElement is JsonObject -> argsElement
                        argsElement != null && argsElement is JsonPrimitive -> {
                            runCatching { SdkJson.parseToJsonElement(argsElement.content).jsonObject }
                                .getOrDefault(JsonObject(emptyMap()))
                        }
                        else -> JsonObject(emptyMap())
                    }
                    if (name.isNotEmpty()) {
                        toolCalls.add(ToolCall(id = id, name = name, arguments = argsObj))
                    }
                }
            }
        }

        val status = root["status"]?.jsonPrimitive?.content
        val finishReason = when {
            status == "requires_action" || toolCalls.isNotEmpty() -> FinishReason.TOOL_CALLS
            status == "length" -> FinishReason.LENGTH
            status == "safety" -> FinishReason.CONTENT_FILTER
            else -> FinishReason.STOP
        }

        val usageObj = root["usage"]?.jsonObject
        val usage = usageObj?.let {
            Usage(
                inputTokens = it["total_input_tokens"]?.jsonPrimitive?.int,
                outputTokens = it["total_output_tokens"]?.jsonPrimitive?.int,
                reasoningTokens = it["total_thought_tokens"]?.jsonPrimitive?.int,
            )
        }

        val interactionId = root["id"]?.jsonPrimitive?.content ?: root["interactionId"]?.jsonPrimitive?.content
        var metadata = ProviderMetadata.Empty
        if (interactionId != null) {
            metadata = metadata.with(KEY_INTERACTION_ID, JsonPrimitive(interactionId))
        }
        if (thoughtSignature != null) {
            metadata = metadata.with(KEY_THOUGHT_SIGNATURE, JsonPrimitive(thoughtSignature))
        }

        val textParts = if (textAccum.isNotEmpty()) listOf(ContentPart.Text(textAccum.toString())) else emptyList()

        return LlmResponse(
            message = Message.Assistant(
                parts = textParts,
                toolCalls = toolCalls,
                reasoning = reasoningAccum.takeIf { it.isNotEmpty() }?.toString(),
                providerMetadata = metadata,
            ),
            finishReason = finishReason,
            usage = usage,
            model = root["model"]?.jsonPrimitive?.content,
            responseId = interactionId,
        )
    }

    // ─── decodeStream ─────────────────────────────────────────────────────────

    override fun decodeStream(frames: Flow<RawFrame>): Flow<LlmStreamEvent> = flow {
        val accumulatedText = StringBuilder()
        val accumulatedReasoning = StringBuilder()
        var thoughtSignature: String? = null
        val accumulatedToolCalls = mutableListOf<ToolCall>()
        var finalUsage: Usage? = null
        var finalInteractionId: String? = null
        var finalFinishReason: FinishReason? = null

        frames.collect { frame ->
            when (frame) {
                is RawFrame.Done  -> return@collect
                is RawFrame.Error -> {
                    throw ProtocolException(ProviderId("gemini"), "Stream error: ${frame.body}")
                }
                is RawFrame.Data  -> {
                    // Each SSE frame is a partial-stream JSON blob.
                    val root = runCatching { SdkJson.parseToJsonElement(frame.data).jsonObject }
                        .getOrElse { return@collect }

                    val interactionId = root["interactionId"]?.jsonPrimitive?.content
                        ?: root["id"]?.jsonPrimitive?.content
                    if (interactionId != null) finalInteractionId = interactionId

                    val usageObj = root["usageMetadata"]?.jsonObject
                    if (usageObj != null) {
                        finalUsage = Usage(
                            inputTokens = usageObj["promptTokenCount"]?.jsonPrimitive?.int,
                            outputTokens = usageObj["candidatesTokenCount"]?.jsonPrimitive?.int,
                            reasoningTokens = usageObj["thoughtsTokenCount"]?.jsonPrimitive?.int,
                            cachedInputTokens = usageObj["cachedContentTokenCount"]?.jsonPrimitive?.int,
                        )
                    }
                    val usageInteractions = root["usage"]?.jsonObject
                    if (usageInteractions != null) {
                        finalUsage = Usage(
                            inputTokens = usageInteractions["total_input_tokens"]?.jsonPrimitive?.int,
                            outputTokens = usageInteractions["total_output_tokens"]?.jsonPrimitive?.int,
                            reasoningTokens = usageInteractions["total_thought_tokens"]?.jsonPrimitive?.int,
                        )
                    }

                    val deltaObj = root["delta"]?.jsonObject
                    if (deltaObj != null) {
                        val text = deltaObj["text"]?.jsonPrimitive?.content
                        val thought = deltaObj["thought"]?.jsonPrimitive?.content
                        if (thought != null) {
                            accumulatedReasoning.append(thought)
                            thoughtSignature = thought
                            emit(LlmStreamEvent.ReasoningDelta(thought))
                        }
                        if (text != null) {
                            accumulatedText.append(text)
                            emit(LlmStreamEvent.TextDelta(text))
                        }
                    }

                    val status = root["status"]?.jsonPrimitive?.content
                    if (status == "completed") {
                        finalFinishReason = FinishReason.STOP
                    } else if (status == "requires_action") {
                        finalFinishReason = FinishReason.TOOL_CALLS
                    }

                    val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                    if (candidate != null) {
                        val finishReasonStr = candidate["finishReason"]?.jsonPrimitive?.content
                        if (finishReasonStr != null) {
                            finalFinishReason = when (finishReasonStr) {
                                "STOP" -> FinishReason.STOP
                                "MAX_TOKENS" -> FinishReason.LENGTH
                                "SAFETY" -> FinishReason.CONTENT_FILTER
                                else -> null
                            }
                        }

                        val content = candidate["content"]?.jsonObject
                        val parts = content?.get("parts")?.jsonArray
                        if (parts != null) {
                            for (part in parts) {
                                val obj = part.jsonObject
                                val isThought = obj["thought"]?.jsonPrimitive?.content == "true"
                                val text = obj["text"]?.jsonPrimitive?.content
                                val fn = obj["functionCall"]?.jsonObject

                                when {
                                    isThought && text != null -> {
                                        accumulatedReasoning.append(text)
                                        thoughtSignature = text
                                        emit(LlmStreamEvent.ReasoningDelta(text))
                                    }
                                    text != null -> {
                                        accumulatedText.append(text)
                                        emit(LlmStreamEvent.TextDelta(text))
                                    }
                                    fn != null -> {
                                        val name = fn["name"]?.jsonPrimitive?.content ?: continue
                                        val args = fn["args"]?.jsonObject ?: JsonObject(emptyMap())
                                        val callId = "gemini-stream-$name-${accumulatedToolCalls.size}"
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
                        }
                    }
                }
            }
        }

        var metadata = ProviderMetadata.Empty
        if (finalInteractionId != null) {
            metadata = metadata.with(KEY_INTERACTION_ID, JsonPrimitive(finalInteractionId))
        }
        if (thoughtSignature != null) {
            metadata = metadata.with(KEY_THOUGHT_SIGNATURE, JsonPrimitive(thoughtSignature))
        }

        val textParts = if (accumulatedText.isNotEmpty()) listOf(ContentPart.Text(accumulatedText.toString())) else emptyList()
        val finishReason = finalFinishReason ?: when {
            accumulatedToolCalls.isNotEmpty() -> FinishReason.TOOL_CALLS
            else -> FinishReason.STOP
        }

        emit(LlmStreamEvent.Completed(
            response = LlmResponse(
                message = Message.Assistant(
                    parts = textParts,
                    toolCalls = accumulatedToolCalls.toList(),
                    reasoning = accumulatedReasoning.takeIf { it.isNotEmpty() }?.toString(),
                    providerMetadata = metadata,
                ),
                finishReason = finishReason,
                usage = finalUsage,
                model = null,
                responseId = finalInteractionId,
            )
        ))
    }

    // ─── mapError ─────────────────────────────────────────────────────────────

    override fun mapError(status: Int, body: String, headers: Headers): ProviderException {
        val pid = ProviderId("gemini")
        val message = extractErrorMessage(body)
        return when (status) {
            400 -> InvalidRequestException(pid, status, message)
            401, 403 -> AuthenticationException(pid, status, message)
            404 -> ModelNotFoundException(pid, modelName = "", message)
            429 -> RateLimitException(pid, headers["Retry-After"]?.toLongOrNull()?.times(1000), message)
            in 500..599 -> ServerException(pid, status, message)
            else -> NetworkException(pid, "Gemini HTTP $status: $message")
        }
    }

    private fun extractErrorMessage(body: String): String {
        val root = runCatching { SdkJson.parseToJsonElement(body).jsonObject }.getOrNull()
        return root?.get("error")?.jsonObject?.get("message")?.jsonPrimitive?.content
            ?: body.take(200)
    }
}
