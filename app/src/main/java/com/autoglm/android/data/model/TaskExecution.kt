package com.autoglm.android.data.model

import java.util.UUID

/**
 * 任务执行状态
 */
enum class TaskStatus {
    PENDING,
    RUNNING,
    PAUSED,
    WAITING_CONFIRMATION,
    WAITING_TAKEOVER,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Agent 动作记录
 */
data class ActionRecord(
    val id: String = UUID.randomUUID().toString(),
    val actionType: String,
    val description: String,
    val thinking: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isSuccess: Boolean = true
)

/**
 * 任务执行数据类 - 用于 Agent 模式
 */
data class TaskExecution(
    val taskId: String = UUID.randomUUID().toString(),
    val taskDescription: String,
    val status: TaskStatus = TaskStatus.PENDING,
    val actions: List<ActionRecord> = emptyList(),
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val errorMessage: String? = null
) {
    val currentStep: Int
        get() = actions.size
    
    val duration: Long
        get() = (endTime ?: System.currentTimeMillis()) - startTime
    
    val formattedDuration: String
        get() {
            val seconds = duration / 1000
            val minutes = seconds / 60
            val remainingSeconds = seconds % 60
            return if (minutes > 0) {
                "${minutes}分${remainingSeconds}秒"
            } else {
                "${remainingSeconds}秒"
            }
        }
    
    val isActive: Boolean
        get() = status == TaskStatus.RUNNING || 
                status == TaskStatus.PAUSED || 
                status == TaskStatus.WAITING_CONFIRMATION ||
                status == TaskStatus.WAITING_TAKEOVER
    
    fun copyWith(
        status: TaskStatus? = null,
        actions: List<ActionRecord>? = null,
        endTime: Long? = null,
        errorMessage: String? = null
    ): TaskExecution = TaskExecution(
        taskId = this.taskId,
        taskDescription = this.taskDescription,
        status = status ?: this.status,
        actions = actions ?: this.actions,
        startTime = this.startTime,
        endTime = endTime ?: this.endTime,
        errorMessage = errorMessage ?: this.errorMessage
    )
}
