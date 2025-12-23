package com.autoglm.android.ui.screens.model

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.autoglm.android.data.model.*
import com.autoglm.android.ui.theme.*

/**
 * 供应商配置页面 - 复刻 Auto-GLM-Android
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderConfigScreen(
    navController: NavController,
    viewModel: ProviderConfigViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddProviderDialog by remember { mutableStateOf(false) }
    var showDefaultModelPicker by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = PrimaryWhite,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "模型配置",
                        style = ZiZipTypography.titleMedium.copy(fontWeight = FontWeight.Medium)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddProviderDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "添加供应商")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryWhite
                )
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Accent)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 默认模型卡片
                item {
                    DefaultModelCard(
                        model = uiState.activeModel,
                        onClick = { showDefaultModelPicker = true }
                    )
                }
                
                // 已选模型数量提示
                item {
                    SelectedModelCountHint(
                        selectedCount = uiState.selectedModelCount,
                        maxCount = 5
                    )
                }
                
                // 供应商列表
                items(uiState.providers) { provider ->
                    ProviderCard(
                        provider = provider,
                        selectedCount = uiState.getSelectedCountForProvider(provider.type),
                        onClick = {
                            navController.navigate("provider_detail/${provider.type.name}")
                        }
                    )
                }
            }
        }
    }
    
    // 添加供应商对话框
    if (showAddProviderDialog) {
        AddProviderDialog(
            onDismiss = { showAddProviderDialog = false },
            onAdd = { name, baseUrl ->
                viewModel.addCustomProvider(name, baseUrl)
                showAddProviderDialog = false
            }
        )
    }
    
    // 默认模型选择器
    if (showDefaultModelPicker) {
        DefaultModelPickerSheet(
            models = uiState.selectedModels,
            activeModelId = uiState.activeModelId,
            onSelect = { model ->
                viewModel.setActiveModel(model.id)
                showDefaultModelPicker = false
            },
            onDismiss = { showDefaultModelPicker = false }
        )
    }
}

@Composable
private fun DefaultModelCard(
    model: ModelConfig?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Grey50,
        border = androidx.compose.foundation.BorderStroke(1.dp, Grey150)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Grey100),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = Grey700,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "默认模型",
                    style = ZiZipTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Grey500
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    model?.displayName ?: "未设置默认模型",
                    style = ZiZipTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Grey900,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    model?.modelName ?: "点击选择默认模型",
                    style = ZiZipTypography.labelSmall,
                    color = Grey500,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Grey400
            )
        }
    }
}

@Composable
private fun SelectedModelCountHint(
    selectedCount: Int,
    maxCount: Int
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Grey50
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "已选择 $selectedCount/$maxCount 个模型",
                style = ZiZipTypography.bodySmall,
                color = Grey600
            )
        }
    }
}

@Composable
private fun ProviderCard(
    provider: ProviderConfig,
    selectedCount: Int,
    onClick: () -> Unit
) {
    val hasApiKey = provider.apiKey.isNotBlank()
    
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Grey50,
        border = if (hasApiKey) {
            androidx.compose.foundation.BorderStroke(1.dp, Accent.copy(alpha = 0.3f))
        } else null
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
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (hasApiKey) Accent.copy(alpha = 0.1f) else Grey100),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    getProviderIcon(provider.type),
                    contentDescription = null,
                    tint = if (hasApiKey) Accent else Grey400,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(14.dp))
            
            // 信息
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        provider.name,
                        style = ZiZipTypography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = Grey800
                    )
                    if (!provider.builtIn && !provider.type.builtIn) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Grey200
                        ) {
                            Text(
                                "自定义",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = ZiZipTypography.labelSmall,
                                color = Grey600
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (hasApiKey) {
                        "${provider.models.size} 个模型${if (selectedCount > 0) "，已选 $selectedCount 个" else ""}"
                    } else {
                        "点击配置 API Key"
                    },
                    style = ZiZipTypography.bodySmall,
                    color = if (hasApiKey) Grey500 else Accent
                )
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Grey400,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun getProviderIcon(type: ModelProviderType): androidx.compose.ui.graphics.vector.ImageVector {
    return when (type) {
        ModelProviderType.OPENAI -> Icons.Default.AutoAwesome
        ModelProviderType.DEEPSEEK -> Icons.Default.WaterDrop
        ModelProviderType.SILICONFLOW, ModelProviderType.SILICON_FLOW -> Icons.Default.Memory
        ModelProviderType.ZHIPU -> Icons.Default.Psychology
        ModelProviderType.GOOGLE, ModelProviderType.GEMINI -> Icons.Default.Star
        ModelProviderType.ANTHROPIC -> Icons.Default.SmartToy
        ModelProviderType.OPENROUTER -> Icons.Default.Router
        ModelProviderType.GROQ -> Icons.Default.Speed
        ModelProviderType.XAI -> Icons.Default.Code
        ModelProviderType.MODELSCOPE, ModelProviderType.MODEL_SCOPE -> Icons.Default.Hub
        ModelProviderType.NVIDIA -> Icons.Default.DeveloperBoard
        ModelProviderType.MOONSHOT -> Icons.Default.Nightlight
        ModelProviderType.QWEN -> Icons.Default.Chat
        ModelProviderType.STEPFUN -> Icons.Default.Stairs
        ModelProviderType.VOLCENGINE -> Icons.Default.Whatshot
        ModelProviderType.HUNYUAN -> Icons.Default.Cloud
        ModelProviderType.CUSTOM, ModelProviderType.OPENAI_COMPATIBLE -> Icons.Default.Cloud
    }
}

@Composable
private fun AddProviderDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加自定义供应商") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    placeholder = { Text("例如: My Provider") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.example.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(name, baseUrl) },
                enabled = name.isNotBlank() && baseUrl.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Grey600)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultModelPickerSheet(
    models: List<ModelConfig>,
    activeModelId: String?,
    onSelect: (ModelConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredModels = remember(models, searchQuery) {
        if (searchQuery.isBlank()) models
        else models.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) ||
            it.modelName.contains(searchQuery, ignoreCase = true)
        }
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PrimaryWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "选择默认模型",
                style = ZiZipTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 搜索框
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索模型") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, null)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Grey150,
                    focusedContainerColor = Grey50,
                    unfocusedContainerColor = Grey50
                )
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (filteredModels.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "未找到匹配的模型",
                        color = Grey500
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(filteredModels) { model ->
                        val isSelected = model.id == activeModelId
                        
                        Surface(
                            onClick = { onSelect(model) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Grey50 else PrimaryWhite,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) Accent.copy(alpha = 0.3f) else Grey150
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        model.displayName,
                                        style = ZiZipTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = Grey900,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        model.modelName,
                                        style = ZiZipTypography.labelSmall,
                                        color = Grey500,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                
                                Icon(
                                    if (isSelected) Icons.Default.Check else Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = if (isSelected) Accent else Grey400
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
