package com.autoglm.android.core.agent

import android.content.Context
import android.util.Log
import com.autoglm.android.core.debug.DebugLogger
import com.autoglm.android.core.shizuku.AndroidShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Shower 服务器管理器
 * 管理虚拟屏幕服务器的生命周期
 * 参考 Operit 实现
 */
object ShowerServerManager {
    private const val TAG = "ShowerServerManager"
    private const val ASSET_JAR_NAME = "shower-server.jar"
    private const val ASSET_APK_NAME = "shower-server.apk"
    private const val LOCAL_JAR_NAME = "shower-server.apk"
    private const val SERVER_PORT = 8986

    /**
     * 检查服务器文件是否存在（JAR 或 APK）
     */
    fun isJarAvailable(context: Context): Boolean {
        // 优先检查 APK（推荐方式）
        if (checkAssetExists(context, ASSET_APK_NAME)) {
            DebugLogger.d(TAG, "APK exists in assets: $ASSET_APK_NAME")
            return true
        }
        // 降级检查 JAR（兼容方式）
        if (checkAssetExists(context, ASSET_JAR_NAME)) {
            DebugLogger.d(TAG, "JAR exists in assets: $ASSET_JAR_NAME")
            return true
        }
        DebugLogger.e(TAG, "No shower-server file found in assets", null)
        return false
    }

