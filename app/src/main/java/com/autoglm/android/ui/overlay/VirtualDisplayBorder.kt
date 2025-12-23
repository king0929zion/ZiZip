package com.autoglm.android.ui.overlay

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.autoglm.android.ui.theme.SuccessColor

/**
 * 虚拟屏幕边框指示器
 *
 * 显示一个绿色圆角边框，标识当前正在使用虚拟屏幕进行自动化。
 * 参考 Operit 的 VirtualDisplayOverlay 简化版。
 *
 * @param isActive 是否激活边框
 * @param modifier 修饰符
 * @param strokeWidth 边框宽度
 * @param cornerRadius 圆角半径
 */
@Composable
fun VirtualDisplayBorder(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 4.dp,
    cornerRadius: Dp = 8.dp
) {
    if (!isActive) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithContent {
                drawContent()
                // 绘制绿色圆角边框
                drawRoundRect(
                    color = SuccessColor,
                    style = Stroke(width = strokeWidth.toPx()),
                    cornerRadius = CornerRadius(cornerRadius.toPx())
                )
            }
    )
}

/**
 * 带有动画效果的虚拟屏幕边框
 * 当自动化进行时显示脉动边框效果
 *
 * @param isActive 是否激活边框
 * @param isProcessing 是否正在处理（显示脉动效果）
 * @param modifier 修饰符
 */
@Composable
fun VirtualDisplayBorderAnimated(
    isActive: Boolean,
    isProcessing: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (!isActive) return

    // TODO: 添加脉动动画效果
    // 当前使用静态边框，后续可添加 InfiniteTransition 实现脉动
    VirtualDisplayBorder(
        isActive = true,
        modifier = modifier,
        strokeWidth = if (isProcessing) 6.dp else 4.dp
    )
}

/**
 * 简单的边框指示器（用于调试）
 *
 * @param isActive 是否激活
 * @param label 显示的标签文本
 */
@Composable
fun VirtualDisplayIndicator(
    isActive: Boolean,
    label: String = "Virtual Display",
    modifier: Modifier = Modifier
) {
    if (!isActive) return

    // 简单实现：仅边框
    VirtualDisplayBorder(
        isActive = isActive,
        modifier = modifier
    )
}
