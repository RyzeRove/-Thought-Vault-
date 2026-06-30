package com.example.thoughtvault.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.thoughtvault.data.local.SettingsDataStore
import com.example.thoughtvault.data.repository.EntryRepository
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
    private val repo: EntryRepository,
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

            // 加载原始文件全文
            val rawResult = repo.fetchRawFile(config.baseUrl, config.username, config.password, date)
            rawResult.fold(
                onSuccess = { raw ->
                    _uiState.update { it.copy(editedRawContent = raw ?: "") }
                },
                onFailure = { }
            )

            // 加载 AI 日报
            try {
                val reportResult = api.getDailyReport(
                    config.baseUrl, config.username, config.password, date
                )
                reportResult.fold(
                    onSuccess = { report ->
                        _uiState.update {
                            it.copy(
                                dailyReportContent = report,
                                editedReport = report ?: "",
                                hasDailyReport = report != null,
                            )
                        }
                    },
                    onFailure = { }
                )
            } catch (_: Exception) { }
        }
    }

    // 编辑原始记录
    fun onRawContentChange(text: String) {
        _uiState.update { it.copy(editedRawContent = text, isRawEditing = true) }
    }

    fun saveRawContent() {
        val config = settingsDataStore.getConfig() ?: return
        val content = _uiState.value.editedRawContent
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            api.putRawFile(config.baseUrl, config.username, config.password, date, content)
                .fold(
                    onSuccess = {
                        _uiState.update { it.copy(isRawEditing = false, isSaving = false) }
                        load() // 重新加载以更新解析结果
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(isSaving = false, error = e.message) }
                    }
                )
        }
    }

    fun cancelRawEdit() {
        // 重新加载以还原
        _uiState.update { it.copy(isRawEditing = false) }
        load()
    }

    // 编辑日报
    fun onReportContentChange(text: String) {
        _uiState.update { it.copy(editedReport = text, isReportEditing = true) }
    }

    fun saveReport() {
        val config = settingsDataStore.getConfig() ?: return
        val content = _uiState.value.editedReport
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            api.putDailyReport(config.baseUrl, config.username, config.password, date, content)
                .fold(
                    onSuccess = {
                        _uiState.update {
                            it.copy(
                                isReportEditing = false, isSaving = false,
                                dailyReportContent = content,
                            )
                        }
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(isSaving = false, error = e.message) }
                    }
                )
        }
    }

    fun cancelReportEdit() {
        _uiState.update { it.copy(isReportEditing = false) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class DayDetailUiState(
    val rawEntries: List<Entry> = emptyList(),
    val dailyReportContent: String? = null,
    val hasDailyReport: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    // 编辑状态
    val isRawEditing: Boolean = false,
    val editedRawContent: String = "",
    val isReportEditing: Boolean = false,
    val editedReport: String = "",
)
