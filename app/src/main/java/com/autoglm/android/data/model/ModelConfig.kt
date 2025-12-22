package com.autoglm.android.data.model

import java.util.UUID

/**
 * 模型提供商类型
 */
enum class ModelProviderType {
    OPENAI,
    GOOGLE,
    ANTHROPIC,
    ZHIPU,
    CUSTOM
}

/**
 * 模型配置数据类
 */
data class ModelConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val displayName: String = name,
    val provider: ModelProviderType = ModelProviderType.CUSTOM,
    val apiKey: String = "",
    val baseUrl: String = "",
    val modelName: String = "",
    val enabled: Boolean = true,
    val isDefault: Boolean = false,
    val extraParams: Map<String, Any> = emptyMap()
) {
    companion object {
        /**
         * AutoGLM 模型预设配置
         */
        fun autoGLMDefault() = ModelConfig(
            id = "autoglm-phone",
            name = "AutoGLM Phone",
            displayName = "AutoGLM",
            provider = ModelProviderType.ZHIPU,
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            modelName = "autoglm-phone",
            enabled = true
        )
    }
}
