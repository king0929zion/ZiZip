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
 * Agent 步骤记录
 */
data class StepRecord(
    val stepNumber: Int,
    val thinking: String = "",
    val action: String = "",
    val success: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 任务执行数据类 - 用于 Agent 模式
 */
data class TaskExecution(
    val taskId: String = UUID.randomUUID().toString(),
    val taskDescription: String,
    val status: TaskStatus = TaskStatus.PENDING,
    val actions: List<ActionRecord> = emptyList(),
    val steps: List<StepRecord> = emptyList(),
    val currentStep: Int = 0,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val errorMessage: String? = null,
    val debugInfo: String? = null
) {
    val stepCount: Int
        get() = steps.size
    
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
        steps: List<StepRecord>? = null,
        currentStep: Int? = null,
        endTime: Long? = null,
        errorMessage: String? = null,
        debugInfo: String? = null
    ): TaskExecution = TaskExecution(
        taskId = this.taskId,
        taskDescription = this.taskDescription,
        status = status ?: this.status,
        actions = actions ?: this.actions,
        steps = steps ?: this.steps,
        currentStep = currentStep ?: this.currentStep,
        startTime = this.startTime,
        endTime = endTime ?: this.endTime,
        errorMessage = errorMessage ?: this.errorMessage,
        debugInfo = debugInfo ?: this.debugInfo
    )
}
