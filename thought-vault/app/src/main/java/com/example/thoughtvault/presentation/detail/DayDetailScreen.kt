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
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearError()
        }
    }

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
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                ) {
                    Spacer(Modifier.height(8.dp))

                    // Tab 切换
                    var selectedTab by remember { mutableIntStateOf(if (state.hasDailyReport) 1 else 0) }

                    if (state.hasDailyReport || state.editedRawContent.isNotEmpty()) {
                        TabRow(selectedTabIndex = selectedTab,
                            containerColor = MaterialTheme.colorScheme.surface,
                        ) {
                            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                                text = { Text("原始记录 (${state.rawEntries.size})") })
                            if (state.hasDailyReport) {
                                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                                    text = { Text("AI 日报") })
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    when (selectedTab) {
                        0 -> RawEntriesView(
                            entries = state.rawEntries,
                            isEditing = state.isRawEditing,
                            editedText = state.editedRawContent,
                            onEdit = { viewModel.onRawContentChange(state.editedRawContent.ifEmpty { buildRawMd(state.rawEntries) }) },
                            onTextChange = viewModel::onRawContentChange,
                            onSave = viewModel::saveRawContent,
                            onCancel = viewModel::cancelRawEdit,
                        )
                        1 -> DailyReportView(
                            markdownContent = state.dailyReportContent ?: "日报尚未生成",
                            isEditing = state.isReportEditing,
                            editedText = state.editedReport,
                            onEdit = { viewModel.onReportContentChange(state.editedReport) },
                            onTextChange = viewModel::onReportContentChange,
                            onSave = viewModel::saveReport,
                            onCancel = viewModel::cancelReportEdit,
                        )
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

private fun buildRawMd(entries: List<Entry>): String {
    val sb = StringBuilder()
    val date = entries.firstOrNull()?.let { e ->
        java.time.LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    } ?: ""
    sb.appendLine("# $date 原始记录")
    sb.appendLine()
    sb.appendLine("---")
    sb.appendLine()
    entries.forEach { entry ->
        sb.appendLine("## ${Entry.formatTime(entry.time)}")
        sb.appendLine()
        sb.appendLine(entry.content)
        sb.appendLine()
    }
    return sb.toString()
}

@Composable
private fun RawEntriesView(
    entries: List<Entry>,
    isEditing: Boolean,
    editedText: String,
    onEdit: () -> Unit,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column {
        // Edit toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            if (isEditing) {
                TextButton(onClick = onCancel) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onSave) { Text("保存") }
            } else {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "编辑", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (isEditing) {
            OutlinedTextField(
                value = editedText,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
        } else if (entries.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Inbox, null, Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("当天暂无记录", color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                entries.forEach { entry ->
                    Card(
                        Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(Entry.formatTime(entry.time),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(entry.content, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyReportView(
    markdownContent: String,
    isEditing: Boolean,
    editedText: String,
    onEdit: () -> Unit,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            if (isEditing) {
                TextButton(onClick = onCancel) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onSave) { Text("保存") }
            } else {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, "编辑", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (isEditing) {
            OutlinedTextField(
                value = editedText,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Card(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    for (line in markdownContent.lines()) {
                        when {
                            line.startsWith("# ") -> {
                                Text(line.removePrefix("# "),
                                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                            }
                            line.startsWith("## ") -> {
                                Spacer(Modifier.height(8.dp))
                                Text(line.removePrefix("## "),
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(4.dp))
                            }
                            line.startsWith("### ") -> {
                                Spacer(Modifier.height(4.dp))
                                Text(line.removePrefix("### "),
                                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                            }
                            line.startsWith("**") -> Text(line.trim('*'),
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            line.startsWith("> ") -> Text(line.removePrefix("> "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            line.startsWith("|") -> Text(line,
                                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            line.startsWith("- ") || line.startsWith("  - ") -> {
                                Row {
                                    Text("  •  ", color = MaterialTheme.colorScheme.primary)
                                    Text(line.trimStart('-', ' '), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            line.startsWith("#") -> Text(line,
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                            line.isNotBlank() -> Text(line, style = MaterialTheme.typography.bodyMedium)
                            else -> Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}
