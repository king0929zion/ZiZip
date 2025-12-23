package com.autoglm.android.data.model

import org.json.JSONObject
import android.util.Log

/**
 * 动作类型枚举
 */
enum class ActionType {
    LAUNCH,
    TAP,
    TYPE,
    TYPE_NAME,
    SWIPE,
    BACK,
    HOME,
    DOUBLE_TAP,
    LONG_PRESS,
    WAIT,
    TAKE_OVER,
    NOTE,
    CALL_API,
    INTERACT,
    UNKNOWN;
    
    companion object {
        fun fromString(name: String): ActionType {
            return when (name.lowercase()) {
                "launch", "open_app" -> LAUNCH
                "tap", "click" -> TAP
                "type", "input" -> TYPE
                "type_name" -> TYPE_NAME
                "swipe", "scroll" -> SWIPE
                "back" -> BACK
                "home" -> HOME
                "double_tap", "doubletap" -> DOUBLE_TAP
                "long_press", "longpress" -> LONG_PRESS
                "wait" -> WAIT
                "take_over", "takeover" -> TAKE_OVER
                "note" -> NOTE
                "call_api", "callapi" -> CALL_API
                "interact" -> INTERACT
                else -> UNKNOWN
            }
        }
    }
}

/**
 * 动作数据类
 * 解析 AI 模型输出的动作指令
 */
data class ActionData(
    val actionName: String,
    val type: ActionType,
    val app: String? = null,
    val element: List<Int>? = null,
    val text: String? = null,
    val start: List<Int>? = null,
    val end: List<Int>? = null,
    val duration: String? = null,
    val message: String? = null,
    val isSensitive: Boolean = false,
    val metadata: String? = null,
    val isFinish: Boolean = false,
    val isDo: Boolean = true
) {
    companion object {
        private const val TAG = "ActionData"
        
        /**
         * 从原始动作字符串解析
         * 支持格式：
         * - do(action_name, param1=value1, param2=value2)
         * - finish(message)
         */
        fun parse(actionString: String): ActionData? {
            val trimmed = actionString.trim()
            Log.d(TAG, "Parsing action: $trimmed")
            
            // 检查是否是 finish 动作
            if (trimmed.startsWith("finish(")) {
                val endIndex = findMatchingParen(trimmed, 6)
                val content = if (endIndex > 7) {
                    trimmed.substring(7, endIndex).trim()
                } else {
                    trimmed.substringAfter("finish(").substringBefore(")").trim()
                }
                
                // 去除引号
                val message = content.trim('"', '\'')
                
                return ActionData(
                    actionName = "finish",
                    type = ActionType.UNKNOWN,
                    message = message,
                    isFinish = true,
                    isDo = false
                )
            }
            
            // 检查是否是 do 动作
            if (trimmed.startsWith("do(")) {
                val endIndex = findMatchingParen(trimmed, 2)
                val content = if (endIndex > 3) {
                    trimmed.substring(3, endIndex)
                } else {
                    trimmed.substringAfter("do(").substringBefore(")")
                }
                
                return parseDoAction(content)
            }
            
            // 尝试直接解析为动作名
            return parseDirectAction(trimmed)
        }
        
        private fun parseDoAction(content: String): ActionData? {
            // 解析参数
            val params = parseParams(content)
            
            val actionName = params["action"] ?: params.entries.firstOrNull()?.key ?: return null
            val type = ActionType.fromString(actionName)
            
            return ActionData(
                actionName = actionName,
                type = type,
                app = params["app"],
                element = parseCoordinates(params["element"]),
                text = params["text"] ?: params["content"],
                start = parseCoordinates(params["start"]),
                end = parseCoordinates(params["end"]),
                duration = params["duration"],
                message = params["message"],
                isSensitive = params["sensitive"]?.toBoolean() ?: false,
                metadata = content
            )
        }
        
        private fun parseDirectAction(action: String): ActionData? {
            // 尝试解析简单格式如 "tap(100, 200)"
            val match = Regex("(\\w+)\\((.*)\\)").find(action) ?: return null
            val actionName = match.groupValues[1]
            val argsStr = match.groupValues[2]
            
            val type = ActionType.fromString(actionName)
            
            // 根据动作类型解析参数
            return when (type) {
                ActionType.TAP, ActionType.DOUBLE_TAP, ActionType.LONG_PRESS -> {
                    val coords = argsStr.split(",").mapNotNull { it.trim().toIntOrNull() }
                    if (coords.size >= 2) {
                        ActionData(
                            actionName = actionName,
                            type = type,
                            element = coords.take(2)
                        )
                    } else null
                }
                ActionType.SWIPE -> {
                    val coords = argsStr.split(",").mapNotNull { it.trim().toIntOrNull() }
                    if (coords.size >= 4) {
                        ActionData(
                            actionName = actionName,
                            type = type,
                            start = listOf(coords[0], coords[1]),
                            end = listOf(coords[2], coords[3])
                        )
                    } else null
                }
                ActionType.TYPE, ActionType.TYPE_NAME -> {
                    val text = argsStr.trim().trim('"', '\'')
                    ActionData(
                        actionName = actionName,
                        type = type,
                        text = text
                    )
                }
                ActionType.LAUNCH -> {
                    val app = argsStr.trim().trim('"', '\'')
                    ActionData(
                        actionName = actionName,
                        type = type,
                        app = app
                    )
                }
                ActionType.WAIT -> {
                    ActionData(
                        actionName = actionName,
                        type = type,
                        duration = argsStr.trim()
                    )
                }
                ActionType.BACK, ActionType.HOME -> {
                    ActionData(
                        actionName = actionName,
                        type = type
                    )
                }
                else -> {
                    ActionData(
                        actionName = actionName,
                        type = type,
                        message = argsStr.trim().trim('"', '\''),
                        metadata = action
                    )
                }
            }
        }
        
        private fun parseParams(content: String): Map<String, String> {
            val params = mutableMapOf<String, String>()
            
            // 首先提取动作名（第一个逗号之前的部分）
            val firstComma = content.indexOf(',')
            if (firstComma == -1) {
                params["action"] = content.trim()
                return params
            }
            
            params["action"] = content.substring(0, firstComma).trim()
            val rest = content.substring(firstComma + 1)
            
            // 解析 key=value 对
            val keyValuePattern = Regex("(\\w+)\\s*=\\s*([^,]+|\"[^\"]*\"|'[^']*')")
            keyValuePattern.findAll(rest).forEach { match ->
                val key = match.groupValues[1]
                var value = match.groupValues[2].trim()
                // 去除引号
                if ((value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length - 1)
                }
                params[key] = value
            }
            
            return params
        }
        
        private fun parseCoordinates(str: String?): List<Int>? {
            if (str.isNullOrBlank()) return null
            
            // 支持格式：[100, 200] 或 (100, 200) 或 100,200
            val cleaned = str.trim().removePrefix("[").removeSuffix("]")
                .removePrefix("(").removeSuffix(")")
            
            val coords = cleaned.split(",").mapNotNull { it.trim().toIntOrNull() }
            return if (coords.size >= 2) coords else null
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
    }
}
