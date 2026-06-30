package com.example.thoughtvault.presentation.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.thoughtvault.data.local.SettingsDataStore
import com.example.thoughtvault.data.remote.WebdavApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val api: WebdavApi,
    private val settings: SettingsDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SummaryUiState())
    val uiState: StateFlow<SummaryUiState> = _uiState.asStateFlow()

    init {
        loadFiles("weekly")
    }

    fun onPeriodChange(period: String) {
        loadFiles(period)
    }

    fun onFileSelect(file: String) {
        val state = _uiState.value
        val config = settings.getConfig() ?: return
        _uiState.update { it.copy(selectedFile = file, isLoadingContent = true, content = null) }

        viewModelScope.launch {
            val path = buildPath(state.selectedPeriod, file)
            val result = api.getSummaryFile(config.baseUrl, config.username, config.password, path)
            _uiState.update {
                it.copy(
                    isLoadingContent = false,
                    content = result.getOrNull() ?: null,
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun loadFiles(period: String) {
        val config = settings.getConfig() ?: return
        _uiState.update { it.copy(
            selectedPeriod = period,
            isLoadingFiles = true,
            files = emptyList(),
            selectedFile = null,
            content = null,
        )}

        viewModelScope.launch {
            val result = when (period) {
                "weekly" -> api.listWeeklyFiles(config.baseUrl, config.username, config.password)
                "monthly" -> api.listMonthlyFiles(config.baseUrl, config.username, config.password)
                "quarterly" -> api.listQuarterlyFiles(config.baseUrl, config.username, config.password)
                "yearly" -> api.listYearlyFiles(config.baseUrl, config.username, config.password)
                else -> Result.failure(IllegalArgumentException("Unknown period: $period"))
            }

            val files = parseFileList(result.getOrNull() ?: "")
            val first = files.firstOrNull()

            _uiState.update { it.copy(
                isLoadingFiles = false,
                files = files,
                selectedFile = first,
                error = result.exceptionOrNull()?.message,
            )}

            // 自动加载第一个文件
            if (first != null) {
                onFileSelect(first)
            }
        }
    }

    private fun buildPath(period: String, id: String): String = when (period) {
        "weekly" -> api.weeklyFilePath(id)
        "monthly" -> api.monthlyFilePath(id)
        "quarterly" -> api.quarterlyFilePath(id)
        "yearly" -> api.yearlyFilePath(id)
        else -> id
    }

    private fun parseFileList(xml: String): List<String> {
        val files = mutableListOf<String>()
        // Synology WebDAV 不返回 displayname，从 href 路径中提取文件名
        val regex = Regex("""<D:href>[^<]*/([^/<]+\.md)</D:href>""")
        regex.findAll(xml).forEach { match ->
            val name = match.groupValues[1].removeSuffix(".md")
            files.add(name)
        }
        return files.sortedDescending()
    }
}

data class SummaryUiState(
    val periods: List<String> = listOf("weekly", "monthly", "quarterly", "yearly"),
    val selectedPeriod: String = "weekly",
    val files: List<String> = emptyList(),
    val selectedFile: String? = null,
    val content: String? = null,
    val isLoadingFiles: Boolean = false,
    val isLoadingContent: Boolean = false,
    val error: String? = null,
)
