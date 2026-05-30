package com.example.thoughtvault.presentation.home

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.thoughtvault.domain.model.Entry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // 错误 Snackbar
    LaunchedEffect(state.saveState) {
        if (state.saveState is SaveState.Error) {
            snackbarHostState.showSnackbar(
                message = (state.saveState as SaveState.Error).message,
                duration = SnackbarDuration.Short,
            )
            viewModel.clearError()
        }
    }

    // 从设置页/历史页返回时刷新连接状态
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("思维札记", fontWeight = FontWeight.Bold)
                        Text(
                            text = when (state.connectionStatus) {
                                is ConnectionStatus.Connected -> "已连接"
                                is ConnectionStatus.NotConfigured -> "未配置 — 请前往设置"
                                is ConnectionStatus.Error -> "连接异常"
                                is ConnectionStatus.Unknown -> "检查中..."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when (state.connectionStatus) {
                                is ConnectionStatus.Connected -> MaterialTheme.colorScheme.primary
                                is ConnectionStatus.NotConfigured -> MaterialTheme.colorScheme.error
                                is ConnectionStatus.Error -> MaterialTheme.colorScheme.error
                                is ConnectionStatus.Unknown -> MaterialTheme.colorScheme.outline
                            }
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "历史")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // 输入区域
            OutlinedTextField(
                value = state.inputText,
                onValueChange = viewModel::onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 280.dp),
                placeholder = { Text("此刻在想什么？写下来吧...") },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 保存按钮
            Button(
                onClick = viewModel::onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = state.inputText.isNotBlank() && state.saveState != SaveState.Saving,
                shape = RoundedCornerShape(12.dp),
            ) {
                    when (state.saveState) {
                        is SaveState.Idle -> {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("保存", style = MaterialTheme.typography.titleMedium)
                        }
                        is SaveState.Saving -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("保存中...", style = MaterialTheme.typography.titleMedium)
                        }
                        is SaveState.Success -> {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("已保存 ✓", style = MaterialTheme.typography.titleMedium)
                        }
                        is SaveState.Error -> Text("重试", style = MaterialTheme.typography.titleMedium)
                    }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 今日已保存条目
            if (state.todayEntries.isNotEmpty()) {
                Text(
                    "今日已记录 ${state.todayEntries.size} 条",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = state.todayEntries.reversed(),
                    ) { entry ->
                        EntryCard(entry)
                    }
                }
            } else {
                // 空状态
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.EditNote,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "今天还没有记录\n开始写下第一条吧",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryCard(entry: Entry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 时间标识
            Text(
                text = Entry.formatTime(entry.time),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(40.dp),
            )
            // 内容预览（截断）
            Text(
                text = entry.content.take(80) + if (entry.content.length > 80) "..." else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                modifier = Modifier.weight(1f).wrapContentWidth(),
            )
        }
    }
}
