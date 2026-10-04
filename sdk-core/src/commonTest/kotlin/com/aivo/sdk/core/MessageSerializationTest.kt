package com.aivo.sdk.core

import com.aivo.sdk.core.model.ContentPart
import com.aivo.sdk.core.model.Message
import com.aivo.sdk.core.model.ProviderMetadata
import com.aivo.sdk.core.model.ToolCall
import com.aivo.sdk.core.model.ToolResult
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageSerializationTest {

    @Test
    fun serialize_system_message_roundtrip() {
        val msg: Message = Message.System("You are a helpful assistant.")
        val json = SdkJson.encodeToString(Message.serializer(), msg)
        val decoded = SdkJson.decodeFromString(Message.serializer(), json)
        assertEquals(msg, decoded)
    }

    @Test
    fun serialize_user_message_roundtrip() {
        val msg: Message = Message.User("Hello!")
        val json = SdkJson.encodeToString(Message.serializer(), msg)
        val decoded = SdkJson.decodeFromString(Message.serializer(), json)
        assertEquals(msg, decoded)
    }

    @Test
    fun serialize_assistant_message_with_tools_roundtrip() {
        val toolCall = ToolCall(
            id = "call_1",
            name = "get_balance",
            arguments = buildJsonObject { put("accountId", "123") },
        )
        val msg: Message = Message.Assistant(
            parts = listOf(ContentPart.Text("Checking your balance...")),
            toolCalls = listOf(toolCall),
            reasoning = "User asked for balance",
            providerMetadata = ProviderMetadata(mapOf("test_key" to JsonPrimitive("test_val"))),
        )

        val json = SdkJson.encodeToString(Message.serializer(), msg)
        val decoded = SdkJson.decodeFromString(Message.serializer(), json)
        assertEquals(msg, decoded)
    }

    @Test
    fun serialize_tool_message_roundtrip() {
        val result = ToolResult.Success(buildJsonObject { put("balance", 100.0) })
        val msg: Message = Message.Tool(
            callId = "call_1",
            toolName = "get_balance",
            result = result,
        )

        val json = SdkJson.encodeToString(Message.serializer(), msg)
        val decoded = SdkJson.decodeFromString(Message.serializer(), json)
        assertEquals(msg, decoded)
    }
}
