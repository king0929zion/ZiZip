package com.autoglm.android.core.agent

import android.util.Base64
import android.util.Log
import com.autoglm.android.core.debug.DebugLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * Shower 虚拟屏幕控制器
 * 通过 WebSocket 与 Shower 服务器通信
 *
 * 参考 Operit 实现，修复了以下问题：
 * 1. 连接超时和 Deferred 清理
 * 2. 从日志中解析虚拟屏幕 ID
 * 3. 截图请求的完整处理
 * 4. 二进制视频帧处理
 */
object ShowerController {
    private const val TAG = "ShowerController"
    private const val HOST = "127.0.0.1"
    private const val PORT = 8986

    private val client = OkHttpClient.Builder()
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var webSocket: WebSocket? = null

    @Volatile
    private var connected = false

    @Volatile
    private var virtualDisplayId: Int? = null

    @Volatile
    private var videoWidth = 0

    @Volatile
    private var videoHeight = 0

    @Volatile
    private var pendingScreenshot: CompletableDeferred<ByteArray?>? = null

    @Volatile
    private var connectingDeferred: CompletableDeferred<Boolean>? = null

    @Volatile
    private var binaryHandler: ((ByteArray) -> Unit)? = null

    fun setBinaryHandler(handler: ((ByteArray) -> Unit)?) {
        binaryHandler = handler
    }

    fun getDisplayId(): Int? = virtualDisplayId
    fun getVideoSize(): Pair<Int, Int>? {
        return if (videoWidth > 0 && videoHeight > 0) {
            Pair(videoWidth, videoHeight)
        } else {
            null
        }
    }
    fun isConnected(): Boolean = connected

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            DebugLogger.i(TAG, "WebSocket 连接成功")
            connected = true
            connectingDeferred?.complete(true)
            connectingDeferred = null
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            DebugLogger.d(TAG, "WebSocket 关闭: $code $reason")
            connected = false
            this@ShowerController.webSocket = null
            connectingDeferred?.complete(false)
            connectingDeferred = null
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            DebugLogger.e(TAG, "WebSocket 连接失败: ${t.message}", t)
            connected = false
            this@ShowerController.webSocket = null
            // Fail any pending screenshot request
            pendingScreenshot?.complete(null)
            pendingScreenshot = null
            connectingDeferred?.complete(false)
            connectingDeferred = null
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            // 首先处理截图响应（避免记录大量 Base64 数据）
            if (text.startsWith("SCREENSHOT_DATA ")) {
                val base64 = text.substring("SCREENSHOT_DATA ".length).trim()
                try {
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    DebugLogger.d(TAG, "收到截图 via WS, size=${bytes.size}")
                    pendingScreenshot?.complete(bytes)
                } catch (e: Exception) {
                    DebugLogger.e(TAG, "解码 SCREENSHOT_DATA 失败", e)
                    pendingScreenshot?.complete(null)
                } finally {
                    pendingScreenshot = null
                }
                return
            } else if (text.startsWith("SCREENSHOT_ERROR")) {
                DebugLogger.w(TAG, "收到 SCREENSHOT_ERROR: $text", null)
                pendingScreenshot?.complete(null)
                pendingScreenshot = null
                return
            }

            DebugLogger.d(TAG, "WS 文本: $text")
            // 从日志中解析虚拟屏幕 ID
            val marker = "Virtual display id="
            val idx = text.indexOf(marker)
            if (idx >= 0) {
                val start = idx + marker.length
                val end = text.indexOfAny(charArrayOf(' ', ',', ';', '\n', '\r'), start)
                    .let { if (it == -1) text.length else it }
                val idStr = text.substring(start, end).trim()
                val id = idStr.toIntOrNull()
                if (id != null) {
                    virtualDisplayId = id
                    DebugLogger.i(TAG, "从日志发现虚拟屏幕 id=$id")
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            // 二进制帧包含 H.264 视频，转发给处理器
            binaryHandler?.invoke(bytes.toByteArray())
        }
    }

