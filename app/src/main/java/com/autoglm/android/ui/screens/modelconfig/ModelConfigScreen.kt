package com.autoglm.android.ui.screens.modelconfig

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.autoglm.android.data.model.*
import com.autoglm.android.data.repository.ModelConfigRepository
import com.autoglm.android.ui.theme.*
import java.util.UUID

/**
 * 模型配置页面 - 参考 rikkahub 设计
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelConfigScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val repository = remember { ModelConfigRepository.getInstance(context) }
    val providers by repository.providers.collectAsState()
    val models by repository.models.collectAsState()
    
    var selectedTab by remember { mutableStateOf(0) }
    var showAddProviderDialog by remember { mutableStateOf(false) }
    var showAddModelDialog by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<ProviderConfig?>(null) }
    var editingModel by remember { mutableStateOf<ModelConfig?>(null) }
    
    val tabs = listOf("供应商", "模型")
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型配置", style = ZiZipTypography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (selectedTab == 0) showAddProviderDialog = true
                        else showAddModelDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "添加")
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
            // Tab 选择
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Grey100,
                contentColor = PrimaryBlack
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }
            
            when (selectedTab) {
                0 -> ProviderList(
                    providers = providers,
                    onToggleEnabled = { provider ->
                        repository.updateProvider(provider.copy(enabled = !provider.enabled))
                    },
                    onEdit = { editingProvider = it },
                    onDelete = { repository.deleteProvider(it.id) }
                )
                1 -> ModelList(
                    models = models,
                    onToggleEnabled = { model ->
                        repository.updateModel(model.copy(enabled = !model.enabled))
                    },
                    onEdit = { editingModel = it },
                    onDelete = { repository.deleteModel(it.id) }
                )
            }
        }
    }
    
    // 添加供应商对话框
    if (showAddProviderDialog) {
        AddProviderDialog(
            onDismiss = { showAddProviderDialog = false },
            onConfirm = { provider ->
                repository.addProvider(provider)
                showAddProviderDialog = false
            }
        )
    }
    
    // 添加模型对话框
    if (showAddModelDialog) {
        AddModelDialog(
            providers = providers.filter { it.enabled },
            onDismiss = { showAddModelDialog = false },
            onConfirm = { model ->
                repository.addModel(model)
                showAddModelDialog = false
            }
        )
    }
    
    // 编辑供应商
    editingProvider?.let { provider ->
        EditProviderDialog(
            provider = provider,
            onDismiss = { editingProvider = null },
            onConfirm = { updated ->
                repository.updateProvider(updated)
                editingProvider = null
            }
        )
    }
    
    // 编辑模型
    editingModel?.let { model ->
        EditModelDialog(
            model = model,
            providers = providers.filter { it.enabled },
            onDismiss = { editingModel = null },
            onConfirm = { updated ->
                repository.updateModel(updated)
                editingModel = null
            }
        )
    }
}

@Composable
private fun ProviderList(
    providers: List<ProviderConfig>,
    onToggleEnabled: (ProviderConfig) -> Unit,
    onEdit: (ProviderConfig) -> Unit,
    onDelete: (ProviderConfig) -> Unit
) {
    if (providers.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Cloud,
            title = "暂无 API 供应商",
            subtitle = "点击右上角 + 添加供应商"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 内置供应商
            item {
                Text(
                    text = "内置供应商",
                    style = ZiZipTypography.labelMedium,
                    color = Grey400,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }
            
            items(providers.filter { it.builtIn }, key = { it.id }) { provider ->
                ProviderCard(
                    provider = provider,
                    onToggleEnabled = { onToggleEnabled(provider) },
                    onEdit = { onEdit(provider) },
                    onDelete = null // 内置的不能删除
                )
            }
            
            // 自定义供应商
            val customProviders = providers.filter { !it.builtIn }
            if (customProviders.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "自定义供应商",
                        style = ZiZipTypography.labelMedium,
                        color = Grey400,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                }
                
                items(customProviders, key = { it.id }) { provider ->
                    ProviderCard(
                        provider = provider,
                        onToggleEnabled = { onToggleEnabled(provider) },
                        onEdit = { onEdit(provider) },
                        onDelete = { onDelete(provider) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: ProviderConfig,
    onToggleEnabled: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (provider.enabled) PrimaryWhite else Grey100
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 图标
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (provider.enabled) Accent.copy(alpha = 0.1f) else Grey150),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Cloud,
                        contentDescription = null,
                        tint = if (provider.enabled) Accent else Grey400,
                        modifier = Modifier.size(22.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = provider.name,
                            style = ZiZipTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = if (provider.enabled) Grey900 else Grey400
                        )
                        if (provider.builtIn) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "内置",
                                style = ZiZipTypography.labelSmall,
                                color = Grey400,
                                modifier = Modifier
                                    .background(Grey100, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = provider.type.displayName,
                        style = ZiZipTypography.labelSmall,
                        color = Grey400
                    )
                }
                
                Switch(
                    checked = provider.enabled,
                    onCheckedChange = { onToggleEnabled() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PrimaryWhite,
                        checkedTrackColor = Accent,
                        uncheckedThumbColor = PrimaryWhite,
                        uncheckedTrackColor = Grey200
                    )
                )
            }
            
            if (provider.enabled && provider.apiKey.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Key,
                        contentDescription = null,
                        tint = SuccessColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "API Key 已配置",
                        style = ZiZipTypography.labelSmall,
                        color = SuccessColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onEdit) {
                    Text("配置", color = Accent)
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("删除", color = ErrorColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelList(
    models: List<ModelConfig>,
    onToggleEnabled: (ModelConfig) -> Unit,
    onEdit: (ModelConfig) -> Unit,
    onDelete: (ModelConfig) -> Unit
) {
    if (models.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Psychology,
            title = "暂无模型配置",
            subtitle = "点击右上角 + 添加模型"
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 按用途分组
            ModelPurpose.values().forEach { purpose ->
                val purposeModels = models.filter { it.purpose == purpose }
                if (purposeModels.isNotEmpty()) {
                    item {
                        Text(
                            text = purpose.displayName,
                            style = ZiZipTypography.labelMedium,
                            color = Grey400,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp, top = 8.dp)
                        )
                    }
                    
                    items(purposeModels, key = { it.id }) { model ->
                        ModelCard(
                            model = model,
                            onToggleEnabled = { onToggleEnabled(model) },
                            onEdit = { onEdit(model) },
                            onDelete = { onDelete(model) }
                        )
                    }
                }
            }
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
    val purposeColor = when (model.purpose) {
        ModelPurpose.CHAT -> Color(0xFF3B82F6)
        ModelPurpose.AGENT -> SuccessColor
        ModelPurpose.OCR -> Color(0xFFF59E0B)
        ModelPurpose.EMBEDDING -> Color(0xFF8B5CF6)
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (model.enabled) PrimaryWhite else Grey100
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(purposeColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (model.purpose) {
                            ModelPurpose.CHAT -> Icons.Outlined.Chat
                            ModelPurpose.AGENT -> Icons.Outlined.SmartToy
                            ModelPurpose.OCR -> Icons.Outlined.Image
                            ModelPurpose.EMBEDDING -> Icons.Outlined.DataObject
                        },
                        contentDescription = null,
                        tint = if (model.enabled) purposeColor else Grey400,
                        modifier = Modifier.size(22.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.displayName,
                        style = ZiZipTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = if (model.enabled) Grey900 else Grey400
                    )
                    Text(
                        text = "${model.providerType.displayName} · ${model.modelName}",
                        style = ZiZipTypography.labelSmall,
                        color = Grey400
                    )
                }
                
                Switch(
                    checked = model.enabled,
                    onCheckedChange = { onToggleEnabled() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PrimaryWhite,
                        checkedTrackColor = purposeColor,
                        uncheckedThumbColor = PrimaryWhite,
                        uncheckedTrackColor = Grey200
                    )
                )
            }
            
            // 能力标签
            if (model.abilities.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    model.abilities.take(3).forEach { ability ->
                        Text(
                            text = ability.displayName,
                            style = ZiZipTypography.labelSmall,
                            color = purposeColor,
                            modifier = Modifier
                                .background(purposeColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onEdit) {
                    Text("编辑", color = Accent)
                }
                TextButton(onClick = onDelete) {
                    Text("删除", color = ErrorColor)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Grey400,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = title, style = ZiZipTypography.bodyLarge, color = Grey400)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, style = ZiZipTypography.labelSmall, color = Grey400)
        }
    }
}

// ==================== 对话框 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddProviderDialog(
    onDismiss: () -> Unit,
    onConfirm: (ProviderConfig) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ModelProviderType.OPENAI_COMPATIBLE) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加 API 供应商") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
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
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && baseUrl.isNotBlank()) {
                        onConfirm(ProviderConfig(
                            name = name,
                            type = selectedType,
                            baseUrl = baseUrl,
                            apiKey = apiKey,
                            builtIn = false,
                            enabled = true
                        ))
                    }
                },
                enabled = name.isNotBlank() && baseUrl.isNotBlank()
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProviderDialog(
    provider: ProviderConfig,
    onDismiss: () -> Unit,
    onConfirm: (ProviderConfig) -> Unit
) {
    var name by remember { mutableStateOf(provider.name) }
    var baseUrl by remember { mutableStateOf(provider.baseUrl) }
    var apiKey by remember { mutableStateOf(provider.apiKey) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("配置 ${provider.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!provider.builtIn) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Base URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    placeholder = { Text("输入你的 API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(provider.copy(
                    name = if (provider.builtIn) provider.name else name,
                    baseUrl = if (provider.builtIn) provider.baseUrl else baseUrl,
                    apiKey = apiKey
                ))
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddModelDialog(
    providers: List<ProviderConfig>,
    onDismiss: () -> Unit,
    onConfirm: (ModelConfig) -> Unit
) {
    var displayName by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }
    var selectedProvider by remember { mutableStateOf(providers.firstOrNull()?.type ?: ModelProviderType.OPENAI_COMPATIBLE) }
    var selectedPurpose by remember { mutableStateOf(ModelPurpose.CHAT) }
    
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
                    placeholder = { Text("如 gpt-4, claude-3") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text("模型用途", style = ZiZipTypography.labelMedium, color = Grey700)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModelPurpose.values().take(3).forEach { purpose ->
                        FilterChip(
                            selected = selectedPurpose == purpose,
                            onClick = { selectedPurpose = purpose },
                            label = { Text(purpose.displayName) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (displayName.isNotBlank() && modelName.isNotBlank()) {
                        onConfirm(ModelConfig(
                            displayName = displayName,
                            modelName = modelName,
                            providerType = selectedProvider,
                            purpose = selectedPurpose,
                            enabled = true
                        ))
                    }
                },
                enabled = displayName.isNotBlank() && modelName.isNotBlank()
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditModelDialog(
    model: ModelConfig,
    providers: List<ProviderConfig>,
    onDismiss: () -> Unit,
    onConfirm: (ModelConfig) -> Unit
) {
    var displayName by remember { mutableStateOf(model.displayName) }
    var modelName by remember { mutableStateOf(model.modelName) }
    var temperature by remember { mutableStateOf(model.temperature.toString()) }
    var maxTokens by remember { mutableStateOf(model.maxTokens.toString()) }
    var systemPrompt by remember { mutableStateOf(model.systemPrompt) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑模型") },
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
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = temperature,
                        onValueChange = { temperature = it },
                        label = { Text("Temperature") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxTokens,
                        onValueChange = { maxTokens = it },
                        label = { Text("Max Tokens") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("系统提示词") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(model.copy(
                    displayName = displayName,
                    modelName = modelName,
                    temperature = temperature.toFloatOrNull() ?: 0.7f,
                    maxTokens = maxTokens.toIntOrNull() ?: 4096,
                    systemPrompt = systemPrompt
                ))
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
