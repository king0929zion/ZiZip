package com.autoglm.android.ui.screens.virtualdisplay

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.autoglm.android.ui.components.ShowerVideoView
import com.autoglm.android.ui.components.TouchEvent
import com.autoglm.android.ui.overlay.VirtualDisplayBorder
import com.autoglm.android.ui.theme.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

private const val TAG = "VirtualDisplayScreen"

/**
 * 虚拟屏幕显示页面 - 美化版
 * 显示 Shower 虚拟显示器的实时视频流
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun VirtualDisplayScreen(
    navController: NavController,
    viewModel: VirtualDisplayViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    val isConnected by viewModel.isConnected.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val videoWidth by viewModel.videoWidth.collectAsState()
    val videoHeight by viewModel.videoHeight.collectAsState()
    val screenRotation by viewModel.screenRotation.collectAsState()
    val zoomLevel by viewModel.zoomLevel.collectAsState()

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

    // Snackbar host for showing messages
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Connection status indicator
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isStreaming -> Accent
                                        isConnected -> Color(0xFFFFA500) // Orange
                                        else -> Color.Red
                                    }
                                )
                                .animateContentSize(
                                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                                )
                        )
                        Column {
                            Text(
                                "虚拟屏幕",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                when {
                                isStreaming -> "正在直播 ${videoWidth}x${videoHeight}"
                                isConnected -> "已连接 - 等待视频流"
                                else -> "连接中..."
                            },
                                color = Grey400,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "返回", tint = Color.White)
                    }
                },
                actions = {
                    // Screen rotation button
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            viewModel.rotateScreen()
                        }
                    ) {
                        Icon(
                            Icons.Default.ScreenRotation,
                            "旋转屏幕",
                            tint = Color.White,
                            modifier = Modifier.rotate(screenRotation.toFloat())
                        )
                    }
                    // Screenshot button
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            scope.launch {
                                val bytes = viewModel.captureScreenshot()
                                if (bytes != null) {
                                    val saved = viewModel.saveScreenshot(context, bytes)
                                    if (saved) {
                                        snackbarHostState.showSnackbar(
                                            message = "截图已保存到相册",
                                            duration = SnackbarDuration.Short
                                        )
                                    } else {
                                        snackbarHostState.showSnackbar(
                                            message = "截图保存失败",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                } else {
                                    snackbarHostState.showSnackbar(
                                        message = "截图失败",
                                        duration = SnackbarDuration.Short
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.PhotoCamera, "截图", tint = Color.White)
                    }
                    // More options menu
                    VirtualDisplayMenu(
                        onZoomIn = { viewModel.setZoomLevel((zoomLevel + 0.1f).coerceAtMost(2f)) },
                        onZoomOut = { viewModel.setZoomLevel((zoomLevel - 0.1f).coerceAtLeast(0.5f)) },
                        onZoomReset = { viewModel.setZoomLevel(1f) },
                        onRefresh = {
                            scope.launch {
                                viewModel.disconnect()
                                viewModel.connect(context)
                            }
                        }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.8f)
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
                zoomLevel = zoomLevel,
                screenRotation = screenRotation,
                onTouchEvent = { event ->
                    // Apply zoom to touch coordinates
                    val adjustedX = (event.x - 0.5f) / zoomLevel + 0.5f
                    val adjustedY = (event.y - 0.5f) / zoomLevel + 0.5f

                    val screenWidth = videoWidth.takeIf { width -> width > 0 } ?: 1080
                    val screenHeight = videoHeight.takeIf { height -> height > 0 } ?: 1920

                    // Handle rotation
                    val (finalX, finalY) = when (screenRotation) {
                        90 -> adjustedY to (1f - adjustedX)
                        180 -> (1f - adjustedX) to (1f - adjustedY)
                        270 -> (1f - adjustedY) to adjustedX
                        else -> adjustedX to adjustedY
                    }

                    val x = (finalX * screenWidth).toInt().coerceIn(0, screenWidth)
                    val y = (finalY * screenHeight).toInt().coerceIn(0, screenHeight)

                    scope.launch {
                        viewModel.handleTouchEvent(event.action, x, y)
                    }
                }
            )

            // Status overlay
            AnimatedVisibility(
                visible = !isStreaming,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    ConnectionStatusCard(
                        isConnected = isConnected,
                        isStreaming = isStreaming
                    )
                }
            }

            // Zoom indicator
            AnimatedVisibility(
                visible = zoomLevel != 1f,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Accent.copy(alpha = 0.9f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        "${(zoomLevel * 100).toInt()}%",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
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
 * 连接状态卡片
 */
