package com.autoglm.android.service.ime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView

/**
 * ZiZip 内置输入法
 * 用于通过 Shizuku 实现可靠的中文文本输入
 */
class ZiZipInputMethod : InputMethodService() {
    
    companion object {
        private const val TAG = "ZiZipInputMethod"
        
        // 广播 Actions
        const val ACTION_INPUT_TEXT = "com.autoglm.android.INPUT_TEXT"
        const val ACTION_INPUT_B64 = "com.autoglm.android.INPUT_B64"
        const val ACTION_CLEAR_TEXT = "com.autoglm.android.CLEAR_TEXT"
        
        // 广播 Extras
        const val EXTRA_TEXT = "text"
        const val EXTRA_MSG = "msg"
        
        // 输入法 ID
        const val IME_ID = "com.autoglm.android/.service.ime.ZiZipInputMethod"
        
        // 静态实例引用
        @Volatile
        private var instance: ZiZipInputMethod? = null
        
        fun getInstance(): ZiZipInputMethod? = instance
        
        /**
         * 直接输入文本（如果输入法实例可用）
         */
        fun inputTextDirect(text: String): Boolean {
            val ime = instance
            if (ime != null) {
                val ic = ime.currentInputConnection
                if (ic != null) {
                    ic.commitText(text, 1)
                    Log.d(TAG, "Direct input success: $text")
                    return true
                }
                Log.e(TAG, "No input connection for direct input")
            } else {
                Log.w(TAG, "InputMethod instance not available")
            }
            return false
        }
        
        /**
         * 检查输入法是否启用
         */
        fun isEnabled(context: Context): Boolean {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) 
                as android.view.inputmethod.InputMethodManager
            val enabledList = imm.enabledInputMethodList
            return enabledList.any { it.id == IME_ID }
        }
        
        /**
         * 检查输入法是否为当前输入法
         */
        fun isCurrentInputMethod(context: Context): Boolean {
            val settings = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.DEFAULT_INPUT_METHOD
            )
            return settings == IME_ID
        }

        /**
         * 切换到 ZiZip 输入法
         * 需要 WRITE_SECURE_SETTINGS 权限 (通过 Shizuku)
         */
        suspend fun switchToThisInputMethod(context: Context): Boolean {
            if (isCurrentInputMethod(context)) {
                Log.d(TAG, "ZiZip IME is already current")
                return true
            }

            if (!isEnabled(context)) {
                Log.e(TAG, "ZiZip IME is not enabled")
                return false
            }

            return try {
                // 使用 Shizuku 执行 settings 命令切换输入法
                com.autoglm.android.core.shizuku.AndroidShellExecutor.executeCommand(
                    "settings put secure default_input_method $IME_ID"
                ).success
            } catch (e: Exception) {
                Log.e(TAG, "Failed to switch IME: ${e.message}", e)
                false
            }
        }

        /**
         * 通过广播输入文本
         */
        fun inputTextViaBroadcast(context: Context, text: String): Boolean {
            return try {
                val intent = Intent(ACTION_INPUT_TEXT).apply {
                    putExtra(EXTRA_TEXT, text)
                    setPackage(context.packageName)
                }
                context.sendBroadcast(intent)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send broadcast: ${e.message}", e)
                false
            }
        }

        /**
         * 通过广播输入 Base64 编码的文本
         */
        fun inputTextViaBase64Broadcast(context: Context, text: String): Boolean {
            return try {
                val encoded = Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP)
                val intent = Intent(ACTION_INPUT_B64).apply {
                    putExtra(EXTRA_MSG, encoded)
                    setPackage(context.packageName)
                }
                context.sendBroadcast(intent)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send base64 broadcast: ${e.message}", e)
                false
            }
        }

        /**
         * 通过广播清除文本
         */
        fun clearTextViaBroadcast(context: Context): Boolean {
            return try {
                val intent = Intent(ACTION_CLEAR_TEXT).apply {
                    setPackage(context.packageName)
                }
                context.sendBroadcast(intent)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send clear broadcast: ${e.message}", e)
                false
            }
        }
    }
    
    private var statusView: TextView? = null
    
    private val inputReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_INPUT_TEXT -> {
                    val text = intent.getStringExtra(EXTRA_TEXT) ?: ""
                    Log.d(TAG, "Received INPUT_TEXT: $text")
                    commitText(text)
                }
                ACTION_INPUT_B64 -> {
                    val encodedText = intent.getStringExtra(EXTRA_MSG) ?: ""
                    Log.d(TAG, "Received INPUT_B64: $encodedText")
                    try {
                        val text = String(Base64.decode(encodedText, Base64.NO_WRAP), Charsets.UTF_8)
                        Log.d(TAG, "Decoded text: $text")
                        commitText(text)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to decode base64: ${e.message}")
                    }
                }
                ACTION_CLEAR_TEXT -> {
                    Log.d(TAG, "Received CLEAR_TEXT")
                    clearText()
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "ZiZipInputMethod created, instance set")
        registerInputReceiver()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "ZiZipInputMethod destroyed, instance cleared")
        unregisterInputReceiver()
    }
    
    private fun registerInputReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_INPUT_TEXT)
            addAction(ACTION_INPUT_B64)
            addAction(ACTION_CLEAR_TEXT)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(inputReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(inputReceiver, filter)
        }
        Log.d(TAG, "Broadcast receiver registered")
    }
    
    private fun unregisterInputReceiver() {
        try {
            unregisterReceiver(inputReceiver)
            Log.d(TAG, "Broadcast receiver unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister receiver: ${e.message}")
        }
    }
    
    override fun onCreateInputView(): View {
        // 创建一个简单的视图，显示 ZiZip 输入法状态
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 10, 20, 10)
            setBackgroundColor(0xFF8B7355.toInt()) // 使用 Accent 颜色
        }
        
        statusView = TextView(this).apply {
            text = "🤖 ZiZip 输入法已激活"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
        }
        
        layout.addView(statusView)
        return layout
    }
    
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Log.d(TAG, "onStartInputView - field: ${info?.fieldName}")
    }
    
    /**
     * 提交文本到当前输入框
     */
    private fun commitText(text: String) {
        val ic = currentInputConnection
        if (ic != null) {
            ic.commitText(text, 1)
            Log.d(TAG, "Text committed: $text")
            updateStatus("✓ 已输入: ${text.take(20)}${if (text.length > 20) "..." else ""}")
        } else {
            Log.e(TAG, "No input connection available")
            updateStatus("✗ 无法输入 - 无焦点")
        }
    }
    
    /**
     * 清除当前输入框的文本
     */
    private fun clearText() {
        val ic = currentInputConnection
        if (ic != null) {
            // 获取当前文本
            val beforeCursor = ic.getTextBeforeCursor(10000, 0) ?: ""
            val afterCursor = ic.getTextAfterCursor(10000, 0) ?: ""
            
            // 删除所有文本
            if (beforeCursor.isNotEmpty()) {
                ic.deleteSurroundingText(beforeCursor.length, 0)
            }
            if (afterCursor.isNotEmpty()) {
                ic.deleteSurroundingText(0, afterCursor.length)
            }
            
            Log.d(TAG, "Text cleared")
            updateStatus("✓ 已清除")
        } else {
            Log.e(TAG, "No input connection for clear")
        }
    }
    
    private fun updateStatus(status: String) {
        statusView?.post {
            statusView?.text = "🤖 ZiZip: $status"
        }
    }
}
