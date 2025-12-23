package com.autoglm.android.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.autoglm.android.data.model.TaskStatus
import com.autoglm.android.ui.components.*
import com.autoglm.android.ui.navigation.Screen
import com.autoglm.android.ui.theme.*
import kotlinx.coroutines.launch

/**
 * 主页面 - 优化后的聊天界面
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
    val historyList by viewModel.historyList.collectAsState()
    
    var inputText by remember { mutableStateOf("") }
    var showModelSelector by remember { mutableStateOf(false) }
    
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    // 自动滚动到底部
    LaunchedEffect(uiState.chatItems.size) {
        if (uiState.chatItems.isNotEmpty()) {
            listState.animateScrollToItem(uiState.chatItems.size - 1)
        }
    }
    
    // 侧边栏抽屉
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Grey100,
                modifier = Modifier.width(300.dp)
            ) {
                HistoryDrawerContent(
                    historyList = historyList,
                    onHistoryItemClick = { conversationId ->
                        viewModel.loadConversation(conversationId)
                        scope.launch { drawerState.close() }
                    },
                    onSettingsClick = {
                        scope.launch { 
                            drawerState.close()
                            navController.navigate(Screen.Settings.route)
                        }
                    },
                    onNewChatClick = {
                        viewModel.startNewConversation()
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            containerColor = Grey100,  // 统一使用米白色背景
            topBar = {
                // 顶部导航栏 - 与对话区统一背景
                HeaderBar(
                    currentModel = viewModel.getActiveModel(),
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onModelSelectorClick = { showModelSelector = true },
                    onNewChatClick = { viewModel.startNewConversation() }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Grey100)  // 统一背景色
            ) {
                // 聊天区域 - 与顶栏统一颜色
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Grey100)  // 统一背景色
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
                
                // 底部输入栏 - 有向下圆角，背景统一
                InputBar(
                    text = inputText,
                    onTextChange = { inputText = it },
                    onSend = {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    },
                    isEnabled = uiState.isInitialized && !uiState.isChatRunning,
                    isAgentRunning = uiState.currentExecution?.isActive == true,
                    onStop = { viewModel.stopTask() },
                    selectedTool = uiState.selectedTool,
                    onToolSelect = { viewModel.selectTool(it) },
                    onImageClick = { /* TODO: 实现图片选择 */ },
                    attachedImages = uiState.attachedImages,
                    onRemoveImage = { viewModel.removeImage(it) }
                )
            }
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
 * 历史记录侧边栏内容
 */
@Composable
private fun HistoryDrawerContent(
    historyList: List<com.autoglm.android.data.model.ConversationSummary>,
    onHistoryItemClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onNewChatClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(vertical = 16.dp)
    ) {
        // 顶部标题
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "历史记录",
                style = ZiZipTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Grey900
            )
            IconButton(onClick = onNewChatClick) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = "新建对话",
                    tint = Grey700
                )
            }
        }
        
        HorizontalDivider(color = Grey150)
        
        // 历史记录列表
        if (historyList.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "暂无对话记录",
                    style = ZiZipTypography.bodyMedium,
                    color = Grey400
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(historyList) { conversation ->
                    HistoryItem(
                        conversation = conversation,
                        onClick = { onHistoryItemClick(conversation.id) }
                    )
                }
            }
        }
        
        HorizontalDivider(color = Grey150)
        
        // 底部设置入口
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSettingsClick() }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = "设置",
                tint = Grey600,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "设置",
                style = ZiZipTypography.bodyLarge,
                color = Grey700
            )
        }
    }
}

/**
 * 历史记录项
 */
