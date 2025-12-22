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
    val isLoading: Boolean = false
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
    val agentModeEnabled: Boolean = false
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
    
    /**
     * 发送消息
     */
    fun sendMessage(content: String) {
        if (content.isBlank()) return
        
        viewModelScope.launch {
            // 添加用户消息
            val userItem = ChatItem(isUser = true, message = content)
            addChatItem(userItem)
            
            // 添加加载状态
            val loadingId = UUID.randomUUID().toString()
            addChatItem(ChatItem(id = loadingId, isUser = false, isLoading = true))
            
            _uiState.value = _uiState.value.copy(isChatRunning = true)
            
            try {
                // 调用模型
                val response = modelProvider.processQuery(content)
                
                // 移除加载状态，添加响应
                removeChatItem(loadingId)
                
                val assistantItem = ChatItem(
                    isUser = false,
                    message = response.message,
                    thinking = response.thinking,
                    actionType = response.action?.actionName,
                    isSuccess = true
                )
                addChatItem(assistantItem)
                
                // 如果是 Agent 模式且有动作
                if (_uiState.value.agentModeEnabled && response.action != null) {
                    // 创建任务执行
                    val execution = TaskExecution(
                        taskDescription = content,
                        status = TaskStatus.RUNNING
                    )
                    _uiState.value = _uiState.value.copy(currentExecution = execution)
                    
                    // 添加任务执行卡片
                    val taskItem = ChatItem(
                        isUser = false,
                        execution = execution
                    )
                    addChatItem(taskItem)
                }
                
            } catch (e: Exception) {
                removeChatItem(loadingId)
                val errorItem = ChatItem(
                    isUser = false,
                    message = "发生错误: ${e.message}",
                    isSuccess = false
                )
                addChatItem(errorItem)
            } finally {
                _uiState.value = _uiState.value.copy(isChatRunning = false)
            }
        }
    }
    
    /**
     * 开始新对话
     */
    fun startNewConversation() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                chatItems = emptyList(),
                currentSessionId = null,
                currentExecution = null
            )
        }
    }
    
    /**
     * 暂停任务
     */
    fun pauseTask() {
        val execution = _uiState.value.currentExecution ?: return
        updateExecution(execution.copyWith(status = TaskStatus.PAUSED))
    }
    
    /**
     * 继续任务
     */
    fun resumeTask() {
        val execution = _uiState.value.currentExecution ?: return
        updateExecution(execution.copyWith(status = TaskStatus.RUNNING))
    }
    
    /**
     * 停止任务
     */
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
        
        // 更新聊天列表中的任务卡片
        val updatedItems = _uiState.value.chatItems.map { item ->
            if (item.execution?.taskId == execution.taskId) {
                item.copy(execution = execution)
            } else {
                item
            }
        }
        _uiState.value = _uiState.value.copy(chatItems = updatedItems)
    }
    
    /**
     * 获取问候语
     */
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
