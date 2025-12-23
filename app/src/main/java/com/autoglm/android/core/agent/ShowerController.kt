package com.autoglm.android.core.agent

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.*
import okio.ByteString
import org.json.JSONObject

/**
 * Shower 虚拟屏幕控制器
 * 通过 WebSocket 与 Shower 服务器通信
 * 参考 Operit 实现
 */
class ShowerController(
    private val host: String = "127.0.0.1",
    private val port: Int = 8986
) {
    companion object {
        private const val TAG = "ShowerController"
    }

    private val client = OkHttpClient.Builder().build()

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
    private var binaryHandler: ((ByteArray) -> Unit)? = null

    @Volatile
    private var pendingScreenshot: CompletableDeferred<ByteArray?>? = null

    @Volatile
    private var connectingDeferred: CompletableDeferred<Boolean>? = null

    @Volatile
    var isStreamingVideo = false
        private set

    fun getDisplayId(): Int? = virtualDisplayId
    fun getVideoSize(): Pair<Int, Int> = Pair(videoWidth, videoHeight)
    fun isConnected(): Boolean = connected
    fun isVideoStreaming(): Boolean = isStreamingVideo

    /**
     * 启用 H.264 视频流
     * 二进制数据将自动转发到 ShowerVideoRenderer
     */
    fun enableVideoStreaming() {
        isStreamingVideo = true
        binaryHandler = { data -> ShowerVideoRenderer.onFrame(data) }
        Log.d(TAG, "Video streaming enabled")
    }

    /**
     * 禁用视频流
     */
    fun disableVideoStreaming() {
        isStreamingVideo = false
        binaryHandler = null
        Log.d(TAG, "Video streaming disabled")
    }

    fun setBinaryHandler(handler: ((ByteArray) -> Unit)?) {
        binaryHandler = handler
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket connected")
            connected = true
            connectingDeferred?.complete(true)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed: $code $reason")
            connected = false
            this@ShowerController.webSocket = null
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure", t)
            connected = false
            connectingDeferred?.complete(false)
            this@ShowerController.webSocket = null
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "Received text: ${text.take(100)}")
            handleTextMessage(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            binaryHandler?.invoke(bytes.toByteArray())
        }
    }

    private fun handleTextMessage(text: String) {
        try {
            when {
                text.startsWith("DISPLAY_CREATED ") -> {
                    val id = text.removePrefix("DISPLAY_CREATED ").trim().toIntOrNull()
                    virtualDisplayId = id
                    Log.d(TAG, "Virtual display created: $id")
                }
                text.startsWith("DISPLAY_SIZE ") -> {
                    val parts = text.removePrefix("DISPLAY_SIZE ").split(" ")
                    if (parts.size >= 2) {
                        videoWidth = parts[0].toIntOrNull() ?: 0
                        videoHeight = parts[1].toIntOrNull() ?: 0
                        Log.d(TAG, "Display size: ${videoWidth}x$videoHeight")
                    }
                }
                text.startsWith("SCREENSHOT_DATA ") -> {
                    val base64 = text.removePrefix("SCREENSHOT_DATA ").trim()
                    try {
                        val bytes = Base64.decode(base64, Base64.DEFAULT)
                        pendingScreenshot?.complete(bytes)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to decode screenshot", e)
                        pendingScreenshot?.complete(null)
                    }
                }
                text.startsWith("SCREENSHOT_ERROR") -> {
                    Log.e(TAG, "Screenshot error: $text")
                    pendingScreenshot?.complete(null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message", e)
        }
    }

    private fun buildUrl(): String = "ws://$host:$port"

    /**
     * 确保 WebSocket 已连接
     */
    suspend fun ensureConnected(): Boolean = withContext(Dispatchers.IO) {
        if (connected && webSocket != null) return@withContext true

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
    suspend fun requestScreenshot(timeoutMs: Long = 3000L): ByteArray? {
        if (!ensureConnected()) return null

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
            Log.w(TAG, "Cannot send, not connected")
            return false
        }
        Log.d(TAG, "Sending: $cmd")
        return ws.send(cmd)
    }

    /**
     * 确保虚拟屏幕已创建
     * 创建后自动启用视频流
     */
    suspend fun ensureDisplay(width: Int, height: Int, dpi: Int, bitrateKbps: Int? = null): Boolean {
        if (!ensureConnected()) return false

        val cmd = buildString {
            append("CREATE_DISPLAY $width $height $dpi")
            if (bitrateKbps != null) {
                append(" $bitrateKbps")
            }
        }

        val success = sendText(cmd)
        if (success) {
            // 启用视频流
            enableVideoStreaming()
        }
        return success
    }

    /**
     * 启动应用
     */
    suspend fun launchApp(packageName: String): Boolean {
        if (!ensureConnected()) return false
        return sendText("LAUNCH $packageName")
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
     */
    fun shutdown() {
        disableVideoStreaming()
        try {
            sendText("SHUTDOWN")
            webSocket?.close(1000, "Client shutdown")
        } catch (e: Exception) {
            Log.e(TAG, "Error during shutdown", e)
        }
        webSocket = null
        connected = false
        virtualDisplayId = null
    }
}
