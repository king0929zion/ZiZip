package com.autoglm.android.ui.screens.debuglog

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.autoglm.android.core.debug.DebugLogger
import kotlinx.coroutines.launch

/**
 * 调试日志屏幕
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogScreen(
    navController: NavController,
    viewModel: DebugLogViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val selectedLevel by viewModel.selectedLevel.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("调试日志") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 级别筛选按钮
                    var showFilterMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "筛选")
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            DebugLogger.Level.entries.forEach { level ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                level.label,
                                                color = levelToColor(level),
                                                fontWeight = if (level == selectedLevel) {
                                                    FontWeight.Bold
                                                } else {
                                                    FontWeight.Normal
                                                }
                                            )
                                            if (level == selectedLevel) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("✓")
                                            }
                                        }
                                    },
                                    onClick = {
                                        viewModel.setFilterLevel(if (level == selectedLevel) null else level)
                                        showFilterMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // 清空日志按钮
                    IconButton(onClick = {
                        viewModel.clearLogs()
                        Toast.makeText(context, "日志已清空", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "清空")
                    }

                    // 导出日志按钮
                    IconButton(onClick = {
                        scope.launch {
                            val path = viewModel.exportLogs(context)
                            if (path.isNotEmpty()) {
                                Toast.makeText(
                                    context,
                                    "日志已导出到: $path",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "导出失败",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "导出")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 统计信息栏
            LogStatsBar(logs = logs, selectedLevel = selectedLevel)

            // 日志列表
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "暂无日志",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val listState = rememberLazyListState()

                LaunchedEffect(logs.size) {
                    // 自动滚动到底部
                    listState.animateScrollToItem(logs.size)
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(logs) { entry ->
                        LogEntryItem(entry = entry)
                    }
                }
            }
        }
    }
}

/**
 * 日志统计栏
 */
@Composable
fun LogStatsBar(logs: List<DebugLogger.LogEntry>, selectedLevel: DebugLogger.Level?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val totalCount = logs.size
        val errorCount = logs.count { it.level == DebugLogger.Level.ERROR }
        val warningCount = logs.count { it.level == DebugLogger.Level.WARNING }
        val infoCount = logs.count { it.level == DebugLogger.Level.INFO }

        Text(
            "总计: $totalCount",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "E: $errorCount",
            style = MaterialTheme.typography.bodySmall,
            color = levelToColor(DebugLogger.Level.ERROR)
        )
        Text(
            "W: $warningCount",
            style = MaterialTheme.typography.bodySmall,
            color = levelToColor(DebugLogger.Level.WARNING)
        )
        Text(
            "I: $infoCount",
            style = MaterialTheme.typography.bodySmall,
            color = levelToColor(DebugLogger.Level.INFO)
        )
        if (selectedLevel != null) {
            Text(
                "筛选: ${selectedLevel.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 单个日志条目
 */
@Composable
fun LogEntryItem(entry: DebugLogger.LogEntry) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (entry.level) {
                DebugLogger.Level.ERROR -> Color.Red.copy(alpha = 0.1f)
                DebugLogger.Level.WARNING -> Color(0xFFFFA500).copy(alpha = 0.1f)
                DebugLogger.Level.INFO -> Color.Blue.copy(alpha = 0.05f)
                else -> Color.Transparent
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 级别标签
                Surface(
                    color = levelToColor(entry.level).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        entry.level.label,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = levelToColor(entry.level),
                        fontWeight = FontWeight.Bold
                    )
                }

                // 时间戳
                Text(
                    formatTimestamp(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Tag 和消息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "[${entry.tag}]",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    entry.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            // 异常信息（展开显示）
            if (entry.throwable != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${entry.throwable?.javaClass?.simpleName}: ${entry.throwable?.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Red,
                    fontFamily = FontFamily.Monospace
                )
                if (expanded) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        android.util.Log.getStackTraceString(entry.throwable).take(500),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Red.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * 日志级别对应的颜色
 */
fun levelToColor(level: DebugLogger.Level): Color {
    return when (level) {
        DebugLogger.Level.VERBOSE -> Color.Gray
        DebugLogger.Level.DEBUG -> Color(0xFF607D8B)
        DebugLogger.Level.INFO -> Color(0xFF2196F3)
        DebugLogger.Level.WARNING -> Color(0xFFFFA500)
        DebugLogger.Level.ERROR -> Color.Red
    }
}

/**
 * 格式化时间戳
 */
fun formatTimestamp(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val format = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
    return format.format(date)
}
