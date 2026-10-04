package com.aivo.sdk.runtime.memory

import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.core.model.Message
import com.aivo.sdk.core.port.MemoryStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe, in-memory implementation of [MemoryStore].
 *
 * Stores conversation histories in RAM; useful for testing, ephemeral chat sessions, and CLI tools.
 */
public class InMemoryMemoryStore : MemoryStore {

    private val mutex = Mutex()
    private val conversations = mutableMapOf<ConversationId, MutableList<Message>>()

    override suspend fun load(id: ConversationId): List<Message> = mutex.withLock {
        conversations[id]?.toList() ?: emptyList()
    }

    override suspend fun append(id: ConversationId, messages: List<Message>) {
        if (messages.isEmpty()) return
        mutex.withLock {
            val list = conversations.getOrPut(id) { mutableListOf() }
            list.addAll(messages)
        }
    }

    override suspend fun clear(id: ConversationId) {
        mutex.withLock {
            conversations.remove(id)
        }
    }
}
