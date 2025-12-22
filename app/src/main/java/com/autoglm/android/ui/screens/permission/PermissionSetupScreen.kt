package com.autoglm.android.ui.screens.permission

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.autoglm.android.service.accessibility.AutoGLMAccessibilityService
import com.autoglm.android.ui.theme.*

/**
 * 权限类型
 */
enum class PermissionType {
    ACCESSIBILITY,
    OVERLAY,
    BATTERY_OPTIMIZATION,
    NOTIFICATION,
    SHIZUKU
}

/**
 * 权限状态
 */
data class PermissionState(
    val type: PermissionType,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isRequired: Boolean = true,
    val isGranted: Boolean = false
)

/**
 * 权限设置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionSetupScreen(
    navController: NavController
) {
    val context = LocalContext.current
    
    // 权限状态
    var permissions by remember { mutableStateOf(listOf<PermissionState>()) }
    
    // 检查权限状态
    fun checkPermissions() {
        permissions = listOf(
            PermissionState(
                type = PermissionType.ACCESSIBILITY,
                title = "无障碍服务",
                description = "用于读取屏幕内容和执行自动化操作",
                icon = Icons.Outlined.Accessibility,
                isRequired = true,
                isGranted = isAccessibilityServiceEnabled(context)
            ),
            PermissionState(
                type = PermissionType.OVERLAY,
                title = "悬浮窗权限",
                description = "用于显示任务状态悬浮窗",
                icon = Icons.Outlined.Layers,
                isRequired = true,
                isGranted = Settings.canDrawOverlays(context)
            ),
            PermissionState(
                type = PermissionType.BATTERY_OPTIMIZATION,
                title = "忽略电池优化",
                description = "保持后台运行，防止服务被系统杀死",
                icon = Icons.Outlined.BatteryChargingFull,
                isRequired = true,
                isGranted = isIgnoringBatteryOptimizations(context)
            ),
            PermissionState(
                type = PermissionType.NOTIFICATION,
                title = "通知权限",
                description = "用于显示任务状态和提醒通知",
                icon = Icons.Outlined.Notifications,
                isRequired = false,
                isGranted = areNotificationsEnabled(context)
            ),
            PermissionState(
                type = PermissionType.SHIZUKU,
                title = "Shizuku 授权",
                description = "提供高级系统操作能力（可选）",
                icon = Icons.Outlined.AdminPanelSettings,
                isRequired = false,
                isGranted = isShizukuGranted()
            )
        )
    }
    
    // 初始化和恢复时检查权限
    LaunchedEffect(Unit) {
        checkPermissions()
    }
    
    // 计算权限统计
    val requiredCount = permissions.count { it.isRequired }
    val grantedRequiredCount = permissions.count { it.isRequired && it.isGranted }
    val allRequiredGranted = grantedRequiredCount == requiredCount
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("权限设置", style = ZiZipTypography.titleMedium) },
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
        ) {
            // 状态概览卡片
            StatusOverviewCard(
                grantedCount = grantedRequiredCount,
                totalCount = requiredCount,
                allGranted = allRequiredGranted,
                modifier = Modifier.padding(16.dp)
            )
            
            // 必需权限
            SectionHeader(
                title = "必需权限",
                subtitle = "应用核心功能需要这些权限"
            )
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                permissions.filter { it.isRequired }.forEachIndexed { index, permission ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = Grey150
                        )
                    }
                    PermissionItem(
                        permission = permission,
                        onClick = {
                            requestPermission(context, permission.type)
                        },
                        onRefresh = { checkPermissions() }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 可选权限
            SectionHeader(
                title = "可选权限",
                subtitle = "增强功能，可根据需要开启"
            )
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                permissions.filter { !it.isRequired }.forEachIndexed { index, permission ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = Grey150
                        )
                    }
                    PermissionItem(
                        permission = permission,
                        onClick = {
                            requestPermission(context, permission.type)
                        },
                        onRefresh = { checkPermissions() }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 刷新按钮
            OutlinedButton(
                onClick = { checkPermissions() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Grey700
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("刷新权限状态")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatusOverviewCard(
    grantedCount: Int,
    totalCount: Int,
    allGranted: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (allGranted) SuccessColor.copy(alpha = 0.1f) else Accent.copy(alpha = 0.1f),
        label = "bg"
    )
    val iconColor by animateColorAsState(
        targetValue = if (allGranted) SuccessColor else Accent,
        label = "icon"
    )
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (allGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    text = if (allGranted) "权限配置完成" else "需要配置权限",
                    style = ZiZipTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Grey900
                )
                Text(
                    text = "已授权 $grantedCount / $totalCount 项必需权限",
                    style = ZiZipTypography.bodyMedium,
                    color = Grey700
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = ZiZipTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Grey900
        )
        Text(
            text = subtitle,
            style = ZiZipTypography.labelSmall,
            color = Grey400
        )
    }
}

@Composable
private fun PermissionItem(
    permission: PermissionState,
    onClick: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (permission.isGranted) SuccessColor.copy(alpha = 0.1f) else Grey100),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = permission.icon,
                contentDescription = null,
                tint = if (permission.isGranted) SuccessColor else Grey400,
                modifier = Modifier.size(22.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // 文字
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = permission.title,
                    style = ZiZipTypography.bodyLarge,
                    color = Grey900
                )
                if (!permission.isRequired) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "可选",
                        style = ZiZipTypography.labelSmall,
                        color = Grey400,
                        modifier = Modifier
                            .background(Grey100, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = permission.description,
                style = ZiZipTypography.labelSmall,
                color = Grey400
            )
        }
        
        // 状态指示
        if (permission.isGranted) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "已授权",
                tint = SuccessColor,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "去设置",
                tint = Grey400,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ============= 权限检查工具函数 =============

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    return enabledServices.any {
        it.resolveInfo.serviceInfo.packageName == context.packageName
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun areNotificationsEnabled(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.areNotificationsEnabled()
    } else {
        true
    }
}

private fun isShizukuGranted(): Boolean {
    // Shizuku 需要额外的库支持，这里暂时返回 false
    // 实际实现需要添加 Shizuku 依赖
    return false
}

private fun requestPermission(context: Context, type: PermissionType) {
    when (type) {
        PermissionType.ACCESSIBILITY -> {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            context.startActivity(intent)
        }
        PermissionType.OVERLAY -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            }
        }
        PermissionType.BATTERY_OPTIMIZATION -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        }
        PermissionType.NOTIFICATION -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                context.startActivity(intent)
            } else {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        }
        PermissionType.SHIZUKU -> {
            // 打开 Shizuku 应用或应用商店
            try {
                val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                if (intent != null) {
                    context.startActivity(intent)
                } else {
                    // 打开应用商店
                    val marketIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("market://details?id=moe.shizuku.privileged.api")
                    }
                    context.startActivity(marketIntent)
                }
            } catch (e: Exception) {
                // 打开网页
                val webIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://shizuku.rikka.app/")
                }
                context.startActivity(webIntent)
            }
        }
    }
}
