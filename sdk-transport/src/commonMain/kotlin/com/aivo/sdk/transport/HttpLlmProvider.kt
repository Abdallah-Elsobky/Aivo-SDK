package com.aivo.sdk.transport

import com.aivo.sdk.core.error.NetworkException
import com.aivo.sdk.core.error.ProtocolException
import com.aivo.sdk.core.error.TimeoutException
import com.aivo.sdk.core.error.TimeoutKind
import com.aivo.sdk.core.model.LlmRequest
import com.aivo.sdk.core.model.LlmResponse
import com.aivo.sdk.core.model.LlmStreamEvent
import com.aivo.sdk.core.model.ProviderCapabilities
import com.aivo.sdk.core.model.ProviderId
import com.aivo.sdk.core.port.LlmProvider
import com.aivo.sdk.core.stream.StreamAssembler
import com.aivo.sdk.transport.auth.AuthStrategy
import com.aivo.sdk.transport.decoder.NdjsonDecoder
import com.aivo.sdk.transport.decoder.SseDecoder
import com.aivo.sdk.transport.protocol.RawFrame
import com.aivo.sdk.transport.protocol.StreamFraming
import com.aivo.sdk.transport.protocol.WireProtocol
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * The single HTTP-aware [LlmProvider] implementation.
 *
 * **All** HTTP mechanics live here exactly once:
 * - Applies [AuthStrategy] per request (suspend, supports rotating tokens).
 * - Sets content-type and custom headers.
 * - Sends the request and checks the HTTP status.
 * - On error: truncates body to 2 KB and delegates to [protocol.mapError].
 * - On success (non-streaming): reads body and delegates to [protocol.decode].
 * - On success (streaming): reads lines, delegates framing to [SseDecoder] / [NdjsonDecoder],
 *   then delegates frame translation to [protocol.decodeStream], then wraps in [StreamAssembler].
 *
 * **No JSON knowledge** — all wire format details are in [protocol].
 */
public class HttpLlmProvider(
    override val id: ProviderId,
    override val capabilities: ProviderCapabilities,
    private val baseUrl: String,
    private val httpClient: HttpClient,
    private val protocol: WireProtocol,
    private val authStrategy: AuthStrategy,
    private val extraHeaders: Map<String, String> = emptyMap(),
    private val streamIdleTimeoutMs: Long = 30_000L,
    private val assembler: StreamAssembler = StreamAssembler(),
    private val sseDecoder: SseDecoder = SseDecoder(),
    private val ndjsonDecoder: NdjsonDecoder = NdjsonDecoder(),
) : LlmProvider {

    private fun resolveUrl(endpointPath: String): String {
        var base = baseUrl.trimEnd('/')
        if (base.endsWith("/interactions")) {
            base = base.substringBeforeLast("/interactions")
        }
        val path = if (endpointPath.startsWith('/')) endpointPath else "/$endpointPath"
        if (base.endsWith(path)) {
            return base
        }
        val firstSegment = path.split('/').firstOrNull { it.isNotEmpty() }
        if (firstSegment != null && base.endsWith("/$firstSegment")) {
            val remaining = path.substringAfter("/$firstSegment")
            return base + remaining
        }
        return base + path
    }

    override suspend fun generate(request: LlmRequest): LlmResponse {
        val endpoint = protocol.endpoint(request, stream = false)
        val body = protocol.encode(request, stream = false)
        return try {
            val response = httpClient.prepareRequest(resolveUrl(endpoint.path)) {
                method = endpoint.method
                authStrategy.apply(this)
                applyHeaders()
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val errorBody = response.body<String>().take(MAX_ERROR_BODY_BYTES)
                    throw protocol.mapError(response.status.value, errorBody, response.headers)
                }
                val responseBody = response.body<String>()
                protocol.decode(responseBody)
            }
            response
        } catch (e: ConnectTimeoutException) {
            throw TimeoutException(id, TimeoutKind.CONNECT, "Connection timeout to ${baseUrl}", e)
        } catch (e: HttpRequestTimeoutException) {
            throw TimeoutException(id, TimeoutKind.REQUEST, "Request timeout to ${baseUrl}", e)
        } catch (e: Exception) {
            if (e is com.aivo.sdk.core.error.SdkException) throw e
            throw NetworkException(id, "Network error: ${e.message}", e)
        }
    }

    override fun stream(request: LlmRequest): Flow<LlmStreamEvent> {
        val endpoint = protocol.endpoint(request, stream = true)
        val body = protocol.encode(request, stream = true)

        val rawFrames: Flow<RawFrame> = flow {
            httpClient.prepareRequest(resolveUrl(endpoint.path)) {
                method = endpoint.method
                authStrategy.apply(this)
                applyHeaders()
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    val errorBody = response.body<String>().take(MAX_ERROR_BODY_BYTES)
                    throw protocol.mapError(response.status.value, errorBody, response.headers)
                }

                val channel: ByteReadChannel = response.body()
                val lineFlow = flow {
                    while (true) {
                        val line = channel.readUTF8Line() ?: break
                        emit(line)
                    }
                }

                val decodedFrames = when (protocol.framing) {
                    StreamFraming.SSE    -> sseDecoder.decode(lineFlow)
                    StreamFraming.NDJSON -> ndjsonDecoder.decode(lineFlow)
                }

                decodedFrames.collect { frame ->
                    emit(frame)
                    if (frame is RawFrame.Done) return@collect
                }
            }
        }.catch { e ->
            if (e is com.aivo.sdk.core.error.SdkException) throw e
            when (e) {
                is ConnectTimeoutException ->
                    throw TimeoutException(id, TimeoutKind.CONNECT, "Connection timeout", e)
                is HttpRequestTimeoutException ->
                    throw TimeoutException(id, TimeoutKind.REQUEST, "Request timeout", e)
                else ->
                    throw NetworkException(id, "Stream error: ${e.message}", e)
            }
        }

        val domainEvents = protocol.decodeStream(rawFrames)
        return assembler.assemble(domainEvents)
    }

    private fun HttpRequestBuilder.applyHeaders() {
        for ((name, value) in extraHeaders) {
            headers.append(name, value)
        }
    }

    private companion object {
        const val MAX_ERROR_BODY_BYTES = 2048
    }
}
