package com.autoglm.android.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.autoglm.android.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 模型配置存储库
 */
class ModelConfigRepository private constructor(private val context: Context) {
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private val _models = MutableStateFlow<List<ModelConfig>>(emptyList())
    val models: StateFlow<List<ModelConfig>> = _models.asStateFlow()
    
    private val _providers = MutableStateFlow<List<ProviderConfig>>(emptyList())
    val providers: StateFlow<List<ProviderConfig>> = _providers.asStateFlow()
    
    private val _activeModelId = MutableStateFlow<String?>(null)
    val activeModelId: StateFlow<String?> = _activeModelId.asStateFlow()
    
    // 按用途分类的活跃模型
    private val _activeChatModelId = MutableStateFlow<String?>(null)
    val activeChatModelId: StateFlow<String?> = _activeChatModelId.asStateFlow()
    
    private val _activeAgentModelId = MutableStateFlow<String?>(null)
    val activeAgentModelId: StateFlow<String?> = _activeAgentModelId.asStateFlow()
    
    private val _activeOcrModelId = MutableStateFlow<String?>(null)
    val activeOcrModelId: StateFlow<String?> = _activeOcrModelId.asStateFlow()
    
    init {
        loadProviders()
        loadModels()
    }
    
    // ================== 供应商管理 ==================
    
    private fun loadProviders() {
        val json = prefs.getString(KEY_PROVIDERS, null)
        _providers.value = if (json != null) {
            parseProvidersFromJson(json)
        } else {
            DefaultProviders.all()
        }
    }
    
    fun addProvider(provider: ProviderConfig) {
        val newList = _providers.value + provider
        saveProviders(newList)
        _providers.value = newList
    }
    
    fun updateProvider(provider: ProviderConfig) {
        val newList = _providers.value.map {
            if (it.id == provider.id) provider else it
        }
        saveProviders(newList)
        _providers.value = newList
    }
    
    fun deleteProvider(providerId: String) {
        // 不能删除内置供应商
        val provider = _providers.value.find { it.id == providerId }
        if (provider?.builtIn == true) return
        
        val newList = _providers.value.filter { it.id != providerId }
        saveProviders(newList)
        _providers.value = newList
    }
    
    fun getProviderById(id: String): ProviderConfig? {
        return _providers.value.find { it.id == id }
    }
    
    fun getEnabledProviders(): List<ProviderConfig> {
        return _providers.value.filter { it.enabled }
    }
    
    // ================== 模型管理 ==================
    
    private fun loadModels() {
        val json = prefs.getString(KEY_MODELS, null)
        _models.value = if (json != null) {
            parseModelsFromJson(json)
        } else {
            // 默认模型
            emptyList()
        }
        
        _activeModelId.value = prefs.getString(KEY_ACTIVE_MODEL, _models.value.firstOrNull()?.id)
        _activeChatModelId.value = prefs.getString(KEY_ACTIVE_CHAT_MODEL, null)
        _activeAgentModelId.value = prefs.getString(KEY_ACTIVE_AGENT_MODEL, null)
        _activeOcrModelId.value = prefs.getString(KEY_ACTIVE_OCR_MODEL, null)
    }
    
    fun getEnabledModels(): List<ModelConfig> = _models.value.filter { it.enabled }
    
    fun getModelsByPurpose(purpose: ModelPurpose): List<ModelConfig> {
        return _models.value.filter { it.purpose == purpose && it.enabled }
    }
    
    fun getActiveModel(): ModelConfig? {
        val activeId = _activeModelId.value
        return _models.value.find { it.id == activeId } ?: _models.value.firstOrNull()
    }
    
    fun getActiveChatModel(): ModelConfig? {
        val id = _activeChatModelId.value
        return _models.value.find { it.id == id && it.purpose == ModelPurpose.CHAT }
    }
    
    fun getActiveAgentModel(): ModelConfig? {
        val id = _activeAgentModelId.value
        return _models.value.find { it.id == id && it.purpose == ModelPurpose.AGENT }
    }
    
    fun getActiveOcrModel(): ModelConfig? {
        val id = _activeOcrModelId.value
        return _models.value.find { it.id == id && it.purpose == ModelPurpose.OCR }
    }
    
    fun setActiveModel(modelId: String) {
        prefs.edit().putString(KEY_ACTIVE_MODEL, modelId).apply()
        _activeModelId.value = modelId
    }
    
    fun setActiveChatModel(modelId: String) {
        prefs.edit().putString(KEY_ACTIVE_CHAT_MODEL, modelId).apply()
        _activeChatModelId.value = modelId
    }
    
    fun setActiveAgentModel(modelId: String) {
        prefs.edit().putString(KEY_ACTIVE_AGENT_MODEL, modelId).apply()
        _activeAgentModelId.value = modelId
    }
    
    fun setActiveOcrModel(modelId: String) {
        prefs.edit().putString(KEY_ACTIVE_OCR_MODEL, modelId).apply()
        _activeOcrModelId.value = modelId
    }
    
    fun addModel(config: ModelConfig) {
        val newList = _models.value + config
        saveModels(newList)
        _models.value = newList
    }
    
