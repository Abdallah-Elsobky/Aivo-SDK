package com.example.aivo.memory

import androidx.room.*
import com.aivo.sdk.core.model.ConversationId
import com.aivo.sdk.core.model.Message
import com.aivo.sdk.core.model.Role
import com.aivo.sdk.runtime.memory.MemoryStore

// -------------------------------------------------------------------------------------
// 1. Room Entity
// -------------------------------------------------------------------------------------

@Entity(
    tableName = "aivo_conversation_messages",
    indices = [Index(value = ["conversation_id", "timestamp"])]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "conversation_id")
    val conversationId: String,
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

// -------------------------------------------------------------------------------------
// 2. Room DAO
// -------------------------------------------------------------------------------------

@Dao
interface MessageDao {
    @Query("SELECT * FROM aivo_conversation_messages WHERE conversation_id = :convoId ORDER BY timestamp ASC")
    suspend fun getMessages(convoId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)

    @Query("DELETE FROM aivo_conversation_messages WHERE conversation_id = :convoId")
    suspend fun deleteConversation(convoId: String)
}

// -------------------------------------------------------------------------------------
// 3. RoomMemoryStore implementing Aivo SDK's MemoryStore port
// -------------------------------------------------------------------------------------

class RoomMemoryStore(
    private val dao: MessageDao
) : MemoryStore {

    override suspend fun load(conversationId: ConversationId): List<Message> {
        return dao.getMessages(conversationId.value).map { entity ->
            val role = when (entity.role.lowercase()) {
                "user" -> Role.User
                "assistant" -> Role.Assistant
                "system" -> Role.System
                else -> Role.User
            }
            Message(role = role, content = entity.content)
        }
    }

    override suspend fun append(conversationId: ConversationId, messages: List<Message>) {
        val entities = messages.map { message ->
            MessageEntity(
                conversationId = conversationId.value,
                role = message.role.name.lowercase(),
                content = message.content()
            )
        }
        dao.insertAll(entities)
    }

    override suspend fun clear(conversationId: ConversationId) {
        dao.deleteConversation(conversationId.value)
    }
}
