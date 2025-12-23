package com.autoglm.android.ui.screens.model

import androidx.compose.foundation.background
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
import androidx.navigation.NavController
import com.autoglm.android.data.model.*
import com.autoglm.android.data.repository.ModelConfigRepository
import com.autoglm.android.ui.theme.*
import kotlinx.coroutines.launch

/**
 * 供应商详情页面 - 配置 API Key 和选择模型
 * 复刻 Auto-GLM-Android 的 provider_detail_page.dart
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderDetailScreen(
    navController: NavController,
    providerType: String
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val modelRepo = remember { ModelConfigRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    
    var provider by remember { mutableStateOf<ProviderConfig?>(null) }
    var apiKey by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var isFetching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showAddModelDialog by remember { mutableStateOf(false) }
    var modelToEdit by remember { mutableStateOf<ModelConfig?>(null) }
    var modelToDelete by remember { mutableStateOf<ModelConfig?>(null) }
    
    var selectedModelIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var activeModelId by remember { mutableStateOf<String?>(null) }
    
    // 加载状态
    LaunchedEffect(Unit) {
        selectedModelIds = modelRepo.selectedModelIds.value
        activeModelId = modelRepo.activeModelId.value
    }
    
    // 加载供应商
    LaunchedEffect(providerType) {
        val type = ModelProviderType.valueOf(providerType)
        provider = modelRepo.providers.value.find { it.type == type }
        apiKey = provider?.apiKey ?: ""
    }
    
    // 刷新供应商
    fun refreshProvider() {
        val type = ModelProviderType.valueOf(providerType)
        provider = modelRepo.providers.value.find { it.type == type }
    }
    
    // 过滤模型
    val filteredModels = remember(provider, searchQuery) {
        val models = provider?.models ?: emptyList()
        if (searchQuery.isBlank()) models
        else models.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) ||
            it.modelName.contains(searchQuery, ignoreCase = true)
        }
    }
    
    Scaffold(
        containerColor = PrimaryWhite,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        provider?.name ?: "加载中...",
                        style = ZiZipTypography.titleMedium.copy(fontWeight = FontWeight.Medium)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddModelDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "添加模型")
                    }
                    if (provider?.builtIn == false && provider?.type?.builtIn == false) {
                        IconButton(onClick = { /* TODO: 删除供应商 */ }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = ErrorColor)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryWhite)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // API Key 配置
            item {
                SectionTitle("API Key")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    placeholder = { Text("输入 API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Grey150,
                        focusedContainerColor = Grey50,
                        unfocusedContainerColor = Grey50
                    ),
                    trailingIcon = {
                        IconButton(onClick = {
                            scope.launch {
                                val p = provider
                                if (p == null) return@launch

                                val newProviders = modelRepo.providers.value.map { prov ->
                                    if (prov.type == p.type) prov.copy(apiKey = apiKey) else prov
                                }
                                // Save via updateProvider
                                val providerToUpdate = newProviders.find { it.type == p.type }
                                if (providerToUpdate != null) {
                                    modelRepo.updateProvider(providerToUpdate)
                                    refreshProvider()
                                }
                            }
                        }) {
                            Icon(Icons.Default.Save, contentDescription = "保存", tint = Accent)
                        }
                    }
                )
            }
            
            // 获取模型按钮
            item {
                SectionTitle("可用模型")
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (apiKey.isNotBlank()) {
                            scope.launch {
                                isFetching = true
                                errorMessage = null
                                try {
                                    val p = provider ?: run {
                                        errorMessage = "供应商未加载"
                                        return@launch
                                    }
                                    val newProviders = modelRepo.providers.value.map { prov ->
                                        if (prov.type == p.type) prov.copy(apiKey = apiKey) else prov
                                    }
                                    val providerToUpdate = newProviders.find { it.type == p.type }
                                    if (providerToUpdate != null) {
                                        modelRepo.updateProvider(providerToUpdate)
                                    }
                                    // Show message instead of fetching
                                    errorMessage = "请手动添加模型（获取模型列表功能尚未实现）"
                                    refreshProvider()
                                } catch (e: Exception) {
                                    errorMessage = e.message ?: "获取失败"
                                } finally {
                                    isFetching = false
                                }
                            }
                        } else {
                            errorMessage = "请先输入 API Key"
                        }
                    },
                    enabled = !isFetching,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    if (isFetching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = PrimaryWhite,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("获取中...")
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("获取模型列表")
                    }
                }
                
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        errorMessage!!,
                        style = ZiZipTypography.bodySmall,
                        color = ErrorColor
                    )
                }
            }
            
            // 搜索模型
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索模型（名称或 Model ID）") },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Grey500) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, null, tint = Grey500)
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
            }
            
            // 模型列表
            if (filteredModels.isEmpty()) {
                item {
                    EmptyModelHint(
                        hasModels = (provider?.models?.size ?: 0) > 0
                    )
                }
            } else {
                items(filteredModels) { model ->
                    ModelItem(
                        model = model,
                        isSelected = model.id in selectedModelIds,
                        isDefault = model.id == activeModelId,
                        onToggle = {
                            scope.launch {
                                val currentIds = selectedModelIds.toMutableSet()
                                if (model.id in currentIds) {
                                    currentIds.remove(model.id)
                                } else if (currentIds.size < 5) {
                                    currentIds.add(model.id)
                                }
                                selectedModelIds = currentIds
                            }
                        },
                        onSetDefault = {
                            scope.launch {
                                val currentIds = selectedModelIds.toMutableSet()
                                if (model.id !in currentIds && currentIds.size < 5) {
                                    currentIds.add(model.id)
                                    selectedModelIds = currentIds
                                }
                                modelRepo.setActiveModel(model.id)
                                activeModelId = model.id
                            }
                        },
                        onEdit = { modelToEdit = model },
                        onDelete = { modelToDelete = model }
                    )
                }
            }
            
            // 选择限制提示
            item {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Grey50
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = Grey500,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "最多可选择 5 个模型，已选 ${selectedModelIds.size} 个",
                            style = ZiZipTypography.bodySmall,
                            color = Grey600
                        )
                    }
                }
            }
        }
    }
    
    // 添加模型对话框
    if (showAddModelDialog) {
        AddModelDialog(
            onDismiss = { showAddModelDialog = false },
            onAdd = { modelId, displayName ->
                scope.launch {
                    provider?.let { p ->
                        val newModel = ModelConfig(
                            displayName = displayName ?: modelId.substringAfterLast("/"),
                            modelName = modelId,
                            providerType = p.type
                        )
                        modelRepo.addModel(newModel)
                        refreshProvider()
                    }
                }
                showAddModelDialog = false
            }
        )
    }
    
    // 编辑模型对话框
    modelToEdit?.let { model ->
        EditModelDialog(
            model = model,
            onDismiss = { modelToEdit = null },
            onSave = { modelId, displayName ->
                    val updatedModel = model.copy(
                        modelName = modelId,
                        displayName = displayName ?: modelId.substringAfterLast("/")
                    )
                    modelRepo.updateModel(updatedModel)
                    refreshProvider()
                modelToEdit = null
            }
        )
    }
    
    // 删除确认对话框
    modelToDelete?.let { model ->
        AlertDialog(
            onDismissRequest = { modelToDelete = null },
            title = { Text("删除模型") },
            text = { Text("确定要删除 ${model.displayName} 吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            provider?.let {
                                modelRepo.deleteModel(model.id)
                                refreshProvider()
                            }
                        }
                        modelToDelete = null
                    }
                ) {
                    Text("删除", color = ErrorColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { modelToDelete = null }) {
                    Text("取消", color = Grey600)
                }
            }
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = ZiZipTypography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = Grey600
    )
}