@Composable
private fun HistoryItem(
    conversation: com.autoglm.android.data.model.ConversationSummary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.ChatBubbleOutline,
            contentDescription = null,
            tint = Grey500,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                conversation.title,
                style = ZiZipTypography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = Grey800,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                conversation.lastMessage,
                style = ZiZipTypography.labelSmall,
                color = Grey500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 顶部导航栏 - 与对话区统一米白色背景
 */
@Composable
private fun HeaderBar(
    currentModel: com.autoglm.android.data.model.ModelConfig?,
    onMenuClick: () -> Unit,
    onModelSelectorClick: () -> Unit,
    onNewChatClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Grey100)  // 与对话区统一
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .height(48.dp)
    ) {
        // 左侧 - 菜单按钮（汉堡菜单，横线样式）
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "菜单",
                tint = Grey700
            )
        }
        
        // 中间 - 模型选择器（居中）
        ModelSelectorButton(
            currentModel = currentModel,
            onClick = onModelSelectorClick,
            modifier = Modifier.align(Alignment.Center)
        )
        
        // 右侧 - 新建对话按钮
        IconButton(
            onClick = onNewChatClick,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Icon(
                imageVector = Icons.Outlined.Edit,  // 新建对话图标
                contentDescription = "新对话",
                tint = Grey700
            )
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
                fontWeight = FontWeight.Light
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
        colors = CardDefaults.cardColors(containerColor = PrimaryWhite)
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
                        fontWeight = FontWeight.SemiBold
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
 * 底部输入栏 - 白色背景，工具区和输入框一起
 */
@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isEnabled: Boolean,
    isAgentRunning: Boolean,
    onStop: () -> Unit,
    selectedTool: ToolType = ToolType.NONE,
    onToolSelect: (ToolType) -> Unit = {},
    onImageClick: () -> Unit = {},
    attachedImages: List<String> = emptyList(),
    onRemoveImage: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val hasText = text.isNotBlank() || attachedImages.isNotEmpty()
    var showToolSelector by remember { mutableStateOf(false) }
    
    // 整个输入区域容器 - 向下圆角
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(PrimaryWhite)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        // 已选择的图片预览
        if (attachedImages.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                attachedImages.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Grey100)
                    ) {
                        Icon(
                            Icons.Outlined.Image,
                            contentDescription = null,
                            tint = Grey400,
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.Center)
                        )
                        // 删除按钮
                        IconButton(
                            onClick = { onRemoveImage(index) },
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.TopEnd)
                                .background(Grey700.copy(alpha = 0.7f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "删除",
                                tint = PrimaryWhite,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
        
        // 输入框 - 一体化设计，无边框，透明背景
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = isEnabled,
            placeholder = {
                Text(
                    text = when {
                        !isEnabled -> "处理中..."
                        selectedTool == ToolType.AGENT -> "描述你想让 Agent 执行的任务..."
                        else -> "Reply to Claude..."
                    },
                    color = Grey400,
                    style = ZiZipTypography.bodyMedium
                )
            },
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent
            ),
            maxLines = 4,
            textStyle = ZiZipTypography.bodyMedium
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 工具栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧工具
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onImageClick,
                    enabled = isEnabled,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = "添加图片",
                        tint = if (isEnabled) Grey600 else Grey400,
                        modifier = Modifier.size(22.dp)
                    )
                }
                // 工具选择按钮
                Box {
                    IconButton(
                        onClick = { showToolSelector = true },
                        enabled = isEnabled,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (selectedTool == ToolType.AGENT) 
                                Icons.Filled.AutoAwesome else Icons.Outlined.Handyman,
                            contentDescription = "工具",
                            tint = if (selectedTool == ToolType.AGENT) Accent 
                                   else if (isEnabled) Grey600 else Grey400,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    
                    // 工具选择下拉菜单
                    DropdownMenu(
                        expanded = showToolSelector,
                        onDismissRequest = { showToolSelector = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("不使用工具") },
                            onClick = { 
                                onToolSelect(ToolType.NONE)
                                showToolSelector = false
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.ChatBubbleOutline, null)
                            },
                            trailingIcon = {
                                if (selectedTool == ToolType.NONE) {
                                    Icon(Icons.Default.Check, null, tint = Accent)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Agent") },
                            onClick = { 
                                onToolSelect(ToolType.AGENT)
                                showToolSelector = false
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.AutoAwesome, null)
                            },
                            trailingIcon = {
                                if (selectedTool == ToolType.AGENT) {
                                    Icon(Icons.Default.Check, null, tint = Accent)
                                }
                            }
                        )
                    }
                }
                
                // 显示当前选择的工具
                if (selectedTool == ToolType.AGENT) {
                    Surface(
                        color = Accent.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "Agent",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = ZiZipTypography.labelSmall,
                            color = Accent
                        )
                    }
                }
            }
            
            // 发送/停止按钮
            Box(
                modifier = Modifier
                    .size(42.dp)
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
