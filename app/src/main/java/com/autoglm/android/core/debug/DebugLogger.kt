package com.autoglm.android.core.debug

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * ZiZip 调试日志系统
 * 提供文件日志、内存日志和日志广播功能
 */
object DebugLogger {
    private const val TAG = "DebugLogger"
    private const val MAX_LOG_LINES = 500
    private const val LOG_FILE_PATH = "/sdcard/Download/ZiZip/zizip_debug.log"

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs

    private val logQueue = ConcurrentLinkedQueue<LogEntry>()

    // 日志级别
    enum class Level(val priority: Int, val label: String) {
        VERBOSE(0, "V"),
        DEBUG(1, "D"),
        INFO(2, "I"),
        WARNING(3, "W"),
        ERROR(4, "E")
    }

    // 日志条目
    data class LogEntry(
        val timestamp: Long,
        val level: Level,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null
    ) {
        override fun toString(): String {
            val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
            val throwableStr = if (throwable != null) ": ${throwable.message}" else ""
            return "$time ${level.label}/$tag: $message$throwableStr"
        }

        fun toDetailedString(): String {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
            val sb = StringBuilder()
            sb.appendLine("[$time] ${level.label}/$tag")
            sb.appendLine("  Message: $message")
            if (throwable != null) {
                sb.appendLine("  Exception: ${throwable.javaClass.simpleName}: ${throwable.message}")
                sb.append(android.util.Log.getStackTraceString(throwable).take(1000))
            }
            return sb.toString()
        }
    }

    /**
     * 记录日志
     */
    @Synchronized
    fun log(level: Level, tag: String, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            throwable = throwable
        )

        // 1. 输出到 Android Log
        when (level) {
            Level.VERBOSE -> Log.v(tag, message, throwable)
            Level.DEBUG -> Log.d(tag, message, throwable)
            Level.INFO -> Log.i(tag, message, throwable)
            Level.WARNING -> Log.w(tag, message, throwable)
            Level.ERROR -> Log.e(tag, message, throwable)
        }

        // 2. 添加到内存队列
        logQueue.offer(entry)
        if (logQueue.size > MAX_LOG_LINES) {
            logQueue.poll()
        }

        // 3. 更新 StateFlow
        _logs.value = logQueue.toList()

        // 4. 写入文件
        logToFile(entry)
    }

    /**
     * 便捷方法
     */
    fun v(tag: String, message: String, throwable: Throwable? = null) = log(Level.VERBOSE, tag, message, throwable)
    fun d(tag: String, message: String, throwable: Throwable? = null) = log(Level.DEBUG, tag, message, throwable)
    fun i(tag: String, message: String, throwable: Throwable? = null) = log(Level.INFO, tag, message, throwable)
    fun w(tag: String, message: String, throwable: Throwable? = null) = log(Level.WARNING, tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log(Level.ERROR, tag, message, throwable)

    /**
     * 写入文件日志
     */
    private fun logToFile(entry: LogEntry) {
        try {
            val logFile = File(LOG_FILE_PATH)
            logFile.parentFile?.mkdirs()

            FileWriter(logFile, true).use { writer ->
                writer.write(entry.toDetailedString())
                writer.write("\n")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file", e)
        }
    }

    /**
     * 清空日志
     */
    @Synchronized
    fun clear() {
        logQueue.clear()
        _logs.value = emptyList()

        // 清空文件
        try {
            File(LOG_FILE_PATH).delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear log file", e)
        }
    }

    /**
     * 获取日志文件内容
     */
    fun getLogFileContent(): String {
        return try {
            File(LOG_FILE_PATH).readText()
        } catch (e: Exception) {
            "无法读取日志文件: ${e.message}"
        }
    }

    /**
     * 导出日志
     */
    fun exportLogs(context: Context): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val exportPath = File(context.getExternalFilesDir(null), "logs/zizip_debug_$timestamp.txt").absolutePath

        try {
            File(exportPath).parentFile?.mkdirs()
            File(exportPath).writeText(getLogFileContent())
            return exportPath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export logs", e)
            return ""
        }
    }

    /**
     * 获取最近的日志（按级别过滤）
     */
    fun getRecentLogs(level: Level? = null): List<LogEntry> {
        return if (level == null) {
            _logs.value
        } else {
            _logs.value.filter { it.level == level }
        }.takeLast(100)
    }

    /**
     * 获取错误日志
     */
    fun getErrorLogs(): List<LogEntry> {
        return _logs.value.filter { it.level == Level.ERROR }
    }

    /**
     * 获取特定 Tag 的日志
     */
    fun getLogsByTag(tag: String): List<LogEntry> {
        return _logs.value.filter { it.tag.equals(tag, ignoreCase = true) }
    }
}

/**
 * 日志扩展函数
 */
fun debugLog(tag: String, message: String, level: DebugLogger.Level = DebugLogger.Level.DEBUG) {
    DebugLogger.log(level, tag, message)
}

fun debugLog(tag: String, message: String, throwable: Throwable) {
    DebugLogger.e(tag, message, throwable)
}
