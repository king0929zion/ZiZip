package com.autoglm.android.core.agent

import android.util.Base64
import android.util.Log
import com.autoglm.android.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * AutoGLM API 客户端
 * 用于与智谱 AutoGLM Phone 模型通信
 * 参考文档: https://docs.bigmodel.cn/cn/guide/models/vlm/autoglm-phone
 */
class AutoGLMApiClient(
    private val settingsRepo: SettingsRepository
) {
    companion object {
        private const val TAG = "AutoGLMApiClient"
        private const val DEFAULT_BASE_URL = "https://open.bigmodel.cn/api/paas/v4"
        private const val DEFAULT_MODEL = "autoglm-phone"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    data class AIResponse(
        val thinking: String?,
        val action: String?,
        val message: String?,
        val success: Boolean = true
    )

    /**
     * 发送步骤请求
     */
    suspend fun sendStep(
        taskDescription: String,
        screenshotPath: String,
        stepNumber: Int,
        previousActions: List<String>
    ): AIResponse = withContext(Dispatchers.IO) {
        val apiKey = settingsRepo.getAutoGLMApiKey()
        val baseUrl = settingsRepo.getAutoGLMBaseUrl().ifBlank { DEFAULT_BASE_URL }
        val model = settingsRepo.getAutoGLMModelName().ifBlank { DEFAULT_MODEL }

        if (apiKey.isBlank()) {
            Log.e(TAG, "No API key configured")
            return@withContext AIResponse(
                thinking = null,
                action = null,
                message = "未配置 AutoGLM API Key",
                success = false
            )
        }

        try {
            // 读取并编码截图
            val screenshotBase64 = encodeImageToBase64(screenshotPath)
            if (screenshotBase64 == null) {
                return@withContext AIResponse(
                    thinking = null,
                    action = null,
                    message = "无法读取截图",
                    success = false
                )
            }

            // 构建请求体
            val requestBody = buildRequestBody(
                model = model,
                taskDescription = taskDescription,
                screenshotBase64 = screenshotBase64,
                stepNumber = stepNumber,
                previousActions = previousActions
            )

            val request = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            Log.d(TAG, "Sending request to $baseUrl/chat/completions")

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            Log.d(TAG, "Response code: ${response.code}")
            Log.d(TAG, "Response body: ${responseBody?.take(500)}")

            if (!response.isSuccessful) {
                return@withContext AIResponse(
                    thinking = null,
                    action = null,
                    message = "API 请求失败: ${response.code} - $responseBody",
                    success = false
                )
            }

            parseResponse(responseBody)
        } catch (e: Exception) {
            Log.e(TAG, "API request failed", e)
            AIResponse(
                thinking = null,
                action = null,
                message = "API 请求异常: ${e.message}",
                success = false
            )
        }
    }

    /**
     * 编码图片为 Base64
     */
    private fun encodeImageToBase64(imagePath: String): String? {
        return try {
            val file = File(imagePath)
            if (!file.exists()) return null
            val bytes = file.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encode image", e)
            null
        }
    }

    /**
     * 构建请求体
     */
    private fun buildRequestBody(
        model: String,
        taskDescription: String,
        screenshotBase64: String,
        stepNumber: Int,
        previousActions: List<String>
    ): JSONObject {
        val messages = JSONArray()

        // 系统消息
        val systemContent = """
            你是一个手机自动化助手。用户会给你一个任务和当前屏幕截图，你需要分析屏幕内容并决定下一步操作。
            
            可用的操作:
            - launch("包名") - 启动应用
            - tap(x, y) - 点击屏幕坐标
            - type("文本") - 输入文本
            - swipe(x1, y1, x2, y2, duration) - 滑动
            - back() - 返回
            - home() - 回到桌面
            - wait(毫秒) - 等待
            - finish("结果") - 任务完成
            - take_over() - 需要用户接管
            
            请按以下格式回复:
            <think>你的思考过程</think>
            <action>你要执行的操作</action>
        """.trimIndent()

        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", systemContent)
        })

        // 用户消息
        val userContent = JSONArray()
        
        // 添加任务描述
        userContent.put(JSONObject().apply {
            put("type", "text")
            put("text", buildString {
                append("任务: $taskDescription\n")
                append("当前步骤: $stepNumber\n")
                if (previousActions.isNotEmpty()) {
                    append("之前的操作: ${previousActions.takeLast(5).joinToString(", ")}\n")
                }
                append("\n请分析当前屏幕并决定下一步操作。")
            })
        })

        // 添加截图
        userContent.put(JSONObject().apply {
            put("type", "image_url")
            put("image_url", JSONObject().apply {
                put("url", "data:image/png;base64,$screenshotBase64")
            })
        })

        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", userContent)
        })

        return JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("max_tokens", 1024)
        }
    }

    /**
     * 解析响应
     */
    private fun parseResponse(responseBody: String?): AIResponse {
        if (responseBody == null) {
            return AIResponse(null, null, "空响应", false)
        }

        try {
            val json = JSONObject(responseBody)
            val choices = json.optJSONArray("choices")
            val firstChoice = choices?.optJSONObject(0)
            val message = firstChoice?.optJSONObject("message")
            val content = message?.optString("content") ?: ""

            // 解析 <think> 和 <action> 标签
            val thinkRegex = Regex("<think>(.*?)</think>", RegexOption.DOT_MATCHES_ALL)
            val actionRegex = Regex("<action>(.*?)</action>", RegexOption.DOT_MATCHES_ALL)

            val thinking = thinkRegex.find(content)?.groupValues?.getOrNull(1)?.trim()
            val action = actionRegex.find(content)?.groupValues?.getOrNull(1)?.trim()

            return AIResponse(
                thinking = thinking,
                action = action,
                message = content,
                success = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse response", e)
            return AIResponse(null, null, "解析响应失败: ${e.message}", false)
        }
    }
}
