package com.autoglm.android.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Chat : Screen("chat")
    object Settings : Screen("settings")
    object History : Screen("history")
    object PermissionSetup : Screen("permission_setup")
    object ModelConfig : Screen("model_config")
    object ModelManage : Screen("model_manage")
    object ProviderConfig : Screen("provider_config")
    object AutoGLMConfig : Screen("autoglm_config")
    object VirtualDisplay : Screen("virtual_display")
    object DebugLog : Screen("debug_log")
}