    private fun buildUrl(): String = "ws://$HOST:$PORT"

    /**
     * 确保 WebSocket 已连接
     * 防止重复连接，使用 existing deferred 机制
     */
    suspend fun ensureConnected(): Boolean = withContext(Dispatchers.IO) {
        if (connected && webSocket != null) {
            return@withContext true
        }

        // 检查是否已有正在进行的连接
        val existing = connectingDeferred
        if (existing != null) {
            return@withContext try {
                withTimeout(5000L) {
                    existing.await()
                }
            } catch (e: Exception) {
                DebugLogger.e(TAG, "等待现有 WebSocket 连接失败", e)
                false
            }
        }

        val deferred = CompletableDeferred<Boolean>()
        connectingDeferred = deferred

        return@withContext try {
            val request = Request.Builder()
                .url(buildUrl())
                .build()

            webSocket = client.newWebSocket(request, listener)

            withTimeout(5000L) {
                deferred.await()
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "连接 WebSocket 失败", e)
            connectingDeferred = null
            false
        }
    }

    /**
     * 确保 WebSocket 已连接（带重试机制）
     * @param maxRetries 最大重试次数，默认 3 次
     * @param retryDelayMs 重试延迟（毫秒），默认 1000ms
     */
    suspend fun ensureConnectedWithRetry(
        maxRetries: Int = 3,
        retryDelayMs: Long = 1000L
    ): Boolean = withContext(Dispatchers.IO) {
        if (connected && webSocket != null) {
            return@withContext true
        }

        // 检查是否已有正在进行的连接
        val existing = connectingDeferred
        if (existing != null) {
            return@withContext try {
                withTimeout(5000L) {
                    existing.await()
                }
            } catch (e: Exception) {
                DebugLogger.e(TAG, "等待现有 WebSocket 连接失败", e)
                false
            }
        }

        // 尝试连接，带重试
        repeat(maxRetries) { attempt ->
            if (connected && webSocket != null) {
                return@withContext true
            }

            val deferred = CompletableDeferred<Boolean>()
            connectingDeferred = deferred

            try {
                val request = Request.Builder()
                    .url(buildUrl())
                    .build()

                webSocket = client.newWebSocket(request, listener)

                val result = withTimeout(5000L) {
                    deferred.await()
                }

                if (result) {
                    DebugLogger.i(TAG, "WebSocket 连接成功 (尝试 ${attempt + 1}/$maxRetries)")
                    return@withContext true
                }
            } catch (e: Exception) {
                DebugLogger.w(TAG, "连接失败 (尝试 ${attempt + 1}/$maxRetries): ${e.message}")

                // 清理失败的连接
                try {
                    webSocket?.cancel()
                } catch (_: Exception) {}
                webSocket = null
                connectingDeferred = null

                // 如果不是最后一次尝试，等待后重试
                if (attempt < maxRetries - 1) {
                    delay(retryDelayMs)
                }
            }
        }

        DebugLogger.e(TAG, "WebSocket 连接失败，已达到最大重试次数 ($maxRetries)", null)
        false
    }

    /**
     * 请求截图
     * 协议：
     *   - 客户端发送: SCREENSHOT
     *   - 服务器回复: SCREENSHOT_DATA <base64_png>
     *   - 或者: SCREENSHOT_ERROR <reason>
     */
    suspend fun requestScreenshot(timeoutMs: Long = 3000L): ByteArray? {
        if (!ensureConnected()) {
            DebugLogger.w(TAG, "未连接，无法截图", null)
            return null
        }

        // 取消之前的截图请求
        pendingScreenshot?.complete(null)
        val deferred = CompletableDeferred<ByteArray?>()
        pendingScreenshot = deferred

        if (!sendText("SCREENSHOT")) {
            DebugLogger.w(TAG, "发送 SCREENSHOT 命令失败", null)
            pendingScreenshot = null
            return null
        }

        return try {
            withTimeout(timeoutMs) {
                deferred.await()
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "截图超时或失败", e)
            pendingScreenshot = null
            null
        }
    }

