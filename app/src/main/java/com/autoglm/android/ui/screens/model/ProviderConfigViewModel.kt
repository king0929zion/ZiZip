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
 * 供应商配置 UI 状态
 */
data class ProviderConfigUiState(
    val isLoading: Boolean = true,
    val providers: List<ProviderConfig> = emptyList(),
    val activeModel: ModelConfig? = null,
    val activeModelId: String? = null,
    val selectedModels: List<ModelConfig> = emptyList(),
    val selectedModelCount: Int = 0,
    val selectedModelIds: Set<String> = emptySet()
) {
    fun getSelectedCountForProvider(type: ModelProviderType): Int {
        return selectedModels.count { it.providerType == type }
    }
}

/**
 * 供应商配置 ViewModel
 */
class ProviderConfigViewModel(application: Application) : AndroidViewModel(application) {
    
    private val modelRepo = ModelConfigRepository.getInstance(application)
    
    private val _uiState = MutableStateFlow(ProviderConfigUiState())
    val uiState: StateFlow<ProviderConfigUiState> = _uiState.asStateFlow()
    
    init {
        loadData()
    }
    
    private fun loadData() {
        viewModelScope.launch {
            modelRepo.initialize()
            updateState()
        }
    }
    
    private fun updateState() {
        val providers = modelRepo.providers.value
        val models = modelRepo.models.value
        val activeModelId = modelRepo.activeModelId.value
        val selectedIds = modelRepo.selectedModelIds.value
        
        val selectedModels = models.filter { it.id in selectedIds }
        val activeModel = models.find { it.id == activeModelId }
        
        _uiState.value = ProviderConfigUiState(
            isLoading = false,
            providers = providers,
            activeModel = activeModel,
            activeModelId = activeModelId,
            selectedModels = selectedModels,
            selectedModelCount = selectedIds.size,
            selectedModelIds = selectedIds
        )
    }
    
    fun setActiveModel(modelId: String) {
        viewModelScope.launch {
            modelRepo.setActiveModel(modelId)
            updateState()
        }
    }
    
    fun addCustomProvider(name: String, baseUrl: String) {
        viewModelScope.launch {
            modelRepo.addCustomProvider(name, baseUrl)
            updateState()
        }
    }
    
    fun refreshProviders() {
        updateState()
    }
}
