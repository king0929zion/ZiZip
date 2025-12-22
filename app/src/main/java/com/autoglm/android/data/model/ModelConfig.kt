package com.autoglm.android.data.model

import java.util.UUID

/**
 * 模型提供商类型（参考 rikkahub）
 */
enum class ModelProviderType(
    val displayName: String,
    val defaultBaseUrl: String,
    val builtIn: Boolean = true
) {
    // 国际提供商
    OPENAI("OpenAI", "https://api.openai.com/v1"),
    GOOGLE("Google Gemini", "https://generativelanguage.googleapis.com/v1beta"),
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta"),  // 别名
    ANTHROPIC("Anthropic", "https://api.anthropic.com/v1"),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1"),
    GROQ("Groq", "https://api.groq.com/openai/v1"),
    XAI("xAI", "https://api.x.ai/v1"),
    
    // 国内提供商
    ZHIPU("智谱 AI", "https://open.bigmodel.cn/api/paas/v4"),
    SILICONFLOW("硅基流动", "https://api.siliconflow.cn/v1"),
    SILICON_FLOW("硅基流动", "https://api.siliconflow.cn/v1"),  // 别名
    MODELSCOPE("魔搭社区", "https://api-inference.modelscope.cn/v1"),
    MODEL_SCOPE("魔搭社区", "https://api-inference.modelscope.cn/v1"),  // 别名
    NVIDIA("英伟达 NIM", "https://integrate.api.nvidia.com/v1"),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1"),
    MOONSHOT("月之暗面", "https://api.moonshot.cn/v1"),
    QWEN("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
    STEPFUN("阶跃星辰", "https://api.stepfun.com/v1"),
    VOLCENGINE("火山引擎", "https://ark.cn-beijing.volces.com/api/v3"),
    HUNYUAN("腾讯混元", "https://api.hunyuan.cloud.tencent.com/v1"),
    
    // 自定义
    OPENAI_COMPATIBLE("OpenAI 兼容", "", builtIn = false),
    CUSTOM("自定义", "", builtIn = false)
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
 * 模型输入模态
 */
enum class Modality(val displayName: String) {
    TEXT("文本"),
    IMAGE("图片")
}

/**
 * 模型能力标签
 */
enum class ModelAbility(val displayName: String) {
    TOOL("工具调用"),
    REASONING("推理能力"),
    JSON_MODE("JSON 输出"),
    STREAMING("流式输出"),
    LONG_CONTEXT("长上下文")
}

/**
 * 模型类型
 */
enum class ModelType {
    CHAT,
    IMAGE,
    EMBEDDING
}

/**
 * 余额查询配置
 */
data class BalanceOption(
    val enabled: Boolean = false,
    val apiPath: String = "/credits",
    val resultPath: String = "data.total_usage"
)

/**
 * 模型配置数据类
 */
data class ModelConfig(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val modelName: String,
    val providerType: ModelProviderType = ModelProviderType.OPENAI_COMPATIBLE,
    val apiKey: String = "",
    val baseUrl: String = "",
    val enabled: Boolean = true,
    val purpose: ModelPurpose = ModelPurpose.CHAT,
    val type: ModelType = ModelType.CHAT,
    // 输入输出模态
    val inputModalities: Set<Modality> = setOf(Modality.TEXT),
    val outputModalities: Set<Modality> = setOf(Modality.TEXT),
    // 模型能力
    val abilities: Set<ModelAbility> = emptySet(),
    // 高级配置
    val maxTokens: Int = 4096,
    val temperature: Float = 0.7f,
    val topP: Float = 1.0f,
    val frequencyPenalty: Float = 0.0f,
    val presencePenalty: Float = 0.0f,
    val systemPrompt: String = "",
    val timeout: Int = 60,
    // 自定义请求头和请求体
    val customHeaders: Map<String, String> = emptyMap(),
    val customBody: Map<String, String> = emptyMap(),
    // 创建和使用时间
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null
) {
    val supportsVision: Boolean
        get() = inputModalities.contains(Modality.IMAGE)
    
    val supportsTool: Boolean
        get() = abilities.contains(ModelAbility.TOOL)
    
    val supportsReasoning: Boolean
        get() = abilities.contains(ModelAbility.REASONING)
}

/**
 * API 供应商配置
 */
data class ProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: ModelProviderType,
    val baseUrl: String,
    val apiKey: String = "",
    val enabled: Boolean = true,
    val builtIn: Boolean = false,
    val description: String = "",
    val models: List<ModelConfig> = emptyList(),
    val balanceOption: BalanceOption = BalanceOption(),
    // 代理配置
    val proxyEnabled: Boolean = false,
    val proxyHost: String = "",
    val proxyPort: Int = 8080
)

/**
 * 默认供应商预设
 */
object DefaultProviders {
    
    val OPENAI = ProviderConfig(
        id = "openai-default",
        name = "OpenAI",
        type = ModelProviderType.OPENAI,
        baseUrl = ModelProviderType.OPENAI.defaultBaseUrl,
        builtIn = true,
        description = "OpenAI 官方 API"
    )
    
    val GOOGLE = ProviderConfig(
        id = "google-default",
        name = "Google Gemini",
        type = ModelProviderType.GOOGLE,
        baseUrl = ModelProviderType.GOOGLE.defaultBaseUrl,
        builtIn = true,
        description = "Google Gemini 系列模型"
    )
    
    val SILICONFLOW = ProviderConfig(
        id = "siliconflow-default",
        name = "硅基流动",
        type = ModelProviderType.SILICONFLOW,
        baseUrl = ModelProviderType.SILICONFLOW.defaultBaseUrl,
        builtIn = true,
        description = "硅基流动 - 高性价比国产模型平台",
        balanceOption = BalanceOption(
            enabled = true,
            apiPath = "/user/info",
            resultPath = "data.totalBalance"
        ),
        models = listOf(
            ModelConfig(
                id = "siliconflow-qwen3-8b",
                displayName = "Qwen3-8B",
                modelName = "Qwen/Qwen3-8B",
                providerType = ModelProviderType.SILICONFLOW,
                inputModalities = setOf(Modality.TEXT),
                abilities = setOf(ModelAbility.TOOL, ModelAbility.REASONING)
            ),
            ModelConfig(
                id = "siliconflow-glm4v",
                displayName = "GLM-4.1V-9B",
                modelName = "THUDM/GLM-4.1V-9B-Thinking",
                providerType = ModelProviderType.SILICONFLOW,
                inputModalities = setOf(Modality.TEXT, Modality.IMAGE)
            )
        )
    )
    
    val MODELSCOPE = ProviderConfig(
        id = "modelscope-default",
        name = "魔搭社区",
        type = ModelProviderType.MODELSCOPE,
        baseUrl = ModelProviderType.MODELSCOPE.defaultBaseUrl,
        builtIn = true,
        description = "魔搭社区 - 阿里云开源模型平台"
    )
    
    val NVIDIA = ProviderConfig(
        id = "nvidia-default",
        name = "英伟达 NIM",
        type = ModelProviderType.NVIDIA,
        baseUrl = ModelProviderType.NVIDIA.defaultBaseUrl,
        builtIn = true,
        description = "NVIDIA NIM - 企业级推理服务"
    )
    
    val GROQ = ProviderConfig(
        id = "groq-default",
        name = "Groq",
        type = ModelProviderType.GROQ,
        baseUrl = ModelProviderType.GROQ.defaultBaseUrl,
        builtIn = true,
        description = "Groq - 超快速推理服务"
    )
    
    val OPENROUTER = ProviderConfig(
        id = "openrouter-default",
        name = "OpenRouter",
        type = ModelProviderType.OPENROUTER,
        baseUrl = ModelProviderType.OPENROUTER.defaultBaseUrl,
        builtIn = true,
        description = "OpenRouter - 多模型聚合平台",
        balanceOption = BalanceOption(
            enabled = true,
            apiPath = "/credits",
            resultPath = "data.total_credits - data.total_usage"
        )
    )
    
    val ZHIPU = ProviderConfig(
        id = "zhipu-default",
        name = "智谱 AI",
        type = ModelProviderType.ZHIPU,
        baseUrl = ModelProviderType.ZHIPU.defaultBaseUrl,
        builtIn = true,
        description = "智谱 AI - GLM 系列模型",
        models = listOf(
            ModelConfig(
                id = "zhipu-autoglm-phone",
                displayName = "AutoGLM Phone",
                modelName = "autoglm-phone",
                providerType = ModelProviderType.ZHIPU,
                purpose = ModelPurpose.AGENT,
                inputModalities = setOf(Modality.TEXT, Modality.IMAGE),
                abilities = setOf(ModelAbility.TOOL)
            )
        )
    )
    
    val DEEPSEEK = ProviderConfig(
        id = "deepseek-default",
        name = "DeepSeek",
        type = ModelProviderType.DEEPSEEK,
        baseUrl = ModelProviderType.DEEPSEEK.defaultBaseUrl,
        builtIn = true,
        description = "DeepSeek - 高性能开源模型",
        balanceOption = BalanceOption(
            enabled = true,
            apiPath = "/user/balance",
            resultPath = "balance_infos[0].total_balance"
        )
    )
    
    val MOONSHOT = ProviderConfig(
        id = "moonshot-default",
        name = "月之暗面",
        type = ModelProviderType.MOONSHOT,
        baseUrl = ModelProviderType.MOONSHOT.defaultBaseUrl,
        builtIn = true,
        description = "月之暗面 - Kimi 模型",
        balanceOption = BalanceOption(
            enabled = true,
            apiPath = "/users/me/balance",
            resultPath = "data.available_balance"
        )
    )
    
    /**
     * 获取所有默认供应商
     */
    fun all(): List<ProviderConfig> = listOf(
        OPENAI,
        GOOGLE,
        SILICONFLOW,
        MODELSCOPE,
        NVIDIA,
        GROQ,
        OPENROUTER,
        ZHIPU,
        DEEPSEEK,
        MOONSHOT
    )
}
