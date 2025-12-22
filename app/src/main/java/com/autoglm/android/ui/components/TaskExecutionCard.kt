package com.autoglm.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.autoglm.android.data.model.TaskExecution
import com.autoglm.android.data.model.TaskStatus
import com.autoglm.android.ui.theme.*

/**
 * 任务执行状态卡片
 * 用于在聊天界面中显示 Agent 任务的执行状态
 */
@Composable
fun TaskExecutionCard(
    execution: TaskExecution,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onStop: () -> Unit = {}
) {
    val statusText = when (execution.status) {
        TaskStatus.PENDING -> "准备中"
        TaskStatus.RUNNING -> "执行中"
        TaskStatus.PAUSED -> "已暂停"
        TaskStatus.WAITING_CONFIRMATION -> "等待确认"
        TaskStatus.WAITING_TAKEOVER -> "需要接管"
        TaskStatus.COMPLETED -> "已完成"
        TaskStatus.FAILED -> "执行失败"
        TaskStatus.CANCELLED -> "已取消"
    }
    
    val statusColor = when (execution.status) {
        TaskStatus.RUNNING -> SuccessColor
        TaskStatus.PAUSED -> Accent
        TaskStatus.COMPLETED -> SuccessColor
        TaskStatus.FAILED, TaskStatus.CANCELLED -> ErrorColor
        else -> Grey700
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onTap() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Grey50),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 状态行
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 状态指示点
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                
                // 状态文字
                Text(
                    text = statusText,
                    style = ZiZipTypography.titleMedium.copy(fontSize = 14.sp),
                    color = Grey900
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                // 操作按钮
                if (execution.isActive) {
                    if (execution.status == TaskStatus.RUNNING) {
                        IconButton(
                            onClick = onPause,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "暂停",
                                tint = Grey700,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else if (execution.status == TaskStatus.PAUSED) {
                        IconButton(
                            onClick = onResume,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "继续",
                                tint = Grey700,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    IconButton(
                        onClick = onStop,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "停止",
                            tint = ErrorColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                // 展开箭头
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "查看详情",
                    tint = Grey400
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 任务描述
            Text(
                text = execution.taskDescription,
                style = ZiZipTypography.bodyMedium,
                color = Grey700,
                maxLines = 2
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 进度信息
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${execution.currentStep} 步",
                    style = ZiZipTypography.labelSmall,
                    color = Grey400
                )
                Text(
                    text = " · ",
                    style = ZiZipTypography.labelSmall,
                    color = Grey400
                )
                Text(
                    text = execution.formattedDuration,
                    style = ZiZipTypography.labelSmall,
                    color = Grey400
                )
                
                if (execution.isActive) {
                    Text(
                        text = " · 点击查看详情",
                        style = ZiZipTypography.labelSmall,
                        color = Accent
                    )
                }
            }
            
            // 最新动作
            execution.actions.lastOrNull()?.let { lastAction ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (lastAction.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (lastAction.isSuccess) SuccessColor else ErrorColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = lastAction.description,
                        style = ZiZipTypography.labelSmall,
                        color = Grey700,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// 扩展 sp 到 Compose 的 TextUnit
private val Int.sp: androidx.compose.ui.unit.TextUnit
    get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