@Composable
private fun ConnectionStatusCard(
    isConnected: Boolean,
    isStreaming: Boolean
) {
    Card(
        modifier = Modifier
            .padding(32.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Grey100
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                !isConnected -> {
                    Icon(
                        Icons.Outlined.WifiOff,
                        contentDescription = null,
                        tint = ErrorColor,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        "连接虚拟屏幕失败",
                        color = Grey900,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "请确保 Shower Server 应用已安装并运行",
                        color = Grey500,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                isConnected && !isStreaming -> {
                    CircularProgressIndicator(color = Accent)
                    Text(
                        "正在建立视频流...",
                        color = Grey700,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * 虚拟屏幕菜单
 */
@Composable
private fun VirtualDisplayMenu(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomReset: () -> Unit,
    onRefresh: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, "更多选项", tint = Color.White)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Grey100)
        ) {
            DropdownMenuItem(
                text = { Text("放大") },
                onClick = {
                    onZoomIn()
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Default.ZoomIn, null, tint = Grey700)
                }
            )
            DropdownMenuItem(
                text = { Text("缩小") },
                onClick = {
                    onZoomOut()
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Default.ZoomOut, null, tint = Grey700)
                }
            )
            DropdownMenuItem(
                text = { Text("重置缩放") },
                onClick = {
                    onZoomReset()
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Default.AspectRatio, null, tint = Grey700)
                }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("刷新连接") },
                onClick = {
                    onRefresh()
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Default.Refresh, null, tint = Grey700)
                }
            )
        }
    }
}

/**
 * 虚拟屏幕视图模型 - 增强版
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

    private val _screenRotation = kotlinx.coroutines.flow.MutableStateFlow(0)
    val screenRotation: StateFlow<Int> = _screenRotation

    private val _zoomLevel = kotlinx.coroutines.flow.MutableStateFlow(1f)
    val zoomLevel: StateFlow<Float> = _zoomLevel

    private var showerController: com.autoglm.android.core.agent.ShowerController? = null

    suspend fun connect(context: Context) {
        val controller = com.autoglm.android.core.agent.ShowerController()
        showerController = controller

        try {
            // Connect to Shower server
            val connected = controller.ensureConnected()
            _isConnected.value = connected

            if (connected) {
                // Create virtual display
                controller.ensureDisplay(1080, 1920, 320)

                // Get video size
                val (width, height) = controller.getVideoSize()
                if (width > 0 && height > 0) {
                    _videoWidth.value = width
                    _videoHeight.value = height
                }

                // Video streaming status
                _isStreaming.value = controller.isConnected()
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

    fun rotateScreen() {
        _screenRotation.value = (_screenRotation.value + 90) % 360
        Log.d(TAG, "Screen rotation: ${_screenRotation.value}")
    }

    fun setZoomLevel(level: Float) {
        _zoomLevel.value = level
        Log.d(TAG, "Zoom level: $level")
    }

    /**
     * 保存截图到相册
     */
    fun saveScreenshot(context: Context, bytes: ByteArray): Boolean {
        return try {
            // Decode to bitmap
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return false

            // Save to Pictures directory
            val picturesDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES
            )
            val ziZipDir = File(picturesDir, "ZiZip")
            if (!ziZipDir.exists()) {
                ziZipDir.mkdirs()
            }

            val timestamp = System.currentTimeMillis()
            val file = File(ziZipDir, "screenshot_$timestamp.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            // Notify media scanner
            val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                data = Uri.fromFile(file)
            }
            context.sendBroadcast(intent)

            Log.d(TAG, "Screenshot saved: ${file.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save screenshot", e)
            false
        }
    }
}
