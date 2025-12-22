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
    private const val LOCAL_JAR_NAME = "shower-server.jar"
    private const val SERVER_PORT = 8986

    /**
     * 确保服务器已启动
     */
    suspend fun ensureServerStarted(context: Context): Boolean {
        // 检查服务器是否已在监听
        if (isServerListening()) {
            Log.d(TAG, "Shower server already listening on 127.0.0.1:$SERVER_PORT")
            return true
        }

        val appContext = context.applicationContext
        val jarFile = try {
            copyJarToExternalDir(appContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy shower-server.jar", e)
            return false
        }

        // 停止现有服务器
        val killCmd = "pkill -f com.ai.assistance.shower.Main >/dev/null 2>&1 || true"
        Log.d(TAG, "Stopping existing server: $killCmd")
        AndroidShellExecutor.executeCommand(killCmd)

        // 复制 jar 到 /data/local/tmp
        val remoteJarPath = "/data/local/tmp/$LOCAL_JAR_NAME"
        val copyCmd = "cp ${jarFile.absolutePath} $remoteJarPath"
        Log.d(TAG, "Copying jar: $copyCmd")
        val copyResult = AndroidShellExecutor.executeCommand(copyCmd)
        if (!copyResult.success) {
            Log.e(TAG, "Failed to copy jar: ${copyResult.error}")
            return false
        }

        // 启动服务器
        val startCmd = "CLASSPATH=$remoteJarPath app_process / com.ai.assistance.shower.Main &"
        Log.d(TAG, "Starting server: $startCmd")
        val startResult = AndroidShellExecutor.executeCommand(startCmd)
        if (!startResult.success) {
            Log.e(TAG, "Failed to start server: ${startResult.error}")
            return false
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
     * 复制 jar 到外部目录
     */
    private suspend fun copyJarToExternalDir(context: Context): File = withContext(Dispatchers.IO) {
        val baseDir = File("/sdcard/Download/ZiZip")
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        val outFile = File(baseDir, LOCAL_JAR_NAME)
        
        try {
            context.assets.open(ASSET_JAR_NAME).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Copied $ASSET_JAR_NAME to ${outFile.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "Asset $ASSET_JAR_NAME not found, using placeholder")
            // 如果 assets 中没有 jar，创建一个占位文件
            // 实际使用时需要添加真正的 shower-server.jar
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
