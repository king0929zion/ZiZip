package com.autoglm.android.service.overlay

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.autoglm.android.ui.overlay.FloatingBall
import com.autoglm.android.ui.overlay.OverlayContent
import com.autoglm.android.ui.overlay.VirtualDisplayBorder
import com.autoglm.android.ui.theme.ZiZipTheme

class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null

    // Lifecycle Management for Compose
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    // Virtual Display Border state
    private var showVirtualBorder by mutableStateOf(false)

    // Virtual Display Border broadcast receiver
    private val borderReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.autoglm.android.action.SET_VIRTUAL_BORDER") {
                val visible = intent.getBooleanExtra("visible", false)
                showVirtualBorder = visible
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // 注册虚拟屏幕边框广播接收器
        registerReceiver(
            borderReceiver,
            IntentFilter("com.autoglm.android.action.SET_VIRTUAL_BORDER")
        )

        showOverlay()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    private var isExpanded = false

    private fun showOverlay() {
        if (overlayView != null) return

        val params = getLayoutParams()

        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                ZiZipTheme {
                    // 虚拟屏幕边框
                    VirtualDisplayBorder(
                        isActive = showVirtualBorder,
                        modifier = Modifier.fillMaxSize()
                    )
                    // 原有的 Overlay 内容
                    OverlayContent(
                        isExpanded = isExpanded,
                        onToggle = { toggleOverlay() }
                    )
                }
            }
        }

        windowManager.addView(overlayView, params)
    }

    /**
     * 设置虚拟屏幕边框可见性
     * @param visible 是否显示边框
     */
    fun setVirtualBorderVisible(visible: Boolean) {
        showVirtualBorder = visible
        // 触发 UI 刷新
        overlayView?.invalidate()
    }

    private fun toggleOverlay() {
        isExpanded = !isExpanded
        if (overlayView != null) {
            windowManager.updateViewLayout(overlayView, getLayoutParams())
        }
    }

    private fun getLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else 
                WindowManager.LayoutParams.TYPE_PHONE
        
        return if (isExpanded) {
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, // Allow outside touch
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }
        } else {
             WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 100
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        if (overlayView != null) {
            windowManager.removeView(overlayView)
            overlayView = null
        }
        // 注销虚拟屏幕边框广播接收器
        try {
            unregisterReceiver(borderReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }

    override fun onBind(intent: Intent): IBinder? = null

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
}
