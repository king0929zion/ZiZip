package com.autoglm.android.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.autoglm.android.ui.theme.*

/**
 * 工具类型枚举
 */
enum class ToolType(
    val displayName: String,
    val description: String,
    val icon: ImageVector,
    val color: Color
) {
    NONE(
        displayName = "无工具",
        description = "普通对话模式",
        icon = Icons.Outlined.Chat,
        color = Grey400
    ),
    AGENT(
        displayName = "Agent",
        description = "AI 自动执行任务",
        icon = Icons.Outlined.SmartToy,
        color = Color(0xFF6B9B7A)  // Green
    ),
    BUILD_APP(
        displayName = "Build APP",
        description = "生成应用代码",
        icon = Icons.Outlined.PhoneAndroid,
        color = Color(0xFF7B68EE)  // Purple
    ),
    CANVAS(
        displayName = "Canvas",
        description = "协作编辑画布",
        icon = Icons.Outlined.Draw,
        color = Color(0xFFE67E22)  // Orange
    )
}

/**
 * 工具选择按钮
 */
@Composable
fun ToolSelectorButton(
    selectedTool: ToolType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selectedTool == ToolType.NONE) {
        Grey100
    } else {
        selectedTool.color.copy(alpha = 0.15f)
    }
    
    val contentColor = if (selectedTool == ToolType.NONE) {
        Grey700
    } else {
        selectedTool.color
    }
    
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        color = containerColor,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = selectedTool.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            
            if (selectedTool != ToolType.NONE) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = selectedTool.displayName,
                    style = ZiZipTypography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = contentColor
                )
            }
            
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * 工具选择底部弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolSelectorSheet(
    selectedTool: ToolType,
    onToolSelected: (ToolType) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PrimaryWhite,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(40.dp, 4.dp)
                    .background(Grey200, RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "选择工具",
                style = ZiZipTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Grey900,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            ToolType.values().forEach { tool ->
                ToolItem(
                    tool = tool,
                    isSelected = tool == selectedTool,
                    onClick = {
                        onToolSelected(tool)
                        onDismiss()
                    }
                )
                
                if (tool != ToolType.values().last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ToolItem(
    tool: ToolType,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isSelected -> tool.color.copy(alpha = 0.1f)
        else -> Grey50
    }
    
    val borderColor = when {
        isSelected -> tool.color.copy(alpha = 0.3f)
        else -> Color.Transparent
    }
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = backgroundColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tool.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = null,
                    tint = tool.color,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 文字
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tool.displayName,
                    style = ZiZipTypography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = Grey900
                )
                Text(
                    text = tool.description,
                    style = ZiZipTypography.labelSmall,
                    color = Grey400
                )
            }
            
            // 选中指示
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "已选中",
                    tint = tool.color,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * 图片附件按钮
 */
@Composable
fun ImageAttachButton(
    hasImages: Boolean,
    imageCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable { onClick() }
            .background(if (hasImages) Accent.copy(alpha = 0.1f) else Grey100)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (hasImages) Icons.Filled.Image else Icons.Outlined.Image,
            contentDescription = "添加图片",
            tint = if (hasImages) Accent else Grey400,
            modifier = Modifier.size(22.dp)
        )
        
        // 图片计数气泡
        if (hasImages && imageCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(16.dp)
                    .background(Accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = imageCount.toString(),
                    style = ZiZipTypography.labelSmall,
                    color = PrimaryWhite
                )
            }
        }
    }
}

/**
 * 附件预览条
 */
@Composable
fun AttachmentPreviewBar(
    attachedImages: List<String>,  // URI strings
    onRemoveImage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (attachedImages.isEmpty()) return
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        attachedImages.forEachIndexed { index, _ ->
            AttachmentThumbnail(
                index = index,
                onRemove = { onRemoveImage(index) }
            )
        }
    }
}

@Composable
private fun AttachmentThumbnail(
    index: Int,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Grey100)
    ) {
        // 图片占位符
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = null,
                tint = Grey400,
                modifier = Modifier.size(24.dp)
            )
        }
        
        // 删除按钮
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .offset(x = 4.dp, y = (-4).dp)
                .background(ErrorColor, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "移除",
                tint = PrimaryWhite,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
