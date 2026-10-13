package com.example.shared.memory

import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.core.model.Message
import com.aivo.sdk.core.model.Role
import com.aivo.sdk.runtime.memory.MemoryStore

/**
 * Cross-platform SQLite persistence for Aivo SDK conversation memory via SQLDelight.
 * Works uniformly across Android, iOS, and Desktop.
 */
class SqlDelightMemoryStore(
    private val selectMessages: (String) -> List<SqlMessageRecord>,
    private val insertMessage: (convoId: String, role: String, content: String, timestamp: Long) -> Unit,
    private val deleteMessages: (String) -> Unit
) : MemoryStore {

    data class SqlMessageRecord(
        val role: String,
        val content: String,
        val timestamp: Long
    )

    override suspend fun load(conversationId: ConversationId): List<Message> {
        return selectMessages(conversationId.value).map { record ->
            val role = when (record.role.lowercase()) {
                "user" -> Role.User
                "assistant" -> Role.Assistant
                "system" -> Role.System
                else -> Role.User
            }
            Message(role = role, content = record.content)
        }
    }

    override suspend fun append(conversationId: ConversationId, messages: List<Message>) {
        val now = System.currentTimeMillis()
        messages.forEach { msg ->
            insertMessage(
                conversationId.value,
                msg.role.name.lowercase(),
                msg.content(),
                now
            )
        }
    }

    override suspend fun clear(conversationId: ConversationId) {
        deleteMessages(conversationId.value)
    }
}
