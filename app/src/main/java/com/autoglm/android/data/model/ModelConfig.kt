package com.autoglm.android.data.model

import java.util.UUID

/**
 * 模型提供商类型
 */
enum class ModelProviderType(val displayName: String, val iconName: String) {
    OPENAI("OpenAI", "openai"),
    OPENAI_COMPATIBLE("OpenAI 兼容", "openai"),
    GOOGLE("Google", "google"),
    ANTHROPIC("Anthropic", "anthropic"),
    ZHIPU("智谱 AI", "zhipu"),
    MOONSHOT("月之暗面", "moonshot"),
    DEEPSEEK("DeepSeek", "deepseek"),
    QWEN("通义千问", "qwen"),
    CUSTOM("自定义", "custom")
}

/**
 * 模型用途类型
 */
enum class ModelPurpose(val displayName: String, val description: String) {
    CHAT("对话模型", "用于日常对话和问答"),
    AGENT("Agent 模型", "用于自动化任务执行"),
    OCR("OCR 识图模型", "用于图片文字识别"),
    EMBEDDING("嵌入模型", "用于文本向量化")
}

/**
 * 模型能力标签
 */
enum class ModelCapability(val displayName: String) {
    VISION("视觉理解"),
    FUNCTION_CALLING("函数调用"),
    JSON_MODE("JSON 输出"),
    STREAMING("流式输出"),
    LONG_CONTEXT("长上下文")
}

/**
 * 模型配置数据类 - 增强版
 */
data class ModelConfig(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val modelName: String,
    val providerType: ModelProviderType = ModelProviderType.OPENAI_COMPATIBLE,
    val apiKey: String = "",
    val baseUrl: String = "",
    val enabled: Boolean = true,
    // 模型用途
    val purpose: ModelPurpose = ModelPurpose.CHAT,
    // 模型能力
    val capabilities: Set<ModelCapability> = emptySet(),
    // 高级配置
    val maxTokens: Int = 4096,
    val temperature: Float = 0.7f,
    val topP: Float = 1.0f,
    val frequencyPenalty: Float = 0.0f,
    val presencePenalty: Float = 0.0f,
    // 系统提示词
    val systemPrompt: String = "",
    // 请求超时（秒）
    val timeout: Int = 60,
    // 自定义请求头
    val customHeaders: Map<String, String> = emptyMap(),
    // 创建时间
    val createdAt: Long = System.currentTimeMillis(),
    // 最后使用时间
    val lastUsedAt: Long? = null
) {
    companion object {
        /**
         * AutoGLM Agent 模型预设
         */
        fun autoGLMAgent() = ModelConfig(
            id = "autoglm-phone",
            displayName = "AutoGLM Agent",
            modelName = "autoglm-phone",
            providerType = ModelProviderType.ZHIPU,
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            purpose = ModelPurpose.AGENT,
            capabilities = setOf(ModelCapability.VISION, ModelCapability.FUNCTION_CALLING),
            enabled = true
        )
        
        /**
         * GPT-4 Vision 预设
         */
        fun gpt4Vision() = ModelConfig(
            id = "gpt-4-vision",
            displayName = "GPT-4 Vision",
            modelName = "gpt-4-vision-preview",
            providerType = ModelProviderType.OPENAI,
            baseUrl = "https://api.openai.com/v1",
            purpose = ModelPurpose.CHAT,
            capabilities = setOf(ModelCapability.VISION, ModelCapability.FUNCTION_CALLING, ModelCapability.JSON_MODE),
            enabled = false
        )
        
        /**
         * Claude 3 预设
         */
        fun claude3() = ModelConfig(
            id = "claude-3",
            displayName = "Claude 3 Sonnet",
            modelName = "claude-3-sonnet-20240229",
            providerType = ModelProviderType.ANTHROPIC,
            baseUrl = "https://api.anthropic.com/v1",
            purpose = ModelPurpose.CHAT,
            capabilities = setOf(ModelCapability.VISION, ModelCapability.LONG_CONTEXT),
            enabled = false
        )
        
        /**
         * 获取预设模型列表
         */
        fun presets(): List<ModelConfig> = listOf(
            autoGLMAgent(),
            gpt4Vision(),
            claude3()
        )
    }
    
    /**
     * 是否支持视觉
     */
    val supportsVision: Boolean
        get() = capabilities.contains(ModelCapability.VISION)
    
    /**
     * 是否支持函数调用
     */
    val supportsFunctionCalling: Boolean
        get() = capabilities.contains(ModelCapability.FUNCTION_CALLING)
}

/**
 * 模型配置分组
 */
data class ModelGroup(
    val purpose: ModelPurpose,
    val models: List<ModelConfig>
)
