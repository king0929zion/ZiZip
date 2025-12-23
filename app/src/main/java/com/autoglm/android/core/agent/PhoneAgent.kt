package com.autoglm.android.core.agent

import android.content.Context
import android.content.Intent
import android.util.Log
import com.autoglm.android.core.shizuku.AndroidShellExecutor
import com.autoglm.android.core.shizuku.ShizukuAuthorizer
import com.autoglm.android.data.model.StepRecord
import com.autoglm.android.data.model.TaskExecution
import com.autoglm.android.data.model.TaskStatus
import com.autoglm.android.data.repository.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Agent 动作类型
 */
sealed class AgentAction {
    data class Launch(val packageName: String) : AgentAction()
    data class Tap(val x: Int, val y: Int) : AgentAction()
    data class Type(val text: String) : AgentAction()
    data class Swipe(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val duration: Int = 300) : AgentAction()
    object Back : AgentAction()
    object Home : AgentAction()
    data class Wait(val durationMs: Int) : AgentAction()
    object TakeOver : AgentAction()
    data class Finish(val result: String) : AgentAction()
}

/**
 * Agent 步骤结果
 */
data class StepResult(
    val success: Boolean,
    val action: AgentAction?,
    val thinking: String = "",
    val message: String = "",
    val shouldContinue: Boolean = true
)

/**
 * PhoneAgent - 手机自动化 Agent 核心逻辑
 * 参考 Operit 的 PhoneAgent.kt 实现
 */