    private fun checkAssetExists(context: Context, assetName: String): Boolean {
        return try {
            val size = context.assets.open(assetName).use { it.available() }
            DebugLogger.d(TAG, "Asset $assetName size: $size bytes")
            size > 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取资源文件名（优先 APK）
     */
    private fun getAssetFileName(): String {
        return ASSET_APK_NAME // 优先使用 APK
    }

    /**
     * 获取本地文件名（带 .apk 后缀）
     */
    private fun getLocalFileName(): String {
        return "shower-server.apk"
    }

    /**
     * 确保服务器已启动
     */
    suspend fun ensureServerStarted(context: Context): Boolean {
        DebugLogger.i(TAG, "========== Shower 服务器启动流程 ==========")

        // 检查服务器是否已在监听
        if (isServerListening()) {
            DebugLogger.i(TAG, "✓ Shower 服务器已在运行 (127.0.0.1:$SERVER_PORT)")
            return true
        }

        // 检查服务器文件是否存在
        DebugLogger.d(TAG, "检查 shower-server.apk 资源...")
        if (!isJarAvailable(context)) {
            DebugLogger.e(TAG, "✗ shower-server.apk 不存在于 assets 目录", null)
            DebugLogger.e(TAG, "解决方法:", null)
            DebugLogger.e(TAG, "  1. 等待 GitHub Actions 自动构建", null)
            DebugLogger.e(TAG, "  2. 或手动构建: cd tools && ./gradlew assembleDebug", null)
            DebugLogger.e(TAG, "  3. 复制 APK: cp tools/app/build/outputs/apk/debug/app-debug.apk app/src/main/assets/shower-server.apk", null)
            return false
        }
        DebugLogger.i(TAG, "✓ 资源文件存在")

        val appContext = context.applicationContext
        val jarFile = try {
            DebugLogger.d(TAG, "复制 APK 到外部目录...")
            copyJarToExternalDir(appContext)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "✗ 复制 APK 失败: ${e.message}", e)
            return false
        }

        // 检查文件是否存在且有内容
        if (!jarFile.exists() || jarFile.length() == 0L) {
            DebugLogger.e(TAG, "✗ APK 文件为空或不存在: ${jarFile.absolutePath}", null)
            return false
        }
        DebugLogger.i(TAG, "✓ APK 已复制 (${jarFile.length()} bytes)")

        // 停止现有服务器
        DebugLogger.d(TAG, "停止现有 Shower 服务器进程...")
        val killCmd = "pkill -f com.ai.assistance.shower.Main >/dev/null 2>&1 || true"
        AndroidShellExecutor.executeCommand(killCmd)
        delay(500) // 等待进程完全终止

        // 复制 APK 到 /data/local/tmp
        val remoteJarPath = "/data/local/tmp/${getLocalFileName()}"
        DebugLogger.d(TAG, "复制 APK 到 $remoteJarPath...")
        val copyCmd = "cp ${jarFile.absolutePath} $remoteJarPath"
        val copyResult = AndroidShellExecutor.executeCommand(copyCmd)
        if (!copyResult.success) {
            DebugLogger.e(TAG, "✗ 复制 APK 到 /data/local/tmp 失败: ${copyResult.error}", null)
            DebugLogger.e(TAG, "可能原因: 存储权限不足或 SELinux 限制", null)
            return false
        }
        DebugLogger.i(TAG, "✓ APK 已复制到 /data/local/tmp")

        // 给予执行权限
        DebugLogger.d(TAG, "设置执行权限...")
        val chmodCmd = "chmod 644 $remoteJarPath" // APK 不需要执行权限
        AndroidShellExecutor.executeCommand(chmodCmd)

        // 启动服务器（后台运行）
        DebugLogger.d(TAG, "启动 Shower 服务器...")
        val startCmd = "CLASSPATH=$remoteJarPath app_process / com.ai.assistance.shower.Main >/data/local/tmp/shower.log 2>&1 &"
        DebugLogger.d(TAG, "启动命令: $startCmd")
        val startResult = AndroidShellExecutor.executeCommand(startCmd)
        if (!startResult.success) {
            DebugLogger.w(TAG, "启动命令返回错误，但服务器可能仍在启动中", null)
        }

        // 等待服务器启动（最多 10 秒）
        DebugLogger.d(TAG, "等待服务器启动...")
        for (attempt in 0 until 50) {
            delay(200)
            if (isServerListening()) {
                DebugLogger.i(TAG, "✓ Shower 服务器启动成功 (${(attempt + 1) * 200}ms)")
                return true
            }
        }

        DebugLogger.e(TAG, "✗ Shower 服务器启动超时", null)
        DebugLogger.e(TAG, "调试信息:", null)
        DebugLogger.e(TAG, "  1. 检查日志: adb shell cat /data/local/tmp/shower.log", null)
        DebugLogger.e(TAG, "  2. 检查进程: adb shell ps | grep shower", null)
        DebugLogger.e(TAG, "  3. 检查端口: adb shell netstat -an | grep 8986", null)
        return false
    }

    /**
     * 停止服务器
     */
    suspend fun stopServer(): Boolean {
        DebugLogger.d(TAG, "停止 Shower 服务器...")
        val cmd = "pkill -f com.ai.assistance.shower.Main >/dev/null 2>&1 || true"
        val result = AndroidShellExecutor.executeCommand(cmd)
        DebugLogger.i(TAG, if (result.success) "✓ 服务器已停止" else "停止命令执行完成")
        return result.success
    }

    /**
     * 复制 APK 到外部目录
     */
    private suspend fun copyJarToExternalDir(context: Context): File = withContext(Dispatchers.IO) {
        val baseDir = File("/sdcard/Download/ZiZip")
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        val outFile = File(baseDir, getLocalFileName())

        // 优先使用 APK，降级使用 JAR
        val assetName = getAssetFileName()
        try {
            context.assets.open(assetName).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            DebugLogger.d(TAG, "已复制 $assetName 到 ${outFile.absolutePath} (${outFile.length()} bytes)")
        } catch (e: Exception) {
            // 如果 APK 不存在，尝试 JAR
            if (assetName == ASSET_APK_NAME) {
                DebugLogger.w(TAG, "APK 不存在，尝试 JAR 降级", null)
                context.assets.open(ASSET_JAR_NAME).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                DebugLogger.d(TAG, "已复制 $ASSET_JAR_NAME 到 ${outFile.absolutePath} (${outFile.length()} bytes)")
            } else {
                throw e
            }
        }

        outFile
    }

    /**
     * 检查服务器是否在监听
     */
    private suspend fun isServerListening(): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", SERVER_PORT), 200)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
