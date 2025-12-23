package com.autoglm.android.ui.screens.debuglog

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoglm.android.core.debug.DebugLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 调试日志 ViewModel
 */
class DebugLogViewModel : ViewModel() {

    private val _selectedLevel = MutableStateFlow<DebugLogger.Level?>(null)
    val selectedLevel: StateFlow<DebugLogger.Level?> = _selectedLevel.asStateFlow()

    /**
     * 获取日志流（带级别过滤）
     */
    val logs: StateFlow<List<DebugLogger.LogEntry>> = DebugLogger.logs

    /**
     * 设置筛选级别
     */
    fun setFilterLevel(level: DebugLogger.Level?) {
        _selectedLevel.value = level
    }

    /**
     * 清空日志
     */
    fun clearLogs() {
        DebugLogger.clear()
    }

    /**
     * 导出日志
     */
    fun exportLogs(context: Context): String {
        return DebugLogger.exportLogs(context)
    }

    /**
     * 获取错误日志
     */
    fun getErrorLogs(): List<DebugLogger.LogEntry> {
        return DebugLogger.getErrorLogs()
    }

    /**
     * 按 Tag 获取日志
     */
    fun getLogsByTag(tag: String): List<DebugLogger.LogEntry> {
        return DebugLogger.getLogsByTag(tag)
    }
}
