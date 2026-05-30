package com.example.thoughtvault.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.thoughtvault.data.local.SettingsDataStore
import com.example.thoughtvault.data.remote.WebdavApi
import com.example.thoughtvault.domain.model.Entry
import com.example.thoughtvault.domain.usecase.LoadDayEntriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val loadDayEntriesUseCase: LoadDayEntriesUseCase,
    private val webdavApi: WebdavApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    fun onMonthChange(yearMonth: YearMonth) {
        _uiState.update { it.copy(selectedMonth = yearMonth, daysWithEntries = emptyList()) }
        loadMonthDays(yearMonth)
    }

    fun onDateSelect(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date, isLoadingEntries = true) }
        loadEntriesForDate(date)
    }

    private fun loadMonthDays(yearMonth: YearMonth) {
        val config = settingsDataStore.getConfig() ?: return

        viewModelScope.launch {
            try {
                val result = webdavApi.listMonthDays(
                    baseUrl = config.baseUrl,
                    username = config.username,
                    password = config.password,
                    year = yearMonth.year,
                    month = yearMonth.monthValue,
                )

                result.fold(
                    onSuccess = { xml ->
                        // 简单解析 PROPFIND 返回的 href，提取日期
                        val dates = parsePropfindDates(xml, yearMonth)
                        _uiState.update { it.copy(daysWithEntries = dates) }
                    },
                    onFailure = { Timber.w(it, "加载月份数据失败") }
                )
            } catch (e: Exception) {
                Timber.w(e, "加载月份异常")
            }
        }
    }

    private fun loadEntriesForDate(date: LocalDate) {
        val config = settingsDataStore.getConfig() ?: return

        viewModelScope.launch {
            val result = loadDayEntriesUseCase.refresh(
                baseUrl = config.baseUrl,
                username = config.username,
                password = config.password,
                date = date,
            )
            result.fold(
                onSuccess = { entries ->
                    _uiState.update {
                        it.copy(
                            selectedDateEntries = entries,
                            isLoadingEntries = false,
                            // 同时获取日报
                            isDailyReportAvailable = false,
                        )
                    }
                    // 检查是否有日报
                    checkDailyReport(config.baseUrl, config.username, config.password, date)
                },
                onFailure = {
                    _uiState.update { it.copy(isLoadingEntries = false) }
                }
            )
        }
    }

    private fun checkDailyReport(baseUrl: String, username: String, password: String, date: LocalDate) {
        viewModelScope.launch {
            val result = webdavApi.getDailyReport(baseUrl, username, password, date)
            result.fold(
                onSuccess = { report ->
                    _uiState.update {
                        it.copy(
                            dailyReportContent = report,
                            isDailyReportAvailable = report != null,
                        )
                    }
                },
                onFailure = { }
            )
        }
    }

    /** 简单解析 PROPFIND XML，提取文件名中的日期 */
    private fun parsePropfindDates(xml: String, yearMonth: YearMonth): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        val regex = Regex("""(\d{4}-\d{2}-\d{2})\.md""")
        regex.findAll(xml).forEach { match ->
            try {
                val date = LocalDate.parse(match.groupValues[1], DateTimeFormatter.ISO_LOCAL_DATE)
                if (date.year == yearMonth.year && date.monthValue == yearMonth.monthValue) {
                    dates.add(date)
                }
            } catch (_: Exception) { }
        }
        return dates.distinct().sorted()
    }
}

data class HistoryUiState(
    val selectedMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate? = null,
    val daysWithEntries: List<LocalDate> = emptyList(),
    val selectedDateEntries: List<Entry> = emptyList(),
    val dailyReportContent: String? = null,
    val isDailyReportAvailable: Boolean = false,
    val isLoadingEntries: Boolean = false,
)
