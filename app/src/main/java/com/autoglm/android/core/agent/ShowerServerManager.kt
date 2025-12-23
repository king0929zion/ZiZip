package com.autoglm.android.core.agent

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.autoglm.android.core.debug.DebugLogger
import com.autoglm.android.core.shizuku.AndroidShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Shower 服务器管理器
 * 管理 Shower Server 独立应用的启动
 *
 * 架构说明：
 * - Shower Server 是一个独立应用（包名: com.ai.assistance.shower）
 * - ZiZip 通过 WebSocket 连接到 Shower Server
 * - 用户需要分别安装两个应用
 */
object ShowerServerManager {
    private const val TAG = "ShowerServerManager"
    private const val SHOWER_PACKAGE = "com.ai.assistance.shower"
    private const val SERVER_PORT = 8986
    private const val SHOWER_MAIN = "com.ai.assistance.shower.Main"

    /**
     * 检查 Shower Server 应用是否已安装
     */
    fun isShowerAppInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(SHOWER_PACKAGE, 0)
            DebugLogger.d(TAG, "✓ Shower Server 应用已安装")
            true
        } catch (e: PackageManager.NameNotFoundException) {
            DebugLogger.w(TAG, "✗ Shower Server 应用未安装", null)
            false
        }
    }

    /**
     * 启动 Shower Server
     * 通过 app_process 启动已安装的 Shower Server 应用
     */
    suspend fun startServer(context: Context): Boolean = withContext(Dispatchers.IO) {
        DebugLogger.i(TAG, "========== 启动 Shower Server ==========")

        // 检查应用是否安装
        if (!isShowerAppInstalled(context)) {
            DebugLogger.e(TAG, "✗ Shower Server 应用未安装", null)
            DebugLogger.e(TAG, "", null)
            DebugLogger.e(TAG, "请先安装 Shower Server 应用:", null)
            DebugLogger.e(TAG, "  1. 从 GitHub 下载 Shower Server APK", null)
            DebugLogger.e(TAG, "  2. 在设备上安装 Shower Server", null)
            DebugLogger.e(TAG, "  3. 打开 Shower Server 应用一次", null)
            return@withContext false
        }

        // 检查是否已在运行
        if (isServerListening()) {
            DebugLogger.i(TAG, "✓ Shower Server 已在运行")
            return@withContext true
        }

        // 通过 am start-service 启动 Shower Server
        // 使用 app_process 方式启动
        val cmd = "CLASSPATH=/data/app/$SHOWER_PACKAGE*/base.apk app_process / com.ai.assistance.shower.Main >/data/local/tmp/shower.log 2>&1 &"
        DebugLogger.d(TAG, "启动命令: $cmd")

        // 首先尝试通过 am 启动
        val startCmd = "am startservice -n $SHOWER_PACKAGE/.Main 2>/dev/null || $cmd"
        val result = AndroidShellExecutor.executeCommand(startCmd)

        if (!result.success) {
            DebugLogger.w(TAG, "启动命令返回错误，但服务可能仍在启动", null)
        }

        // 等待服务器启动（最多 10 秒）
        DebugLogger.d(TAG, "等待服务器启动...")
        for (attempt in 0 until 50) {
            delay(200)
            if (isServerListening()) {
                DebugLogger.i(TAG, "✓ Shower Server 启动成功 (${(attempt + 1) * 200}ms)")
                return@withContext true
            }
        }

        DebugLogger.e(TAG, "✗ Shower Server 启动超时", null)
        DebugLogger.e(TAG, "调试信息:", null)
        DebugLogger.e(TAG, "  1. 检查日志: adb shell cat /data/local/tmp/shower.log", null)
        DebugLogger.e(TAG, "  2. 检查进程: adb shell ps | grep shower", null)
        DebugLogger.e(TAG, "  3. 手动打开 Shower Server 应用", null)
        false
    }

    /**
     * 停止服务器
     */
    suspend fun stopServer(): Boolean {
        DebugLogger.d(TAG, "停止 Shower Server...")
        val cmd = "pkill -f $SHOWER_MAIN >/dev/null 2>&1 || true"
        val result = AndroidShellExecutor.executeCommand(cmd)
        DebugLogger.i(TAG, if (result.success) "✓ 服务器已停止" else "停止命令执行完成")
        return result.success
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

    /**
     * 获取 Shower Server 下载信息
     */
    fun getDownloadInfo(): DownloadInfo {
        return DownloadInfo(
            packageName = SHOWER_PACKAGE,
            appName = "Shower Server",
            description = "虚拟屏幕服务应用",
            version = "1.0",
            downloadUrl = "https://github.com/king0929zion/ZiZip/releases",
            instructions = listOf(
                "1. 下载 Shower Server APK",
                "2. 在设备上安装 APK",
                "3. 打开 Shower Server 应用一次（初始化服务）",
                "4. 返回 ZiZip 使用 Agent 模式"
            )
        )
    }

    data class DownloadInfo(
        val packageName: String,
        val appName: String,
        val description: String,
        val version: String,
        val downloadUrl: String,
        val instructions: List<String>
    )
}
