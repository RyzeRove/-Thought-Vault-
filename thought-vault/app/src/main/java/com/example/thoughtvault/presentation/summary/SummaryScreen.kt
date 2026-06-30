package com.example.thoughtvault.presentation.summary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    viewModel: SummaryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("汇总分析") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 周期选择器
            ScrollableTabRow(
                selectedTabIndex = state.periods.indexOf(state.selectedPeriod),
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 8.dp,
            ) {
                state.periods.forEachIndexed { index, period ->
                    Tab(
                        selected = state.selectedPeriod == period,
                        onClick = { viewModel.onPeriodChange(period) },
                        text = {
                            Text(
                                when (period) {
                                    "weekly" -> "周报"
                                    "monthly" -> "月报"
                                    "quarterly" -> "季报"
                                    "yearly" -> "年报"
                                    else -> period
                                }
                            )
                        },
                    )
                }
            }

            if (state.isLoadingFiles) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.files.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Description, null, Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("暂无数据", color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    // 左侧文件列表
                    LazyColumn(
                        modifier = Modifier
                            .width(120.dp)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(state.files) { file ->
                            val isSelected = file == state.selectedFile
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.onFileSelect(file) },
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else
                                    MaterialTheme.colorScheme.surface,
                            ) {
                                Text(
                                    text = file,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }

                    // 右侧内容区
                    if (state.isLoadingContent) {
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (state.content != null) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            val md = state.content ?: ""
                            val lines = md.split("\n")
                            for (line in lines) {
                                val trimmed = line.trim()
                                when {
                                    trimmed.startsWith("# ") && !trimmed.startsWith("## ") -> {
                                        Text(trimmed.removePrefix("# "),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(vertical = 4.dp))
                                    }
                                    trimmed.startsWith("## ") -> {
                                        Text(trimmed.removePrefix("## "),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(vertical = 4.dp))
                                    }
                                    trimmed.startsWith("### ") -> {
                                        Text(trimmed.removePrefix("### "),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(vertical = 2.dp))
                                    }
                                    trimmed.startsWith("> ") -> {
                                        Text(trimmed.removePrefix("> "),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.padding(vertical = 2.dp))
                                    }
                                    trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                                        Text("• ${trimmed.removePrefix("- ").removePrefix("* ")}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp))
                                    }
                                    trimmed.startsWith("|") -> {
                                        // 简化表格渲染
                                        Text(trimmed,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.padding(vertical = 1.dp))
                                    }
                                    trimmed.isEmpty() -> {
                                        Spacer(Modifier.height(8.dp))
                                    }
                                    trimmed.startsWith("---") -> {
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                    }
                                    else -> {
                                        Text(trimmed,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    } else {
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            Text("选择一份报告查看", color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }

            // 错误提示
            state.error?.let { error ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = viewModel::clearError) { Text("关闭") }
                    }
                ) {
                    Text(error)
                }
            }
        }
    }
}