@Composable
private fun EmptyModelHint(hasModels: Boolean) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Grey50
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = Grey300,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (hasModels) "未找到匹配的模型" else "暂无模型",
                style = ZiZipTypography.bodyMedium,
                color = Grey500
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (hasModels) "换个关键词试试" else "请先配置 API Key 并获取模型列表",
                style = ZiZipTypography.bodySmall,
                color = Grey400
            )
        }
    }
}

@Composable
private fun ModelItem(
    model: ModelConfig,
    isSelected: Boolean,
    isDefault: Boolean,
    onToggle: () -> Unit,
    onSetDefault: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) Accent.copy(alpha = 0.05f) else Grey50,
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(1.dp, Accent.copy(alpha = 0.3f))
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = Accent,
                    uncheckedColor = Grey400
                )
            )
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    model.displayName,
                    style = ZiZipTypography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (isSelected) Accent else Grey800,
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
            
            IconButton(onClick = onSetDefault, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (isDefault) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "设为默认",
                    tint = if (isDefault) Accent else Grey600,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "编辑",
                    tint = Grey600,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除",
                    tint = Grey600,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun AddModelDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String?) -> Unit
) {
    var modelId by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加模型") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text("Model ID") },
                    placeholder = { Text("例如：gpt-4o") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("显示名称（可选）") },
                    placeholder = { Text("不填则自动从 modelId 生成") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(modelId, displayName.takeIf { it.isNotBlank() }) },
                enabled = modelId.isNotBlank(),
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

@Composable
private fun EditModelDialog(
    model: ModelConfig,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit
) {
    var modelId by remember { mutableStateOf(model.modelName) }
    var displayName by remember { mutableStateOf(model.displayName) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑模型") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text("Model ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("显示名称（可选）") },
                    placeholder = { Text("不填则自动从 modelId 生成") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(modelId, displayName.takeIf { it.isNotBlank() }) },
                enabled = modelId.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Grey600)
            }
        }
    )
}
