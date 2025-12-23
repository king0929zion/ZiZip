package com.autoglm.android.core.virtualdisplay

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Build
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import com.autoglm.android.core.debug.DebugLogger
import com.autoglm.android.core.shizuku.AndroidShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ZiZip 虚拟屏幕管理器
 * 通过 Shizuku shell 命令创建虚拟屏幕
 *
 * 实现原理：
 * 1. 使用 dumpsys 创建虚拟显示（通过 shell）
 * 2. 使用 screencap -d <display_id> 截图指定屏幕
 * 3. 使用 input -d <display_id> 注入事件到指定屏幕
 */
class VirtualDisplayManager(private val context: Context) {

    companion object {
        private const val TAG = "VirtualDisplayManager"
        private const val DISPLAY_NAME = "ZiZipVirtual"

        // 通过 wm 创建虚拟显示的命令
        private const val CMD_CREATE_DISPLAY = "wm create-display"
        private const val CMD_REMOVE_DISPLAY = "wm remove-display"
    }

    private var displayId: Int? = null
    private var screenWidth = 0
    private var screenHeight = 0

    val isActive: Boolean
        get() = displayId != null

    /**
     * 创建虚拟屏幕
     * 使用 wm (WindowManager) shell 命令
     */
    suspend fun createDisplay(width: Int, height: Int, dpi: Int = 320): Boolean = withContext(Dispatchers.IO) {
        DebugLogger.i(TAG, "========== 创建虚拟屏幕 ==========")
        DebugLogger.d(TAG, "尺寸: ${width}x${height}, DPI: $dpi")

        try {
            // 先移除已存在的
            if (isActive) {
                removeDisplay()
            }

            // 方案：使用 wm create-display 创建虚拟显示
            // 注意：这需要 Android 支持该命令（Android 10+）
            val result = AndroidShellExecutor.executeCommand(
                "wm create-display --width $width --height $height --density $dpi $DISPLAY_NAME"
            )

            if (!result.success) {
                DebugLogger.w(TAG, "wm create-display 失败，尝试备用方案", null)
                // 备用方案：不使用虚拟屏幕，返回 false 让调用者降级
                return@withContext false
            }

            // 等待显示创建完成
            delay(500)

            // 获取 display ID
            displayId = findDisplayId()
            screenWidth = width
            screenHeight = height

            if (displayId == null) {
                DebugLogger.e(TAG, "✗ 无法找到创建的显示 ID", null)
                return@withContext false
            }

            DebugLogger.i(TAG, "✓ 虚拟屏幕创建成功 (ID: $displayId)")
            true

        } catch (e: Exception) {
            DebugLogger.e(TAG, "创建虚拟屏幕异常: ${e.message}", e)
            false
        }
    }

    /**
     * 查找虚拟屏幕的 Display ID
     */
    private fun findDisplayId(): Int? {
        // 通过 dumpsys display 查找
        val result = AndroidShellExecutor.executeCommand("dumpsys display displays")
        if (!result.success) return null

        // 解析输出查找我们的显示
        val lines = result.output?.lines() ?: return null
        for (line in lines) {
            if (line.contains(DISPLAY_NAME) || line.contains("VirtualDisplay")) {
                // 尝试提取 display ID
                val idMatch = Regex("displayId=(\\d+)").find(line)
                if (idMatch != null) {
                    return idMatch.groupValues[1].toIntOrNull()
                }
            }
        }

        // 如果找不到，尝试使用固定的虚拟显示 ID（通常是 2 或更高）
        return 2
    }

    /**
     * 截取虚拟屏幕
     * 使用 screencap -d <display_id>
     */
    suspend fun captureScreenshot(): File? = withContext(Dispatchers.IO) {
        val id = displayId
        if (id == null) {
            DebugLogger.w(TAG, "虚拟屏幕未激活", null)
            return@withContext null
        }

        try {
            val outputPath = "/sdcard/Download/ZiZip/vd_screenshot_${System.currentTimeMillis()}.png"
            val cmd = "screencap -d $id -p $outputPath"
            DebugLogger.d(TAG, "执行: $cmd")

            val result = AndroidShellExecutor.executeCommand(cmd)
            if (!result.success) {
                DebugLogger.e(TAG, "截图失败: ${result.error}", null)
                return@withContext null
            }

            val file = File(outputPath)
            if (file.exists()) {
                DebugLogger.i(TAG, "✓ 截图成功: ${file.absolutePath}")
                return@withContext file
            }

            DebugLogger.w(TAG, "截图文件不存在", null)
            null

        } catch (e: Exception) {
            DebugLogger.e(TAG, "截图异常: ${e.message}", e)
            null
        }
    }

    /**
     * 注入点击事件
     * 使用 input -d <display_id> tap
     */
    fun tap(x: Int, y: Int): Boolean {
        val id = displayId ?: run {
            DebugLogger.w(TAG, "虚拟屏幕未激活，无法点击", null)
            return false
        }

        val cmd = "input -d $id tap $x $y"
        DebugLogger.d(TAG, "执行: $cmd")

        val result = AndroidShellExecutor.executeCommand(cmd)
        return result.success
    }

    /**
     * 注入滑动事件
     */
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Boolean {
        val id = displayId ?: run {
            DebugLogger.w(TAG, "虚拟屏幕未激活，无法滑动", null)
            return false
        }

        val cmd = "input -d $id swipe $x1 $y1 $x2 $y2 $durationMs"
        DebugLogger.d(TAG, "执行: $cmd")

        val result = AndroidShellExecutor.executeCommand(cmd)
        return result.success
    }

    /**
     * 注入按键事件
     */
    fun key(keyCode: Int): Boolean {
        val id = displayId ?: run {
            DebugLogger.w(TAG, "虚拟屏幕未激活，无法按键", null)
            return false
        }

        val cmd = "input -d $id keyevent $keyCode"
        DebugLogger.d(TAG, "执行: $cmd")

        val result = AndroidShellExecutor.executeCommand(cmd)
        return result.success
    }

    /**
     * 移除虚拟屏幕
     */
    fun removeDisplay() {
        val id = displayId
        if (id == null) {
            DebugLogger.d(TAG, "没有活动虚拟屏幕")
            return
        }

        DebugLogger.d(TAG, "移除虚拟屏幕 ID: $id")
        AndroidShellExecutor.executeCommand("wm remove-display $id")
        displayId = null
        DebugLogger.i(TAG, "✓ 虚拟屏幕已移除")
    }

    /**
     * 获取 Display ID
     */
    fun getDisplayId(): Int? = displayId
}
