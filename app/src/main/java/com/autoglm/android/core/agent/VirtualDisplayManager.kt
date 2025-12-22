package com.autoglm.android.core.agent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import java.io.File
import java.io.FileOutputStream

/**
 * 虚拟屏幕管理器
 * 使用 Android 原生 API 创建虚拟屏幕
 * 参考 Operit 实现
 */
class VirtualDisplayManager private constructor(private val context: Context) {
    companion object {
        private const val TAG = "VirtualDisplayManager"

        @Volatile
        private var instance: VirtualDisplayManager? = null

        fun getInstance(context: Context): VirtualDisplayManager {
            return instance ?: synchronized(this) {
                instance ?: VirtualDisplayManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var displayId: Int? = null

    /**
     * 确保虚拟屏幕已创建
     */
    fun ensureVirtualDisplay(): Int? {
        if (virtualDisplay != null && displayId != null) {
            return displayId
        }
        return createVirtualDisplay()
    }

    fun getDisplayId(): Int? = displayId

    /**
     * 释放虚拟屏幕资源
     */
    fun release() {
        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing virtual display", e)
        }
        virtualDisplay = null

        try {
            imageReader?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing ImageReader", e)
        }
        imageReader = null
        displayId = null
    }

    /**
     * 捕获最新帧到文件
     */
    fun captureLatestFrameToFile(file: File): Boolean {
        val reader = imageReader ?: return false
        var image: Image? = null
        
        return try {
            image = reader.acquireLatestImage() ?: return false

            val width = image.width
            val height = image.height
            if (width <= 0 || height <= 0) return false

            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width

            val bitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
            FileOutputStream(file).use { out ->
                cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            // 回收 bitmap
            if (bitmap != cropped) bitmap.recycle()
            cropped.recycle()
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing frame", e)
            false
        } finally {
            try {
                image?.close()
            } catch (_: Exception) {}
        }
    }

    private fun createVirtualDisplay(): Int? {
        return try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)

            val statusBarHeight = getStatusBarHeight()
            val width = metrics.widthPixels
            val height = (metrics.heightPixels - statusBarHeight).coerceAtLeast(1)
            val densityDpi = metrics.densityDpi

            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader = reader
            val surface: Surface = reader.surface

            val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION

            val vd = displayManager.createVirtualDisplay(
                "ZiZipVirtualDisplay",
                width,
                height,
                densityDpi,
                surface,
                flags
            )
            virtualDisplay = vd

            val display = vd.display
            val id = display?.displayId
            displayId = id

            Log.d(TAG, "Created virtual display id=$id, size=${width}x$height, density=$densityDpi")
            id
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create virtual display", e)
            null
        }
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }
}
