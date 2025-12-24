package com.autoglm.android.core.agent

import android.content.Context
import android.content.Intent
import android.util.Log
import com.autoglm.android.core.debug.DebugLogger
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

    // Shower 虚拟屏幕支持（ShowerController 是单例 object）
    private var isShowerAvailable = false
    
    /**
     * 执行任务
     */
    suspend fun runTask(taskDescription: String): TaskExecution = withContext(Dispatchers.IO) {
        DebugLogger.i(TAG, "========== 任务开始 ==========")
        DebugLogger.i(TAG, "任务描述: $taskDescription")

        // 检查权限
        DebugLogger.d(TAG, "检查 Shizuku 权限...")
        if (!ShizukuAuthorizer.hasShizukuPermission()) {
            DebugLogger.e(TAG, "缺少 Shizuku 权限", null)
            DebugLogger.e(TAG, "解决方法: 打开 Shizuku app 并授权 ZiZip", null)
            return@withContext TaskExecution(
                taskDescription = taskDescription,
                status = TaskStatus.FAILED,
                errorMessage = "缺少 Shizuku 权限。请打开 Shizuku app 授权 ZiZip。",
                debugInfo = buildString {
                    """
                    1. 确认 Shizuku app 已安装
                    2. 打开 Shizuku app
                    3. 找到 ZiZip 并点击授权
                    4. 返回 ZiZip 重试
                    """.trimIndent()
                }
            )
        }
        DebugLogger.i(TAG, "✓ Shizuku 权限已授予")

        // 初始化 Shower 虚拟屏幕
        val showerEnabled = settingsRepo.isShowerEnabled()
        DebugLogger.i(TAG, "Shower 设置: ${if (showerEnabled) "启用" else "禁用"}")

        if (showerEnabled) {
            try {
                DebugLogger.d(TAG, "初始化 Shower 虚拟屏幕...")
                DebugLogger.d(TAG, "1. 启动 Shower 服务器...")
                val serverStarted = ShowerServerManager.startServer(context)

                if (serverStarted) {
                    DebugLogger.i(TAG, "  ✓ Shower 服务器已启动")
                } else {
                    DebugLogger.w(TAG, "  ✗ Shower 服务器启动失败")
                    DebugLogger.w(TAG, "    原因: shower-server.apk 不存在或启动失败")
                    DebugLogger.w(TAG, "    影响: 将使用普通截图模式（无虚拟屏幕）")
                }

                DebugLogger.d(TAG, "2. 创建虚拟显示器...")
                val metrics = context.resources.displayMetrics
                CoordinateNormalizer.init(metrics.widthPixels, metrics.heightPixels)
                val displayCreated = ShowerController.ensureDisplay(
                    metrics.widthPixels,
                    metrics.heightPixels,
                    metrics.densityDpi,
                    bitrateKbps = 3000
                )
                isShowerAvailable = displayCreated

                if (displayCreated) {
                    DebugLogger.i(TAG, "  ✓ 虚拟显示器已创建 (${metrics.widthPixels}x${metrics.heightPixels})")
                } else {
                    DebugLogger.w(TAG, "  ✗ 虚拟显示器创建失败")
                }

                // 显示虚拟屏幕边框
                if (displayCreated) {
                    setVirtualBorderVisible(true)
                }
            } catch (e: Exception) {
                DebugLogger.e(TAG, "Shower 初始化异常: ${e.message}", e)
                DebugLogger.w(TAG, "继续使用普通模式（降级）")
                isShowerAvailable = false
            }
        }

        // 初始化坐标系统
        val metrics = context.resources.displayMetrics
        CoordinateNormalizer.init(metrics.widthPixels, metrics.heightPixels)
        DebugLogger.d(TAG, "屏幕尺寸: ${metrics.widthPixels}x${metrics.heightPixels}")
        DebugLogger.d(TAG, "坐标系统: ${settingsRepo.getCoordinateSystem()}")

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

                DebugLogger.d(TAG, "----- 步骤 $stepCount -----")

                // 截图
                DebugLogger.d(TAG, "执行截图...")
                val screenshotPath = captureScreenshot()
                if (screenshotPath == null) {
                    DebugLogger.e(TAG, "截图失败", null)
                    DebugLogger.e(TAG, "可能原因: Shizuku 服务未运行或权限不足")
                    updateExecution {
                        copy(
                            status = TaskStatus.FAILED,
                            errorMessage = "截图失败",
                            debugInfo = buildString {
                                """
                                截图操作失败，请检查：
                                1. Shizuku 服务是否正在运行
                                2. ZiZip 是否已获得 Shizuku 授权
                                3. 运行: adb shell pm list packages | grep shizuku
                                """.trimIndent()
                            }
                        )
                    }
                    break
                }
                DebugLogger.i(TAG, "✓ 截图成功: $screenshotPath")

                // 调用 AI
                DebugLogger.d(TAG, "发送请求到 AI API...")
                val stepResult = try {
                    val response = apiClient.sendStep(
                        taskDescription = taskDescription,
                        screenshotPath = screenshotPath,
                        stepNumber = stepCount,
                        previousActions = _execution.value?.steps?.map { it.action } ?: emptyList()
                    )
                    parseAIResponse(response)
                } catch (e: Exception) {
                    DebugLogger.e(TAG, "AI API 请求失败: ${e.message}", e)
                    StepResult(
                        success = false,
                        action = null,
                        thinking = "",
                        message = "AI 请求失败: ${e.message}",
                        shouldContinue = false
                    )
                }

                if (!stepResult.success) {
                    DebugLogger.e(TAG, "AI 返回错误: ${stepResult.message}", null)
                    updateExecution {
                        copy(
                            status = TaskStatus.FAILED,
                            errorMessage = stepResult.message,
                            debugInfo = "AI 思考过程: ${stepResult.thinking}"
                        )
                    }
                    break
                }

                // 记录 AI 思考过程
                if (stepResult.thinking.isNotEmpty()) {
                    DebugLogger.d(TAG, "AI 思考: ${stepResult.thinking.take(100)}...")
                }

                // 执行动作
                val action = stepResult.action
                if (action != null) {
                    DebugLogger.d(TAG, "执行动作: $action")
                    val actionSuccess = executeAction(action)
                    if (!actionSuccess && action !is AgentAction.Finish) {
                        DebugLogger.w(TAG, "动作执行失败，继续尝试")
                    } else if (actionSuccess) {
                        DebugLogger.i(TAG, "✓ 动作执行成功")
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
            DebugLogger.i(TAG, "任务已取消")
            updateExecution {
                copy(
                    status = TaskStatus.CANCELLED,
                    endTime = System.currentTimeMillis(),
                    debugInfo = "用户取消任务"
                )
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "任务异常: ${e.message}", e)
            updateExecution {
                copy(
                    status = TaskStatus.FAILED,
                    endTime = System.currentTimeMillis(),
                    errorMessage = e.message,
                    debugInfo = buildString {
                        appendLine("异常类型: ${e.javaClass.simpleName}")
                        appendLine("异常消息: ${e.message}")
                        appendLine("堆栈跟踪:")
                        appendLine(android.util.Log.getStackTraceString(e).take(1000))
                    }
                )
            }
        } finally {
            DebugLogger.i(TAG, "========== 任务结束 ==========")
            // 任务结束，隐藏虚拟屏幕边框
            setVirtualBorderVisible(false)
            // 关闭 Shower 连接
            try {
                ShowerController.shutdown()
                DebugLogger.d(TAG, "Shower 连接已关闭")
            } catch (e: Exception) {
                DebugLogger.w(TAG, "关闭 Shower 连接失败", e)
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
            DebugLogger.d(TAG, "虚拟边框: ${if (visible) "显示" else "隐藏"}")
        } catch (e: Exception) {
            DebugLogger.w(TAG, "设置虚拟边框失败", e)
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
                DebugLogger.d(TAG, "使用 Shower 截图...")
                // 尝试 2 次 Shower 截图（防止暂时性失败）
                var showerBytes: ByteArray? = null
                repeat(2) { attempt ->
                    if (showerBytes == null) {
                        try {
                            showerBytes = ShowerController.requestScreenshot(timeoutMs = 3000L)
                            if (showerBytes != null) {
                                DebugLogger.i(TAG, "Shower 截图成功 (尝试 ${attempt + 1})")
                                return@repeat
                            }
                        } catch (e: Exception) {
                            DebugLogger.w(TAG, "Shower 截图失败 (尝试 ${attempt + 1}): ${e.message}")
                            if (attempt < 1) delay(500)
                        }
                    }
                }

                // 使用局部变量避免 smart cast 问题
                val bytes = showerBytes
                if (bytes != null) {
                    File(path).writeBytes(bytes)
                    DebugLogger.i(TAG, "✓ Shower 截图成功: $path")
                    return path
                }
            } catch (e: Exception) {
                DebugLogger.w(TAG, "Shower 截图失败，降级到 screencap", e)
            }
        }

        // 降级到 screencap 命令
        DebugLogger.d(TAG, "使用 screencap 命令截图...")
        val success = AndroidShellExecutor.screenshot(path)
        return if (success) {
            DebugLogger.i(TAG, "✓ screencap 截图成功: $path")
            path
        } else {
            DebugLogger.e(TAG, "截图失败", null)
            null
        }
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
            DebugLogger.w(TAG, "空动作字符串")
            return AgentAction.Finish("完成（无动作）")
        }

        // 解析格式: action(params)
        val regex = Regex("""(\w+)\(([^)]*)\)""")
        val match = regex.find(trimmed)

        if (match == null) {
            DebugLogger.e(TAG, "动作解析失败: '$actionStr' (格式不匹配)", null)
            return null
        }

        val actionName = match.groupValues[1].lowercase()
        val params = match.groupValues[2]

        DebugLogger.d(TAG, "解析动作: $actionName($params)")

        return when (actionName) {
            "launch" -> AgentAction.Launch(params.trim().removeSurrounding("\""))
            "tap" -> {
                val coords = params.split(",").map { it.trim().toIntOrNull() ?: 0 }
                if (coords.size >= 2) AgentAction.Tap(coords[0], coords[1]) else {
                    DebugLogger.e(TAG, "无效的点击坐标: $params", null)
                    null
                }
            }
            "type" -> AgentAction.Type(params.trim().removeSurrounding("\""))
            "swipe" -> {
                val coords = params.split(",").map { it.trim().toIntOrNull() ?: 0 }
                if (coords.size >= 4) {
                    AgentAction.Swipe(coords[0], coords[1], coords[2], coords[3], coords.getOrNull(4) ?: 300)
                } else {
                    DebugLogger.e(TAG, "无效的滑动坐标: $params (需要 x1,y1,x2,y2)", null)
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
                DebugLogger.e(TAG, "未知动作类型: '$actionName'", null)
                null
            }
        }
    }
    
    /**
     * 执行动作
     */
    private suspend fun executeAction(action: AgentAction): Boolean {
        DebugLogger.d(TAG, "执行动作: $action")

        return when (action) {
            is AgentAction.Launch -> {
                // 如果 Shower 可用，通过 Shower 启动
                if (isShowerAvailable) {
                    ShowerController.launchApp(action.packageName)
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
                    ShowerController.tap(pixelX, pixelY)
                } else {
                    AndroidShellExecutor.tap(pixelX, pixelY)
                }
            }
            is AgentAction.Type -> {
                // 使用 ImeManager 自动管理输入法切换
                com.autoglm.android.core.ime.ImeManager.inputText(context, action.text)
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
                    ShowerController.swipe(sx, sy, ex, ey, action.duration.toLong())
                } else {
                    AndroidShellExecutor.swipe(sx, sy, ex, ey, action.duration)
                }
            }
            is AgentAction.Back -> {
                if (isShowerAvailable) {
                    ShowerController.key(4) // KEYCODE_BACK
                } else {
                    AndroidShellExecutor.pressBack()
                }
            }
            is AgentAction.Home -> {
                if (isShowerAvailable) {
                    ShowerController.key(3) // KEYCODE_HOME
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
