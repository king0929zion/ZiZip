package com.autoglm.android.core.agent

import android.util.Base64
import android.util.Log
import com.autoglm.android.core.debug.DebugLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * Shower 虚拟屏幕控制器
 * 通过 WebSocket 与 Shower 服务器通信
 */
class ShowerController(
    private val host: String = "127.0.0.1",
    private val port: Int = 8986
) {
    companion object {
        private const val TAG = "ShowerController"
    }

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

    fun getDisplayId(): Int? = virtualDisplayId
    fun getVideoSize(): Pair<Int, Int> = Pair(videoWidth, videoHeight)
    fun isConnected(): Boolean = connected

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            DebugLogger.i(TAG, "WebSocket 连接成功")
            connected = true
            connectingDeferred?.complete(true)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            DebugLogger.d(TAG, "WebSocket 关闭: $code $reason")
            connected = false
            this@ShowerController.webSocket = null
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            DebugLogger.e(TAG, "WebSocket 连接失败: ${t.message}", t)
            connected = false
            connectingDeferred?.complete(false)
            this@ShowerController.webSocket = null
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            DebugLogger.d(TAG, "收到消息: ${text.take(100)}")
            handleTextMessage(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            // 二进制消息（H.264 视频帧）
            // 暂不处理视频流
        }
    }

    private fun handleTextMessage(text: String) {
        try {
            when {
                text.startsWith("DISPLAY_CREATED ") -> {
                    val id = text.removePrefix("DISPLAY_CREATED ").trim().toIntOrNull()
                    virtualDisplayId = id
                    DebugLogger.i(TAG, "虚拟屏幕已创建: $id")
                }
                text.startsWith("DISPLAY_SIZE ") -> {
                    val parts = text.removePrefix("DISPLAY_SIZE ").split(" ")
                    if (parts.size >= 2) {
                        videoWidth = parts[0].toIntOrNull() ?: 0
                        videoHeight = parts[1].toIntOrNull() ?: 0
                        DebugLogger.d(TAG, "显示尺寸: ${videoWidth}x$videoHeight")
                    }
                }
                text.startsWith("SCREENSHOT_DATA ") -> {
                    val base64 = text.removePrefix("SCREENSHOT_DATA ").trim()
                    try {
                        val bytes = Base64.decode(base64, Base64.DEFAULT)
                        pendingScreenshot?.complete(bytes)
                    } catch (e: Exception) {
                        DebugLogger.e(TAG, "解码截图失败", e)
                        pendingScreenshot?.complete(null)
                    }
                }
                text.startsWith("SCREENSHOT_ERROR") -> {
                    DebugLogger.e(TAG, "截图错误: $text", null)
                    pendingScreenshot?.complete(null)
                }
                // 日志消息
                else -> {
                    DebugLogger.d(TAG, "[Server] $text")
                }
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "处理消息失败", e)
        }
    }

    private fun buildUrl(): String = "ws://$host:$port"

    /**
     * 确保 WebSocket 已连接
     */
    suspend fun ensureConnected(): Boolean = withContext(Dispatchers.IO) {
        if (connected && webSocket != null) {
            return@withContext true
        }

        DebugLogger.d(TAG, "连接到 Shower 服务器: ${buildUrl()}")

        val deferred = CompletableDeferred<Boolean>()
        connectingDeferred = deferred

        val request = Request.Builder()
            .url(buildUrl())
            .build()

        webSocket = client.newWebSocket(request, listener)

        // 等待连接完成
        withTimeoutOrNull(5000L) {
            deferred.await()
        } ?: false
    }

    /**
     * 请求截图
     */
    suspend fun requestScreenshot(timeoutMs: Long = 5000L): ByteArray? {
        if (!ensureConnected()) {
            DebugLogger.w(TAG, "未连接，无法截图", null)
            return null
        }

        val deferred = CompletableDeferred<ByteArray?>()
        pendingScreenshot = deferred

        sendText("SCREENSHOT")

        return withTimeoutOrNull(timeoutMs) {
            deferred.await()
        }
    }

    private fun sendText(cmd: String): Boolean {
        val ws = webSocket
        if (ws == null || !connected) {
            DebugLogger.w(TAG, "无法发送，未连接", null)
            return false
        }
        DebugLogger.d(TAG, "发送: $cmd")
        return ws.send(cmd)
    }

    /**
     * 确保虚拟屏幕已创建
     */
    suspend fun ensureDisplay(width: Int, height: Int, dpi: Int, bitrateKbps: Int? = null): Boolean {
        if (!ensureConnected()) {
            return false
        }

        val cmd = buildString {
            append("CREATE_DISPLAY $width $height $dpi")
            if (bitrateKbps != null) {
                append(" $bitrateKbps")
            }
        }

        DebugLogger.i(TAG, "创建虚拟屏幕: $cmd")
        return sendText(cmd)
    }

    /**
     * 启动应用
     */
    fun launchApp(packageName: String): Boolean {
        if (!connected) return false
        val cmd = "LAUNCH_APP $packageName"
        DebugLogger.d(TAG, "启动应用: $cmd")
        return sendText(cmd)
    }

    /**
     * 点击
     */
    fun tap(x: Int, y: Int): Boolean {
        if (!connected) return false
        return sendText("TAP $x $y")
    }

    /**
     * 滑动
     */
    fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long = 300L): Boolean {
        if (!connected) return false
        return sendText("SWIPE $startX $startY $endX $endY $durationMs")
    }

    /**
     * 按键
     */
    fun key(keyCode: Int): Boolean {
        if (!connected) return false
        return sendText("KEY $keyCode")
    }

    /**
     * 触摸按下
     */
    fun touchDown(x: Int, y: Int): Boolean {
        if (!connected) return false
        return sendText("TOUCH_DOWN $x $y")
    }

    /**
     * 触摸移动
     */
    fun touchMove(x: Int, y: Int): Boolean {
        if (!connected) return false
        return sendText("TOUCH_MOVE $x $y")
    }

    /**
     * 触摸抬起
     */
    fun touchUp(x: Int, y: Int): Boolean {
        if (!connected) return false
        return sendText("TOUCH_UP $x $y")
    }

    /**
     * 关闭连接
     */
    fun shutdown() {
        DebugLogger.d(TAG, "关闭连接")
        try {
            sendText("DESTROY_DISPLAY")
            webSocket?.close(1000, "Client shutdown")
        } catch (e: Exception) {
            DebugLogger.e(TAG, "关闭时出错", e)
        }
        webSocket = null
        connected = false
        virtualDisplayId = null
    }
}
