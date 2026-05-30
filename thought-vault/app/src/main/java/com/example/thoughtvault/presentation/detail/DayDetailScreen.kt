package com.example.thoughtvault.presentation.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.thoughtvault.domain.model.Entry
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    viewModel: DayDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dayOfWeek = viewModel.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINESE)
    val dateDisplay = "${viewModel.date.format(DateTimeFormatter.ISO_LOCAL_DATE)} $dayOfWeek"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(dateDisplay) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.load() }) {
                            Text("重试")
                        }
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    Spacer(Modifier.height(8.dp))

                    // Tab 切换：原始记录 / AI 日报
                    var selectedTab by remember { mutableIntStateOf(if (state.hasDailyReport) 1 else 0) }

                    if (state.hasDailyReport) {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = MaterialTheme.colorScheme.surface,
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("原始记录 (${state.rawEntries.size})") }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("AI 日报") }
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    when (selectedTab) {
                        0 -> RawEntriesView(state.rawEntries)
                        1 -> DailyReportView(state.dailyReportContent ?: "日报尚未生成")
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun RawEntriesView(entries: List<Entry>) {
    if (entries.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Inbox,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.outlineVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text("当天暂无记录", color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            entries.forEach { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = Entry.formatTime(entry.time),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = entry.content,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyReportView(markdownContent: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
    ) {
        // 简易 Markdown 渲染（将 Markdown 文本显示为格式化文本）
        // TODO: 后续可替换为 compose-markdown 库做完整渲染
        Column(modifier = Modifier.padding(16.dp)) {
            val lines = markdownContent.lines()
            for (line in lines) {
                when {
                    line.startsWith("# ") -> {
                        Text(
                            text = line.removePrefix("# "),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    line.startsWith("## ") -> {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = line.removePrefix("## "),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    line.startsWith("### ") -> {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = line.removePrefix("### "),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    line.startsWith("**") -> {
                        Text(
                            text = line.trim('*'),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    line.startsWith("> ") -> {
                        Text(
                            text = line.removePrefix("> "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    line.startsWith("|") -> {
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    line.startsWith("- ") || line.startsWith("  - ") -> {
                        Row {
                            Text("  •  ", color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = line.trimStart('-', ' '),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    line.startsWith("#") -> {
                        Text(
                            text = line,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    line.isNotBlank() -> {
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    else -> Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}
