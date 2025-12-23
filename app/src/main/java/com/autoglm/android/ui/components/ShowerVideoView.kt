package com.autoglm.android.ui.components

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.autoglm.android.core.agent.ShowerVideoRenderer
import com.autoglm.android.ui.overlay.VirtualDisplayBorder
import kotlinx.coroutines.delay
import android.util.Log
import android.view.MotionEvent

private const val TAG = "ShowerVideoView"

/**
 * 触摸事件回调
 */
data class TouchEvent(
    val action: Int,
    val x: Float,
    val y: Float
)

/**
 * Shower 虚拟屏幕视频显示组件
 * 显示 H.264 视频流并处理触摸事件
 */
@Composable
fun ShowerVideoView(
    modifier: Modifier = Modifier,
    videoWidth: Int = 1080,
    videoHeight: Int = 1920,
    isStreaming: Boolean = false,
    onTouchEvent: (TouchEvent) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var surface by remember { mutableStateOf<Surface?>(null) }
    var isAttached by remember { mutableStateOf(false) }
    var isReady by remember { mutableStateOf(false) }

    // Lifecycle management
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // Reattach when resuming
                    surface?.let {
                        isAttached = ShowerVideoRenderer.attach(it, videoWidth, videoHeight)
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    // Detach when pausing
                    ShowerVideoRenderer.detach()
                    isAttached = false
                    isReady = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            ShowerVideoRenderer.detach()
        }
    }

    // Update streaming state
    LaunchedEffect(isStreaming) {
        if (isStreaming && isAttached) {
            // Give decoder time to initialize
            delay(500)
            isReady = true
        } else if (!isStreaming) {
            isReady = false
        }
    }

    Box(modifier = modifier) {
        if (!isReady || !isStreaming) {
            // Loading/placeholder state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (!isStreaming) {
                    Text(
                        text = "虚拟屏幕未启动",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }

        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            Log.d(TAG, "Surface created: ${videoWidth}x$videoHeight")
                            surface = holder.surface
                            isAttached = ShowerVideoRenderer.attach(
                                holder.surface,
                                videoWidth,
                                videoHeight
                            )
                            if (!isAttached) {
                                Log.e(TAG, "Failed to attach video renderer")
                            }
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int
                        ) {
                            Log.d(TAG, "Surface changed: ${width}x$height")
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            Log.d(TAG, "Surface destroyed")
                            ShowerVideoRenderer.detach()
                            surface = null
                            isAttached = false
                            isReady = false
                        }
                    })

                    // Touch event handling
                    setOnTouchListener { view, event ->
                        if (isReady && isStreaming) {
                            val normalizedX = event.x / view.width.toFloat()
                            val normalizedY = event.y / view.height.toFloat()

                            onTouchEvent(TouchEvent(event.action, normalizedX, normalizedY))

                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    Log.d(TAG, "Touch down: ${event.x}, ${event.y}")
                                    view.performClick()
                                }
                                MotionEvent.ACTION_UP -> {
                                    Log.d(TAG, "Touch up: ${event.x}, ${event.y}")
                                }
                            }
                        }
                        true
                    }
                }
            },
            update = { view ->
                // Keep view updated
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * 虚拟屏幕视频覆盖层
 * 包含视频显示和边框指示器
 */
@Composable
fun VirtualDisplayVideoOverlay(
    modifier: Modifier = Modifier,
    videoWidth: Int = 1080,
    videoHeight: Int = 1920,
    isStreaming: Boolean = false,
    onTouchEvent: (TouchEvent) -> Unit = {}
) {
    Box(modifier = modifier) {
        ShowerVideoView(
            modifier = Modifier.fillMaxSize(),
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            isStreaming = isStreaming,
            onTouchEvent = onTouchEvent
        )

        // Virtual display border can be added here if needed
        VirtualDisplayBorder(
            isActive = isStreaming,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * 从 Shower WebSocket 接收视频帧并渲染
 * 此函数应在收到 WebSocket 二进制消息时调用
 */
fun onVideoFrameReceived(data: ByteArray) {
    ShowerVideoRenderer.onFrame(data)
}

/**
 * 截取当前虚拟屏幕画面
 */
suspend fun captureVirtualDisplayScreenshot(): ByteArray? {
    return ShowerVideoRenderer.captureCurrentFramePng()
}
