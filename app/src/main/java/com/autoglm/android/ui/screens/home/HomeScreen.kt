package com.autoglm.android.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.autoglm.android.data.model.TaskStatus
import com.autoglm.android.ui.components.*
import com.autoglm.android.ui.navigation.Screen
import com.autoglm.android.ui.theme.*

/**
 * 主页面 - Gemini 风格聊天界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val models by viewModel.models.collectAsState()
    val activeModelId by viewModel.activeModelId.collectAsState()
    
    var inputText by remember { mutableStateOf("") }
    var showModelSelector by remember { mutableStateOf(false) }
    
    val listState = rememberLazyListState()
    
    // 自动滚动到底部
    LaunchedEffect(uiState.chatItems.size) {
        if (uiState.chatItems.isNotEmpty()) {
            listState.animateScrollToItem(uiState.chatItems.size - 1)
        }
    }
    
    Scaffold(
        containerColor = Grey100
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 顶部导航栏
            HeaderBar(
                currentModel = viewModel.getActiveModel(),
                onHistoryClick = { navController.navigate(Screen.History.route) },
                onModelSelectorClick = { showModelSelector = true },
                onNewChatClick = { viewModel.startNewConversation() },
                onSettingsClick = { navController.navigate(Screen.Settings.route) }
            )
            
            // 聊天区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(PrimaryWhite)
            ) {
                if (uiState.chatItems.isEmpty()) {
                    // 空状态
                    EmptyState(greeting = viewModel.getGreeting())
                } else {
                    // 聊天列表
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = 20.dp,
                            vertical = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Agent Banner
                        uiState.currentExecution?.let { execution ->
                            if (execution.isActive) {
                                item {
                                    AgentBanner(
                                        execution = execution,
                                        onPause = { viewModel.pauseTask() },
                                        onResume = { viewModel.resumeTask() },
                                        onStop = { viewModel.stopTask() },
                                        onClick = { /* TODO: 打开虚拟屏幕预览 */ }
                                    )
                                }
                            }
                        }
                        
                        items(uiState.chatItems, key = { it.id }) { item ->
                            ChatItemView(
                                item = item,
                                onTaskTap = { /* TODO: 打开任务详情 */ },
                                onPause = { viewModel.pauseTask() },
                                onResume = { viewModel.resumeTask() },
                                onStop = { viewModel.stopTask() }
                            )
                        }
                    }
                }
            }
            
            // 底部输入栏
            InputBar(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    viewModel.sendMessage(inputText)
                    inputText = ""
                },
                isEnabled = uiState.isInitialized && !uiState.isChatRunning,
                isAgentRunning = uiState.currentExecution?.isActive == true,
                onStop = { viewModel.stopTask() }
            )
        }
    }
    
    // 模型选择器底部弹窗
    if (showModelSelector) {
        ModelSelectorSheet(
            models = models.filter { it.enabled },
            currentModelId = activeModelId,
            onModelSelected = { model ->
                viewModel.setActiveModel(model.id)
            },
            onDismiss = { showModelSelector = false }
        )
    }
}

/**
 * 顶部导航栏
 */
@Composable
private fun HeaderBar(
    currentModel: com.autoglm.android.data.model.ModelConfig?,
    onHistoryClick: () -> Unit,
    onModelSelectorClick: () -> Unit,
    onNewChatClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Grey100)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(44.dp)
    ) {
        // 左侧 - 历史记录
        IconButton(
            onClick = onHistoryClick,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = "历史记录",
                tint = Grey700
            )
        }
        
        // 中间 - 模型选择器
        ModelSelectorButton(
            currentModel = currentModel,
            onClick = onModelSelectorClick,
            modifier = Modifier.align(Alignment.Center)
        )
        
        // 右侧 - 新对话 + 设置
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onNewChatClick) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "新对话",
                    tint = Grey700
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "设置",
                    tint = Grey700
                )
            }
        }
    }
}

/**
 * 空状态
 */
@Composable
private fun EmptyState(
    greeting: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = greeting,
            style = ZiZipTypography.headlineMedium.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.Light
            ),
            color = Grey700,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Agent 状态横幅
 */
