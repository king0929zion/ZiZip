package com.autoglm.android.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoglm.android.data.model.ChatMessage
import com.autoglm.android.data.model.MessageSender
import com.autoglm.android.data.model.MessageStatus
import com.autoglm.android.data.repository.MockModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val modelProvider = MockModelProvider()

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            content = text,
            sender = MessageSender.USER
        )

        _messages.value = _messages.value + userMsg

        viewModelScope.launch {
            val response = modelProvider.processQuery(text)
            _messages.value = _messages.value + response
        }
    }
}
