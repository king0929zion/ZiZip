package com.autoglm.android.ui.screens.home

import android.app.Application
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autoglm.android.core.agent.AutoGLMApiClient
import com.autoglm.android.core.agent.PhoneAgent
import com.autoglm.android.core.api.ChatClient
import com.autoglm.android.core.api.ChatClientException
import com.autoglm.android.core.api.Message
import com.autoglm.android.data.model.*
import com.autoglm.android.data.repository.HistoryRepository
import com.autoglm.android.data.repository.ModelConfigRepository
import com.autoglm.android.data.repository.SettingsRepository
import com.autoglm.android.ui.components.ToolType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
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
    
    // 真实的聊天客户端
    private val chatClient = ChatClient(modelRepo, settingsRepo)
    
    // Agent 相关
    private val apiClient = AutoGLMApiClient(settingsRepo)
    private val phoneAgent = PhoneAgent(application, apiClient)
    
    // 对话历史（用于上下文）
    private val conversationHistory = mutableListOf<Message>()
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    val models: StateFlow<List<ModelConfig>> = modelRepo.models
    val activeModelId: StateFlow<String?> = modelRepo.activeModelId
    
    // 历史记录列表
    private val _historyList = MutableStateFlow<List<ConversationSummary>>(emptyList())
    val historyList: StateFlow<List<ConversationSummary>> = _historyList.asStateFlow()
    
    init {
        initialize()
        loadHistory()
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
    
    private fun loadHistory() {
        viewModelScope.launch {
            // TODO: 从 historyRepo 加载真实数据
            _historyList.value = listOf(
                ConversationSummary("1", "今天的任务", "帮我打开微信发送消息", System.currentTimeMillis()),
                ConversationSummary("2", "文件整理", "整理下载文件夹", System.currentTimeMillis() - 86400000),
                ConversationSummary("3", "购物清单", "帮我列一个购物清单", System.currentTimeMillis() - 172800000)
            )
        }
    }
    
    fun loadConversation(conversationId: String) {
        viewModelScope.launch {
            // TODO: 从 historyRepo 加载对话内容
            // 目前清空并显示一条提示消息
            _uiState.value = _uiState.value.copy(
                chatItems = listOf(
                    ChatItem(isUser = false, message = "已加载对话 $conversationId")
                ),
                currentSessionId = conversationId
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
                when (selectedTool) {
                    ToolType.AGENT -> {
                        removeChatItem(loadingId)
                        // 使用真正的 PhoneAgent 执行任务
                        addChatItem(ChatItem(
                            isUser = false,
                            message = "正在启动 Agent 执行任务...",
                            toolUsed = ToolType.AGENT
                        ))
                        
                        // 异步执行 Agent 任务
                        launch {
                            try {
                                val execution = phoneAgent.runTask(content)
                                _uiState.value = _uiState.value.copy(currentExecution = execution)
                                
                                val statusMessage = when (execution.status) {
                                    TaskStatus.COMPLETED -> "✅ 任务执行完成！"
                                    TaskStatus.FAILED -> "❌ 任务执行失败: ${execution.errorMessage ?: "未知错误"}"
                                    TaskStatus.CANCELLED -> "⏹ 任务已取消"
                                    else -> "任务状态: ${execution.status}"
                                }
                                
                                addChatItem(ChatItem(
                                    isUser = false,
                                    execution = execution,
                                    message = statusMessage,
                                    toolUsed = ToolType.AGENT,
                                    isSuccess = execution.status == TaskStatus.COMPLETED
                                ))
                            } catch (e: Exception) {
                                addChatItem(ChatItem(
                                    isUser = false,
                                    message = "❌ Agent 执行出错: ${e.message}",
                                    toolUsed = ToolType.AGENT,
                                    isSuccess = false
                                ))
                            }
                        }
                    }
                    else -> {
                        // 使用 ChatClient 进行真实对话
                        val imageBase64 = if (attachedImages.isNotEmpty()) {
                            // 读取第一张图片并转为 Base64
                            try {
                                val file = File(attachedImages.first())
                                if (file.exists()) {
                                    Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                                } else null
                            } catch (e: Exception) { null }
                        } else null
                        
                        // 添加用户消息到历史
                        conversationHistory.add(Message.user(content, imageBase64))
                        
                        // 发送到模型
                        val response = chatClient.sendMessage(conversationHistory)
                        
                        removeChatItem(loadingId)
                        
                        // 添加助手消息到历史
                        conversationHistory.add(Message.assistant(response.rawContent))
                        
                        // 显示响应
                        val displayContent = response.content.ifBlank { response.rawContent }
                        addChatItem(ChatItem(
                            isUser = false,
                            message = displayContent,
                            thinking = response.thinking.takeIf { it.isNotBlank() },
                            isSuccess = true
                        ))
                    }
                }
                
            } catch (e: ChatClientException) {
                removeChatItem(loadingId)
                addChatItem(ChatItem(
                    isUser = false,
                    message = "❌ ${e.message}",
                    isSuccess = false
                ))
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
            conversationHistory.clear()
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
        phoneAgent.pause()
        val execution = _uiState.value.currentExecution ?: return
        updateExecution(execution.copyWith(status = TaskStatus.PAUSED))
    }
    
    fun resumeTask() {
        phoneAgent.resume()
        val execution = _uiState.value.currentExecution ?: return
        updateExecution(execution.copyWith(status = TaskStatus.RUNNING))
    }
    
    fun stopTask() {
        phoneAgent.stop()
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
