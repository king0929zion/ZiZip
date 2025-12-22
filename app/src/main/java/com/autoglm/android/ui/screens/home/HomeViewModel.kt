package com.autoglm.android.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autoglm.android.data.model.*
import com.autoglm.android.data.repository.HistoryRepository
import com.autoglm.android.data.repository.ModelConfigRepository
import com.autoglm.android.data.repository.SettingsRepository
import com.autoglm.android.domain.model.MockModelProvider
import com.autoglm.android.domain.model.ModelProvider
import com.autoglm.android.ui.components.ToolType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 聊天项数据类
 */
data class ChatItem(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val message: String? = null,
    val thinking: String? = null,
    val actionType: String? = null,
    val isSuccess: Boolean = true,
    val execution: TaskExecution? = null,
    val isLoading: Boolean = false,
    val toolUsed: ToolType? = null,
    val attachedImages: List<String> = emptyList()
)

/**
 * Home 页面 UI 状态
 */
data class HomeUiState(
    val chatItems: List<ChatItem> = emptyList(),
    val isInitialized: Boolean = false,
    val isLoading: Boolean = false,
    val isChatRunning: Boolean = false,
    val currentExecution: TaskExecution? = null,
    val currentSessionId: String? = null,
    val errorMessage: String? = null,
    val language: String = "zh",
    val agentModeEnabled: Boolean = false,
    val selectedTool: ToolType = ToolType.NONE,
    val attachedImages: List<String> = emptyList()
)

