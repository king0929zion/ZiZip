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
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Shower 服务器管理器
 * 管理 Shower Server 的启动
 *
 * 架构说明（参考 Operit 实现）：
 * 1. 首先从 assets 复制 shower-server.apk 到设备存储
 * 2. 如果应用未安装，自动安装 APK
 * 3. 通过 app_process 启动 Shower Server 服务
 * 4. 支持已安装应用的直接启动
 */
object ShowerServerManager {
    private const val TAG = "ShowerServerManager"
    private const val SHOWER_PACKAGE = "com.ai.assistance.shower"
    private const val SERVER_PORT = 8986
    private const val SHOWER_MAIN = "com.ai.assistance.shower.ShowerServer"
    private const val ASSET_APK_NAME = "shower-server.apk"
    private const val LOCAL_APK_NAME = "shower-server.apk"

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
     * 从 assets 复制 APK 到外部存储
     * 参考 Operit 的 copyJarToExternalDir 实现
     */
    private suspend fun copyApkToExternalDir(context: Context): File? = withContext(Dispatchers.IO) {
        val baseDir = File("/sdcard/Download/ZiZip")
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        val outFile = File(baseDir, LOCAL_APK_NAME)

        try {
            context.assets.open(ASSET_APK_NAME).use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }
            DebugLogger.i(TAG, "✓ 已复制 APK 到: ${outFile.absolutePath}")
            outFile
        } catch (e: Exception) {
            DebugLogger.e(TAG, "✗ 复制 APK 失败: ${e.message}", e)
            null
        }
    }

    /**
     * 安装 APK 到系统
     */
    private suspend fun installApk(apkPath: String): Boolean = withContext(Dispatchers.IO) {
        val cmd = "pm install -r -g \"$apkPath\""
        DebugLogger.d(TAG, "安装命令: $cmd")
        val result = AndroidShellExecutor.executeCommand(cmd)

        if (result.success) {
            DebugLogger.i(TAG, "✓ APK 安装成功")
            true
        } else {
            DebugLogger.e(TAG, "✗ APK 安装失败: ${result.stderr}", null)
            false
        }
    }

    /**
     * 启动 Shower Server
     * 自动从 assets 安装 APK（如果需要）并启动服务
     */
    suspend fun startServer(context: Context): Boolean = withContext(Dispatchers.IO) {
        DebugLogger.i(TAG, "========== 启动 Shower Server ==========")

        // 检查是否已在运行
        if (isServerListening()) {
            DebugLogger.i(TAG, "✓ Shower Server 已在运行")
            return@withContext true
        }

        // 检查应用是否安装
        var installed = isShowerAppInstalled(context)

        // 如果未安装，尝试从 assets 复制并安装
        if (!installed) {
            DebugLogger.i(TAG, "应用未安装，正在从 assets 安装...")

            val apkFile = copyApkToExternalDir(context)
            if (apkFile != null) {
                if (installApk(apkFile.absolutePath)) {
                    // 等待安装完成
                    delay(1000)
                    installed = isShowerAppInstalled(context)
                }
            }

            if (!installed) {
                DebugLogger.e(TAG, "", null)
                DebugLogger.e(TAG, "✗ 无法自动安装 Shower Server", null)
                DebugLogger.e(TAG, "", null)
                DebugLogger.e(TAG, "请手动安装 Shower Server:", null)
                DebugLogger.e(TAG, "  1. 从 GitHub 下载 Shower Server APK", null)
                DebugLogger.e(TAG, "  2. 在设备上安装 APK", null)
                DebugLogger.e(TAG, "  3. 打开 Shower Server 应用一次", null)
                DebugLogger.e(TAG, "", null)
                DebugLogger.e(TAG, "下载地址: https://github.com/king0929zion/ZiZip/releases", null)
                return@withContext false
            }
        }

        // 通过 app_process 启动 Shower Server 服务
        // 使用已安装 APK 的路径
        val cmd = "CLASSPATH=/data/app/$SHOWER_PACKAGE*/base.apk app_process / $SHOWER_MAIN >/data/local/tmp/shower.log 2>&1 &"
        DebugLogger.d(TAG, "启动命令: $cmd")

        // 首先尝试通过 am start 启动 Activity（确保应用初始化）
        val startCmd = "am start -n $SHOWER_PACKAGE/.MainActivity 2>/dev/null; $cmd"
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
        DebugLogger.e(TAG, "", null)
        DebugLogger.e(TAG, "调试信息:", null)
        DebugLogger.e(TAG, "  1. 检查日志: adb shell cat /data/local/tmp/shower.log", null)
        DebugLogger.e(TAG, "  2. 检查进程: adb shell ps | grep shower", null)
        DebugLogger.e(TAG, "  3. 手动打开 Shower Server 应用", null)
        false
    }

    /**
     * 停止服务器
     * 参考 Operit 实现，使用 || true 确保命令总是返回成功
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
     * 获取 Shower Server 信息
     * 更新为反映新的自动安装功能
     */
    fun getServerInfo(): ServerInfo {
        return ServerInfo(
            packageName = SHOWER_PACKAGE,
            appName = "Shower Server",
            description = "虚拟屏幕服务应用（支持自动安装）",
            version = "1.0",
            sourceUrl = "https://github.com/king0929zion/ZiZip/releases",
            features = listOf(
                "自动从 assets 安装（无需手动下载）",
                "通过 app_process 后台运行",
                "WebSocket 通信（端口 8986）",
                "H.264 视频流编码"
            ),
            manualInstructions = listOf(
                "1. 下载 Shower Server APK",
                "2. 在设备上安装 APK",
                "3. 打开 Shower Server 应用一次（初始化服务）",
                "4. 返回 ZiZip 使用 Agent 模式"
            )
        )
    }

    data class ServerInfo(
        val packageName: String,
        val appName: String,
        val description: String,
        val version: String,
        val sourceUrl: String,
        val features: List<String>,
        val manualInstructions: List<String>
    )
}
