package com.autoglm.android.core.display

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.autoglm.android.core.debug.DebugLogger
import com.autoglm.android.core.shizuku.AndroidShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ZiZip 屏幕控制器
 * 使用 Shizuku 直接操作真实屏幕
 *
 * 功能：
 * - 截图 (screencap)
 * - 点击 (input tap)
 * - 滑动 (input swipe)
 * - 按键 (input keyevent)
 * - 文本输入 ( ZiZip 输入法)
 */
class ScreenController(private val context: Context) {

    companion object {
        private const val TAG = "ScreenController"
        private const val SCREENSHOT_DIR = "/sdcard/Download/ZiZip"
    }

    /**
     * 截取屏幕
     */
    suspend fun captureScreenshot(): File? = withContext(Dispatchers.IO) {
        DebugLogger.d(TAG, "开始截图...")

        try {
            // 确保目录存在
            val dir = File(SCREENSHOT_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val timestamp = System.currentTimeMillis()
            val outputPath = "$SCREENSHOT_DIR/screenshot_$timestamp.png"

            // 执行 screencap
            val result = AndroidShellExecutor.executeCommand("screencap -p $outputPath")

            if (!result.success) {
                DebugLogger.e(TAG, "截图失败: ${result.error}", null)
                return@withContext null
            }

            val file = File(outputPath)
            if (!file.exists() || file.length() == 0L) {
                DebugLogger.e(TAG, "截图文件无效", null)
                return@withContext null
            }

            DebugLogger.i(TAG, "✓ 截图成功 (${file.length()} bytes)")
            return@withContext file

        } catch (e: Exception) {
            DebugLogger.e(TAG, "截图异常: ${e.message}", e)
            null
        }
    }

    /**
     * 点击
     */
    suspend fun tap(x: Int, y: Int): Boolean {
        DebugLogger.d(TAG, "TAP: ($x, $y)")
        val result = AndroidShellExecutor.executeCommand("input tap $x $y")
        return result.success
    }

    /**
     * 滑动
     */
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 300): Boolean {
        DebugLogger.d(TAG, "SWIPE: ($x1,$y1) -> ($x2,$y2)")
        val result = AndroidShellExecutor.executeCommand("input swipe $x1 $y1 $x2 $y2 $durationMs")
        return result.success
    }

    /**
     * 按键
     */
    suspend fun key(keyCode: Int): Boolean {
        DebugLogger.d(TAG, "KEY: $keyCode")
        val result = AndroidShellExecutor.executeCommand("input keyevent $keyCode")
        return result.success
    }

    /**
     * 输入文本
     * 使用 ZiZip 输入法
     */
    fun inputText(text: String): Boolean {
        DebugLogger.d(TAG, "TEXT: \"$text\"")
        return com.autoglm.android.service.ime.ZiZipInputMethod.inputTextDirect(text)
    }

    /**
     * 返回键
     */
    suspend fun pressBack(): Boolean = key(4)

    /**
     * Home 键
     */
    suspend fun pressHome(): Boolean = key(3)

    /**
     * 获取屏幕尺寸
     */
    fun getScreenSize(): Pair<Int, Int> {
        val metrics = context.resources.displayMetrics
        return Pair(metrics.widthPixels, metrics.heightPixels)
    }
}
