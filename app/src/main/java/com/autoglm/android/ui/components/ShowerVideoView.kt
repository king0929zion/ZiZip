package com.autoglm.android.ui.components

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import kotlinx.coroutines.delay
import android.util.Log
import android.view.MotionEvent
import android.view.HapticFeedbackConstants

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
 * Shower 虚拟屏幕视频显示组件 - 增强版
 * 支持 H.264 视频流显示、缩放、旋转
 */
@Composable
fun ShowerVideoView(
    modifier: Modifier = Modifier,
    videoWidth: Int = 1080,
    videoHeight: Int = 1920,
    isStreaming: Boolean = false,
    zoomLevel: Float = 1f,
    screenRotation: Int = 0,
    onTouchEvent: (TouchEvent) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var surface by remember { mutableStateOf<Surface?>(null) }
    var isAttached by remember { mutableStateOf(false) }
    var isReady by remember { mutableStateOf(false) }

    // Apply rotation modifier
    val rotationModifier = Modifier.rotate(screenRotation.toFloat())

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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!isStreaming) {
                        // Not connected state
                        Icon(
                            android.vector.graphics.drawable.Icon.createWithContext(
                                context,
                                androidx.compose.ui.R.drawable.ic_flower_placeholder
                            ),
                            contentDescription = null,
                            tint = Grey600,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "虚拟屏幕未启动",
                            color = Grey500,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        // Loading state
                        CircularProgressIndicator(
                            color = Accent,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "正在加载视频流...",
                            color = Grey400,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            Log.d(TAG, "Surface created: ${videoWidth}x$videoHeight, zoom: $zoomLevel, rotation: $screenRotation")
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

                    // Touch event handling with haptic feedback
                    setOnTouchListener { view, event ->
                        if (isReady && isStreaming) {
                            val normalizedX = event.x / view.width.toFloat()
                            val normalizedY = event.y / view.height.toFloat()

                            onTouchEvent(TouchEvent(event.action, normalizedX, normalizedY))

                            // Haptic feedback on touch
                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    Log.d(TAG, "Touch down: ${event.x}, ${event.y}")
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
                // Keep view updated with current state
            },
            modifier = Modifier
                .fillMaxSize()
                .then(rotationModifier)
                .clickable { /* Consume clicks */ }
        )
    }
}

/**
 * 触摸波纹效果组件
 */
@Composable
fun TouchRipple(
    modifier: Modifier = Modifier,
    x: Float,
    y: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ripple")
    val rippleSize by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleSize"
    )
    val rippleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rippleAlpha"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(rippleSize.dp)
                .background(
                    Accent.copy(alpha = rippleAlpha * 0.3f),
                    CircleShape
                )
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
