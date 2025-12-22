package com.autoglm.android.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.autoglm.android.data.repository.SettingsRepository
import com.autoglm.android.service.accessibility.AutoGLMAccessibilityService
import com.autoglm.android.ui.navigation.Screen
import com.autoglm.android.ui.theme.*

/**
 * 设置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository.getInstance(context) }
    
    var agentModeEnabled by remember { mutableStateOf(settingsRepo.isAgentModeEnabled()) }
    var accessibilityEnabled by remember { mutableStateOf(false) }
    var overlayPermissionGranted by remember { mutableStateOf(false) }
    
    // 检查权限状态
    LaunchedEffect(Unit) {
        accessibilityEnabled = AutoGLMAccessibilityService.instance != null
        overlayPermissionGranted = Settings.canDrawOverlays(context)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", style = ZiZipTypography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Grey100
                )
            )
        },
        containerColor = Grey100
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            // 权限设置
            SectionHeader(title = "权限设置")
            SettingsCard {
                // 权限配置入口
                NavigationTile(
                    icon = Icons.Outlined.Security,
                    title = "权限配置",
                    description = if (accessibilityEnabled && overlayPermissionGranted) 
                        "所有必需权限已授权" else "需要配置权限",
                    onClick = { navController.navigate(Screen.PermissionSetup.route) }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 模型配置
            SectionHeader(title = "模型配置")
            SettingsCard {
                NavigationTile(
                    icon = Icons.Outlined.Tune,
                    title = "模型管理",
                    description = "搜索、启用和配置模型",
                    onClick = { navController.navigate(Screen.ModelManage.route) }
                )
                
                HorizontalDivider(color = Grey150)
                
                NavigationTile(
                    icon = Icons.Outlined.SmartToy,
                    title = "对话模型",
                    description = "配置主要对话 AI 模型",
                    onClick = { navController.navigate(Screen.ProviderConfig.route) }
                )
                
                HorizontalDivider(color = Grey150)
                
                NavigationTile(
                    icon = Icons.Outlined.Psychology,
                    title = "AutoGLM 配置",
                    description = "配置智谱 AutoGLM Agent",
                    onClick = { navController.navigate(Screen.AutoGLMConfig.route) }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 关于
            SectionHeader(title = "关于")
            SettingsCard {
                NavigationTile(
                    icon = Icons.Outlined.Info,
                    title = "关于 ZiZip",
                    description = "版本 1.0.0",
                    onClick = { /* TODO: 显示关于信息 */ }
                )
                
                HorizontalDivider(color = Grey150)
                
                NavigationTile(
                    icon = Icons.Outlined.Code,
                    title = "GitHub",
                    description = "查看源代码",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse("https://github.com")
                        }
                        context.startActivity(intent)
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = ZiZipTypography.labelSmall.copy(
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
        ),
        color = Grey400,
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PrimaryWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(content = content)
    }
}

@Composable
private fun PermissionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isEnabled) SuccessColor else Grey400,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = ZiZipTypography.bodyLarge,
                color = Grey900
            )
            Text(
                text = description,
                style = ZiZipTypography.labelSmall,
                color = if (isEnabled) SuccessColor else Grey400
            )
        }
        
        Icon(
            imageVector = if (isEnabled) Icons.Default.CheckCircle else Icons.Default.ChevronRight,
            contentDescription = null,
            tint = if (isEnabled) SuccessColor else Grey400,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SwitchTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isChecked) Accent else Grey400,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = ZiZipTypography.bodyLarge,
                color = Grey900
            )
            Text(
                text = description,
                style = ZiZipTypography.labelSmall,
                color = Grey400
            )
        }
        
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PrimaryWhite,
                checkedTrackColor = Accent,
                uncheckedThumbColor = PrimaryWhite,
                uncheckedTrackColor = Grey200
            )
        )
    }
}

@Composable
private fun NavigationTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Grey700,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = ZiZipTypography.bodyLarge,
                color = Grey900
            )
            Text(
                text = description,
                style = ZiZipTypography.labelSmall,
                color = Grey400
            )
        }
        
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Grey400,
            modifier = Modifier.size(20.dp)
        )
    }
}
