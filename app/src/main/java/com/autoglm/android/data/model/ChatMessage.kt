package com.autoglm.android.data.model

import java.util.UUID

/**
 * 消息发送者类型
 */
enum class MessageSender {
    USER,
    ASSISTANT
}

/**
 * 消息状态
 */
enum class MessageStatus {
    SENDING,
    SENT,
    FAILED
}

/**
 * 聊天消息数据类
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val sender: MessageSender,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT,
    val thinking: String? = null,
    val actionType: String? = null,
    val isSuccess: Boolean = true
)

/**
 * 聊天会话数据类
 */
data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "新对话",
    val messages: List<ChatMessage> = emptyList(),
    val modelId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdatedAt: Long = System.currentTimeMillis()
) {
    val lastMessage: ChatMessage?
        get() = messages.lastOrNull()
}
