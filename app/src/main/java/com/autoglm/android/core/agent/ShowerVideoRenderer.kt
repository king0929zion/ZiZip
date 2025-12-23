package com.autoglm.android.core.agent

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.Surface
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * H.264 视频解码器
 * 用于解码 Shower 虚拟屏幕的 H.264 视频流并渲染到 Surface
 *
 * 参考 Operit 实现，修复了以下问题：
 * 1. SPS/PPS 在 detach 时不清空（避免重新 attach 后黑屏）
 * 2. AVCC 转 Annex-B 格式转换
 * 3. 真正的 PixelCopy 截图实现
 * 4. NAL 单元类型检测
 * 5. 解码器错误时的恢复机制
 */
object ShowerVideoRenderer {
    private const val TAG = "ShowerVideoRenderer"

    private val lock = Any()

    @Volatile
    private var decoder: MediaCodec? = null

    @Volatile
    private var surface: Surface? = null

    @Volatile
    private var csd0: ByteArray? = null  // SPS

    @Volatile
    private var csd1: ByteArray? = null  // PPS

    private val pendingFrames = mutableListOf<ByteArray>()

    @Volatile
    private var width: Int = 0

    @Volatile
    private var height: Int = 0

    fun attach(surface: Surface, videoWidth: Int, videoHeight: Int): Boolean {
        synchronized(lock) {
            this.surface = surface
            this.width = videoWidth
            this.height = videoHeight
            // 注意：不要在这里清空 csd0/csd1，否则当 Surface 重新创建但服务器
            // 不再发送 SPS/PPS 时，解码器将永远无法重新初始化，导致黑屏。
            // 仅在此处释放旧 decoder，并清空待处理帧。
            releaseDecoderLocked()
            pendingFrames.clear()

            Log.d(TAG, "Attached to surface: ${videoWidth}x$videoHeight")
            return true
        }
    }

    fun detach() {
        synchronized(lock) {
            Log.d(TAG, "Detaching from surface")
            releaseDecoderLocked()
            surface = null
            // 不要清空 csd0/csd1，让后续重新 attach 时仍可用之前的 SPS/PPS，避免黑屏
            pendingFrames.clear()
        }
    }

