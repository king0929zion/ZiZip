package com.autoglm.android.ui.screens.autoglmconfig

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.autoglm.android.data.repository.SettingsRepository
import com.autoglm.android.ui.theme.*

/**
 * AutoGLM 配置页面 - 智谱 API 专用配置
 * 参考 Auto-GLM-Android 的 autoglm_config_page.dart
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoGLMConfigScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository.getInstance(context) }
    
    var apiKey by remember { mutableStateOf(settingsRepo.getAutoGLMApiKey()) }
    var baseUrl by remember { mutableStateOf(settingsRepo.getAutoGLMBaseUrl()) }
    var modelName by remember { mutableStateOf(settingsRepo.getAutoGLMModelName()) }
    var isSaving by remember { mutableStateOf(false) }
    
    val isConfigured = apiKey.isNotBlank()
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    Scaffold(
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryWhite
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = PrimaryWhite
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 说明卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Accent.copy(alpha = 0.05f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Accent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "AutoGLM 专用配置",
                            style = ZiZipTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = Grey800
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "AutoGLM 用于自动化操作手机，当您使用 Agent 模式时，将使用此配置。",
                        style = ZiZipTypography.bodyMedium,
                        color = Grey600
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 状态指示
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isConfigured) SuccessColor.copy(alpha = 0.1f) else Grey50
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isConfigured) SuccessColor else WarningColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isConfigured) "已配置，可以使用 Agent 模式" else "未配置，Agent 模式不可用",
                        style = ZiZipTypography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = if (isConfigured) SuccessColor else WarningColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // API Key 输入
            Text(
                "API Key",
                style = ZiZipTypography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Grey600
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                placeholder = { Text("输入智谱 API Key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Grey200,
                    focusedContainerColor = Grey50,
                    unfocusedContainerColor = Grey50
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "前往智谱开放平台获取 →",
                style = ZiZipTypography.labelSmall,
                color = Accent,
                modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://open.bigmodel.cn/usercenter/apikeys")
                    }
                    context.startActivity(intent)
                }
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Base URL 输入
            Text(
                "Base URL",
                style = ZiZipTypography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Grey600
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                placeholder = { Text("https://open.bigmodel.cn/api/paas/v4") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Grey200,
                    focusedContainerColor = Grey50,
                    unfocusedContainerColor = Grey50
                )
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // 模型名称输入
            Text(
                "模型名称",
                style = ZiZipTypography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Grey600
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = modelName,
                onValueChange = { modelName = it },
                placeholder = { Text("autoglm-phone") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Grey200,
                    focusedContainerColor = Grey50,
                    unfocusedContainerColor = Grey50
                )
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 保存按钮
            Button(
                onClick = {
                    isSaving = true
                    // 保存配置
                    settingsRepo.setAutoGLMApiKey(apiKey)
                    settingsRepo.setAutoGLMBaseUrl(
                        if (baseUrl.isBlank()) "https://open.bigmodel.cn/api/paas/v4" else baseUrl
                    )
                    settingsRepo.setAutoGLMModelName(
                        if (modelName.isBlank()) "autoglm-phone" else modelName
                    )
                    isSaving = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = PrimaryWhite,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "保存配置",
                        style = ZiZipTypography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 恢复默认按钮
            TextButton(
                onClick = {
                    baseUrl = "https://open.bigmodel.cn/api/paas/v4"
                    modelName = "autoglm-phone"
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    "恢复默认配置",
                    style = ZiZipTypography.bodyMedium,
                    color = Grey500
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 帮助说明
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Grey50)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "使用说明",
                        style = ZiZipTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Grey700
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "1. 访问智谱开放平台注册账号\n" +
                        "2. 在用户中心获取 API Key\n" +
                        "3. 将 API Key 填入上方输入框\n" +
                        "4. 保存配置后即可使用 Agent 模式",
                        style = ZiZipTypography.bodySmall,
                        color = Grey500,
                        lineHeight = ZiZipTypography.bodySmall.lineHeight * 1.5
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        "了解更多 →",
                        style = ZiZipTypography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = Accent,
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://docs.bigmodel.cn/cn/guide/models/vlm/autoglm-phone")
                            }
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}
