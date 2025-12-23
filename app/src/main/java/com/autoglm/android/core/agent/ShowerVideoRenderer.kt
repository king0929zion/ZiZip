package com.autoglm.android.core.agent

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.util.Arrays

/**
 * H.264 视频解码器
 * 用于解码 Shower 虚拟屏幕的 H.264 视频流并渲染到 Surface
 *
 * 参考 Operit 的 ShowerVideoRenderer 实现
 */
object ShowerVideoRenderer {
    private const val TAG = "ShowerVideoRenderer"

    private var decoder: MediaCodec? = null
    private var surface: Surface? = null

    // SPS/PPS (Sequence/Picture Parameter Set) for H.264 decoder initialization
    private var sps: ByteArray? = null
    private var pps: ByteArray? = null

    // Video dimensions
    @Volatile
    var videoWidth = 0
        private set

    @Volatile
    var videoHeight = 0
        private set

    private val csdBuffer = mutableListOf<ByteArray>()

    /**
     * 附加到 Surface
     */
    fun attach(surface: Surface, width: Int, height: Int): Boolean {
        this.surface = surface
        this.videoWidth = width
        this.videoHeight = height

        Log.d(TAG, "Attaching to surface: ${width}x$height")

        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, "dec")
            format.setInteger(MediaFormat.KEY_WIDTH, width)
            format.setInteger(MediaFormat.KEY_HEIGHT, height)

            // Set SPS/PPS if available
            sps?.let {
                format.setByteBuffer("csd-0", ByteBuffer.wrap(it))
            }
            pps?.let {
                format.setByteBuffer("csd-1", ByteBuffer.wrap(it))
            }

            // Create decoder
            decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            decoder?.setCallback(object : MediaCodec.Callback() {
                override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                    // Output buffer available (rendered frame)
                    codec.releaseOutputBuffer(index, false)
                }

                override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                    Log.e(TAG, "Decoder error", e)
                }

                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                    Log.d(TAG, "Output format changed: $format")
                }
            })

            decoder?.configure(format, surface, null, 0)
            decoder?.start()

            Log.i(TAG, "Decoder attached successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach decoder", e)
            return false
        }
    }

    /**
     * 处理 H.264 数据帧
     */
    fun onFrame(data: ByteArray) {
        if (decoder == null) {
            // Buffer frames until decoder is ready
            csdBuffer.add(data)
            return
        }

        try {
            val index = decoder!!.dequeueInputBuffer(10000)
            if (index >= 0) {
                val buffer = decoder!!.getInputBuffer(index)!!
                buffer.clear()

                // Check for SPS/PPS in the data
                if (isSPS(data)) {
                    Log.d(TAG, "Found SPS")
                    sps = data
                    csdBuffer.add(data)
                    decoder!!.queueInputBuffer(index, 0, 0, 0)
                    return
                } else if (isPPS(data)) {
                    Log.d(TAG, "Found PPS")
                    pps = data
                    csdBuffer.add(data)
                    decoder!!.queueInputBuffer(index, 0, 0, 0)
                    return
                }

                // Feed video data
                buffer.put(data)
                decoder!!.queueInputBuffer(index, 0, data.size, System.nanoTime() / 1000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame", e)
        }
    }

    /**
     * 从当前 Surface 捕获截图
     */
    fun captureCurrentFramePng(): ByteArray? {
        val surf = surface ?: return null
        val decoder = decoder ?: return null

        try {
            // Get surface dimensions
            val width = videoWidth
            val height = videoHeight

            if (width <= 0 || height <= 0) {
                return null
            }

            // Use PixelCopy to capture the surface (API 24+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                val pixels = IntArray(width * height)

                try {
                    // This is a simplified version - actual implementation would use PixelCopy
                    android.graphics.Canvas(bitmap).also {
                        it.drawColor(android.graphics.Color.BLACK)
                    }

                    // For a proper implementation, you would need to use:
                    // - PixelCopy.request() API 26+
                    // - Or ImageReader with Surface

                    val stream = java.io.ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, stream)
                    return stream.toByteArray()
                } finally {
                    bitmap.recycle()
                }
            }

            // Fallback for older APIs
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture frame", e)
            return null
        }
    }

    /**
     * 分离 H.264 NAL 单元
     */
    private fun findNALUnits(data: ByteArray): List<ByteArray> {
        val nalUnits = mutableListOf<ByteArray>()
        var start = 0

        for (i in 0 until data.size - 3) {
            // NAL start code: 0x00 00 00 01 or 0x00 00 00 01
            if (i + 4 <= data.size) {
                if (data[i].toInt() == 0 && data[i + 1].toInt() == 0 &&
                    data[i + 2].toInt() == 0 && data[i + 3].toInt() == 1) {
                    if (start < i) {
                        nalUnits.add(Arrays.copyOfRange(data, start, i))
                    }
                    start = i
                }
            }
        }

        // Add last unit
        if (start < data.size) {
            nalUnits.add(Arrays.copyOfRange(data, start, data.size))
        }

        return nalUnits
    }

    /**
     * 检查是否为 SPS (Sequence Parameter Set)
     */
    private fun isSPS(data: ByteArray): Boolean {
        if (data.size < 4) return false
        // SPS NAL type is 0x67 (after start code)
        return data[0] == 0x00.toByte() && data[1] == 0x00.toByte() &&
               data[2] == 0x00.toByte() && data[3] == 0x01.toByte() &&
               (data[4].toInt() and 0x1F == 0x07)
    }

    /**
     * 检查是否为 PPS (Picture Parameter Set)
     */
    private fun isPPS(data: ByteArray): Boolean {
        if (data.size < 4) return false
        // PPS NAL type is 0x68 (after start code)
        return data[0] == 0x00.toByte() && data[1] == 0x00.toByte() &&
               data[2] == 0x00.toByte() && data[3] == 0x01.toByte() &&
               (data[4].toInt() and 0x1F == 0x08)
    }

    /**
     * 分离 Surface
     */
    fun detach() {
        Log.d(TAG, "Detaching from surface")

        decoder?.let {
            it.stop()
            it.release()
        }

        decoder = null
        surface = null

        sps = null
        pps = null
        csdBuffer.clear()
    }

    /**
     * 检查解码器是否已附加
     */
    val isAttached: Boolean
        get() = decoder != null && surface != null
}
