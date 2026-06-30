package com.example.thoughtvault.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.example.thoughtvault.data.local.SettingsDataStore
import com.example.thoughtvault.data.remote.WebdavApi
import com.example.thoughtvault.notification.ReminderScheduler
import com.example.thoughtvault.push.PushConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsDataStore: SettingsDataStore,
    private val api: WebdavApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var pollingJob: kotlinx.coroutines.Job? = null

    init {
        val config = settingsDataStore.getConfig()
        if (config != null) {
            _uiState.update {
                it.copy(
                    username = config.username,
                    password = config.password,
                    hasExistingConfig = true,
                )
            }
        }
        val reminderEnabled = settingsDataStore.isReminderEnabled()
        _uiState.update { it.copy(reminderEnabled = reminderEnabled) }
        // 定时拉推送状态，onResume 时也会刷新
        startPushStatusPolling()
    }

    private fun startPushStatusPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                _uiState.update { it.copy(pushConnected = PushConnectionManager.instance.isConnected) }
                delay(5_000)
            }
        }
    }

    fun onResume() {
        _uiState.update { it.copy(pushConnected = PushConnectionManager.instance.isConnected) }
        startPushStatusPolling()
    }

    fun onPause() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun onUsernameChange(username: String) {
        _uiState.update { it.copy(username = username, testResult = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, testResult = null) }
    }

    fun onSave() {
        val state = _uiState.value
        val username = state.username.trim()
        val password = state.password

        if (username.isEmpty() || password.isEmpty()) {
            _uiState.update { it.copy(testResult = TestResult.Error("请填写用户名和密码")) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                settingsDataStore.saveConfig(username, password)
                _uiState.update {
                    it.copy(hasExistingConfig = true, isSaving = false, testResult = TestResult.Success("配置已保存"))
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, testResult = TestResult.Error("保存失败：${e.message}"))
                }
            }
        }
    }

    fun onTestConnection() {
        val state = _uiState.value
        val username = state.username.trim()
        if (username.isEmpty()) {
            _uiState.update { it.copy(testResult = TestResult.Error("请先填写用户名")) }
            return
        }

        val baseUrl = settingsDataStore.buildBaseUrl(username)
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, testResult = null) }
            val result = api.testConnection(baseUrl, username, state.password)
            _uiState.update {
                it.copy(
                    isTesting = false,
                    testResult = if (result.isSuccess) TestResult.Success("连接成功！")
                    else TestResult.Error("连接失败：${result.exceptionOrNull()?.message ?: "未知错误"}")
                )
            }
        }
    }

    fun onClearConfig() {
        settingsDataStore.clearConfig()
        _uiState.update { SettingsUiState() }
    }

    fun onReminderToggle(enabled: Boolean) {
        _uiState.update { it.copy(reminderEnabled = enabled) }
        settingsDataStore.setReminderEnabled(enabled)
        if (enabled) {
            ReminderScheduler.schedule(appContext)
        } else {
            ReminderScheduler.cancel(appContext)
        }
    }
}

data class SettingsUiState(
    val username: String = "",
    val password: String = "",
    val hasExistingConfig: Boolean = false,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val testResult: TestResult? = null,
    val reminderEnabled: Boolean = true,
    val pushConnected: Boolean = false,
)

sealed interface TestResult {
    data class Success(val message: String) : TestResult
    data class Error(val message: String) : TestResult
}
