package com.example.thoughtvault.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.thoughtvault.data.local.NasConfig
import com.example.thoughtvault.data.local.SettingsDataStore
import com.example.thoughtvault.data.remote.ConnectionException
import com.example.thoughtvault.data.remote.WebdavApi
import com.example.thoughtvault.domain.model.Entry
import com.example.thoughtvault.domain.usecase.LoadDayEntriesUseCase
import com.example.thoughtvault.domain.usecase.SaveEntryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val saveEntryUseCase: SaveEntryUseCase,
    private val loadDayEntriesUseCase: LoadDayEntriesUseCase,
    private val settingsDataStore: SettingsDataStore,
    private val api: WebdavApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val config: NasConfig? get() = settingsDataStore.getConfig()

    init {
        if (config == null) {
            _uiState.update { it.copy(connectionStatus = ConnectionStatus.NotConfigured) }
        } else {
            loadTodayEntries()
            testConnection()
        }
    }

    fun onResume() {
        if (config != null && shouldRefresh()) {
            loadTodayEntriesFromNas()
            testConnection()
        }
    }

    private var lastRefreshTime: Long = 0

    /** 距离上次刷新超过 30 秒才重新请求，避免频繁切换页面时重复请求 */
    private fun shouldRefresh(): Boolean {
        val now = System.currentTimeMillis()
        val elapsed = now - lastRefreshTime
        if (elapsed < 30_000) return false
        lastRefreshTime = now
        return true
    }

    fun onTextChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun onSave() {
        val cfg = config
        if (cfg == null) {
            _uiState.update { it.copy(connectionStatus = ConnectionStatus.NotConfigured) }
            return
        }

        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(saveState = SaveState.Saving) }

            val result = saveEntryUseCase(
                baseUrl = cfg.baseUrl,
                username = cfg.username,
                password = cfg.password,
                content = text,
            )

            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(inputText = "", saveState = SaveState.Success)
                    }
                    loadTodayEntriesFromNas()
                    kotlinx.coroutines.delay(2000)
                    _uiState.update { it.copy(saveState = SaveState.Idle) }
                },
                onFailure = { error ->
                    Timber.w(error, "保存失败")
                    val message = when (error) {
                        is ConnectionException -> error.message ?: "连接失败"
                        else -> "保存失败：${error.message}"
                    }
                    _uiState.update { it.copy(saveState = SaveState.Error(message)) }
                }
            )
        }
    }

    fun testConnection() {
        val cfg = config ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingConnection = true) }
            val result = api.testConnection(cfg.baseUrl, cfg.username, cfg.password)
            _uiState.update {
                it.copy(
                    isTestingConnection = false,
                    connectionStatus = if (result.isSuccess) ConnectionStatus.Connected
                    else ConnectionStatus.Error(
                        (result.exceptionOrNull() as? ConnectionException)?.message ?: "连接失败"
                    )
                )
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(saveState = SaveState.Idle) }
    }

    private fun loadTodayEntries() {
        val today = LocalDate.now()
        viewModelScope.launch {
            loadDayEntriesUseCase(today).collect { entries ->
                _uiState.update { it.copy(todayEntries = entries) }
            }
        }
    }

    private fun loadTodayEntriesFromNas() {
        val cfg = config ?: return
        viewModelScope.launch {
            val result = loadDayEntriesUseCase.refresh(
                baseUrl = cfg.baseUrl,
                username = cfg.username,
                password = cfg.password,
                date = LocalDate.now(),
            )
            result.fold(
                onSuccess = { entries -> _uiState.update { it.copy(todayEntries = entries) } },
                onFailure = { Timber.w(it, "刷新今日条目失败") }
            )
        }
    }
}

data class HomeUiState(
    val inputText: String = "",
    val todayEntries: List<Entry> = emptyList(),
    val saveState: SaveState = SaveState.Idle,
    val connectionStatus: ConnectionStatus = ConnectionStatus.Unknown,
    val isTestingConnection: Boolean = false,
)

sealed interface SaveState {
    data object Idle : SaveState
    data object Saving : SaveState
    data object Success : SaveState
    data class Error(val message: String) : SaveState
}

sealed interface ConnectionStatus {
    data object Unknown : ConnectionStatus
    data object NotConfigured : ConnectionStatus
    data object Connected : ConnectionStatus
    data class Error(val message: String) : ConnectionStatus
}
