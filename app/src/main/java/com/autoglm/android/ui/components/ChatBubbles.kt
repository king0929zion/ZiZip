package com.autoglm.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.autoglm.android.ui.theme.*

/**
 * 用户消息气泡
 */
@Composable
fun UserBubble(
    message: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                .background(PrimaryBlack)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = message,
                style = ZiZipTypography.bodyMedium,
                color = PrimaryWhite
            )
        }
    }
}

/**
 * 助手消息气泡
 */
@Composable
fun AssistantBubble(
    message: String?,
    modifier: Modifier = Modifier,
    thinking: String? = null,
    actionType: String? = null,
    isSuccess: Boolean = true,
    thinkingLabel: String = "思考中"
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        // 思考过程（可展开）
        if (!thinking.isNullOrBlank()) {
            var showThinking by remember { mutableStateOf(false) }
            
            TextButton(
                onClick = { showThinking = !showThinking },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (showThinking) "▼ $thinkingLabel" else "▶ $thinkingLabel",
                    style = ZiZipTypography.labelSmall,
                    color = Grey400
                )
            }
            
            AnimatedVisibility(
                visible = showThinking,
                enter = fadeIn() + expandVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Grey50)
                        .padding(12.dp)
                ) {
                    Text(
                        text = thinking,
                        style = ZiZipTypography.labelSmall,
                        color = Grey700
                    )
                }
            }
        }
        
        // 动作标签
        if (!actionType.isNullOrBlank()) {
            Row(
                modifier = Modifier.padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (isSuccess) SuccessColor else ErrorColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = actionType,
                    style = ZiZipTypography.labelSmall,
                    color = if (isSuccess) SuccessColor else ErrorColor
                )
            }
        }
        
        // 消息内容
        if (!message.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                    .background(Grey100)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = message,
                    style = ZiZipTypography.bodyMedium,
                    color = Grey900
                )
            }
        }
    }
}

/**
 * 加载中气泡
 */
@Composable
fun LoadingBubble(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                .background(Grey100)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Grey400)
                    )
                }
            }
        }
    }
}
