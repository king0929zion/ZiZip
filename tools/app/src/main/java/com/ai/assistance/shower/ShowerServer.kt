package com.ai.assistance.shower

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.*
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shower Server 主类
 * 提供 WebSocket 服务和虚拟屏幕功能
 */
object ShowerServer {
    private const val TAG = "ShowerServer"
    private const val SERVER_PORT = 8986

    private lateinit var context: Context
    private lateinit var wsServer: ShowerWebSocketServer
    private val isRunning = AtomicBoolean(false)

    @JvmStatic
    fun start(ctx: Context) {
        if (isRunning.get()) {
            Log.i(TAG, "Server already running")
            return
        }

        context = ctx.applicationContext
        Log.i(TAG, "========== Starting Shower Server ==========")
        Log.i(TAG, "Port: $SERVER_PORT")

        try {
            // 启动 WebSocket 服务
            wsServer = ShowerWebSocketServer(InetSocketAddress(SERVER_PORT))
            wsServer.start()
            isRunning.set(true)
            Log.i(TAG, "WebSocket server started on port $SERVER_PORT")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server", e)
        }
    }

    @JvmStatic
    fun stop() {
        if (!isRunning.get()) return

        try {
            wsServer.stop()
            isRunning.set(false)
            Log.i(TAG, "Server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }
    }

    /**
     * WebSocket 服务器
     */
    class ShowerWebSocketServer(address: InetSocketAddress) : WebSocketServer(address) {
        private var virtualDisplay: VirtualDisplay? = null
        private var imageReader: ImageReader? = null
        private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        override fun onOpen(conn: WebSocket, handshake: ClientHandshake?) {
            Log.i(TAG, "Client connected: ${conn.remoteSocketAddress}")
        }

        override fun onClose(conn: WebSocket, code: Int, reason: String?, remote: Boolean) {
            Log.i(TAG, "Client disconnected: ${conn.remoteSocketAddress}")
        }

        override fun onMessage(conn: WebSocket, message: String?) {
            if (message == null) return

            Log.d(TAG, "Received: $message")
            scope.launch {
                handleMessage(conn, message)
            }
        }

        override fun onError(conn: WebSocket?, ex: Exception?) {
            Log.e(TAG, "WebSocket error", ex)
        }

        override fun onStart() {
            Log.i(TAG, "WebSocket server started")
        }

        private suspend fun handleMessage(conn: WebSocket, message: String) {
            try {
                val parts = message.split(" ", limit = 2)
                val command = parts[0]
                val args = if (parts.size > 1) parts[1] else ""

                when (command) {
                    "PING" -> {
                        conn.send("PONG")
                    }
                    "CREATE_DISPLAY" -> {
                        createDisplay(conn, args)
                    }
                    "SCREENSHOT" -> {
                        captureScreenshot(conn)
                    }
                    "TAP" -> {
                        handleTap(args)
                    }
                    "SWIPE" -> {
                        handleSwipe(args)
                    }
                    "KEY" -> {
                        handleKey(args)
                    }
                    "STOP_DISPLAY" -> {
                        stopDisplay()
                        conn.send("OK DISPLAY_STOPPED")
                    }
                    else -> {
                        conn.send("ERROR Unknown command: $command")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling message", e)
                conn.send("ERROR ${e.message}")
            }
        }

        private fun createDisplay(conn: WebSocket, args: String) {
            val dimensions = args.split("x")
            if (dimensions.size != 2) {
                conn.send("ERROR Invalid format. Use: CREATE_DISPLAY <width>x<height>")
                return
            }

            val width = dimensions[0].toIntOrNull() ?: 1080
            val height = dimensions[1].toIntOrNull() ?: 1920

            Log.i(TAG, "Creating virtual display: ${width}x$height")

            try {
                // 停止旧的显示
                stopDisplay()

                // 获取屏幕密度
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val metrics = DisplayMetrics()
                wm.defaultDisplay.getMetrics(metrics)
                val density = metrics.densityDpi

                // 创建 ImageReader
                imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

                // 创建 VirtualDisplay
                val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                virtualDisplay = displayManager.createVirtualDisplay(
                    "ShowerVirtual",
                    width, height, density,
                    imageReader?.surface,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                )

                conn.send("OK DISPLAY_CREATED ${width}x$height")
                Log.i(TAG, "Virtual display created: ${width}x$height")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create display", e)
                conn.send("ERROR ${e.message}")
            }
        }

        private fun captureScreenshot(conn: WebSocket) {
            val reader = imageReader
            if (reader == null) {
                conn.send("ERROR No virtual display")
                return
            }

            try {
                val image: Image? = reader.acquireLatestImage()
                if (image == null) {
                    conn.send("ERROR No image available")
                    return
                }

                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * image.width

                val bitmap = Bitmap.createBitmap(
                    image.width + rowPadding / pixelStride,
                    image.height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                image.close()

                // 裁剪掉填充
                val finalBitmap = if (rowPadding != 0) {
                    Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
                } else {
                    bitmap
                }

                // 转换为 PNG
                val stream = ByteArrayOutputStream()
                finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                val byteArray = stream.toByteArray()
                val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)

                conn.send("OK SCREENSHOT ${base64.length}")
                conn.send(base64)

                finalBitmap.recycle()
                bitmap.recycle()
            } catch (e: Exception) {
                Log.e(TAG, "Screenshot failed", e)
                conn.send("ERROR ${e.message}")
            }
        }

        private fun handleTap(args: String) {
            val coords = args.split(" ")
            if (coords.size != 2) return

            val x = coords[0].toIntOrNull() ?: return
            val y = coords[1].toIntOrNull() ?: return

            // 通过 shell 注入点击事件
            try {
                val displayId = virtualDisplay?.display?.displayId ?: return
                val process = Runtime.getRuntime().exec("input -d $displayId tap $x $y")
                process.waitFor()
                Log.d(TAG, "Tapped: ($x, $y) on display $displayId")
            } catch (e: Exception) {
                Log.e(TAG, "Tap failed", e)
            }
        }

        private fun handleSwipe(args: String) {
            val parts = args.split(" ")
            if (parts.size != 5) return

            val x1 = parts[0].toIntOrNull() ?: return
            val y1 = parts[1].toIntOrNull() ?: return
            val x2 = parts[2].toIntOrNull() ?: return
            val y2 = parts[3].toIntOrNull() ?: return
            val duration = parts[4].toLongOrNull() ?: 300

            try {
                val displayId = virtualDisplay?.display?.displayId ?: return
                val process = Runtime.getRuntime().exec("input -d $displayId swipe $x1 $y1 $x2 $y2 $duration")
                process.waitFor()
                Log.d(TAG, "Swiped: ($x1,$y1) -> ($x2,$y2) on display $displayId")
            } catch (e: Exception) {
                Log.e(TAG, "Swipe failed", e)
            }
        }

        private fun handleKey(args: String) {
            val keyCode = args.toIntOrNull() ?: return

            try {
                val displayId = virtualDisplay?.display?.displayId ?: return
                val process = Runtime.getRuntime().exec("input -d $displayId keyevent $keyCode")
                process.waitFor()
                Log.d(TAG, "Key: $keyCode on display $displayId")
            } catch (e: Exception) {
                Log.e(TAG, "Key failed", e)
            }
        }

        private fun stopDisplay() {
            virtualDisplay?.release()
            imageReader?.close()
            virtualDisplay = null
            imageReader = null
            Log.i(TAG, "Virtual display stopped")
        }
    }
}
