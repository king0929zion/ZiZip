package com.autoglm.android.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 应用设置存储库
 * 使用 SharedPreferences 存储用户设置
 */
class SettingsRepository private constructor(private val context: Context) {
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private val _agentModeEnabled = MutableStateFlow(false)
    val agentModeEnabled: StateFlow<Boolean> = _agentModeEnabled.asStateFlow()
    
    private val _language = MutableStateFlow(DEFAULT_LANGUAGE)
    val language: StateFlow<String> = _language.asStateFlow()
    
    init {
        loadSettings()
    }
    
    private fun loadSettings() {
        _agentModeEnabled.value = prefs.getBoolean(KEY_AGENT_MODE, false)
        _language.value = prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }
    
    // ================== 基础设置 ==================
    
    fun getNickname(): String? = prefs.getString(KEY_NICKNAME, null)
    
    fun setNickname(nickname: String) {
        prefs.edit().putString(KEY_NICKNAME, nickname).apply()
    }
    
    fun getMaxSteps(): Int = prefs.getInt(KEY_MAX_STEPS, DEFAULT_MAX_STEPS)
    
    fun setMaxSteps(steps: Int) {
        prefs.edit().putInt(KEY_MAX_STEPS, steps).apply()
    }
    
    // ================== Agent 模式 ==================
    
    fun isAgentModeEnabled(): Boolean = _agentModeEnabled.value
    
    fun setAgentModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AGENT_MODE, enabled).apply()
        _agentModeEnabled.value = enabled
    }
    
    // ================== 语言设置 ==================
    
    fun getLanguage(): String = _language.value
    
    fun setLanguage(lang: String) {
        prefs.edit().putString(KEY_LANGUAGE, lang).apply()
        _language.value = lang
    }
    
    // ================== 引导状态 ==================
    
    fun isOnboardingCompleted(): Boolean = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    
    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }
    
    // ================== 活跃模型 ==================
    
    fun getActiveModelId(): String? = prefs.getString(KEY_ACTIVE_MODEL_ID, null)
    
    fun setActiveModelId(modelId: String?) {
        prefs.edit().putString(KEY_ACTIVE_MODEL_ID, modelId).apply()
    }
    
    // ================== AutoGLM 配置 ==================
    
    fun getAutoGLMApiKey(): String = prefs.getString(KEY_AUTOGLM_API_KEY, "") ?: ""
    
    fun setAutoGLMApiKey(apiKey: String) {
        prefs.edit().putString(KEY_AUTOGLM_API_KEY, apiKey).apply()
    }
    
    fun getAutoGLMBaseUrl(): String = prefs.getString(KEY_AUTOGLM_BASE_URL, DEFAULT_AUTOGLM_BASE_URL) ?: DEFAULT_AUTOGLM_BASE_URL
    
    fun setAutoGLMBaseUrl(baseUrl: String) {
        prefs.edit().putString(KEY_AUTOGLM_BASE_URL, baseUrl).apply()
    }
    
    fun getAutoGLMModelName(): String = prefs.getString(KEY_AUTOGLM_MODEL_NAME, DEFAULT_AUTOGLM_MODEL_NAME) ?: DEFAULT_AUTOGLM_MODEL_NAME
    
    fun setAutoGLMModelName(modelName: String) {
        prefs.edit().putString(KEY_AUTOGLM_MODEL_NAME, modelName).apply()
    }
    
    fun isAutoGLMConfigured(): Boolean = getAutoGLMApiKey().isNotBlank()
    
    companion object {
        private const val PREFS_NAME = "zizip_settings"
        
        // Keys
        private const val KEY_NICKNAME = "nickname"
        private const val KEY_MAX_STEPS = "max_steps"
        private const val KEY_AGENT_MODE = "agent_mode_enabled"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_ACTIVE_MODEL_ID = "active_model_id"
        private const val KEY_AUTOGLM_API_KEY = "autoglm_api_key"
        private const val KEY_AUTOGLM_BASE_URL = "autoglm_base_url"
        private const val KEY_AUTOGLM_MODEL_NAME = "autoglm_model_name"
        
        // Defaults
        private const val DEFAULT_LANGUAGE = "zh"
        private const val DEFAULT_MAX_STEPS = 20
        private const val DEFAULT_AUTOGLM_BASE_URL = "https://open.bigmodel.cn/api/paas/v4"
        private const val DEFAULT_AUTOGLM_MODEL_NAME = "autoglm-phone"
        
        @Volatile
        private var instance: SettingsRepository? = null
        
        fun getInstance(context: Context): SettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
