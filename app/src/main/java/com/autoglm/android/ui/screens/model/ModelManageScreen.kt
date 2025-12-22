package com.autoglm.android.ui.screens.model

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.autoglm.android.data.model.*
import com.autoglm.android.ui.theme.*

/**
 * 模型管理页面
 * 支持搜索、启用/禁用模型、配置 API Key
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManageScreen(
    navController: NavController,
    viewModel: ModelManageViewModel = viewModel()
) {
    val providers by viewModel.providers.collectAsState()
    val models by viewModel.models.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedProviderId by viewModel.selectedProviderId.collectAsState()
    
    var showAddModelDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf<ProviderConfig?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型管理", style = ZiZipTypography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddModelDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "添加模型")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Grey100)
            )
        },
        containerColor = Grey100
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 搜索栏
            SearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            // 供应商筛选器
            ProviderFilter(
                providers = providers,
                selectedProviderId = selectedProviderId,
                onProviderSelected = { viewModel.setSelectedProvider(it) },
                onConfigureApiKey = { showApiKeyDialog = it }
            )
            
            // 模型列表
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filteredModels = models.filter { model ->
                    (searchQuery.isBlank() || 
                     model.displayName.contains(searchQuery, ignoreCase = true) ||
                     model.modelName.contains(searchQuery, ignoreCase = true)) &&
                    (selectedProviderId == null || model.providerType.name == selectedProviderId)
                }
                
                if (filteredModels.isEmpty()) {
                    item {
                        EmptyModelState()
                    }
                } else {
                    items(filteredModels, key = { it.id }) { model ->
                        ModelCard(
                            model = model,
                            onToggleEnabled = { viewModel.toggleModelEnabled(model.id) },
                            onEdit = { /* TODO: 编辑模型 */ },
                            onDelete = { viewModel.deleteModel(model.id) }
                        )
                    }
                }
            }
        }
    }
    
    // API Key 配置对话框
    showApiKeyDialog?.let { provider ->
        ApiKeyDialog(
            provider = provider,
            onDismiss = { showApiKeyDialog = null },
            onSave = { apiKey ->
                viewModel.updateProviderApiKey(provider.id, apiKey)
                showApiKeyDialog = null
            }
        )
    }
    
    // 添加模型对话框
    if (showAddModelDialog) {
        AddModelDialog(
            providers = providers,
            onDismiss = { showAddModelDialog = false },
            onAdd = { model ->
                viewModel.addModel(model)
                showAddModelDialog = false
            }
        )
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("搜索模型...", color = Grey400) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = Grey500)
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "清除", tint = Grey500)
                }
            }
        },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent,
            unfocusedBorderColor = Grey200,
            focusedContainerColor = PrimaryWhite,
            unfocusedContainerColor = PrimaryWhite
        ),
        singleLine = true
    )
}

@Composable
private fun ProviderFilter(
    providers: List<ProviderConfig>,
    selectedProviderId: String?,
    onProviderSelected: (String?) -> Unit,
    onConfigureApiKey: (ProviderConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedProviderId == null,
                onClick = { onProviderSelected(null) },
                label = { Text("全部") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Accent,
                    selectedLabelColor = PrimaryWhite
                )
            )
        }
        items(providers.size) { index ->
            val provider = providers[index]
            FilterChip(
                selected = selectedProviderId == provider.type.name,
                onClick = { onProviderSelected(provider.type.name) },
                label = { Text(provider.name) },
                trailingIcon = {
                    if (provider.apiKey.isBlank()) {
                        Icon(
                            Icons.Outlined.Key,
                            contentDescription = "需要配置 API Key",
                            tint = WarningColor,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onConfigureApiKey(provider) }
                        )
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Accent,
                    selectedLabelColor = PrimaryWhite
                )
            )
        }
    }
}

@Composable
private fun ModelCard(
    model: ModelConfig,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = PrimaryWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 模型图标
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (model.enabled) Accent.copy(alpha = 0.1f) else Grey100),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (model.purpose) {
                        ModelPurpose.AGENT -> Icons.Outlined.SmartToy
                        ModelPurpose.OCR -> Icons.Outlined.DocumentScanner
                        ModelPurpose.EMBEDDING -> Icons.Outlined.DataArray
                        else -> Icons.Outlined.Chat
                    },
                    contentDescription = null,
                    tint = if (model.enabled) Accent else Grey400,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 模型信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.displayName,
                    style = ZiZipTypography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = Grey900,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = model.modelName,
                    style = ZiZipTypography.labelSmall,
                    color = Grey500,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // 标签
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ModelTag(text = model.providerType.displayName)
                    if (model.supportsVision) {
                        ModelTag(text = "视觉", color = SuccessColor)
                    }
                    if (model.supportsTool) {
                        ModelTag(text = "工具", color = Accent)
                    }
                }
            }
            
            // 启用开关
            Switch(
                checked = model.enabled,
                onCheckedChange = { onToggleEnabled() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PrimaryWhite,
                    checkedTrackColor = Accent,
                    uncheckedThumbColor = Grey400,
                    uncheckedTrackColor = Grey200
                )
            )
        }
    }
}

@Composable
private fun ModelTag(
    text: String,
    color: Color = Grey500,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = ZiZipTypography.labelSmall,
        color = color,
        modifier = modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun EmptyModelState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.SearchOff,
                contentDescription = null,
                tint = Grey400,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "未找到模型",
                style = ZiZipTypography.bodyLarge,
                color = Grey500
            )
        }
    }
}

@Composable
private fun ApiKeyDialog(
    provider: ProviderConfig,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var apiKey by remember { mutableStateOf(provider.apiKey) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("配置 ${provider.name} API Key") },
        text = {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                placeholder = { Text("请输入 API Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(apiKey) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun AddModelDialog(
    providers: List<ProviderConfig>,
    onDismiss: () -> Unit,
    onAdd: (ModelConfig) -> Unit
) {
    var displayName by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }
    var selectedProvider by remember { mutableStateOf(providers.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加模型") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("显示名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("模型 ID") },
                    placeholder = { Text("如 gpt-4o, claude-3-5-sonnet") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // 供应商选择
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedProvider?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("供应商") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        providers.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.name) },
                                onClick = {
                                    selectedProvider = provider
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (displayName.isNotBlank() && modelName.isNotBlank() && selectedProvider != null) {
                        onAdd(
                            ModelConfig(
                                displayName = displayName,
                                modelName = modelName,
                                providerType = selectedProvider!!.type,
                                baseUrl = selectedProvider!!.baseUrl
                            )
                        )
                    }
                },
                enabled = displayName.isNotBlank() && modelName.isNotBlank()
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
