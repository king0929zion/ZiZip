package com.autoglm.android.core.api

import android.util.Base64
import android.util.Log
import com.autoglm.android.data.model.ModelConfig
import com.autoglm.android.data.model.ModelProviderType
import com.autoglm.android.data.repository.ModelConfigRepository
import com.autoglm.android.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 模型响应数据类
 */
data class ModelResponse(
    val thinking: String = "",
    val action: String = "",
    val content: String = "",
    val rawContent: String = ""
)

/**
 * OpenAI 兼容的模型客户端
 * 参考 Auto-GLM-Android 的 model_client.dart
 */
class ChatClient(
    private val modelRepo: ModelConfigRepository,
    private val settingsRepo: SettingsRepository
) {
    companion object {
        private const val TAG = "ChatClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var currentCall: Call? = null

    /**
     * 发送消息到模型
     */
    suspend fun sendMessage(
        messages: List<Message>,
        model: ModelConfig? = null,
        temperature: Float = 0.7f,
        maxTokens: Int? = null
    ): ModelResponse = withContext(Dispatchers.IO) {
        val activeModel = model ?: modelRepo.getActiveModel()
            ?: throw ChatClientException("未选择任何模型，请先配置模型")

        val provider = modelRepo.providers.value.find { it.type == activeModel.providerType }
        val apiKey = activeModel.apiKey.ifBlank { provider?.apiKey ?: "" }
        
        if (apiKey.isBlank()) {
            throw ChatClientException("未配置 API Key，请在设置中配置")
        }

        val baseUrl = activeModel.baseUrl.ifBlank { 
            provider?.baseUrl ?: activeModel.providerType.defaultBaseUrl 
        }

        val requestUrl = buildChatCompletionsUrl(baseUrl)
        Log.d(TAG, "Request URL: $requestUrl")

        val requestBody = buildRequestBody(
            modelName = activeModel.modelName,
            messages = messages,
            temperature = temperature,
            maxTokens = maxTokens
        )

        val request = Request.Builder()
            .url(requestUrl)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            val call = client.newCall(request)
            currentCall = call
            
            val response = call.execute()
            currentCall = null

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                throw ChatClientException(parseErrorMessage(response.code, errorBody))
            }

            val responseBody = response.body?.string() 
                ?: throw ChatClientException("响应为空")

            parseResponse(responseBody)
        } catch (e: IOException) {
            throw ChatClientException("网络连接失败: ${e.message}")
        }
    }

    /**
     * 取消当前请求
     */
    fun cancelRequest() {
        currentCall?.cancel()
        currentCall = null
    }

    private fun buildChatCompletionsUrl(baseUrl: String): String {
        val normalized = baseUrl.trimEnd('/')
        return if (normalized.endsWith("/v1") || normalized.contains("/v1/")) {
            "$normalized/chat/completions"
        } else if (normalized.endsWith("/chat/completions")) {
            normalized
        } else {
            "$normalized/v1/chat/completions"
        }
    }

    private fun buildRequestBody(
        modelName: String,
        messages: List<Message>,
        temperature: Float,
        maxTokens: Int?
    ): String {
        val json = JSONObject()
        json.put("model", modelName)
        json.put("temperature", temperature)
        json.put("stream", false)
        
        if (maxTokens != null) {
            json.put("max_tokens", maxTokens)
        }

        val messagesArray = JSONArray()
        messages.forEach { message ->
            val msgJson = JSONObject()
            msgJson.put("role", message.role)
            
            if (message.imageBase64 != null) {
                // 带图片的消息
                val contentArray = JSONArray()
                
                val imageObj = JSONObject()
                imageObj.put("type", "image_url")
                val imageUrlObj = JSONObject()
                imageUrlObj.put("url", "data:image/png;base64,${message.imageBase64}")
                imageObj.put("image_url", imageUrlObj)
                contentArray.put(imageObj)
                
                val textObj = JSONObject()
                textObj.put("type", "text")
                textObj.put("text", message.content)
                contentArray.put(textObj)
                
                msgJson.put("content", contentArray)
            } else {
                msgJson.put("content", message.content)
            }
            
            messagesArray.put(msgJson)
        }
        
        json.put("messages", messagesArray)
        return json.toString()
    }

    private fun parseResponse(responseBody: String): ModelResponse {
        try {
            val json = JSONObject(responseBody)
            
            // 检查错误
            if (json.has("error")) {
                val error = json.optJSONObject("error")
                val message = error?.optString("message") ?: json.optString("error")
                throw ChatClientException("API 错误: $message")
            }
            
            val choices = json.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                throw ChatClientException("响应格式错误: 没有 choices")
            }
            
            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.optJSONObject("message")
                ?: throw ChatClientException("响应格式错误: 没有 message")
            
            val content = message.optString("content", "")
            
            // 解析思考和动作
            val (thinking, action) = parseThinkingAndAction(content)
            
            return ModelResponse(
                thinking = thinking,
                action = action,
                content = if (action.isBlank()) content else "",
                rawContent = content
            )
        } catch (e: Exception) {
            if (e is ChatClientException) throw e
            throw ChatClientException("解析响应失败: ${e.message}")
        }
    }

    private fun parseThinkingAndAction(content: String): Pair<String, String> {
        var thinking = ""
        var action = ""
        
        // 优先使用 <answer> 标签
        if (content.contains("<answer>")) {
            val parts = content.split("<answer>")
            thinking = parts[0]
                .replace("<think>", "")
                .replace("</think>", "")
                .trim()
            action = parts.getOrNull(1)
                ?.replace("</answer>", "")
                ?.trim() ?: ""
        } else {
            // 尝试匹配 do(...) 或 finish(...)
            val doIndex = content.indexOf("do(")
            val finishIndex = content.indexOf("finish(")
            
            when {
                doIndex != -1 -> {
                    val closeIndex = findMatchingParen(content, doIndex + 2)
                    if (closeIndex != -1) {
                        action = content.substring(doIndex, closeIndex + 1)
                        thinking = content.substring(0, doIndex).trim()
                    } else {
                        action = content.substring(doIndex).trim()
                        thinking = content.substring(0, doIndex).trim()
                    }
                }
                finishIndex != -1 -> {
                    val closeIndex = findMatchingParen(content, finishIndex + 6)
                    if (closeIndex != -1) {
                        action = content.substring(finishIndex, closeIndex + 1)
                        thinking = content.substring(0, finishIndex).trim()
                    } else {
                        action = content.substring(finishIndex).trim()
                        thinking = content.substring(0, finishIndex).trim()
                    }
                }
                else -> {
                    // 无法识别，返回整个内容
                    action = ""
                }
            }
        }
        
        return Pair(thinking, action)
    }

    private fun findMatchingParen(s: String, openIndex: Int): Int {
        var depth = 0
        var inDoubleQuote = false
        var inSingleQuote = false
        
        for (i in openIndex until s.length) {
            val c = s[i]
            
            when {
                c == '"' && !inSingleQuote -> inDoubleQuote = !inDoubleQuote
                c == '\'' && !inDoubleQuote -> inSingleQuote = !inSingleQuote
                !inDoubleQuote && !inSingleQuote -> {
                    when (c) {
                        '(' -> depth++
                        ')' -> {
                            if (depth == 0) return i
                            depth--
                        }
                    }
                }
            }
        }
        return -1
    }

    private fun parseErrorMessage(code: Int, body: String): String {
        return when (code) {
            401 -> "API Key 无效，请检查配置"
            403 -> "API 访问被拒绝，请检查权限"
            429 -> "请求频率过高，请稍后重试"
            500 -> "服务器错误，请稍后重试"
            404 -> "API 路径不存在，请检查 Base URL 是否包含 /v1"
            else -> {
                try {
                    val json = JSONObject(body)
                    val error = json.optJSONObject("error")
                    error?.optString("message") ?: "请求失败 ($code)"
                } catch (e: Exception) {
                    "请求失败 ($code): $body"
                }
            }
        }
    }
}

/**
 * 消息数据类
 */
data class Message(
    val role: String,  // "system", "user", "assistant"
    val content: String,
    val imageBase64: String? = null
) {
    companion object {
        fun system(content: String) = Message("system", content)
        fun user(content: String, imageBase64: String? = null) = Message("user", content, imageBase64)
        fun assistant(content: String) = Message("assistant", content)
        
        /**
         * 从文件创建带图片的用户消息
         */
        suspend fun userWithImage(content: String, imageFile: File): Message = 
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val bytes = imageFile.readBytes()
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                user(content, base64)
            }
    }
}

/**
 * 客户端异常
 */
class ChatClientException(message: String) : Exception(message)