@Composable
private fun AgentBanner(
    execution: com.autoglm.android.data.model.TaskExecution,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusText = when (execution.status) {
        TaskStatus.RUNNING -> "Agent 执行中"
        TaskStatus.PAUSED -> "Agent 已暂停"
        TaskStatus.WAITING_CONFIRMATION -> "等待确认"
        TaskStatus.WAITING_TAKEOVER -> "需要接管"
        else -> "任务中"
    }
    
    val statusColor = when (execution.status) {
        TaskStatus.RUNNING -> SuccessColor
        TaskStatus.PAUSED -> Accent
        else -> Grey700
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Grey50)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态指示点
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            
            Spacer(modifier = Modifier.width(10.dp))
            
            // 状态信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = statusText,
                    style = ZiZipTypography.bodyMedium.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    ),
                    color = Grey900
                )
                Text(
                    text = "${execution.currentStep} 步 · ${execution.formattedDuration} · 点击查看虚拟屏幕",
                    style = ZiZipTypography.labelSmall,
                    color = Grey400
                )
            }
            
            // 控制按钮
            if (execution.status == TaskStatus.RUNNING) {
                IconButton(onClick = onPause, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Pause, "暂停", tint = Grey700, modifier = Modifier.size(20.dp))
                }
            } else if (execution.status == TaskStatus.PAUSED) {
                IconButton(onClick = onResume, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.PlayArrow, "继续", tint = Grey700, modifier = Modifier.size(20.dp))
                }
            }
            
            IconButton(onClick = onStop, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Stop, "停止", tint = ErrorColor, modifier = Modifier.size(20.dp))
            }
            
            Icon(Icons.Default.ChevronRight, "展开", tint = Grey400)
        }
    }
}

/**
 * 聊天项视图
 */
@Composable
private fun ChatItemView(
    item: ChatItem,
    onTaskTap: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    when {
        item.isLoading -> {
            LoadingBubble()
        }
        item.isUser -> {
            UserBubble(message = item.message ?: "")
        }
        item.execution != null -> {
            TaskExecutionCard(
                execution = item.execution,
                onTap = onTaskTap,
                onPause = onPause,
                onResume = onResume,
                onStop = onStop
            )
        }
        else -> {
            AssistantBubble(
                message = item.message,
                thinking = item.thinking,
                actionType = item.actionType,
                isSuccess = item.isSuccess
            )
        }
    }
}

/**
 * 底部输入栏
 */
@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isEnabled: Boolean,
    isAgentRunning: Boolean,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasText = text.isNotBlank()
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Grey100)
            .padding(16.dp)
    ) {
        // 输入框
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = isEnabled,
            placeholder = {
                Text(
                    text = if (!isEnabled) "处理中..." else "输入消息...",
                    color = Grey400
                )
            },
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Grey200,
                unfocusedBorderColor = Grey200,
                focusedContainerColor = Grey100,
                unfocusedContainerColor = Grey100
            ),
            maxLines = 4
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 工具栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧工具
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { /* TODO: 添加图片 */ },
                    enabled = isEnabled
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = "添加图片",
                        tint = if (isEnabled) Grey700 else Grey400
                    )
                }
                IconButton(
                    onClick = { /* TODO: 工具选择 */ },
                    enabled = isEnabled
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Handyman,
                        contentDescription = "工具",
                        tint = if (isEnabled) Grey700 else Grey400
                    )
                }
            }
            
            // 发送/停止按钮
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isAgentRunning -> ErrorColor.copy(alpha = 0.15f)
                            hasText && isEnabled -> Accent
                            else -> Grey200
                        }
                    )
                    .clickable(enabled = isAgentRunning || (hasText && isEnabled)) {
                        if (isAgentRunning) onStop() else onSend()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isAgentRunning) Icons.Default.Stop else Icons.Default.ArrowUpward,
                    contentDescription = if (isAgentRunning) "停止" else "发送",
                    tint = when {
                        isAgentRunning -> ErrorColor
                        hasText && isEnabled -> PrimaryWhite
                        else -> Grey400
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
