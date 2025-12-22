package com.autoglm.android.domain.model

/**
 * 屏幕上下文 - 用于 AI 模型理解当前屏幕状态
 */
data class ScreenContext(
    val screenshot: ByteArray? = null,
    val nodeTree: String? = null,
    val currentPackage: String? = null,
    val currentActivity: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScreenContext

        if (screenshot != null) {
            if (other.screenshot == null) return false
            if (!screenshot.contentEquals(other.screenshot)) return false
        } else if (other.screenshot != null) return false
        if (nodeTree != other.nodeTree) return false
        if (currentPackage != other.currentPackage) return false
        if (currentActivity != other.currentActivity) return false

        return true
    }

    override fun hashCode(): Int {
        var result = screenshot?.contentHashCode() ?: 0
        result = 31 * result + (nodeTree?.hashCode() ?: 0)
        result = 31 * result + (currentPackage?.hashCode() ?: 0)
        result = 31 * result + (currentActivity?.hashCode() ?: 0)
        return result
    }
}

/**
 * Agent 动作类型
 */
enum class ActionType {
    TAP,
    SWIPE,
    SCROLL,
    TYPE,
    LAUNCH_APP,
    BACK,
    HOME,
    SCREENSHOT,
    WAIT,
    COMPLETE,
    UNKNOWN
}

/**
 * Agent 动作
 */
data class AgentAction(
    val actionType: ActionType,
    val actionName: String = actionType.name.lowercase(),
    val element: List<Int>? = null,  // [x, y] 坐标
    val text: String? = null,
    val app: String? = null,
    val duration: Long? = null,
    val startPoint: List<Int>? = null,
    val endPoint: List<Int>? = null
)

/**
 * 模型响应
 */
data class ModelResponse(
    val message: String,
    val thinking: String? = null,
    val action: AgentAction? = null,
    val isComplete: Boolean = false,
    val needsConfirmation: Boolean = false,
    val needsTakeover: Boolean = false
)

/**
 * 模型提供者接口
 */
interface ModelProvider {
    /**
     * 处理用户查询
     * @param query 用户输入
     * @param screenContext 当前屏幕上下文（可选）
     * @return 模型响应
     */
    suspend fun processQuery(query: String, screenContext: ScreenContext? = null): ModelResponse
    
    /**
     * 获取提供者名称
     */
    val providerName: String
    
    /**
     * 检查是否支持 Agent 模式
     */
    val supportsAgentMode: Boolean
}
