package com.autoglm.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.autoglm.android.ui.screens.home.HomeScreen
import com.autoglm.android.ui.screens.settings.SettingsScreen
import com.autoglm.android.ui.screens.history.HistoryScreen
import com.autoglm.android.ui.screens.permission.PermissionSetupScreen
import com.autoglm.android.ui.screens.model.ModelManageScreen
import com.autoglm.android.ui.screens.model.ProviderConfigScreen
import com.autoglm.android.ui.screens.model.ProviderDetailScreen
import com.autoglm.android.ui.screens.autoglmconfig.AutoGLMConfigScreen
import com.autoglm.android.ui.screens.virtualdisplay.VirtualDisplayScreen
import com.autoglm.android.ui.screens.debuglog.DebugLogScreen

/**
 * 应用导航图
 */
@Composable
fun ZiZipNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // 主页
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }
        
        // 设置
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
        
        // 历史记录
        composable(Screen.History.route) {
            HistoryScreen(navController = navController)
        }
        
        // 聊天详情（可选，可能与 Home 合并）
        composable(Screen.Chat.route) {
            HomeScreen(navController = navController)
        }
        
        // 权限设置页面
        composable(Screen.PermissionSetup.route) {
            PermissionSetupScreen(navController = navController)
        }
        
        // 模型管理页面
        composable(Screen.ModelManage.route) {
            ModelManageScreen(navController = navController)
        }
        
        // 供应商配置页面（模型配置主页）
        composable(Screen.ProviderConfig.route) {
            ProviderConfigScreen(navController = navController)
        }
        
        // 供应商详情页面
        composable(
            route = "provider_detail/{providerType}",
            arguments = listOf(navArgument("providerType") { type = NavType.StringType })
        ) { backStackEntry ->
            val providerType = backStackEntry.arguments?.getString("providerType") ?: ""
            ProviderDetailScreen(navController = navController, providerType = providerType)
        }
        
        // AutoGLM 配置页面
        composable(Screen.AutoGLMConfig.route) {
            AutoGLMConfigScreen(navController = navController)
        }

        // 虚拟屏幕显示页面
        composable(Screen.VirtualDisplay.route) {
            VirtualDisplayScreen(navController = navController)
        }

        // 调试日志页面
        composable(Screen.DebugLog.route) {
            DebugLogScreen(navController = navController)
        }
    }
}
