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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.autoglm.android.data.model.ModelConfig
import com.autoglm.android.data.model.ModelProviderType
import com.autoglm.android.data.repository.ModelConfigRepository
import com.autoglm.android.ui.theme.*
import java.util.UUID

/**
 * 模型配置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelConfigScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val repository = remember { ModelConfigRepository.getInstance(context) }
    val models by repository.models.collectAsState()
    val activeModelId by repository.activeModelId.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var editingModel by remember { mutableStateOf<ModelConfig?>(null) }
    
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
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "添加模型")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Grey100
                )
            )
        },
        containerColor = Grey100
    ) { paddingValues ->
        if (models.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Psychology,
                        contentDescription = null,
                        tint = Grey400,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "暂无模型配置",
                        style = ZiZipTypography.bodyLarge,
                        color = Grey400
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "点击右上角 + 添加模型",
                        style = ZiZipTypography.labelSmall,
                        color = Grey400
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Accent
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("添加模型")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(models, key = { it.id }) { model ->
                    ModelConfigItem(
                        model = model,
                        isActive = model.id == activeModelId,
                        onSetActive = { repository.setActiveModel(model.id) },
                        onEdit = { editingModel = model },
                        onDelete = { repository.deleteModel(model.id) },
                        onToggleEnabled = {
                            repository.updateModel(model.copy(enabled = !model.enabled))
                        }
                    )
                }
            }
        }
    }
    
    // 添加模型对话框
    if (showAddDialog) {
        AddModelDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { displayName, modelName, apiKey, baseUrl, providerType ->
                val newModel = ModelConfig(
                    id = UUID.randomUUID().toString(),
                    displayName = displayName,
                    modelName = modelName,
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    providerType = providerType,
                    enabled = true
                )
                repository.addModel(newModel)
                showAddDialog = false
            }
        )
    }
    
    // 编辑模型对话框
    editingModel?.let { model ->
        EditModelDialog(
            model = model,
            onDismiss = { editingModel = null },
            onConfirm = { updatedModel ->
                repository.updateModel(updatedModel)
                editingModel = null
            }
        )
    }
}

@Composable
private fun ModelConfigItem(
    model: ModelConfig,
    isActive: Boolean,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Accent.copy(alpha = 0.1f) else PrimaryWhite
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 图标
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isActive) Accent.copy(alpha = 0.2f) else Grey100),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SmartToy,
                        contentDescription = null,
                        tint = if (isActive) Accent else Grey700,
                        modifier = Modifier.size(22.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // 信息
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = model.displayName,
                            style = ZiZipTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = Grey900
                        )
                        if (isActive) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "当前",
                                style = ZiZipTypography.labelSmall,
                                color = Accent,
                                modifier = Modifier
                                    .background(Accent.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = model.modelName,
                        style = ZiZipTypography.labelSmall,
                        color = Grey400
                    )
                }
                
                // 开关
                Switch(
                    checked = model.enabled,
                    onCheckedChange = { onToggleEnabled() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PrimaryWhite,
                        checkedTrackColor = Accent,
                        uncheckedThumbColor = PrimaryWhite,
                        uncheckedTrackColor = Grey200
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (!isActive && model.enabled) {
                    TextButton(onClick = onSetActive) {
                        Text("设为当前", color = Accent)
                    }
                }
                TextButton(onClick = onEdit) {
                    Text("编辑", color = Grey700)
                }
                TextButton(onClick = { showDeleteConfirm = true }) {
                    Text("删除", color = ErrorColor)
                }
            }
        }
    }
    
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除模型") },
            text = { Text("确定要删除「${model.displayName}」吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("删除", color = ErrorColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddModelDialog(
    onDismiss: () -> Unit,
    onConfirm: (displayName: String, modelName: String, apiKey: String, baseUrl: String, providerType: ModelProviderType) -> Unit
) {
    var displayName by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var providerType by remember { mutableStateOf(ModelProviderType.OPENAI_COMPATIBLE) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加模型") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                    label = { Text("模型名称") },
                    placeholder = { Text("如 gpt-4, claude-3") },
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
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL（可选）") },
                    placeholder = { Text("https://api.openai.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (displayName.isNotBlank() && modelName.isNotBlank()) {
                        onConfirm(displayName, modelName, apiKey, baseUrl, providerType)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditModelDialog(
    model: ModelConfig,
    onDismiss: () -> Unit,
    onConfirm: (ModelConfig) -> Unit
) {
    var displayName by remember { mutableStateOf(model.displayName) }
    var modelName by remember { mutableStateOf(model.modelName) }
    var apiKey by remember { mutableStateOf(model.apiKey) }
    var baseUrl by remember { mutableStateOf(model.baseUrl) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑模型") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
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
                    label = { Text("模型名称") },
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
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (displayName.isNotBlank() && modelName.isNotBlank()) {
                        onConfirm(model.copy(
                            displayName = displayName,
                            modelName = modelName,
                            apiKey = apiKey,
                            baseUrl = baseUrl
                        ))
                    }
                },
                enabled = displayName.isNotBlank() && modelName.isNotBlank()
            ) {
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
