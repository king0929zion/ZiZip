package com.autoglm.android.data.model

/**
 * 对话摘要数据类（用于侧边栏历史记录）
 */
data class ConversationSummary(
    val id: String,
    val title: String,
    val lastMessage: String,
    val timestamp: Long
)
