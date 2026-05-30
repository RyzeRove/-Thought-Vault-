package com.example.thoughtvault.presentation.history

import androidx.compose.foundation.clickable
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
import com.example.thoughtvault.domain.model.Entry
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToDayDetail: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("历史记录") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // 月份选择器
            MonthSelector(
                selectedMonth = state.selectedMonth,
                onMonthChange = viewModel::onMonthChange,
            )

            Spacer(Modifier.height(16.dp))

            // 有记录的日期列表
            if (state.daysWithEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.EventBusy,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${state.selectedMonth.monthValue}月暂无记录",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.daysWithEntries.sortedDescending()) { date ->
                        DayRow(
                            date = date,
                            isSelected = date == state.selectedDate,
                            onClick = {
                                viewModel.onDateSelect(date)
                                onNavigateToDayDetail(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthSelector(
    selectedMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onMonthChange(selectedMonth.minusMonths(1)) }) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "上月")
        }

        Text(
            text = "${selectedMonth.year}年 ${selectedMonth.monthValue}月",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        IconButton(onClick = { onMonthChange(selectedMonth.plusMonths(1)) }) {
            Icon(Icons.Default.ChevronRight, contentDescription = "下月")
        }
    }
}

@Composable
private fun DayRow(
    date: LocalDate,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINESE)
    val isToday = date == LocalDate.now()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 日期数字
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isToday) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface,
            ) {
                Text(
                    text = String.format("%02d", date.dayOfMonth),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isToday) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                )
            }

            Column {
                Text(
                    text = "${date.monthValue}月${date.dayOfMonth}日 $dayOfWeek",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                )
            }

            Spacer(Modifier.weight(1f))

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
