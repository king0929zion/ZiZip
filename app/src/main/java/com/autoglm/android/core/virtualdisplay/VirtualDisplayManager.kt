package com.autoglm.android.core.virtualdisplay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.Surface
import com.autoglm.android.core.debug.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * ZiZip 内置虚拟屏幕管理器
 * 直接在应用内创建和管理虚拟屏幕，无需外部 APK
 *
 * 功能：
 * - 通过反射创建 VirtualDisplay
 * - 截图功能（使用 ImageReader）
 * - 输入事件路由到虚拟屏幕
 */
class VirtualDisplayManager(private val context: Context) {

    companion object {
        private const val TAG = "VirtualDisplayManager"
        private const val DISPLAY_NAME = "ZiZipVirtualDisplay"

        // 虚拟屏幕标志位
        private const val FLAG_PUBLIC = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
        private const val FLAG_PRESENTATION = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
        private const val FLAG_OWN_CONTENT_ONLY = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
        private const val FLAG_SUPPORTS_TOUCH = 1 shl 6  // 0x40

        // Android 13+ 标志
        private const val FLAG_TRUSTED = 1 shl 10  // Android 13
        private const val FLAG_ALWAYS_UNLOCKED = 1 shl 12  // Android 13
    }

    private var virtualDisplay: VirtualDisplay? = null
    private var displayId: Int = Display.INVALID_DISPLAY
    private var imageReader: ImageReader? = null
    private var readerSurface: Surface? = null

    val display: Display?
        get() = virtualDisplay?.display

    val isActive: Boolean
        get() = virtualDisplay != null

    /**
     * 创建虚拟屏幕
     * @param width 宽度（像素）
     * @param height 高度（像素）
     * @param densityDpi DPI 密度
     * @return 是否创建成功
     */
    @SuppressLint("WrongConstant")
    suspend fun createDisplay(
        width: Int,
        height: Int,
        densityDpi: Int = 320
    ): Boolean = withContext(Dispatchers.IO) {
        DebugLogger.i(TAG, "========== 创建虚拟屏幕 ==========")
        DebugLogger.d(TAG, "尺寸: ${width}x${height}, DPI: $densityDpi")

        try {
            // 如果已存在，先释放
            releaseDisplay()

            // 对齐到 8 的倍数（H.264 编码器要求）
            val alignedWidth = width and 0xFFFFFFF8
            val alignedHeight = height and 0xFFFFFFF8
            val finalWidth = maxOf(8, alignedWidth)
            val finalHeight = maxOf(8, alignedHeight)

            DebugLogger.d(TAG, "对齐后尺寸: ${finalWidth}x${finalHeight}")

            // 创建 ImageReader 用于截图
            imageReader = ImageReader.newInstance(finalWidth, finalHeight, PixelFormat.RGBA_8888, 2)
            readerSurface = imageReader?.surface

            if (readerSurface == null) {
                DebugLogger.e(TAG, "✗ 创建 ImageReader Surface 失败", null)
                return@withContext false
            }

            // 计算标志位
            var flags = FLAG_PUBLIC or FLAG_PRESENTATION or FLAG_OWN_CONTENT_ONLY or FLAG_SUPPORTS_TOUCH

            if (Build.VERSION.SDK_INT >= 33) {
                flags = flags or FLAG_TRUSTED or FLAG_ALWAYS_UNLOCKED
                DebugLogger.d(TAG, "使用 Android 13+ 标志")
            }

            // 通过反射创建 DisplayManager
            val dm = createDisplayManager()
            if (dm == null) {
                DebugLogger.e(TAG, "✗ 创建 DisplayManager 失败", null)
                return@withContext false
            }

            // 创建虚拟屏幕
            DebugLogger.d(TAG, "调用 createVirtualDisplay...")
            virtualDisplay = dm.createVirtualDisplay(
                DISPLAY_NAME,
                finalWidth,
                finalHeight,
                densityDpi,
                readerSurface,
                flags or DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR
            )

            if (virtualDisplay == null) {
                DebugLogger.e(TAG, "✗ createVirtualDisplay 返回 null", null)
                return@withContext false
            }

            // 获取 display ID
            display =let {
                displayId = it.displayId
                DebugLogger.i(TAG, "✓ 虚拟屏幕创建成功 (ID: $displayId)")
                true
            } ?: run {
                DebugLogger.e(TAG, "✗ 获取 Display 失败", null)
                false
            }

        } catch (e: SecurityException) {
            DebugLogger.e(TAG, "✗ 权限不足: ${e.message}", e)
            DebugLogger.e(TAG, "需要 CREATE_VIRTUAL_DISPLAY 权限", null)
            false
        } catch (e: Exception) {
            DebugLogger.e(TAG, "✗ 创建虚拟屏幕异常: ${e.message}", e)
            false
        }
    }

