package com.autoglm.android.core.shizuku

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Android Shell 命令执行器
 * 通过 Shizuku 执行 ADB 级别的 shell 命令
 */
object AndroidShellExecutor {
    private const val TAG = "AndroidShellExecutor"
    private const val DEFAULT_TIMEOUT_MS = 30000L

    data class CommandResult(
        val exitCode: Int,
        val output: String,
        val error: String,
        val success: Boolean = exitCode == 0
    )

    /**
     * 执行 shell 命令
     */
    suspend fun executeCommand(
        command: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): CommandResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Executing command: $command")
        
        // 检查 Shizuku 权限
        if (!ShizukuAuthorizer.hasShizukuPermission()) {
            Log.e(TAG, "No Shizuku permission")
            return@withContext CommandResult(
                exitCode = -1,
                output = "",
                error = "No Shizuku permission",
                success = false
            )
        }

        try {
            val result = withTimeoutOrNull(timeoutMs) {
                executeShizukuCommand(command)
            }
            
            result ?: CommandResult(
                exitCode = -1,
                output = "",
                error = "Command timed out after ${timeoutMs}ms",
                success = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command: ${e.message}", e)
            CommandResult(
                exitCode = -1,
                output = "",
                error = e.message ?: "Unknown error",
                success = false
            )
        }
    }

    private fun executeShizukuCommand(command: String): CommandResult {
        var process: Process? = null
        try {
            // 使用 Runtime.exec 并依赖 Shizuku 的 shell 权限
            // Shizuku 授权后，应用可以执行需要 shell 权限的命令
            val processBuilder = ProcessBuilder("sh", "-c", command)
            processBuilder.redirectErrorStream(false)
            process = processBuilder.start()
            
            val outputReader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val errorReader = java.io.BufferedReader(java.io.InputStreamReader(process.errorStream))
            
            val output = StringBuilder()
            val error = StringBuilder()
            
            // 读取输出
            var line: String?
            while (outputReader.readLine().also { line = it } != null) {
                output.appendLine(line)
            }
            
            // 读取错误
            while (errorReader.readLine().also { line = it } != null) {
                error.appendLine(line)
            }
            
            val exitCode = process.waitFor()
            
            Log.d(TAG, "Command exit code: $exitCode")
            Log.d(TAG, "Command output: ${output.toString().take(500)}")
            if (error.isNotEmpty()) {
                Log.w(TAG, "Command error: ${error.toString().take(500)}")
            }
            
            return CommandResult(
                exitCode = exitCode,
                output = output.toString().trim(),
                error = error.toString().trim(),
                success = exitCode == 0
            )
        } finally {
            try {
                process?.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying process", e)
            }
        }
    }

    /**
     * 执行 tap 命令
     */
    suspend fun tap(x: Int, y: Int): Boolean {
        val result = executeCommand("input tap $x $y")
        return result.success
    }

    /**
     * 执行 swipe 命令
     */
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 300): Boolean {
        val result = executeCommand("input swipe $x1 $y1 $x2 $y2 $durationMs")
        return result.success
    }

    /**
     * 执行 keyevent 命令
     */
    suspend fun keyEvent(keyCode: Int): Boolean {
        val result = executeCommand("input keyevent $keyCode")
        return result.success
    }

    /**
     * 执行文本输入命令
     */
    suspend fun inputText(text: String): Boolean {
        // 对特殊字符进行转义
        val escapedText = text.replace("'", "'\\''")
        val result = executeCommand("input text '$escapedText'")
        return result.success
    }

    /**
     * 启动应用
     */
    suspend fun launchApp(packageName: String): Boolean {
        val result = executeCommand(
            "monkey -p $packageName -c android.intent.category.LAUNCHER 1 2>/dev/null"
        )
        return result.success
    }

    /**
     * 截图并保存到指定路径
     */
    suspend fun screenshot(outputPath: String): Boolean {
        val result = executeCommand("screencap -p $outputPath")
        return result.success
    }

    /**
     * 按返回键
     */
    suspend fun pressBack(): Boolean = keyEvent(4)

    /**
     * 按 Home 键
     */
    suspend fun pressHome(): Boolean = keyEvent(3)

    /**
     * 获取当前前台应用包名
     */
    suspend fun getCurrentPackage(): String? {
        val result = executeCommand(
            "dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' | head -1"
        )
        if (!result.success) return null
        
        // 解析输出获取包名
        val regex = Regex("\\{[^}]*([a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+)/")
        val match = regex.find(result.output)
        return match?.groupValues?.getOrNull(1)
    }
}
