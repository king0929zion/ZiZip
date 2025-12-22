package com.autoglm.android.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * AutoGLM 无障碍服务
 * 提供屏幕操作和内容获取功能
 */
class AutoGLMAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "onServiceConnected - 服务已连接")
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 可以在这里捕获屏幕事件用于分析
        event?.let {
            when (it.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    Log.v(TAG, "窗口状态变化: ${it.packageName} - ${it.className}")
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    // 内容变化，可用于监听页面加载完成
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt - 服务被中断")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy - 服务已销毁")
        instance = null
    }
    
    // ================== 屏幕内容获取 ==================
    
    /**
     * 获取当前屏幕的节点树（简化版）
     */
    fun getScreenContent(): String {
        val rootNode = rootInActiveWindow ?: return "无法获取屏幕内容"
        return buildNodeTree(rootNode, 0)
    }
    
    private fun buildNodeTree(node: AccessibilityNodeInfo, depth: Int): String {
        val sb = StringBuilder()
        val indent = "  ".repeat(depth)
        
        val className = node.className?.toString()?.substringAfterLast('.') ?: "Unknown"
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        
        val displayText = when {
            text.isNotBlank() -> "\"$text\""
            contentDesc.isNotBlank() -> "[$contentDesc]"
            else -> ""
        }
        
        val clickable = if (node.isClickable) " [可点击]" else ""
        val editable = if (node.isEditable) " [可编辑]" else ""
        
        sb.append("$indent$className$displayText$clickable$editable (${bounds.centerX()},${bounds.centerY()})\n")
        
        // 递归处理子节点（限制深度）
        if (depth < 8) {
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    sb.append(buildNodeTree(child, depth + 1))
                    child.recycle()
                }
            }
        }
        
        return sb.toString()
    }
    
    /**
     * 获取当前前台应用包名
     */
    fun getCurrentPackage(): String? {
        return rootInActiveWindow?.packageName?.toString()
    }
    
    // ================== 手势操作 ==================
    
    /**
     * 执行点击操作
     */
    suspend fun performClick(x: Int, y: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "手势操作需要 Android N 以上版本")
            return false
        }
        
        return suspendCancellableCoroutine { continuation ->
            val path = Path().apply {
                moveTo(x.toFloat(), y.toFloat())
            }
            
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                .build()
            
            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "点击完成: ($x, $y)")
                    continuation.resume(true)
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "点击取消: ($x, $y)")
                    continuation.resume(false)
                }
            }
            
            dispatchGesture(gesture, callback, null)
        }
    }
    
    /**
     * 执行滑动操作
     */
    suspend fun performSwipe(
        startX: Int, startY: Int,
        endX: Int, endY: Int,
        duration: Long = 300
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "手势操作需要 Android N 以上版本")
            return false
        }
        
        return suspendCancellableCoroutine { continuation ->
            val path = Path().apply {
                moveTo(startX.toFloat(), startY.toFloat())
                lineTo(endX.toFloat(), endY.toFloat())
            }
            
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()
            
            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "滑动完成: ($startX,$startY) -> ($endX,$endY)")
                    continuation.resume(true)
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "滑动取消")
                    continuation.resume(false)
                }
            }
            
            dispatchGesture(gesture, callback, null)
        }
    }
    
    /**
     * 执行文本输入
     * 需要先聚焦到输入框
     */
    fun inputText(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        
        if (focusedNode == null) {
            Log.w(TAG, "没有找到聚焦的输入框")
            rootNode.recycle()
            return false
        }
        
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        
        val success = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        Log.d(TAG, "输入文本: \"$text\" - ${if (success) "成功" else "失败"}")
        
        focusedNode.recycle()
        rootNode.recycle()
        return success
    }
    
    /**
     * 执行返回操作
     */
    fun performBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }
    
    /**
     * 返回主屏幕
     */
    fun performHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }
    
    /**
     * 打开最近任务
     */
    fun performRecents(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    companion object {
        const val TAG = "AutoGLMA11y"
        
        @Volatile
        var instance: AutoGLMAccessibilityService? = null
            private set
        
        /**
         * 检查服务是否已启用
         */
        fun isEnabled(): Boolean = instance != null
        
        /**
         * 执行全局操作
         */
        fun doGlobalAction(action: Int): Boolean {
            return instance?.performGlobalAction(action) ?: false
        }
        
        /**
         * 获取屏幕内容
         */
        fun getContent(): String? {
            return instance?.getScreenContent()
        }
        
        /**
         * 执行点击
         */
        suspend fun click(x: Int, y: Int): Boolean {
            return instance?.performClick(x, y) ?: false
        }
        
        /**
         * 执行滑动
         */
        suspend fun swipe(startX: Int, startY: Int, endX: Int, endY: Int): Boolean {
            return instance?.performSwipe(startX, startY, endX, endY) ?: false
        }
        
        /**
         * 输入文本
         */
        fun typeText(text: String): Boolean {
            return instance?.inputText(text) ?: false
        }
        
        /**
         * 返回
         */
        fun back(): Boolean {
            return instance?.performBack() ?: false
        }
        
        /**
         * 主屏幕
         */
        fun home(): Boolean {
            return instance?.performHome() ?: false
        }
    }
}
