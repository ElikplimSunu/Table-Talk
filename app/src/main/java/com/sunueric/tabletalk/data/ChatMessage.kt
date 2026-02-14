package com.sunueric.tabletalk.data

import java.util.UUID

sealed class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis()
) {
    data class UserMessage(val content: String) : ChatMessage()
    data class AgentMessage(val content: String) : ChatMessage()
    data class TableMessage(
        val headers: List<String>,
        val rows: List<List<String>>, // First 10 rows for preview
        val totalRows: Int
    ) : ChatMessage()
    data class ErrorMessage(val content: String) : ChatMessage()
    object LoadingMessage : ChatMessage()
    object ThinkingMessage : ChatMessage() // Optional specific state for "Agent is thinking..."
}