    private fun releaseDecoderLocked() {
        val dec = decoder
        decoder = null
        if (dec != null) {
            try {
                dec.stop()
            } catch (_: Exception) {
            }
            try {
                dec.release()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * 处理 H.264 数据帧
     */
    fun onFrame(data: ByteArray) {
        synchronized(lock) {
            if (surface == null || width <= 0 || height <= 0) {
                // 缓冲帧直到 surface 准备好
                pendingFrames.add(data)
                return
            }

            val packet = maybeAvccToAnnexb(data)

            // 如果解码器尚未初始化，需要从流中找到 SPS 和 PPS 包（csd-0, csd-1）
            if (decoder == null) {
                val nalType = findNalUnitType(packet)
                var reinitialized = false
                if (nalType == 7) { // SPS
                    if (csd0 == null) {
                        csd0 = packet
                        Log.d(TAG, "捕获 SPS (csd-0), size=${packet.size}")
                    }
                } else if (nalType == 8) { // PPS
                    if (csd1 == null) {
                        csd1 = packet
                        Log.d(TAG, "捕获 PPS (csd-1), size=${packet.size}")
                    }
                } else {
                    // 缓冲其他帧直到解码器准备好
                    if (pendingFrames.size < 100) { // 限制缓冲区大小
                        pendingFrames.add(packet)
                    }
                }

                if (csd0 != null && csd1 != null) {
                    initDecoderLocked()
                    // 初始化后，处理缓冲的帧
                    val framesToProcess = pendingFrames.toList()
                    pendingFrames.clear()
                    Log.d(TAG, "处理 ${framesToProcess.size} 个待处理帧")
                    framesToProcess.forEach { frame -> queueFrameToDecoder(frame) }
                }
                return // 等待更多包以找到 SPS 和 PPS
            }

            // 解码器初始化后，直接将帧加入队列
            queueFrameToDecoder(packet)
        }
    }

    /**
     * 查找 NAL 单元类型
     */
    private fun findNalUnitType(packet: ByteArray): Int {
        var offset = -1
        // 查找起始码 00 00 01 或 00 00 00 01
        for (i in 0 until packet.size - 3) {
            if (packet[i] == 0.toByte() && packet[i+1] == 0.toByte()) {
                if (packet[i+2] == 1.toByte()) {
                    offset = i + 3
                    break
                } else if (i + 3 < packet.size && packet[i+2] == 0.toByte() && packet[i+3] == 1.toByte()) {
                    offset = i + 4
                    break
                }
            }
        }

        if (offset != -1 && offset < packet.size) {
            return (packet[offset].toInt() and 0x1F)
        }
        return -1 // 未找到或无效
    }

    /**
     * 将帧加入解码器队列
     */
    private fun queueFrameToDecoder(packet: ByteArray) {
        synchronized(lock) {
            val dec = decoder ?: return
            try {
                val inIndex = dec.dequeueInputBuffer(10000) // 使用小超时
                if (inIndex >= 0) {
                    val inputBuffer: ByteBuffer? = dec.getInputBuffer(inIndex)
                    if (inputBuffer != null) {
                        inputBuffer.clear()
                        inputBuffer.put(packet)
                        dec.queueInputBuffer(inIndex, 0, packet.size, System.nanoTime() / 1000, 0)
                    }
                }

                val bufferInfo = BufferInfo()
                var outIndex = dec.dequeueOutputBuffer(bufferInfo, 0)
                while (outIndex >= 0) {
                    dec.releaseOutputBuffer(outIndex, true) // 渲染到 surface
                    outIndex = dec.dequeueOutputBuffer(bufferInfo, 0)
                }
            } catch (e: Exception) {
                Log.e(TAG, "解码器错误", e)
                // 仅重置 decoder 与待处理帧，保留已捕获的 SPS/PPS（csd0/csd1），
                // 这样后续新帧到来时可以重新初始化解码器，避免进入永久黑屏状态。
                releaseDecoderLocked()
                pendingFrames.clear()
            }
        }
    }

    /**
     * 将长度前缀（AVCC）包转换为 Annex-B 格式（如果必要）
     *
     * 如果包已经以 0x00000001 或 0x000001?? 开头，则原样返回
     */
    private fun maybeAvccToAnnexb(packet: ByteArray): ByteArray {
        if (packet.size >= 4) {
            val b0 = packet[0].toInt() and 0xFF
            val b1 = packet[1].toInt() and 0xFF
            val b2 = packet[2].toInt() and 0xFF
            val b3 = packet[3].toInt() and 0xFF
            // 0x00000001 或 0x000001?? 模式
            if (b0 == 0 && b1 == 0 && ((b2 == 0 && b3 == 1) || b2 == 1)) {
                return packet
            }
        }

        val out = ByteArrayOutputStream()
        var i = 0
        val n = packet.size
        while (i + 4 <= n) {
            val nalLen =
                ((packet[i].toInt() and 0xFF) shl 24) or
                ((packet[i + 1].toInt() and 0xFF) shl 16) or
                ((packet[i + 2].toInt() and 0xFF) shl 8) or
                (packet[i + 3].toInt() and 0xFF)
            i += 4
            if (nalLen <= 0 || i + nalLen > n) {
                // 不是有效的 AVCC 包，返回原始数据
                return packet
            }
            out.write(byteArrayOf(0, 0, 0, 1))
            out.write(packet, i, nalLen)
            i += nalLen
        }

        val result = out.toByteArray()
        return if (result.isNotEmpty()) result else packet
    }

    /**
     * 捕获解码器 surface 上渲染的当前视频帧为 PNG
     *
     * 这在解码器输出 surface 上使用 PixelCopy，因此截图始终与覆盖层中
     * 显示的实时视频流一致。
     */
    suspend fun captureCurrentFramePng(): ByteArray? {
        val s: Surface
        val w: Int
        val h: Int
        synchronized(lock) {
            val localSurface = surface
            if (localSurface == null || width <= 0 || height <= 0) {
                Log.w(TAG, "captureCurrentFramePng: 无 surface 或无效尺寸")
                return null
            }
            s = localSurface
            w = width
            h = height
        }

        if (Build.VERSION.SDK_INT < 26) {
            Log.w(TAG, "captureCurrentFramePng: PixelCopy 需要 API 26")
            return null
        }

        return withContext(Dispatchers.Main) {
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            suspendCancellableCoroutine { cont ->
                val handler = Handler(Looper.getMainLooper())
                PixelCopy.request(s, bitmap, { result ->
                    if (result == PixelCopy.SUCCESS) {
                        try {
                            val baos = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                            cont.resume(baos.toByteArray())
                        } catch (e: Exception) {
                            Log.e(TAG, "截图压缩错误", e)
                            cont.resume(null)
                        } finally {
                            bitmap.recycle()
                        }
                    } else {
                        Log.w(TAG, "PixelCopy 失败，代码=$result")
                        bitmap.recycle()
                        cont.resume(null)
                    }
                }, handler)
            }
        }
    }

    private fun initDecoderLocked() {
        val s = surface ?: return
        val localCsd0 = csd0 ?: return
        val localCsd1 = csd1 ?: return
        if (width <= 0 || height <= 0) return

        try {
            // 镜像 Python 客户端行为：确保 csd-0 / csd-1 是 Annex-B 格式
            // 以防编码器产生 AVCC 风格的长度前缀缓冲区。
            val csd0Annexb = maybeAvccToAnnexb(localCsd0)
            val csd1Annexb = maybeAvccToAnnexb(localCsd1)

            val format = MediaFormat.createVideoFormat("video/avc", width, height)
            format.setByteBuffer("csd-0", ByteBuffer.wrap(csd0Annexb))
            format.setByteBuffer("csd-1", ByteBuffer.wrap(csd1Annexb))

            val dec = MediaCodec.createDecoderByType("video/avc")
            dec.configure(format, s, null, 0)
            dec.start()
            decoder = dec
            Log.i(TAG, "MediaCodec 解码器已初始化: ${width}x$height")
        } catch (e: Exception) {
            Log.e(TAG, "初始化解码器失败", e)
            releaseDecoderLocked()
        }
    }

    val isAttached: Boolean
        get() = decoder != null && surface != null
}