/**
 * Home 页面 ViewModel
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    
    private val settingsRepo = SettingsRepository.getInstance(application)
    private val modelRepo = ModelConfigRepository.getInstance(application)
    private val historyRepo = HistoryRepository.getInstance(application)
    
    private val modelProvider: ModelProvider = MockModelProvider()
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    val models: StateFlow<List<ModelConfig>> = modelRepo.models
    val activeModelId: StateFlow<String?> = modelRepo.activeModelId
    
    init {
        initialize()
    }
    
    private fun initialize() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isInitialized = true,
                language = settingsRepo.getLanguage(),
                agentModeEnabled = settingsRepo.isAgentModeEnabled()
            )
        }
    }
    
    fun getActiveModel(): ModelConfig? = modelRepo.getActiveModel()
    
    fun setActiveModel(modelId: String) {
        modelRepo.setActiveModel(modelId)
    }
    
    fun selectTool(tool: ToolType) {
        _uiState.value = _uiState.value.copy(selectedTool = tool)
    }
    
    fun addImage(uri: String) {
        val currentImages = _uiState.value.attachedImages
        if (currentImages.size < 5) {
            _uiState.value = _uiState.value.copy(
                attachedImages = currentImages + uri
            )
        }
    }
    
    fun removeImage(index: Int) {
        val currentImages = _uiState.value.attachedImages.toMutableList()
        if (index in currentImages.indices) {
            currentImages.removeAt(index)
            _uiState.value = _uiState.value.copy(attachedImages = currentImages)
        }
    }
    
    fun clearImages() {
        _uiState.value = _uiState.value.copy(attachedImages = emptyList())
    }
    
    fun sendMessage(content: String) {
        if (content.isBlank() && _uiState.value.attachedImages.isEmpty()) return
        
        val selectedTool = _uiState.value.selectedTool
        val attachedImages = _uiState.value.attachedImages.toList()
        
        viewModelScope.launch {
            val userItem = ChatItem(
                isUser = true, 
                message = content,
                toolUsed = if (selectedTool != ToolType.NONE) selectedTool else null,
                attachedImages = attachedImages
            )
            addChatItem(userItem)
            clearImages()
            
            val loadingId = UUID.randomUUID().toString()
            addChatItem(ChatItem(id = loadingId, isUser = false, isLoading = true))
            
            _uiState.value = _uiState.value.copy(isChatRunning = true)
            
            try {
                val response = when (selectedTool) {
                    ToolType.AGENT -> modelProvider.processQuery("$content [AGENT_MODE]")
                    ToolType.BUILD_APP -> modelProvider.processQuery("$content [BUILD_APP_MODE]")
                    ToolType.CANVAS -> modelProvider.processQuery("$content [CANVAS_MODE]")
                    ToolType.NONE -> modelProvider.processQuery(content)
                }
                
                removeChatItem(loadingId)
                
                when (selectedTool) {
                    ToolType.AGENT -> {
                        val execution = TaskExecution(
                            taskDescription = content,
                            status = TaskStatus.RUNNING
                        )
                        _uiState.value = _uiState.value.copy(currentExecution = execution)
                        
                        addChatItem(ChatItem(
                            isUser = false,
                            execution = execution,
                            message = "正在执行任务...",
                            toolUsed = ToolType.AGENT
                        ))
                    }
                    ToolType.BUILD_APP -> {
                        addChatItem(ChatItem(
                            isUser = false,
                            message = "🚀 正在生成应用代码...\n\n${response.message ?: "代码生成完成！"}",
                            toolUsed = ToolType.BUILD_APP,
                            isSuccess = true
                        ))
                    }
                    ToolType.CANVAS -> {
                        addChatItem(ChatItem(
                            isUser = false,
                            message = "🎨 Canvas 画布已创建\n\n${response.message ?: "您可以开始编辑了。"}",
                            toolUsed = ToolType.CANVAS,
                            isSuccess = true
                        ))
                    }
                    ToolType.NONE -> {
                        addChatItem(ChatItem(
                            isUser = false,
                            message = response.message,
                            thinking = response.thinking,
                            actionType = response.action?.actionName,
                            isSuccess = true
                        ))
                    }
                }
                
            } catch (e: Exception) {
                removeChatItem(loadingId)
                addChatItem(ChatItem(
                    isUser = false,
                    message = "发生错误: ${e.message}",
                    isSuccess = false
                ))
            } finally {
                _uiState.value = _uiState.value.copy(isChatRunning = false)
            }
        }
    }
    
    fun startNewConversation() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                chatItems = emptyList(),
                currentSessionId = null,
                currentExecution = null,
                selectedTool = ToolType.NONE,
                attachedImages = emptyList()
            )
        }
    }
    
    fun pauseTask() {
        val execution = _uiState.value.currentExecution ?: return
        updateExecution(execution.copyWith(status = TaskStatus.PAUSED))
    }
    
    fun resumeTask() {
        val execution = _uiState.value.currentExecution ?: return
        updateExecution(execution.copyWith(status = TaskStatus.RUNNING))
    }
    
    fun stopTask() {
        val execution = _uiState.value.currentExecution ?: return
        updateExecution(execution.copyWith(
            status = TaskStatus.CANCELLED,
            endTime = System.currentTimeMillis()
        ))
        _uiState.value = _uiState.value.copy(currentExecution = null)
    }
    
    private fun addChatItem(item: ChatItem) {
        _uiState.value = _uiState.value.copy(
            chatItems = _uiState.value.chatItems + item
        )
    }
    
    private fun removeChatItem(id: String) {
        _uiState.value = _uiState.value.copy(
            chatItems = _uiState.value.chatItems.filter { it.id != id }
        )
    }
    
    private fun updateExecution(execution: TaskExecution) {
        _uiState.value = _uiState.value.copy(currentExecution = execution)
        
        val updatedItems = _uiState.value.chatItems.map { item ->
            if (item.execution?.taskId == execution.taskId) {
                item.copy(execution = execution)
            } else {
                item
            }
        }
        _uiState.value = _uiState.value.copy(chatItems = updatedItems)
    }
    
    fun getGreeting(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val nickname = settingsRepo.getNickname() ?: "there"
        
        return when {
            hour < 12 -> "Good morning, $nickname"
            hour < 18 -> "Good afternoon, $nickname"
            else -> "Good evening, $nickname"
        }
    }
}
