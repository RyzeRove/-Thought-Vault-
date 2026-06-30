package com.example.thoughtvault.presentation.todo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.thoughtvault.domain.model.TodoItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    viewModel: TodoViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 添加对话框
    if (state.showAddDialog) {
        AlertDialog(
            onDismissRequest = viewModel::hideAddDialog,
            title = { Text("添加待办") },
            text = {
                Column {
                    OutlinedTextField(
                        value = state.addText,
                        onValueChange = viewModel::setAddText,
                        placeholder = { Text("要做什么？") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("类型：")
                        FilterChip(
                            selected = !state.addIsLong,
                            onClick = { viewModel.setAddLong(false) },
                            label = { Text("近期") },
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = state.addIsLong,
                            onClick = { viewModel.setAddLong(true) },
                            label = { Text("长期") },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (state.addText.isNotBlank()) {
                            viewModel.addTodo(state.addText.trim(), state.addIsLong)
                            viewModel.hideAddDialog()
                        }
                    },
                ) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::hideAddDialog) { Text("取消") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("待办事项") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::showAddDialog) {
                        Icon(Icons.Default.Add, contentDescription = "添加")
                    }
                    IconButton(onClick = viewModel::load) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val pending = state.items.filter { !it.isDone }
            val completed = state.items.filter { it.isDone }
            val dayTasks = pending.filter { !it.isLongTerm }
            val longTasks = pending.filter { it.isLongTerm }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 近期待办
                if (dayTasks.isNotEmpty()) {
                    item {
                        Text("近期待办", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    items(dayTasks, key = { "${it.content}_${it.date}" }) { t ->
                        TodoCard(t, onDone = { viewModel.markDone(t) }, onDelete = { viewModel.deleteTodo(t) })
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                // 长期计划
                if (longTasks.isNotEmpty()) {
                    item {
                        Text("长期计划", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.tertiary)
                    }
                    items(longTasks, key = { "${it.content}_${it.date}" }) { t ->
                        TodoCard(t, onDone = { viewModel.markDone(t) }, onDelete = { viewModel.deleteTodo(t) })
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                // 已完成
                if (completed.isNotEmpty()) {
                    item {
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        Text("已完成", fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.outline)
                    }
                    items(completed, key = { "${it.content}_${it.date}" }) { t ->
                        DoneCard(t)
                    }
                }

                // 空状态
                if (state.items.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Checklist, null, Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(Modifier.height(8.dp))
                                Text("暂无待办事项", color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun TodoCard(item: TodoItem, onDone: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isLongTerm)
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDone, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.CheckCircle, "完成",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(item.content, style = MaterialTheme.typography.bodyMedium)
                Text(item.date, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "删除", Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun DoneCard(item: TodoItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.width(12.dp))
            Text(item.content, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline)
        }
    }
}