class PhoneAgent(
    private val context: Context,
    private val apiClient: AutoGLMApiClient
) {
    companion object {
        private const val TAG = "PhoneAgent"
        private const val MAX_STEPS = 30
        private const val STEP_DELAY_MS = 500L
    }

    private val settingsRepo = SettingsRepository.getInstance(context)

    private val _execution = MutableStateFlow<TaskExecution?>(null)
    val execution: StateFlow<TaskExecution?> = _execution.asStateFlow()

    private var currentJob: Job? = null
    private var isPaused = false

    // Shower 虚拟屏幕支持
    private val showerController = ShowerController()
    private var isShowerAvailable = false
    
    /**
     * 执行任务
     */
    suspend fun runTask(taskDescription: String): TaskExecution = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting task: $taskDescription")

        // 检查权限
        if (!ShizukuAuthorizer.hasShizukuPermission()) {
            Log.e(TAG, "No Shizuku permission")
            return@withContext TaskExecution(
                taskDescription = taskDescription,
                status = TaskStatus.FAILED,
                errorMessage = "缺少 Shizuku 权限"
            )
        }

        // 初始化 Shower 虚拟屏幕
        val showerEnabled = settingsRepo.isShowerEnabled()
        Log.i(TAG, "Shower enabled in settings: $showerEnabled")

        if (showerEnabled) {
            try {
                val serverStarted = ShowerServerManager.ensureServerStarted(context)
                if (serverStarted) {
                    val metrics = context.resources.displayMetrics
                    CoordinateNormalizer.init(metrics.widthPixels, metrics.heightPixels)
                    val displayCreated = showerController.ensureDisplay(
                        metrics.widthPixels,
                        metrics.heightPixels,
                        metrics.densityDpi,
                        bitrateKbps = 3000
                    )
                    isShowerAvailable = displayCreated
                    Log.i(TAG, "Shower initialized: server=$serverStarted, display=$displayCreated")

                    // 显示虚拟屏幕边框
                    if (displayCreated) {
                        setVirtualBorderVisible(true)
                    }
                } else {
                    Log.w(TAG, "Shower server not available, using fallback mode")
                    isShowerAvailable = false
                }
            } catch (e: Exception) {
                Log.w(TAG, "Shower initialization failed, continuing without it", e)
                isShowerAvailable = false
            }
        } else {
            Log.i(TAG, "Shower disabled in settings, skipping virtual display")
            isShowerAvailable = false
        }

        // 即使不使用 Shower，也需要初始化坐标系统
        val metrics = context.resources.displayMetrics
        CoordinateNormalizer.init(metrics.widthPixels, metrics.heightPixels)

        val execution = TaskExecution(
            taskDescription = taskDescription,
            status = TaskStatus.RUNNING,
            startTime = System.currentTimeMillis()
        )
        _execution.value = execution

        try {
            var stepCount = 0
            var shouldContinue = true
            
            while (shouldContinue && stepCount < MAX_STEPS && !isPaused) {
                stepCount++
                updateExecution { copy(currentStep = stepCount) }
                
                Log.d(TAG, "Executing step $stepCount")
                
                // 截图
                val screenshotPath = captureScreenshot()
                if (screenshotPath == null) {
                    Log.e(TAG, "Failed to capture screenshot")
                    updateExecution { copy(status = TaskStatus.FAILED, errorMessage = "截图失败") }
                    break
                }
                
                // 调用 AI
                val stepResult = try {
                    val response = apiClient.sendStep(
                        taskDescription = taskDescription,
                        screenshotPath = screenshotPath,
                        stepNumber = stepCount,
                        previousActions = _execution.value?.steps?.map { it.action } ?: emptyList()
                    )
                    parseAIResponse(response)
                } catch (e: Exception) {
                    Log.e(TAG, "AI request failed", e)
                    StepResult(
                        success = false,
                        action = null,
                        message = "AI 请求失败: ${e.message}",
                        shouldContinue = false
                    )
                }
                
                if (!stepResult.success) {
                    updateExecution { 
                        copy(
                            status = TaskStatus.FAILED, 
                            errorMessage = stepResult.message
                        ) 
                    }
                    break
                }
                
                // 执行动作
                val action = stepResult.action
                if (action != null) {
                    val actionSuccess = executeAction(action)
                    if (!actionSuccess && action !is AgentAction.Finish) {
                        Log.e(TAG, "Action failed: $action")
                        // 动作失败不一定要停止，继续尝试
                    }
                    
                    // 记录步骤
                    val step = StepRecord(
                        stepNumber = stepCount,
                        thinking = stepResult.thinking,
                        action = action.toString(),
                        success = actionSuccess
                    )
                    updateExecution { copy(steps = steps + step) }
                }
                
                shouldContinue = stepResult.shouldContinue
                
                // 等待一段时间让界面响应
                delay(STEP_DELAY_MS)
            }
            
            // 任务完成
            if (shouldContinue && stepCount >= MAX_STEPS) {
                updateExecution { 
                    copy(
                        status = TaskStatus.COMPLETED,
                        endTime = System.currentTimeMillis(),
                        errorMessage = "达到最大步骤数"
                    ) 
                }
            } else if (!shouldContinue) {
                updateExecution { 
                    copy(
                        status = TaskStatus.COMPLETED,
                        endTime = System.currentTimeMillis()
                    ) 
                }
            }
            
        } catch (e: CancellationException) {
            Log.d(TAG, "Task cancelled")
            updateExecution { 
                copy(
                    status = TaskStatus.CANCELLED,
                    endTime = System.currentTimeMillis()
                ) 
            }
        } catch (e: Exception) {
            Log.e(TAG, "Task failed with exception", e)
            updateExecution { 
                copy(
                    status = TaskStatus.FAILED,
                    endTime = System.currentTimeMillis(),
                    errorMessage = e.message
                )
            }
        } finally {
            // 任务结束，隐藏虚拟屏幕边框
            setVirtualBorderVisible(false)
            // 关闭 Shower 连接
            try {
                showerController.shutdown()
            } catch (e: Exception) {
                Log.w(TAG, "Error shutting down Shower", e)
            }
        }

        _execution.value ?: execution
    }

    /**
     * 设置虚拟屏幕边框可见性
     * 通过 OverlayService 控制
     */
    private fun setVirtualBorderVisible(visible: Boolean) {
        try {
            val intent = Intent("com.autoglm.android.action.SET_VIRTUAL_BORDER").apply {
                putExtra("visible", visible)
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set virtual border visibility", e)
        }
    }
    
    /**
     * 暂停任务
     */
    fun pause() {
        isPaused = true
        updateExecution { copy(status = TaskStatus.PAUSED) }
    }
    
    /**
     * 恢复任务
     */
    fun resume() {
        isPaused = false
        updateExecution { copy(status = TaskStatus.RUNNING) }
    }
    
    /**
     * 停止任务
     */
    fun stop() {
        currentJob?.cancel()
        updateExecution { 
            copy(
                status = TaskStatus.CANCELLED,
                endTime = System.currentTimeMillis()
            ) 
        }
    }
    
    /**
     * 截图 - 双路径支持
     * 优先使用 Shower WebSocket 截图（无 UI 干扰），降级到 screencap 命令
     */
    private suspend fun captureScreenshot(): String? {
        val path = "${context.cacheDir}/screenshot_${System.currentTimeMillis()}.png"

        // 优先使用 Shower 截图（如果可用）
        if (isShowerAvailable) {
            try {
                val showerBytes = showerController.requestScreenshot(timeoutMs = 3000L)
                if (showerBytes != null) {
                    File(path).writeBytes(showerBytes)
                    Log.d(TAG, "Screenshot captured via Shower: $path")
                    return path
                }
            } catch (e: Exception) {
                Log.w(TAG, "Shower screenshot failed, falling back to screencap", e)
            }
        }

        // 降级到 screencap 命令
        val success = AndroidShellExecutor.screenshot(path)
        return if (success) {
            Log.d(TAG, "Screenshot captured via screencap: $path")
            path
        } else null
    }
    
    /**
     * 解析 AI 响应
     */
    private fun parseAIResponse(response: AutoGLMApiClient.AIResponse): StepResult {
        val thinking = response.thinking ?: ""
        val actionStr = response.action ?: ""
        
        // 解析动作
        val action = parseAction(actionStr)
        
        // 判断是否继续
        val shouldContinue = action !is AgentAction.Finish && action !is AgentAction.TakeOver
        
        return StepResult(
            success = true,
            action = action,
            thinking = thinking,
            message = response.message ?: "",
            shouldContinue = shouldContinue
        )
    }
    
    /**
     * 解析动作字符串
     */
    private fun parseAction(actionStr: String): AgentAction? {
        val trimmed = actionStr.trim()

        // 空动作检查
        if (trimmed.isEmpty()) {
            Log.w(TAG, "Empty action string")
            return AgentAction.Finish("完成（无动作）")
        }

        // 解析格式: action(params)
        val regex = Regex("""(\w+)\(([^)]*)\)""")
        val match = regex.find(trimmed)

        if (match == null) {
            Log.w(TAG, "Failed to parse action: '$actionStr' (format mismatch)")
            return null
        }

        val actionName = match.groupValues[1].lowercase()
        val params = match.groupValues[2]

        return when (actionName) {
            "launch" -> AgentAction.Launch(params.trim().removeSurrounding("\""))
            "tap" -> {
                val coords = params.split(",").map { it.trim().toIntOrNull() ?: 0 }
                if (coords.size >= 2) AgentAction.Tap(coords[0], coords[1]) else {
                    Log.w(TAG, "Invalid tap coordinates: $params")
                    null
                }
            }
            "type" -> AgentAction.Type(params.trim().removeSurrounding("\""))
            "swipe" -> {
                val coords = params.split(",").map { it.trim().toIntOrNull() ?: 0 }
                if (coords.size >= 4) {
                    AgentAction.Swipe(coords[0], coords[1], coords[2], coords[3], coords.getOrNull(4) ?: 300)
                } else {
                    Log.w(TAG, "Invalid swipe coordinates: $params (need x1,y1,x2,y2)")
                    null
                }
            }
            "back" -> AgentAction.Back
            "home" -> AgentAction.Home
            "wait" -> {
                val duration = params.trim().toIntOrNull() ?: 1000
                AgentAction.Wait(duration)
            }
            "take_over", "takeover" -> AgentAction.TakeOver
            "finish", "done" -> AgentAction.Finish(params.trim().removeSurrounding("\""))
            else -> {
                Log.w(TAG, "Unknown action type: '$actionName'")
                null
            }
        }
    }
    
    /**
     * 执行动作
     */
    private suspend fun executeAction(action: AgentAction): Boolean {
        Log.d(TAG, "Executing action: $action")

        return when (action) {
            is AgentAction.Launch -> {
                // 如果 Shower 可用，通过 Shower 启动
                if (isShowerAvailable) {
                    showerController.launchApp(action.packageName)
                } else {
                    AndroidShellExecutor.launchApp(action.packageName)
                }
            }
            is AgentAction.Tap -> {
                // 坐标归一化：检测并转换 0-1000 坐标
                val (pixelX, pixelY) = if (CoordinateNormalizer.isNormalized(action.x, action.y)) {
                    CoordinateNormalizer.toPixel(action.x, action.y)
                } else {
                    action.x to action.y
                }
                // 如果 Shower 可用，通过 Shower 执行
                if (isShowerAvailable) {
                    showerController.tap(pixelX, pixelY)
                } else {
                    AndroidShellExecutor.tap(pixelX, pixelY)
                }
            }
            is AgentAction.Type -> {
                // 多重降级策略：直接输入法 → 广播输入法 → shell input
                val imeSuccess = com.autoglm.android.service.ime.ZiZipInputMethod.inputTextDirect(action.text)
                if (!imeSuccess) {
                    // 尝试切换到 ZiZip 输入法后再输入
                    val switched = com.autoglm.android.service.ime.ZiZipInputMethod.switchToThisInputMethod(context)
                    if (switched) {
                        delay(200) // 等待输入法切换
                        val directSuccess = com.autoglm.android.service.ime.ZiZipInputMethod.inputTextDirect(action.text)
                        if (directSuccess) {
                            true
                        } else {
                            // 最后尝试广播方式
                            com.autoglm.android.service.ime.ZiZipInputMethod.inputTextViaBase64Broadcast(context, action.text)
                        }
                    } else {
                        // 降级到 shell input（不支持中文）
                        AndroidShellExecutor.inputText(action.text)
                    }
                } else {
                    true
                }
            }
            is AgentAction.Swipe -> {
                // 坐标归一化：检测并转换 0-1000 坐标
                val (sx, sy) = if (CoordinateNormalizer.isNormalized(action.x1, action.y1)) {
                    CoordinateNormalizer.toPixel(action.x1, action.y1)
                } else {
                    action.x1 to action.y1
                }
                val (ex, ey) = if (CoordinateNormalizer.isNormalized(action.x2, action.y2)) {
                    CoordinateNormalizer.toPixel(action.x2, action.y2)
                } else {
                    action.x2 to action.y2
                }
                // 如果 Shower 可用，通过 Shower 执行
                if (isShowerAvailable) {
                    showerController.swipe(sx, sy, ex, ey, action.duration.toLong())
                } else {
                    AndroidShellExecutor.swipe(sx, sy, ex, ey, action.duration)
                }
            }
            is AgentAction.Back -> {
                if (isShowerAvailable) {
                    showerController.key(4) // KEYCODE_BACK
                } else {
                    AndroidShellExecutor.pressBack()
                }
            }
            is AgentAction.Home -> {
                if (isShowerAvailable) {
                    showerController.key(3) // KEYCODE_HOME
                } else {
                    AndroidShellExecutor.pressHome()
                }
            }
            is AgentAction.Wait -> {
                delay(action.durationMs.toLong())
                true
            }
            is AgentAction.TakeOver -> {
                // 需要用户接管
                updateExecution { copy(status = TaskStatus.WAITING_TAKEOVER) }
                true
            }
            is AgentAction.Finish -> true
        }
    }
    
    private fun updateExecution(update: TaskExecution.() -> TaskExecution) {
        _execution.value = _execution.value?.update()
    }
}
