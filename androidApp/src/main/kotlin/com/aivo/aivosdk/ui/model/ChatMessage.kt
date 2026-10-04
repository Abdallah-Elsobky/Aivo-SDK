package com.aivo.aivosdk.ui.model

/**
 * UI representation of a chat message in the conversation.
 */
data class ChatMessage(
    val id: String,
    val sender: String,
    val text: String,
    val isUser: Boolean,
    val activeTool: String? = null,
)
