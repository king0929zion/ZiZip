package com.autoglm.android.ui.overlay

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.autoglm.android.ui.theme.*

/**
 * 虚拟屏幕边框指示器 - 增强版
 *
 * 显示一个动态边框，标识当前正在使用虚拟屏幕进行自动化。
 * 包含脉动动画效果和状态指示器。
 */
@Composable
fun VirtualDisplayBorder(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 3.dp,
    cornerRadius: Dp = 12.dp
) {
    if (!isActive) return

    // Animation for pulsing effect
    val infiniteTransition = rememberInfiniteTransition(label = "border_pulse")

    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "border_alpha"
    )

    val lineWidth by infiniteTransition.animateDp(
        initialValue = strokeWidth,
        targetValue = strokeWidth + 2.dp,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "border_width"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        // Main border
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    drawRoundRect(
                        color = SuccessColor.copy(alpha = borderAlpha),
                        style = Stroke(width = lineWidth.toPx()),
                        cornerRadius = CornerRadius(cornerRadius.toPx())
                    )
                }
        )

        // Corner indicators
        CornerIndicator(modifier = Modifier.align(Alignment.TopStart))
        CornerIndicator(modifier = Modifier.align(Alignment.TopEnd))
        CornerIndicator(modifier = Modifier.align(Alignment.BottomStart))
        CornerIndicator(modifier = Modifier.align(Alignment.BottomEnd))
    }
}

/**
 * 角落指示器组件
 * 显示在边框的四个角落，增加视觉效果
 */
@Composable
private fun CornerIndicator(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "corner_pulse")

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corner_scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corner_alpha"
    )

    Box(
        modifier = modifier
            .padding(12.dp)
            .size(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp * scale)
                .offset(x = (-6).dp * (scale - 1), y = (-6).dp * (scale - 1))
        ) {
            // Outer glow
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        SuccessColor.copy(alpha = 0.3f * alpha),
                        RoundedCornerShape(4.dp)
                    )
            )
            // Inner dot
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .align(Alignment.Center)
                    .background(
                        SuccessColor.copy(alpha = alpha),
                        CircleShape
                    )
            )
        }
    }
}

/**
 * 带有动画效果的虚拟屏幕边框
 * 当自动化进行时显示脉动边框效果
 */
@Composable
fun VirtualDisplayBorderAnimated(
    isActive: Boolean,
    isProcessing: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (!isActive) return

    // Use animated version when processing
    if (isProcessing) {
        VirtualDisplayBorder(
            isActive = true,
            modifier = modifier,
            strokeWidth = 5.dp,
            cornerRadius = 16.dp
        )
    } else {
        VirtualDisplayBorder(
            isActive = true,
            modifier = modifier
        )
    }
}

/**
 * 虚拟屏幕状态指示器
 * 显示在屏幕角落，包含状态文字和图标
 */
@Composable
fun VirtualDisplayStatusIndicator(
    isActive: Boolean,
    statusText: String = "正在控制",
    modifier: Modifier = Modifier
) {
    if (!isActive) return

    val infiniteTransition = rememberInfiniteTransition(label = "status_pulse")

    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "status_dot_alpha"
    )

    Row(
        modifier = modifier
            .padding(16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(SuccessColor.copy(alpha = dotAlpha), CircleShape)
        )

        // Status text
        Text(
            text = statusText,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall
        )

        // Robot icon
        Icon(
            imageVector = Icons.Rounded.SmartToy,
            contentDescription = null,
            tint = SuccessColor,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * 完整的虚拟屏幕覆盖层
 * 包含边框、状态指示器和角落标记
 */
@Composable
fun VirtualDisplayOverlay(
    isActive: Boolean,
    statusText: String = "正在控制",
    showStatus: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        VirtualDisplayBorder(
            isActive = isActive
        )

        if (showStatus) {
            VirtualDisplayStatusIndicator(
                isActive = isActive,
                statusText = statusText,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

/**
 * 简单的边框指示器（用于调试）
 */
@Composable
fun VirtualDisplayIndicator(
    isActive: Boolean,
    label: String = "Virtual Display",
    modifier: Modifier = Modifier
) {
    if (!isActive) return

    VirtualDisplayOverlay(
        isActive = isActive,
        statusText = label,
        showStatus = true,
        modifier = modifier
    )
}
