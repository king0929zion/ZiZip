package com.autoglm.android.domain.model

import kotlinx.coroutines.delay

/**
 * Mock 模型提供者
 * 用于 UI 开发和测试，模拟 AI 模型响应
 */
class MockModelProvider : ModelProvider {
    
    override val providerName: String = "Mock Provider"
    override val supportsAgentMode: Boolean = true
    
    private val greetings = listOf(
        "你好！我是 ZiZip，你的智能助手。有什么我可以帮助你的吗？",
        "嗨！很高兴见到你。今天想让我帮你做些什么？",
        "你好呀！我是你的 AI 助理，准备好帮你完成任务了！"
    )
    
    private val responses = listOf(
        "好的，我来帮你处理这个任务。",
        "明白了，让我看看该怎么做。",
        "收到！我正在分析你的请求...",
        "没问题，我会尽力帮助你完成这个任务。"
    )
    
    private val thinkingPhrases = listOf(
        "正在分析用户需求...",
        "理解任务目标...",
        "制定执行计划...",
        "准备执行操作..."
    )
    
    override suspend fun processQuery(query: String, screenContext: ScreenContext?): ModelResponse {
        // 模拟网络延迟
        delay(500 + (Math.random() * 1000).toLong())
        
        val lowerQuery = query.lowercase()
        
        return when {
            // 问候
            lowerQuery.contains("你好") || lowerQuery.contains("hello") || lowerQuery.contains("hi") -> {
                ModelResponse(
                    message = greetings.random(),
                    thinking = "用户在打招呼，回复问候语"
                )
            }
            
            // 打开应用
            lowerQuery.contains("打开") || lowerQuery.contains("启动") || lowerQuery.contains("open") -> {
                val appName = extractAppName(query)
                ModelResponse(
                    message = "好的，我来帮你打开${appName}。",
                    thinking = "用户想要打开应用：$appName，准备执行 launch 操作",
                    action = AgentAction(
                        actionType = ActionType.LAUNCH_APP,
                        app = appName
                    )
                )
            }
            
            // 点击操作
            lowerQuery.contains("点击") || lowerQuery.contains("click") || lowerQuery.contains("tap") -> {
                ModelResponse(
                    message = "正在执行点击操作...",
                    thinking = "用户需要点击某个元素，分析屏幕内容确定点击位置",
                    action = AgentAction(
                        actionType = ActionType.TAP,
                        element = listOf(540, 960)  // 模拟屏幕中心点
                    )
                )
            }
            
            // 输入文字
            lowerQuery.contains("输入") || lowerQuery.contains("type") || lowerQuery.contains("写") -> {
                val textToType = extractTextToType(query)
                ModelResponse(
                    message = "正在输入文字：$textToType",
                    thinking = "用户需要输入文字，准备执行 type 操作",
                    action = AgentAction(
                        actionType = ActionType.TYPE,
                        text = textToType
                    )
                )
            }
            
            // 返回
            lowerQuery.contains("返回") || lowerQuery.contains("back") -> {
                ModelResponse(
                    message = "正在返回上一页...",
                    thinking = "用户需要返回，执行 back 操作",
                    action = AgentAction(actionType = ActionType.BACK)
                )
            }
            
            // 滑动
            lowerQuery.contains("滑动") || lowerQuery.contains("swipe") || lowerQuery.contains("scroll") -> {
                ModelResponse(
                    message = "正在滑动屏幕...",
                    thinking = "用户需要滑动操作，执行 swipe",
                    action = AgentAction(
                        actionType = ActionType.SWIPE,
                        startPoint = listOf(540, 1500),
                        endPoint = listOf(540, 500)
                    )
                )
            }
            
            // 通用响应
            else -> {
                ModelResponse(
                    message = responses.random(),
                    thinking = thinkingPhrases.random()
                )
            }
        }
    }
    
    private fun extractAppName(query: String): String {
        val patterns = listOf("打开", "启动", "open", "launch")
        var result = query
        patterns.forEach { pattern ->
            result = result.replace(pattern, "", ignoreCase = true)
        }
        return result.trim().ifEmpty { "应用" }
    }
    
    private fun extractTextToType(query: String): String {
        val patterns = listOf("输入", "type", "写")
        var result = query
        patterns.forEach { pattern ->
            result = result.replace(pattern, "", ignoreCase = true)
        }
        return result.trim().ifEmpty { "测试文字" }
    }
}
