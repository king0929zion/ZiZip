package com.autoglm.android.core.agent

import android.util.Log

/**
 * 坐标归一化工具
 * 将 0-1000 的归一化坐标转换为屏幕像素坐标
 *
 * 归一化坐标系统：
 * - (0, 0) = 屏幕左上角
 * - (1000, 1000) = 屏幕右下角
 * - (500, 500) = 屏幕中心
 *
 * 参考 Operit 的 parseRelativePoint 实现
 */
object CoordinateNormalizer {
    private const val TAG = "CoordinateNormalizer"
    private const val NORMALIZED_MAX = 1000

    @Volatile
    private var screenWidth: Int = 1080

    @Volatile
    private var screenHeight: Int = 2400

    /**
     * 初始化屏幕尺寸
     * @param width 屏幕宽度（像素）
     * @param height 屏幕高度（像素）
     */
    fun init(width: Int, height: Int) {
        screenWidth = if (width > 0) width else 1080
        screenHeight = if (height > 0) height else 2400
        Log.d(TAG, "Initialized with screen size: ${screenWidth}x$screenHeight")
    }

    /**
     * 检测坐标是否为归一化坐标
     * @param x X 坐标值
     * @param y Y 坐标值
     * @return 如果两个坐标都在 0-1000 范围内，认为是归一化坐标
     */
    fun isNormalized(x: Int, y: Int): Boolean {
        return x in 0..NORMALIZED_MAX && y in 0..NORMALIZED_MAX
    }

    /**
     * 将归一化坐标转换为像素坐标
     * @param x 归一化 X 坐标 (0-1000)
     * @param y 归一化 Y 坐标 (0-1000)
     * @return 像素坐标对 (pixelX, pixelY)
     */
    fun toPixel(x: Int, y: Int): Pair<Int, Int> {
        val pixelX = (x * screenWidth / NORMALIZED_MAX.toDouble()).toInt()
            .coerceIn(0, screenWidth)
        val pixelY = (y * screenHeight / NORMALIZED_MAX.toDouble()).toInt()
            .coerceIn(0, screenHeight)
        return pixelX to pixelY
    }

    /**
     * 批量转换多个坐标点
     * @param points 坐标列表，每个点为 Pair<Int, Int>
     * @return 转换后的像素坐标列表
     */
    fun toPixelBatch(points: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        return points.map { (x, y) -> toPixel(x, y) }
    }

    /**
     * 获取当前屏幕尺寸
     */
    fun getScreenSize(): Pair<Int, Int> = screenWidth to screenHeight

    /**
     * 设置屏幕尺寸（用于动态调整）
     */
    fun setScreenSize(width: Int, height: Int) {
        init(width, height)
    }
}
