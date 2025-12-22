package com.autoglm.android.ui.screens.providerconfig

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.autoglm.android.data.model.*
import com.autoglm.android.data.repository.ModelConfigRepository
import com.autoglm.android.ui.theme.*

/**
 * 供应商配置页面 - 参考 Auto-GLM-Android 的 provider_config_page.dart
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderConfigScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val repository = remember { ModelConfigRepository.getInstance(context) }
    val providers by repository.providers.collectAsState()
    val models by repository.models.collectAsState()
    val activeModelId by repository.activeModelId.collectAsState()
    
    var showAddProviderDialog by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<ProviderConfig?>(null) }
    var showModelPicker by remember { mutableStateOf(false) }
    
    Scaffold(
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
        },
        containerColor = PrimaryWhite
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // 默认模型卡片
            item {
                DefaultModelCard(
                    activeModel = models.find { it.id == activeModelId },
                    onClick = { showModelPicker = true }
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // 已选模型数量提示
            item {
                val enabledCount = models.count { it.enabled }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Grey50)
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
                            "已启用 $enabledCount 个模型",
                            style = ZiZipTypography.bodySmall,
                            color = Grey600
                        )
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            // 供应商列表
            items(providers) { provider ->
                ProviderCard(
                    provider = provider,
                    modelCount = models.count { it.providerType == provider.type },
                    onClick = { editingProvider = provider }
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
    
    // 添加供应商对话框
    if (showAddProviderDialog) {
        AddProviderDialog(
            onDismiss = { showAddProviderDialog = false },
            onAdd = { name, baseUrl ->
                val newProvider = ProviderConfig(
                    id = "custom_${System.currentTimeMillis()}",
                    name = name,
                    type = ModelProviderType.CUSTOM,
                    baseUrl = baseUrl,
                    apiKey = "",
                    enabled = true,
                    builtIn = false
                )
                repository.addProvider(newProvider)
                showAddProviderDialog = false
            }
        )
    }
    
    // 编辑供应商对话框
    editingProvider?.let { provider ->
        EditProviderDialog(
            provider = provider,
            onDismiss = { editingProvider = null },
            onSave = { updatedProvider ->
                repository.updateProvider(updatedProvider)
                editingProvider = null
            },
            onDelete = if (!provider.builtIn) {
                {
                    repository.deleteProvider(provider.id)
                    editingProvider = null
                }
            } else null
        )
    }
    
    // 模型选择器
    if (showModelPicker) {
        ModelPickerBottomSheet(
            models = models.filter { it.enabled },
            activeModelId = activeModelId,
            onSelect = { model ->
                repository.setActiveModel(model.id)
                showModelPicker = false
            },
            onDismiss = { showModelPicker = false }
        )
    }
}

@Composable
private fun DefaultModelCard(
    activeModel: ModelConfig?,
    onClick: () -> Unit
) {
    val title = activeModel?.displayName ?: "未设置默认模型"
    val subtitle = activeModel?.modelName ?: "点击选择默认模型"
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Grey50)
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
                    title,
                    style = ZiZipTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Grey900,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = ZiZipTypography.labelSmall,
                    color = Grey500,
                    maxLines = 1
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
private fun ProviderCard(
    provider: ProviderConfig,
    modelCount: Int,
    onClick: () -> Unit
) {
    val hasApiKey = provider.apiKey.isNotBlank()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Grey50),
        border = if (hasApiKey) {
            CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(Accent.copy(alpha = 0.3f))
            )
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
                        style = ZiZipTypography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = Grey800
                    )
                    if (!provider.builtIn) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Grey200)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "自定义",
                                style = ZiZipTypography.labelSmall.copy(fontSize = ZiZipTypography.labelSmall.fontSize * 0.85f),
                                color = Grey600
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (hasApiKey) "$modelCount 个模型可用" else "点击配置 API Key",
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
private fun AddProviderDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, baseUrl: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加自定义供应商") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    placeholder = { Text("例如: My Provider") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.example.com/v1") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (name.isNotBlank() && baseUrl.isNotBlank()) {
                        onAdd(name, baseUrl)
                    }
                },
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
private fun EditProviderDialog(
    provider: ProviderConfig,
    onDismiss: () -> Unit,
    onSave: (ProviderConfig) -> Unit,
    onDelete: (() -> Unit)?
) {
    var apiKey by remember { mutableStateOf(provider.apiKey) }
    var baseUrl by remember { mutableStateOf(provider.baseUrl) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(provider.name) },
        text = {
            Column {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (onDelete != null) {
                    Spacer(modifier = Modifier.height(24.dp))
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = ErrorColor)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("删除此供应商")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    onSave(provider.copy(apiKey = apiKey, baseUrl = baseUrl))
                },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerBottomSheet(
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
        containerColor = PrimaryWhite,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                "选择默认模型",
                style = ZiZipTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Grey900,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 搜索框
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜索模型") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Grey500) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Grey500)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Grey200,
                    focusedContainerColor = Grey50,
                    unfocusedContainerColor = Grey50
                )
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (filteredModels.isEmpty()) {
                Text(
                    "未找到匹配的模型",
                    style = ZiZipTypography.bodyMedium,
                    color = Grey500,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredModels) { model ->
                        val isSelected = model.id == activeModelId
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(model) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Grey50 else PrimaryWhite
                            ),
                            border = if (isSelected) {
                                CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(Accent.copy(alpha = 0.3f))
                                )
                            } else {
                                CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(Grey150)
                                )
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        model.displayName,
                                        style = ZiZipTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = Grey900,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        model.modelName,
                                        style = ZiZipTypography.labelSmall,
                                        color = Grey500,
                                        maxLines = 1
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "已选择",
                                        tint = Accent
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = Grey400
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getProviderIcon(type: ModelProviderType): ImageVector {
    return when (type) {
        ModelProviderType.OPENAI -> Icons.Outlined.AutoAwesome
        ModelProviderType.ANTHROPIC -> Icons.Outlined.Science
        ModelProviderType.GEMINI -> Icons.Outlined.Diamond
        ModelProviderType.DEEPSEEK -> Icons.Outlined.WaterDrop
        ModelProviderType.SILICON_FLOW -> Icons.Outlined.Memory
        ModelProviderType.ZHIPU -> Icons.Outlined.Psychology
        ModelProviderType.NVIDIA -> Icons.Outlined.DeveloperBoard
        ModelProviderType.MODEL_SCOPE -> Icons.Outlined.Hub
        ModelProviderType.OPENROUTER -> Icons.Outlined.Router
        ModelProviderType.GROQ -> Icons.Outlined.Speed
        else -> Icons.Outlined.Cloud
    }
}
