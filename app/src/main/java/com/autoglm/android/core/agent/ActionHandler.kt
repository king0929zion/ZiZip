package com.autoglm.android.core.agent

import android.util.Log
import com.autoglm.android.core.shizuku.AndroidShellExecutor
import com.autoglm.android.data.model.ActionData
import com.autoglm.android.data.model.ActionType

/**
 * 动作执行结果
 */
data class ActionResult(
    val success: Boolean,
    val shouldFinish: Boolean,
    val message: String? = null,
    val requiresConfirmation: Boolean = false
)

/**
 * 动作处理器
 * 负责将 AI 模型输出的动作转换为实际的设备操作
 * 参考 Auto-GLM-Android 的 action_handler.dart
 */
class ActionHandler(
    private val isCancelled: () -> Boolean = { false },
    private val onConfirmation: suspend (String) -> Boolean = { true },
    private val onTakeover: suspend (String) -> Unit = {}
) {
    companion object {
        private const val TAG = "ActionHandler"
        
        // 常见应用包名映射
        private val APP_PACKAGES = mapOf(
            "微信" to "com.tencent.mm",
            "wechat" to "com.tencent.mm",
            "WeChat" to "com.tencent.mm",
            "支付宝" to "com.eg.android.AlipayGphone",
            "alipay" to "com.eg.android.AlipayGphone",
            "淘宝" to "com.taobao.taobao",
            "taobao" to "com.taobao.taobao",
            "抖音" to "com.ss.android.ugc.aweme",
            "tiktok" to "com.ss.android.ugc.aweme",
            "bilibili" to "tv.danmaku.bili",
            "B站" to "tv.danmaku.bili",
            "哔哩哔哩" to "tv.danmaku.bili",
            "高德地图" to "com.autonavi.minimap",
            "amap" to "com.autonavi.minimap",
            "百度地图" to "com.baidu.BaiduMap",
            "美团" to "com.sankuai.meituan",
            "meituan" to "com.sankuai.meituan",
            "饿了么" to "me.ele",
            "eleme" to "me.ele",
            "京东" to "com.jingdong.app.mall",
            "jd" to "com.jingdong.app.mall",
            "拼多多" to "com.xunmeng.pinduoduo",
            "pinduoduo" to "com.xunmeng.pinduoduo",
            "网易云音乐" to "com.netease.cloudmusic",
            "QQ音乐" to "com.tencent.qqmusic",
            "QQ" to "com.tencent.mobileqq",
            "小红书" to "com.xingin.xhs",
            "xiaohongshu" to "com.xingin.xhs",
            "设置" to "com.android.settings",
            "settings" to "com.android.settings",
            "相机" to "com.android.camera",
            "camera" to "com.android.camera",
            "浏览器" to "com.android.browser",
            "browser" to "com.android.browser",
            "chrome" to "com.android.chrome",
            "Chrome" to "com.android.chrome"
        )
        
        fun getPackageName(appName: String): String? {
            return APP_PACKAGES[appName] ?: APP_PACKAGES[appName.lowercase()]
        }
    }

    /**
     * 执行动作
     */
    suspend fun execute(action: ActionData, displayId: Int? = null): ActionResult {
        Log.d(TAG, "Executing action: ${action.actionName}")
        
        // 处理结束动作
        if (action.isFinish) {
            return ActionResult(
                success = true,
                shouldFinish = true,
                message = action.message
            )
        }
        
        // 不是 do 动作
        if (!action.isDo) {
            return ActionResult(
                success = false,
                shouldFinish = true,
                message = "Unknown action type: ${action.metadata}"
            )
        }
        
        // 根据动作类型执行
        return try {
            when (action.type) {
                ActionType.LAUNCH -> handleLaunch(action)
                ActionType.TAP -> handleTap(action)
                ActionType.TYPE, ActionType.TYPE_NAME -> handleType(action)
                ActionType.SWIPE -> handleSwipe(action)
                ActionType.BACK -> handleBack()
                ActionType.HOME -> handleHome()
                ActionType.DOUBLE_TAP -> handleDoubleTap(action)
                ActionType.LONG_PRESS -> handleLongPress(action)
                ActionType.WAIT -> handleWait(action)
                ActionType.TAKE_OVER -> handleTakeover(action)
                ActionType.NOTE -> handleNote(action)
                ActionType.CALL_API -> handleCallApi(action)
                ActionType.INTERACT -> handleInteract(action)
                else -> ActionResult(
                    success = false,
                    shouldFinish = false,
                    message = "Unknown action: ${action.actionName}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Action failed: ${e.message}", e)
            ActionResult(
                success = false,
                shouldFinish = false,
                message = "Action failed: ${e.message}"
            )
        }
    }

    private suspend fun handleLaunch(action: ActionData): ActionResult {
        val appName = action.app
        if (appName.isNullOrBlank()) {
            return ActionResult(
                success = false,
                shouldFinish = false,
                message = "No app name specified"
            )
        }
        
        val packageName = getPackageName(appName)
        if (packageName == null) {
            return ActionResult(
                success = false,
                shouldFinish = false,
                message = "App not found: $appName"
            )
        }
        
        val success = AndroidShellExecutor.launchApp(packageName)
        return ActionResult(success = success, shouldFinish = false)
    }

    private suspend fun handleTap(action: ActionData): ActionResult {
        val element = action.element
        if (element == null || element.size < 2) {
            return ActionResult(
                success = false,
                shouldFinish = false,
                message = "No element coordinates"
            )
        }
        
        // 检查敏感操作
        if (action.isSensitive && !action.message.isNullOrBlank()) {
            val confirmed = onConfirmation(action.message!!)
            if (!confirmed) {
                return ActionResult(
                    success = false,
                    shouldFinish = true,
                    message = "User cancelled sensitive operation"
                )
            }
        }
        
        val success = AndroidShellExecutor.tap(element[0], element[1])
        return ActionResult(success = success, shouldFinish = false)
    }

    private suspend fun handleType(action: ActionData): ActionResult {
        val text = action.text ?: ""
        if (text.isEmpty()) {
            return ActionResult(success = true, shouldFinish = false)
        }
        
        // 等待确保输入框获取焦点
        kotlinx.coroutines.delay(500)
        
        val success = AndroidShellExecutor.inputText(text)
        
        // 输入后等待文本稳定
        kotlinx.coroutines.delay(300)
        
        return ActionResult(
            success = success,
            shouldFinish = false,
            message = if (!success) "Failed to type text: $text" else null
        )
    }

    private suspend fun handleSwipe(action: ActionData): ActionResult {
        val start = action.start
        val end = action.end
        
        if (start == null || start.size < 2 || end == null || end.size < 2) {
            return ActionResult(
                success = false,
                shouldFinish = false,
                message = "Missing swipe coordinates"
            )
        }
        
        val success = AndroidShellExecutor.swipe(start[0], start[1], end[0], end[1])
        return ActionResult(success = success, shouldFinish = false)
    }

    private suspend fun handleBack(): ActionResult {
        val success = AndroidShellExecutor.pressBack()
        return ActionResult(success = success, shouldFinish = false)
    }

    private suspend fun handleHome(): ActionResult {
        val success = AndroidShellExecutor.pressHome()
        return ActionResult(success = success, shouldFinish = false)
    }

    private suspend fun handleDoubleTap(action: ActionData): ActionResult {
        val element = action.element
        if (element == null || element.size < 2) {
            return ActionResult(
                success = false,
                shouldFinish = false,
                message = "No element coordinates"
            )
        }
        
        // 双击实现：两次快速点击
        AndroidShellExecutor.tap(element[0], element[1])
        kotlinx.coroutines.delay(100)
        val success = AndroidShellExecutor.tap(element[0], element[1])
        return ActionResult(success = success, shouldFinish = false)
    }

    private suspend fun handleLongPress(action: ActionData): ActionResult {
        val element = action.element
        if (element == null || element.size < 2) {
            return ActionResult(
                success = false,
                shouldFinish = false,
                message = "No element coordinates"
            )
        }
        
        // 长按实现：swipe 原地不动 1 秒
        val success = AndroidShellExecutor.swipe(
            element[0], element[1], 
            element[0], element[1], 
            1000
        )
        return ActionResult(success = success, shouldFinish = false)
    }

    private suspend fun handleWait(action: ActionData): ActionResult {
        val durationStr = action.duration ?: "1 seconds"
        val regex = Regex("(\\d+)")
        val match = regex.find(durationStr)
        val seconds = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        
        val totalMs = seconds * 1000
        val tickMs = 100
        var elapsed = 0
        
        while (elapsed < totalMs) {
            if (isCancelled()) {
                return ActionResult(
                    success = false,
                    shouldFinish = true,
                    message = "任务已停止"
                )
            }
            kotlinx.coroutines.delay(tickMs.toLong())
            elapsed += tickMs
        }
        
        return ActionResult(success = true, shouldFinish = false)
    }

    private suspend fun handleTakeover(action: ActionData): ActionResult {
        val message = action.message ?: "请完成当前操作后继续"
        
        if (isCancelled()) {
            return ActionResult(success = false, shouldFinish = true, message = "任务已停止")
        }
        
        // 调用接管回调
        onTakeover(message)
        
        // 等待用户操作完成
        val totalMs = 30000
        val tickMs = 200
        var elapsed = 0
        
        while (elapsed < totalMs) {
            if (isCancelled()) {
                return ActionResult(success = false, shouldFinish = true, message = "任务已停止")
            }
            kotlinx.coroutines.delay(tickMs.toLong())
            elapsed += tickMs
        }
        
        return ActionResult(success = true, shouldFinish = false)
    }

    private fun handleNote(action: ActionData): ActionResult {
        // 记录页面内容
        return ActionResult(success = true, shouldFinish = false)
    }

    private fun handleCallApi(action: ActionData): ActionResult {
        // 调用 API
        return ActionResult(success = true, shouldFinish = false)
    }

    private fun handleInteract(action: ActionData): ActionResult {
        return ActionResult(
            success = true,
            shouldFinish = false,
            message = "User interaction required"
        )
    }
}
