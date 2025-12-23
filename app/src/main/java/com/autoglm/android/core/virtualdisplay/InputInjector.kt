package com.autoglm.android.core.virtualdisplay

import android.hardware.input.InputManager
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import com.autoglm.android.core.debug.DebugLogger

/**
 * ZiZip 内置输入事件注入器
 * 通过反射调用 InputManager.injectInputEvent
 * 支持触摸、滑动、按键事件
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
            DebugLogger.d(TAG, "Display ID 设置为: $value")
        }

    @Volatile
    private var touchActive = false
    private var touchDownTime = 0L

    @Throws(Exception::class)
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
                DebugLogger.w(TAG, "setDisplayId 方法不存在（API < 17）", null)
                null
            }

            DebugLogger.i(TAG, "✓ InputInjector 初始化成功")
        } catch (e: Exception) {
            DebugLogger.e(TAG, "✗ InputInjector 初始化失败", e)
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
                    DebugLogger.w(TAG, "设置 Display ID 失败: ${e.message}", e)
                }
            }

            // 注入事件
            val result = injectInputEventMethod.invoke(inputManager, event, 0) as Boolean
            if (!result) {
                DebugLogger.w(TAG, "注入事件返回 false", null)
            }
            result
        } catch (e: Exception) {
            DebugLogger.e(TAG, "注入事件异常: ${e.message}", e)
            false
        }
    }

    /**
     * 点击
     */
    fun injectTap(x: Float, y: Float): Boolean {
        DebugLogger.d(TAG, "TAP: ($x, $y)")
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
    suspend fun injectSwipe(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        durationMs: Long
    ): Boolean {
        DebugLogger.d(TAG, "SWIPE: ($x1,$y1) -> ($x2,$y2), ${durationMs}ms")

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
     * 按下触摸
     */
    fun touchDown(x: Float, y: Float): Boolean {
        DebugLogger.d(TAG, "TOUCH_DOWN: ($x, $y)")
        touchDownTime = SystemClock.uptimeMillis()
        touchActive = true

        val event = MotionEvent.obtain(touchDownTime, touchDownTime, MotionEvent.ACTION_DOWN, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        return injectEvent(event)
    }

    /**
     * 移动触摸
     */
    fun touchMove(x: Float, y: Float): Boolean {
        if (!touchActive) {
            return touchDown(x, y)
        }

        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(touchDownTime, now, MotionEvent.ACTION_MOVE, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        return injectEvent(event)
    }

    /**
     * 抬起触摸
     */
    fun touchUp(x: Float, y: Float): Boolean {
        if (!touchActive) {
            // 如果没有按下，直接点击
            return injectTap(x, y)
        }

        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(touchDownTime, now, MotionEvent.ACTION_UP, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        touchActive = false
        return injectEvent(event)
    }

    /**
     * 按键
     */
    fun injectKey(keyCode: Int): Boolean {
        DebugLogger.d(TAG, "KEY: $keyCode")

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

    /**
     * 输入文本
     */
    fun injectText(text: String): Boolean {
        DebugLogger.d(TAG, "TEXT: \"$text\"")

        val keyEventMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
        val events = keyEventMap.getEvents(text.toCharArray()) ?: return false

        var success = true
        for (event in events) {
            event.source = InputDevice.SOURCE_KEYBOARD
            if (!injectEvent(event)) {
                success = false
            }
        }
        return success
    }

    /**
     * 返回键
     */
    fun pressBack(): Boolean = injectKey(KeyEvent.KEYCODE_BACK)

    /**
     * Home 键
     */
    fun pressHome(): Boolean = injectKey(KeyEvent.KEYCODE_HOME)

    /**
     * Enter 键
     */
    fun pressEnter(): Boolean = injectKey(KeyEvent.KEYCODE_ENTER)
}
