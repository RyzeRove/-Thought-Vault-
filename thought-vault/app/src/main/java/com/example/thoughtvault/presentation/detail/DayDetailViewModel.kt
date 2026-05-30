package com.example.thoughtvault.presentation.detail

import androidx.lifecycle.SavedStateHandle
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
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class DayDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val settingsDataStore: SettingsDataStore,
    private val loadDayEntriesUseCase: LoadDayEntriesUseCase,
    private val api: WebdavApi,
) : ViewModel() {

    private val dateStr: String = savedStateHandle["date"] ?: LocalDate.now().toString()
    val date: LocalDate = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)

    private val _uiState = MutableStateFlow(DayDetailUiState())
    val uiState: StateFlow<DayDetailUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        val config = settingsDataStore.getConfig() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // 加载原始条目
            val entriesResult = loadDayEntriesUseCase.refresh(
                baseUrl = config.baseUrl, username = config.username,
                password = config.password, date = date,
            )
            entriesResult.fold(
                onSuccess = { entries ->
                    _uiState.update { it.copy(rawEntries = entries, isLoading = false) }
                },
                onFailure = { error ->
                    Timber.w(error, "加载详情失败")
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
            )

            // 加载 AI 日报
            try {
                val reportResult = api.getDailyReport(
                    config.baseUrl, config.username, config.password, date
                )
                reportResult.fold(
                    onSuccess = { report ->
                        _uiState.update {
                            it.copy(dailyReportContent = report, hasDailyReport = report != null)
                        }
                    },
                    onFailure = { }
                )
            } catch (_: Exception) { }
        }
    }
}

data class DayDetailUiState(
    val rawEntries: List<Entry> = emptyList(),
    val dailyReportContent: String? = null,
    val hasDailyReport: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)
