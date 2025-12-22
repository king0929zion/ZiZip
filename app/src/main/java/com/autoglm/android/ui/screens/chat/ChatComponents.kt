package com.autoglm.android.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.autoglm.android.domain.model.ChatMessage
import com.autoglm.android.ui.theme.*

@Composable
fun ChatBubble(message: ChatMessage) {
    val align = if (message.isUser) Alignment.End else Alignment.Start
    val backgroundColor = if (message.isUser) PrimaryBlack else PrimaryWhite
    val contentColor = if (message.isUser) PrimaryWhite else PrimaryBlack
    val shape = if (message.isUser) {
        RoundedCornerShape(20.dp, 4.dp, 20.dp, 20.dp)
    } else {
        RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = align
    ) {
        Box(
            modifier = Modifier
                .background(backgroundColor, shape)
                .padding(16.dp)
                .widthIn(max = 280.dp) // Max width constraint
        ) {
            Text(
                text = message.text,
                style = ZiZipTypography.bodyLarge,
                color = contentColor
            )
        }
    }
}