    fun updateModel(config: ModelConfig) {
        val newList = _models.value.map { 
            if (it.id == config.id) config else it 
        }
        saveModels(newList)
        _models.value = newList
    }
    
    fun deleteModel(modelId: String) {
        val newList = _models.value.filter { it.id != modelId }
        saveModels(newList)
        _models.value = newList
        
        if (_activeModelId.value == modelId) {
            _activeModelId.value = newList.firstOrNull()?.id
        }
    }
    
    fun setModelEnabled(modelId: String, enabled: Boolean) {
        val model = _models.value.find { it.id == modelId } ?: return
        updateModel(model.copy(enabled = enabled))
    }
    
    // ================== JSON 序列化 ==================
    
    private fun saveProviders(providers: List<ProviderConfig>) {
        val jsonArray = JSONArray()
        providers.forEach { provider ->
            jsonArray.put(providerToJson(provider))
        }
        prefs.edit().putString(KEY_PROVIDERS, jsonArray.toString()).apply()
    }
    
    private fun providerToJson(provider: ProviderConfig): JSONObject {
        return JSONObject().apply {
            put("id", provider.id)
            put("name", provider.name)
            put("type", provider.type.name)
            put("baseUrl", provider.baseUrl)
            put("apiKey", provider.apiKey)
            put("enabled", provider.enabled)
            put("builtIn", provider.builtIn)
            put("description", provider.description)
        }
    }
    
    private fun parseProvidersFromJson(json: String): List<ProviderConfig> {
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                ProviderConfig(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    type = try {
                        ModelProviderType.valueOf(obj.optString("type", "CUSTOM"))
                    } catch (e: Exception) {
                        ModelProviderType.CUSTOM
                    },
                    baseUrl = obj.optString("baseUrl", ""),
                    apiKey = obj.optString("apiKey", ""),
                    enabled = obj.optBoolean("enabled", true),
                    builtIn = obj.optBoolean("builtIn", false),
                    description = obj.optString("description", "")
                )
            }
        } catch (e: Exception) {
            DefaultProviders.all()
        }
    }
    
    private fun saveModels(models: List<ModelConfig>) {
        val jsonArray = JSONArray()
        models.forEach { model ->
            jsonArray.put(modelToJson(model))
        }
        prefs.edit().putString(KEY_MODELS, jsonArray.toString()).apply()
    }
    
    private fun modelToJson(model: ModelConfig): JSONObject {
        return JSONObject().apply {
            put("id", model.id)
            put("displayName", model.displayName)
            put("modelName", model.modelName)
            put("providerType", model.providerType.name)
            put("apiKey", model.apiKey)
            put("baseUrl", model.baseUrl)
            put("enabled", model.enabled)
            put("purpose", model.purpose.name)
            put("type", model.type.name)
            put("maxTokens", model.maxTokens)
            put("temperature", model.temperature.toDouble())
            put("topP", model.topP.toDouble())
            put("systemPrompt", model.systemPrompt)
            put("timeout", model.timeout)
        }
    }
    
    private fun parseModelsFromJson(json: String): List<ModelConfig> {
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                ModelConfig(
                    id = obj.getString("id"),
                    displayName = obj.getString("displayName"),
                    modelName = obj.optString("modelName", ""),
                    providerType = try {
                        ModelProviderType.valueOf(obj.optString("providerType", "OPENAI_COMPATIBLE"))
                    } catch (e: Exception) {
                        ModelProviderType.OPENAI_COMPATIBLE
                    },
                    apiKey = obj.optString("apiKey", ""),
                    baseUrl = obj.optString("baseUrl", ""),
                    enabled = obj.optBoolean("enabled", true),
                    purpose = try {
                        ModelPurpose.valueOf(obj.optString("purpose", "CHAT"))
                    } catch (e: Exception) {
                        ModelPurpose.CHAT
                    },
                    type = try {
                        ModelType.valueOf(obj.optString("type", "CHAT"))
                    } catch (e: Exception) {
                        ModelType.CHAT
                    },
                    maxTokens = obj.optInt("maxTokens", 4096),
                    temperature = obj.optDouble("temperature", 0.7).toFloat(),
                    topP = obj.optDouble("topP", 1.0).toFloat(),
                    systemPrompt = obj.optString("systemPrompt", ""),
                    timeout = obj.optInt("timeout", 60)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    companion object {
        private const val PREFS_NAME = "zizip_models"
        private const val KEY_MODELS = "model_configs"
        private const val KEY_PROVIDERS = "provider_configs"
        private const val KEY_ACTIVE_MODEL = "active_model_id"
        private const val KEY_ACTIVE_CHAT_MODEL = "active_chat_model_id"
        private const val KEY_ACTIVE_AGENT_MODEL = "active_agent_model_id"
        private const val KEY_ACTIVE_OCR_MODEL = "active_ocr_model_id"
        
        @Volatile
        private var instance: ModelConfigRepository? = null
        
        fun getInstance(context: Context): ModelConfigRepository {
            return instance ?: synchronized(this) {
                instance ?: ModelConfigRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
