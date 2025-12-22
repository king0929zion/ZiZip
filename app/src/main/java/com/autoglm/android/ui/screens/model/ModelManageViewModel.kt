package com.autoglm.android.ui.screens.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autoglm.android.data.model.*
import com.autoglm.android.data.repository.ModelConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 模型管理 ViewModel
 */
class ModelManageViewModel(application: Application) : AndroidViewModel(application) {
    
    private val modelRepo = ModelConfigRepository.getInstance(application)
    
    val models: StateFlow<List<ModelConfig>> = modelRepo.models
    
    private val _providers = MutableStateFlow(DefaultProviders.all())
    val providers: StateFlow<List<ProviderConfig>> = _providers.asStateFlow()
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _selectedProviderId = MutableStateFlow<String?>(null)
    val selectedProviderId: StateFlow<String?> = _selectedProviderId.asStateFlow()
    
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun setSelectedProvider(providerId: String?) {
        _selectedProviderId.value = providerId
    }
    
    fun toggleModelEnabled(modelId: String) {
        viewModelScope.launch {
            modelRepo.toggleModelEnabled(modelId)
        }
    }
    
    fun deleteModel(modelId: String) {
        viewModelScope.launch {
            modelRepo.deleteModel(modelId)
        }
    }
    
    fun addModel(model: ModelConfig) {
        viewModelScope.launch {
            modelRepo.addModel(model)
        }
    }
    
    fun updateProviderApiKey(providerId: String, apiKey: String) {
        viewModelScope.launch {
            val updated = _providers.value.map { provider ->
                if (provider.id == providerId) {
                    provider.copy(apiKey = apiKey)
                } else provider
            }
            _providers.value = updated
            
            // 同时更新该供应商下所有模型的 API Key
            models.value.filter { it.providerType.name == providerId }
                .forEach { model ->
                    modelRepo.updateModel(model.copy(apiKey = apiKey))
                }
        }
    }
}
