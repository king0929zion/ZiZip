package com.autoglm.android.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.autoglm.android.data.model.ChatMessage
import com.autoglm.android.data.model.ChatSession
import com.autoglm.android.data.model.MessageSender
import com.autoglm.android.data.model.MessageStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 对话历史存储库
 */
class HistoryRepository private constructor(private val context: Context) {
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()
    
    init {
        loadSessions()
    }
    
    private fun loadSessions() {
        val json = prefs.getString(KEY_SESSIONS, null)
        _sessions.value = if (json != null) {
            parseSessionsFromJson(json)
        } else {
            emptyList()
        }
    }
    
    // ================== 会话管理 ==================
    
    fun getSession(sessionId: String): ChatSession? {
        return _sessions.value.find { it.id == sessionId }
    }
    
    fun createSession(title: String = "新对话", modelId: String? = null): ChatSession {
        val session = ChatSession(title = title, modelId = modelId)
        val newList = listOf(session) + _sessions.value
        saveSessions(newList)
        _sessions.value = newList
        return session
    }
    
    fun saveSession(session: ChatSession) {
        val newList = _sessions.value.map {
            if (it.id == session.id) session else it
        }.let { list ->
            if (list.none { it.id == session.id }) listOf(session) + list else list
        }
        saveSessions(newList)
        _sessions.value = newList
    }
    
    fun deleteSession(sessionId: String) {
        val newList = _sessions.value.filter { it.id != sessionId }
        saveSessions(newList)
        _sessions.value = newList
    }
    
    fun addMessageToSession(sessionId: String, message: ChatMessage) {
        val session = getSession(sessionId) ?: return
        val updatedSession = session.copy(
            messages = session.messages + message,
            lastUpdatedAt = System.currentTimeMillis()
        )
        saveSession(updatedSession)
    }
    
    // ================== JSON 序列化 ==================
    
    private fun saveSessions(sessions: List<ChatSession>) {
        val jsonArray = JSONArray()
        sessions.forEach { session ->
            jsonArray.put(sessionToJson(session))
        }
        prefs.edit().putString(KEY_SESSIONS, jsonArray.toString()).apply()
    }
    
    private fun sessionToJson(session: ChatSession): JSONObject {
        val messagesArray = JSONArray()
        session.messages.forEach { msg ->
            messagesArray.put(messageToJson(msg))
        }
        
        return JSONObject().apply {
            put("id", session.id)
            put("title", session.title)
            put("modelId", session.modelId)
            put("createdAt", session.createdAt)
            put("lastUpdatedAt", session.lastUpdatedAt)
            put("messages", messagesArray)
        }
    }
    
    private fun messageToJson(message: ChatMessage): JSONObject {
        return JSONObject().apply {
            put("id", message.id)
            put("content", message.content)
            put("sender", message.sender.name)
            put("timestamp", message.timestamp)
            put("status", message.status.name)
            put("thinking", message.thinking)
            put("actionType", message.actionType)
            put("isSuccess", message.isSuccess)
        }
    }
    
    private fun parseSessionsFromJson(json: String): List<ChatSession> {
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                val messagesArray = obj.optJSONArray("messages") ?: JSONArray()
                val messages = (0 until messagesArray.length()).map { j ->
                    parseMessage(messagesArray.getJSONObject(j))
                }
                
                ChatSession(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    modelId = obj.optString("modelId", null),
                    createdAt = obj.getLong("createdAt"),
                    lastUpdatedAt = obj.getLong("lastUpdatedAt"),
                    messages = messages
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun parseMessage(obj: JSONObject): ChatMessage {
        return ChatMessage(
            id = obj.getString("id"),
            content = obj.getString("content"),
            sender = try {
                MessageSender.valueOf(obj.getString("sender"))
            } catch (e: Exception) {
                MessageSender.USER
            },
            timestamp = obj.getLong("timestamp"),
            status = try {
                MessageStatus.valueOf(obj.optString("status", "SENT"))
            } catch (e: Exception) {
                MessageStatus.SENT
            },
            thinking = obj.optString("thinking", null),
            actionType = obj.optString("actionType", null),
            isSuccess = obj.optBoolean("isSuccess", true)
        )
    }
    
    companion object {
        private const val PREFS_NAME = "zizip_history"
        private const val KEY_SESSIONS = "chat_sessions"
        
        @Volatile
        private var instance: HistoryRepository? = null
        
        fun getInstance(context: Context): HistoryRepository {
            return instance ?: synchronized(this) {
                instance ?: HistoryRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
