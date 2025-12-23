package com.autoglm.android.ui.screens.virtualdisplay

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.autoglm.android.ui.components.ShowerVideoView
import com.autoglm.android.ui.components.TouchEvent
import com.autoglm.android.ui.overlay.VirtualDisplayBorder
import com.autoglm.android.ui.theme.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.util.Log

private const val TAG = "VirtualDisplayScreen"

/**
 * 虚拟屏幕显示页面
 * 显示 Shower 虚拟显示器的实时视频流
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VirtualDisplayScreen(
    navController: NavController,
    viewModel: VirtualDisplayViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isConnected by viewModel.isConnected.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val videoWidth by viewModel.videoWidth.collectAsState()
    val videoHeight by viewModel.videoHeight.collectAsState()

    // Initialize video streaming on enter
    LaunchedEffect(Unit) {
        viewModel.connect(context)
    }

    // Cleanup on exit
    DisposableEffect(Unit) {
        onDispose {
            viewModel.disconnect()
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "虚拟屏幕",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            when {
                                isStreaming -> "正在直播"
                                isConnected -> "已连接 - 等待视频流"
                                else -> "连接中..."
                            },
                            color = Grey400,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "返回", tint = Color.White)
                    }
                },
                actions = {
                    // 截图按钮
                    IconButton(
                        onClick = {
                            scope.launch {
                                viewModel.captureScreenshot()?.let { bytes ->
                                    // TODO: Save or share screenshot
                                    Log.d(TAG, "Screenshot captured: ${bytes.size} bytes")
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.PhotoCamera, "截图", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Video display
            ShowerVideoView(
                modifier = Modifier.fillMaxSize(),
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                isStreaming = isStreaming,
                onTouchEvent = { event ->
                    // Convert normalized coordinates to pixel coordinates
                    val screenWidth = videoWidth.takeIf { width -> width > 0 } ?: 1080
                    val screenHeight = videoHeight.takeIf { height -> height > 0 } ?: 1920

                    val x = (event.x * screenWidth).toInt()
                    val y = (event.y * screenHeight).toInt()

                    scope.launch {
                        viewModel.handleTouchEvent(event.action, x, y)
                    }
                }
            )

            // Status indicator
            if (!isStreaming) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (isConnected) {
                            CircularProgressIndicator(color = Accent)
                            Text(
                                "等待视频流...",
                                color = Grey400,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            CircularProgressIndicator(color = Grey600)
                            Text(
                                "连接虚拟屏幕中...",
                                color = Grey400,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // Virtual display border overlay
            VirtualDisplayBorder(
                isActive = isStreaming,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * 虚拟屏幕视图模型
 */
class VirtualDisplayViewModel : androidx.lifecycle.ViewModel() {
    private val _isConnected = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _isStreaming = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming

    private val _videoWidth = kotlinx.coroutines.flow.MutableStateFlow(1080)
    val videoWidth: StateFlow<Int> = _videoWidth

    private val _videoHeight = kotlinx.coroutines.flow.MutableStateFlow(1920)
    val videoHeight: StateFlow<Int> = _videoHeight

    private var showerController: com.autoglm.android.core.agent.ShowerController? = null

    suspend fun connect(context: Context) {
        val controller = com.autoglm.android.core.agent.ShowerController()
        showerController = controller

        try {
            // Connect to Shower server
            val connected = controller.ensureConnected()
            _isConnected.value = connected

            if (connected) {
                // Get video size
                val (width, height) = controller.getVideoSize()
                if (width > 0 && height > 0) {
                    _videoWidth.value = width
                    _videoHeight.value = height
                }

                // Video streaming is enabled when display is created
                _isStreaming.value = controller.isVideoStreaming()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to virtual display", e)
            _isConnected.value = false
        }
    }

    fun disconnect() {
        showerController?.shutdown()
        showerController = null
        _isConnected.value = false
        _isStreaming.value = false
    }

    suspend fun handleTouchEvent(action: Int, x: Int, y: Int) {
        val controller = showerController ?: return

        try {
            when (action) {
                0 -> { // ACTION_DOWN
                    controller.touchDown(x, y)
                }
                1 -> { // ACTION_UP
                    controller.touchUp(x, y)
                }
                2 -> { // ACTION_MOVE
                    controller.touchMove(x, y)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle touch event", e)
        }
    }

    suspend fun captureScreenshot(): ByteArray? {
        return showerController?.requestScreenshot(timeoutMs = 3000L)
    }
}
