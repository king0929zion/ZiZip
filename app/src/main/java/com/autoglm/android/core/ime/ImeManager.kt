package com.autoglm.android.core.ime

import android.content.Context
import android.util.Log
import com.autoglm.android.core.debug.DebugLogger
import com.autoglm.android.core.shizuku.AndroidShellExecutor
import com.autoglm.android.service.ime.ZiZipInputMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 输入法管理器
 * 自动切换输入法完成 AI 输入，然后自动切回
 */
object ImeManager {
    private const val TAG = "ImeManager"

    private var previousImeId: String? = null
    private var isZiZipActive = false

    /**
     * 检查 ZiZip 输入法是否已启用
     */
    fun isEnabled(context: Context): Boolean {
        return ZiZipInputMethod.isEnabled(context)
    }

    /**
     * 检查 ZiZip 输入法是否为当前输入法
     */
    fun isCurrentInputMethod(context: Context): Boolean {
        return ZiZipInputMethod.isCurrentInputMethod(context)
    }

    /**
     * 切换到 ZiZip 输入法
     * @return 是否成功切换
     */
    suspend fun switchToZiZip(context: Context): Boolean = withContext(Dispatchers.IO) {
        DebugLogger.i(TAG, "========== 切换到 ZiZip 输入法 ==========")

        // 检查是否已启用
        if (!isEnabled(context)) {
            DebugLogger.e(TAG, "✗ ZiZip 输入法未启用", null)
            DebugLogger.e(TAG, "请在系统设置中启用 ZiZip 输入法", null)
            return@withContext false
        }

        // 如果已经是当前输入法，直接返回
        if (isCurrentInputMethod(context)) {
            DebugLogger.i(TAG, "✓ ZiZip 输入法已是当前输入法")
            isZiZipActive = true
            return@withContext true
        }

        // 保存当前输入法 ID
        val currentIme = getCurrentInputMethodId(context)
        if (currentIme != null && currentIme != ZiZipInputMethod.IME_ID) {
            previousImeId = currentIme
            DebugLogger.d(TAG, "保存当前输入法: $currentIme")
        }

        // 切换到 ZiZip 输入法
        val result = ZiZipInputMethod.switchToThisInputMethod(context)
        if (result) {
            isZiZipActive = true
            DebugLogger.i(TAG, "✓ 已切换到 ZiZip 输入法")
        } else {
            DebugLogger.e(TAG, "✗ 切换到 ZiZip 输入法失败", null)
        }

        result
    }

    /**
     * 切换回之前的输入法
     */
    suspend fun switchBack(context: Context): Boolean = withContext(Dispatchers.IO) {
        DebugLogger.i(TAG, "========== 切换回原输入法 ==========")

        val previous = previousImeId
        if (previous == null) {
            DebugLogger.w(TAG, "没有保存的原输入法", null)
            isZiZipActive = false
            return@withContext true // 没有原输入法也算成功
        }

        if (previous == ZiZipInputMethod.IME_ID) {
            DebugLogger.d(TAG, "原输入法就是 ZiZip，无需切换")
            isZiZipActive = false
            return@withContext true
        }

        // 切换回原输入法
        val result = switchToInputMethod(context, previous)
        if (result) {
            isZiZipActive = false
            DebugLogger.i(TAG, "✓ 已切换回原输入法: $previous")
        } else {
            DebugLogger.w(TAG, "切换回原输入法失败，但继续执行", null)
            // 即使失败也不阻塞流程
            isZiZipActive = false
        }

        result
    }

    /**
     * 输入文本（自动切换输入法）
     * @return 是否成功输入
     */
    suspend fun inputText(context: Context, text: String): Boolean = withContext(Dispatchers.IO) {
        DebugLogger.i(TAG, "========== AI 输入文本 ==========")
        DebugLogger.d(TAG, "内容: \"${text.take(50)}${if (text.length > 50) "..." else ""}\"")

        // 如果输入法未启用，使用广播方式
        if (!isEnabled(context)) {
            DebugLogger.w(TAG, "ZiZip 输入法未启用，尝试广播方式", null)
            val result = ZiZipInputMethod.inputTextViaBroadcast(context, text)
            if (result) {
                DebugLogger.i(TAG, "✓ 通过广播输入成功")
            } else {
                DebugLogger.e(TAG, "✗ 广播输入失败", null)
            }
            return@withContext result
        }

        // 切换到 ZiZip 输入法
        val switched = switchToZiZip(context)
        if (!switched) {
            DebugLogger.e(TAG, "✗ 无法切换到 ZiZip 输入法", null)
            return@withContext false
        }

        // 等待输入法切换完成
        kotlinx.coroutines.delay(300)

        // 输入文本
        val result = ZiZipInputMethod.inputTextDirect(text)
        if (result) {
            DebugLogger.i(TAG, "✓ 文本输入成功")
        } else {
            DebugLogger.e(TAG, "✗ 文本输入失败", null)
        }

        // 切换回原输入法
        switchBack(context)

        result
    }

    /**
     * 清空输入框（自动切换输入法）
     */
    suspend fun clearText(context: Context): Boolean = withContext(Dispatchers.IO) {
        DebugLogger.i(TAG, "========== 清空输入框 ==========")

        if (!isEnabled(context)) {
            DebugLogger.w(TAG, "ZiZip 输入法未启用，尝试广播方式", null)
            return@withContext ZiZipInputMethod.clearTextViaBroadcast(context)
        }

        val switched = switchToZiZip(context)
        if (!switched) return@withContext false

        kotlinx.coroutines.delay(300)

        // 通过广播清除
        val result = ZiZipInputMethod.clearTextViaBroadcast(context)

        switchBack(context)
        result
    }

    /**
     * 获取当前输入法 ID
     */
    private fun getCurrentInputMethodId(context: Context): String? {
        return try {
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.DEFAULT_INPUT_METHOD
            )
        } catch (e: Exception) {
            DebugLogger.e(TAG, "获取当前输入法失败", e)
            null
        }
    }

    /**
     * 切换到指定输入法
     */
    private suspend fun switchToInputMethod(context: Context, imeId: String): Boolean {
        return try {
            val cmd = "settings put secure default_input_method $imeId"
            val result = AndroidShellExecutor.executeCommand(cmd)
            DebugLogger.d(TAG, "切换输入法命令: $cmd, 结果: ${result.success}")
            result.success
        } catch (e: Exception) {
            DebugLogger.e(TAG, "切换输入法失败", e)
            false
        }
    }

    /**
     * 检查 ZiZip 是否为激活状态
     */
    fun isActive(): Boolean = isZiZipActive
}
