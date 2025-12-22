package com.autoglm.android.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.autoglm.android.data.model.ModelConfig
import com.autoglm.android.data.model.ModelProviderType
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
    
    private val _activeModelId = MutableStateFlow<String?>(null)
    val activeModelId: StateFlow<String?> = _activeModelId.asStateFlow()
    
    init {
        loadModels()
    }
    
    private fun loadModels() {
        val json = prefs.getString(KEY_MODELS, null)
        _models.value = if (json != null) {
            parseModelsFromJson(json)
        } else {
            // 默认添加 AutoGLM 模型
            listOf(ModelConfig.autoGLMDefault())
        }
        _activeModelId.value = prefs.getString(KEY_ACTIVE_MODEL, _models.value.firstOrNull()?.id)
    }
    
    // ================== 模型管理 ==================
    
    fun getEnabledModels(): List<ModelConfig> = _models.value.filter { it.enabled }
    
    fun getActiveModel(): ModelConfig? {
        val activeId = _activeModelId.value
        return _models.value.find { it.id == activeId } ?: _models.value.firstOrNull()
    }
    
    fun setActiveModel(modelId: String) {
        prefs.edit().putString(KEY_ACTIVE_MODEL, modelId).apply()
        _activeModelId.value = modelId
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
    
    fun removeModel(modelId: String) {
        val newList = _models.value.filter { it.id != modelId }
        saveModels(newList)
        _models.value = newList
        
        // 如果删除的是当前活跃模型，切换到第一个
        if (_activeModelId.value == modelId) {
            _activeModelId.value = newList.firstOrNull()?.id
        }
    }
    
    fun setModelEnabled(modelId: String, enabled: Boolean) {
        val model = _models.value.find { it.id == modelId } ?: return
        updateModel(model.copy(enabled = enabled))
    }
    
    // ================== JSON 序列化 ==================
    
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
            put("name", model.name)
            put("displayName", model.displayName)
            put("provider", model.provider.name)
            put("apiKey", model.apiKey)
            put("baseUrl", model.baseUrl)
            put("modelName", model.modelName)
            put("enabled", model.enabled)
            put("isDefault", model.isDefault)
        }
    }
    
    private fun parseModelsFromJson(json: String): List<ModelConfig> {
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                ModelConfig(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    displayName = obj.optString("displayName", obj.getString("name")),
                    provider = try {
                        ModelProviderType.valueOf(obj.optString("provider", "CUSTOM"))
                    } catch (e: Exception) {
                        ModelProviderType.CUSTOM
                    },
                    apiKey = obj.optString("apiKey", ""),
                    baseUrl = obj.optString("baseUrl", ""),
                    modelName = obj.optString("modelName", ""),
                    enabled = obj.optBoolean("enabled", true),
                    isDefault = obj.optBoolean("isDefault", false)
                )
            }
        } catch (e: Exception) {
            listOf(ModelConfig.autoGLMDefault())
        }
    }
    
    companion object {
        private const val PREFS_NAME = "zizip_models"
        private const val KEY_MODELS = "model_configs"
        private const val KEY_ACTIVE_MODEL = "active_model_id"
        
        @Volatile
        private var instance: ModelConfigRepository? = null
        
        fun getInstance(context: Context): ModelConfigRepository {
            return instance ?: synchronized(this) {
                instance ?: ModelConfigRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
