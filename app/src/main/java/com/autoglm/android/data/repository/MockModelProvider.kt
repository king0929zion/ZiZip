package com.autoglm.android.data.repository

import com.autoglm.android.data.model.ChatMessage
import com.autoglm.android.data.model.MessageSender
import kotlinx.coroutines.delay
import java.util.UUID

class MockModelProvider {

    suspend fun processQuery(query: String): ChatMessage {
        delay(1500) // Simulate network/processing delay
        return ChatMessage(
            id = UUID.randomUUID().toString(),
            content = "I received your request: \"$query\". Since I am a mock agent, I cannot execute real actions yet, but the UI is working!",
            sender = MessageSender.ASSISTANT
        )
    }
}