    private fun sendText(cmd: String): Boolean {
        val ws = webSocket
        if (ws == null || !connected) {
            DebugLogger.w(TAG, "无法发送，未连接: $cmd", null)
            return false
        }
        DebugLogger.d(TAG, "发送: $cmd")
        return try {
            ws.send(cmd)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "发送命令失败: $cmd", e)
            false
        }
    }

    /**
     * 确保虚拟屏幕已创建
     * 参考 Operit：先 DESTROY 再 CREATE，确保编码器重新初始化
     */
    suspend fun ensureDisplay(width: Int, height: Int, dpi: Int, bitrateKbps: Int? = null): Boolean {
        if (!ensureConnected()) {
            return false
        }

        // 先销毁现有虚拟屏幕，确保编码器重新发送 SPS/PPS
        sendText("DESTROY_DISPLAY")

        // 对齐宽高到 8 的倍数
        var alignedWidth = width and -8
        var alignedHeight = height and -8
        if (alignedWidth <= 0 || alignedHeight <= 0) {
            alignedWidth = maxOf(2, width)
            alignedHeight = maxOf(2, height)
        }

        videoWidth = alignedWidth
        videoHeight = alignedHeight

        val cmd = buildString {
            append("CREATE_DISPLAY ")
            append(alignedWidth)
            append(' ')
            append(alignedHeight)
            append(' ')
            append(dpi)
            if (bitrateKbps != null && bitrateKbps > 0) {
                append(' ')
                append(bitrateKbps)
            }
        }

        DebugLogger.i(TAG, "创建虚拟屏幕: $cmd")
        return sendText(cmd)
    }

    /**
     * 启动应用
     */
    suspend fun launchApp(packageName: String): Boolean {
        if (!ensureConnected()) return false
        if (packageName.isBlank()) return false
        return sendText("LAUNCH_APP $packageName")
    }

    /**
     * 点击
     */
    suspend fun tap(x: Int, y: Int): Boolean {
        if (!ensureConnected()) return false
        return sendText("TAP $x $y")
    }

    /**
     * 滑动
     */
    suspend fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long = 300L): Boolean {
        if (!ensureConnected()) return false
        return sendText("SWIPE $startX $startY $endX $endY $durationMs")
    }

    /**
     * 按键
     */
    suspend fun key(keyCode: Int): Boolean {
        if (!ensureConnected()) return false
        return sendText("KEY $keyCode")
    }

    /**
     * 触摸按下
     */
    suspend fun touchDown(x: Int, y: Int): Boolean {
        if (!ensureConnected()) return false
        return sendText("TOUCH_DOWN $x $y")
    }

    /**
     * 触摸移动
     */
    suspend fun touchMove(x: Int, y: Int): Boolean {
        if (!ensureConnected()) return false
        return sendText("TOUCH_MOVE $x $y")
    }

    /**
     * 触摸抬起
     */
    suspend fun touchUp(x: Int, y: Int): Boolean {
        if (!ensureConnected()) return false
        return sendText("TOUCH_UP $x $y")
    }

    /**
     * 关闭连接
     * 确保正确关闭 WebSocket 并清理所有资源
     */
    fun shutdown() {
        DebugLogger.d(TAG, "关闭连接")
        try {
            sendText("DESTROY_DISPLAY")
            webSocket?.close(1000, "Client shutdown")
            // 取消WebSocket以确保连接被关闭
            try {
                webSocket?.cancel()
            } catch (e: Exception) {
                // ignore
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "关闭时出错", e)
        } finally {
            webSocket = null
            connected = false
            virtualDisplayId = null
            videoWidth = 0
            videoHeight = 0
            binaryHandler = null
            // 清理 deferred 防止下次连接时使用旧的
            connectingDeferred?.complete(false)
            connectingDeferred = null
            // 清理待处理的截图请求
            pendingScreenshot?.complete(null)
            pendingScreenshot = null
        }
    }
}