    /**
     * 通过反射创建 DisplayManager
     * 需要使用隐藏构造函数 DisplayManager(Context)
     */
    private fun createDisplayManager(): DisplayManager? {
        return try {
            val ctor = DisplayManager::class.java.getDeclaredConstructor(Context::class.java)
            ctor.isAccessible = true
            ctor.newInstance(context)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "反射创建 DisplayManager 失败", e)
            null
        }
    }

    /**
     * 截取虚拟屏幕当前画面
     * @return PNG 格式的字节数组，失败返回 null
     */
    suspend fun captureScreenshot(): ByteArray? = withContext(Dispatchers.IO) {
        if (!isActive) {
            DebugLogger.w(TAG, "虚拟屏幕未激活，无法截图", null)
            return@withContext null
        }

        val reader = imageReader
        if (reader == null) {
            DebugLogger.e(TAG, "ImageReader 为 null", null)
            return@withContext null
        }

        try {
            DebugLogger.d(TAG, "开始截图...")

            // 等待新帧
            var image: Image? = null
            val startTime = System.currentTimeMillis()
            val timeout = 3000L

            while (image == null && System.currentTimeMillis() - startTime < timeout) {
                image = reader.acquireLatestImage()
                if (image == null) {
                    kotlinx.coroutines.delay(50)
                }
            }

            if (image == null) {
                DebugLogger.w(TAG, "截图超时", null)
                return@withContext null
            }

            // 转换为 Bitmap
            val bitmap = imageToBitmap(image)
            image.close()

            if (bitmap == null) {
                DebugLogger.e(TAG, "转换 Bitmap 失败", null)
                return@withContext null
            }

            // 压缩为 PNG
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            bitmap.recycle()

            val result = stream.toByteArray()
            DebugLogger.i(TAG, "✓ 截图成功 (${result.size} bytes)")
            result

        } catch (e: Exception) {
            DebugLogger.e(TAG, "截图失败: ${e.message}", e)
            null
        }
    }

    /**
     * 将 Image 转换为 Bitmap
     */
    private fun imageToBitmap(image: Image): Bitmap? {
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        // 创建 Bitmap
        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )

        bitmap.copyPixelsFromBuffer(buffer)

        // 如果有填充，裁剪掉
        return if (rowPadding == 0) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        }
    }

    /**
     * 获取虚拟屏幕的 Surface
     * 用于 MediaCodec 编码器连接
     */
    fun getSurface(): Surface? {
        return readerSurface
    }

    /**
     * 设置显示输入法策略（需要反射）
     * @param local 是否使用本地输入法（true）还是系统默认（false）
     */
    fun setImePolicy(local: Boolean): Boolean {
        if (displayId == Display.INVALID_DISPLAY) {
            DebugLogger.w(TAG, "无效的 display ID", null)
            return false
        }

        return try {
            // 通过反射调用 WindowManager.setDisplayImePolicy
            val wmClass = Class.forName("android.view.WindowManager")
            val wmInstance = wmClass.getMethod("getInstance").invoke(null)
            val setImePolicyMethod = wmClass.getMethod(
                "setDisplayImePolicy",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )

            val policy = if (local) 1 else 0  // DISPLAY_IME_POLICY_LOCAL = 1
            setImePolicyMethod.invoke(wmInstance, displayId, policy)
            DebugLogger.d(TAG, "IME 策略已设置: ${if (local) "LOCAL" else "DEFAULT"}")
            true
        } catch (e: Exception) {
            DebugLogger.w(TAG, "设置 IME 策略失败（可能不支持）: ${e.message}", e)
            false
        }
    }

    /**
     * 释放虚拟屏幕
     */
    fun releaseDisplay() {
        DebugLogger.d(TAG, "释放虚拟屏幕...")
        virtualDisplay?.release()
        virtualDisplay = null
        displayId = Display.INVALID_DISPLAY

        readerSurface?.release()
        readerSurface = null

        imageReader?.close()
        imageReader = null

        DebugLogger.i(TAG, "✓ 虚拟屏幕已释放")
    }
}
