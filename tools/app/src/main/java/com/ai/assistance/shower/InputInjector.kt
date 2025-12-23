package com.ai.assistance.shower

import android.hardware.input.InputManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import android.util.Log

/**
 * Shower Server 输入事件注入器
 * 通过反射调用 InputManager.injectInputEvent
 * 不需要 Shizuku 或 root 权限
 */
class InputInjector {
    companion object {
        private const val TAG = "InputInjector"
    }

    private val inputManager: Any
    private val injectInputEventMethod: java.lang.reflect.Method
    private val setDisplayIdMethod: java.lang.reflect.Method?

    var displayId: Int = 0
        set(value) {
            field = value
            Log.d(TAG, "Display ID: $value")
        }

    @Volatile
    private var touchDownTime = 0L

    @Suppress("TooGenericExceptionCaught")
    constructor() {
        try {
            // 获取 InputManager 实例
            val imeClass = InputManager::class.java
            val getInstanceMethod = imeClass.getDeclaredMethod("getInstance")
            getInstanceMethod.isAccessible = true
            inputManager = getInstanceMethod.invoke(null)!!

            // 获取 injectInputEvent 方法
            injectInputEventMethod = imeClass.getDeclaredMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.javaPrimitiveType
            )
            injectInputEventMethod.isAccessible = true

            // 获取 setDisplayId 方法（API 17+）
            setDisplayIdMethod = try {
                InputEvent::class.java.getMethod("setDisplayId", Int::class.javaPrimitiveType)
            } catch (e: NoSuchMethodException) {
                Log.w(TAG, "setDisplayId not available (API < 17)")
                null
            }

            Log.i(TAG, "InputInjector initialized")
        } catch (e: Exception) {
            Log.e(TAG, "InputInjector init failed", e)
            throw e
        }
    }

    /**
     * 注入输入事件
     */
    private fun injectEvent(event: InputEvent): Boolean {
        return try {
            // 设置 Display ID（路由到虚拟屏幕）
            if (displayId != 0 && setDisplayIdMethod != null) {
                try {
                    setDisplayIdMethod.invoke(event, displayId)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to set Display ID: ${e.message}")
                }
            }

            // 注入事件
            val result = injectInputEventMethod.invoke(inputManager, event, 0) as Boolean
            if (!result) {
                Log.w(TAG, "injectEvent returned false")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "injectEvent error", e)
            false
        }
    }

    /**
     * 点击
     */
    fun tap(x: Float, y: Float): Boolean {
        Log.d(TAG, "TAP: ($x, $y)")
        val now = SystemClock.uptimeMillis()

        // DOWN 事件
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        val downResult = injectEvent(down)

        // UP 事件
        val up = MotionEvent.obtain(now, now + 10, MotionEvent.ACTION_UP, x, y, 0)
        up.source = InputDevice.SOURCE_TOUCHSCREEN
        val upResult = injectEvent(up)

        return downResult && upResult
    }

    /**
     * 滑动
     */
    suspend fun swipe(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        durationMs: Long
    ): Boolean {
        Log.d(TAG, "SWIPE: ($x1,$y1) -> ($x2,$y2), ${durationMs}ms")

        val startTime = SystemClock.uptimeMillis()
        val endTime = startTime + durationMs

        // DOWN 事件
        val down = MotionEvent.obtain(startTime, startTime, MotionEvent.ACTION_DOWN, x1, y1, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        injectEvent(down)

        // 分步移动
        val steps = 10
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            val x = x1 + (x2 - x1) * t
            val y = y1 + (y2 - y1) * t
            val eventTime = startTime + (durationMs * t).toLong()

            val move = MotionEvent.obtain(startTime, eventTime, MotionEvent.ACTION_MOVE, x, y, 0)
            move.source = InputDevice.SOURCE_TOUCHSCREEN
            injectEvent(move)

            val stepDelay = (durationMs / steps).toLong()
            if (stepDelay > 0) {
                kotlinx.coroutines.delay(stepDelay)
            }
        }

        // UP 事件
        val up = MotionEvent.obtain(startTime, endTime, MotionEvent.ACTION_UP, x2, y2, 0)
        up.source = InputDevice.SOURCE_TOUCHSCREEN
        return injectEvent(up)
    }

    /**
     * 按键
     */
    fun key(keyCode: Int): Boolean {
        Log.d(TAG, "KEY: $keyCode")

        val now = SystemClock.uptimeMillis()

        // DOWN
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0)
        down.source = InputDevice.SOURCE_KEYBOARD
        val downResult = injectEvent(down)

        // UP
        val up = KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0)
        up.source = InputDevice.SOURCE_KEYBOARD
        val upResult = injectEvent(up)

        return downResult && upResult
    }
}
