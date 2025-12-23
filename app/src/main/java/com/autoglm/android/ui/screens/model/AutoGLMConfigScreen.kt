package com.autoglm.android.ui.screens.model

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.autoglm.android.data.repository.SettingsRepository
import com.autoglm.android.ui.theme.*
import kotlinx.coroutines.launch

/**
 * AutoGLM 配置页面 - 独立于主模型
 * 复刻 Auto-GLM-Android 的 autoglm_config_page.dart
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoGLMConfigScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("https://open.bigmodel.cn/api/paas/v4/chat/completions") }
    var modelName by remember { mutableStateOf("autoglm-phone") }
    var isSaving by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    
    // 加载配置
    LaunchedEffect(Unit) {
        apiKey = settingsRepo.getAutoGLMApiKey()
        baseUrl = settingsRepo.getAutoGLMBaseUrl().ifBlank { 
            "https://open.bigmodel.cn/api/paas/v4/chat/completions" 
        }
        modelName = settingsRepo.getAutoGLMModelName().ifBlank { "autoglm-phone" }
        isLoading = false
    }
    
    val isConfigured = apiKey.isNotBlank()
    
    Scaffold(
        containerColor = PrimaryWhite,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "AutoGLM 配置",
                        style = ZiZipTypography.titleMedium.copy(fontWeight = FontWeight.Medium)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryWhite)
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Accent)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 说明卡片
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Accent.copy(alpha = 0.05f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Accent.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Accent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "AutoGLM 专用配置",
                                style = ZiZipTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = Grey800
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "AutoGLM 用于自动化操作手机，与主对话模型独立配置。当您使用 Agent 模式时，将使用此配置。",
                            style = ZiZipTypography.bodySmall,
                            color = Grey600,
                            lineHeight = ZiZipTypography.bodySmall.lineHeight * 1.5f
                        )
                    }
                }
                
                // 状态指示
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isConfigured) SuccessColor.copy(alpha = 0.1f) else Grey50
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isConfigured) Icons.Default.CheckCircle else Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = if (isConfigured) SuccessColor else WarningColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isConfigured) "已配置，可以使用 Agent 模式" else "未配置，Agent 模式不可用",
                            style = ZiZipTypography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = if (isConfigured) SuccessColor else WarningColor
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // API Key
                SectionTitle("API Key")
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    placeholder = { Text("输入智谱 API Key") },
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
                
                // 获取 API Key 链接
                TextButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.bigmodel.cn/usercenter/apikeys"))
                        context.startActivity(intent)
                    },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        "前往智谱开放平台获取 →",
                        style = ZiZipTypography.labelSmall,
                        color = Accent
                    )
                }
                
                // Base URL
                SectionTitle("Base URL")
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    placeholder = { Text("https://open.bigmodel.cn/api/paas/v4/chat/completions") },
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
                
                // 模型名称
                SectionTitle("模型名称")
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    placeholder = { Text("autoglm-phone") },
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
                
                Spacer(modifier = Modifier.weight(1f))
                
                // 保存按钮
                Button(
                    onClick = {
                        scope.launch {
                            isSaving = true
                            settingsRepo.setAutoGLMApiKey(apiKey)
                            settingsRepo.setAutoGLMBaseUrl(baseUrl.ifBlank { 
                                "https://open.bigmodel.cn/api/paas/v4/chat/completions" 
                            })
                            settingsRepo.setAutoGLMModelName(modelName.ifBlank { "autoglm-phone" })
                            isSaving = false
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = PrimaryWhite,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("保存配置", style = ZiZipTypography.bodyMedium)
                    }
                }
                
                // 重置按钮
                TextButton(
                    onClick = {
                        baseUrl = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
                        modelName = "autoglm-phone"
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("恢复默认配置", color = Grey500)
                }
            }
        }
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
