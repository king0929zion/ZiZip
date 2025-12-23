package com.autoglm.android.core.agent

import android.content.Context
import android.util.Log
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
        if (checkAssetExists(context, ASSET_APK_NAME)) return true
        // 降级检查 JAR（兼容方式）
        return checkAssetExists(context, ASSET_JAR_NAME)
    }

    private fun checkAssetExists(context: Context, assetName: String): Boolean {
        return try {
            context.assets.open(assetName).use { it.available() > 0 }
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
        // 检查服务器是否已在监听
        if (isServerListening()) {
            Log.d(TAG, "Shower server already listening on 127.0.0.1:$SERVER_PORT")
            return true
        }

        // 检查服务器文件是否存在
        if (!isJarAvailable(context)) {
            Log.e(TAG, "shower-server.apk not found in assets.")
            Log.e(TAG, "To build: cd tools && ./gradlew assembleDebug")
            Log.e(TAG, "Then copy: cp tools/app/build/outputs/apk/debug/app-debug.apk app/src/main/assets/shower-server.apk")
            return false
        }

        val appContext = context.applicationContext
        val jarFile = try {
            copyJarToExternalDir(appContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy shower-server.apk", e)
            return false
        }

        // 检查文件是否存在且有内容
        if (!jarFile.exists() || jarFile.length() == 0L) {
            Log.e(TAG, "APK file is empty or doesn't exist: ${jarFile.absolutePath}")
            return false
        }

        // 停止现有服务器
        val killCmd = "pkill -f com.ai.assistance.shower.Main >/dev/null 2>&1 || true"
        Log.d(TAG, "Stopping existing server: $killCmd")
        AndroidShellExecutor.executeCommand(killCmd)

        // 复制 APK 到 /data/local/tmp
        val remoteJarPath = "/data/local/tmp/${getLocalFileName()}"
        val copyCmd = "cp ${jarFile.absolutePath} $remoteJarPath"
        Log.d(TAG, "Copying apk: $copyCmd")
        val copyResult = AndroidShellExecutor.executeCommand(copyCmd)
        if (!copyResult.success) {
            Log.e(TAG, "Failed to copy apk: ${copyResult.error}")
            return false
        }

        // 给予执行权限
        val chmodCmd = "chmod +x $remoteJarPath"
        AndroidShellExecutor.executeCommand(chmodCmd)

        // 启动服务器（后台运行）
        val startCmd = "CLASSPATH=$remoteJarPath app_process / com.ai.assistance.shower.Main >/dev/null 2>&1 &"
        Log.d(TAG, "Starting server: $startCmd")
        val startResult = AndroidShellExecutor.executeCommand(startCmd)
        if (!startResult.success) {
            Log.w(TAG, "Start command returned error, but server may still be starting: ${startResult.error}")
        }

        // 等待服务器启动（最多 10 秒）
        for (attempt in 0 until 50) {
            delay(200)
            if (isServerListening()) {
                Log.d(TAG, "Server started after ${(attempt + 1) * 200}ms")
                return true
            }
        }

        Log.e(TAG, "Server did not start within expected time")
        return false
    }

    /**
     * 停止服务器
     */
    suspend fun stopServer(): Boolean {
        val cmd = "pkill -f com.ai.assistance.shower.Main >/dev/null 2>&1 || true"
        val result = AndroidShellExecutor.executeCommand(cmd)
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
            Log.d(TAG, "Copied $assetName to ${outFile.absolutePath} (${outFile.length()} bytes)")
        } catch (e: Exception) {
            // 如果 APK 不存在，尝试 JAR
            if (assetName == ASSET_APK_NAME) {
                Log.w(TAG, "APK not found, trying JAR fallback")
                context.assets.open(ASSET_JAR_NAME).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Copied $ASSET_JAR_NAME to ${outFile.absolutePath} (${outFile.length()} bytes)")
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
