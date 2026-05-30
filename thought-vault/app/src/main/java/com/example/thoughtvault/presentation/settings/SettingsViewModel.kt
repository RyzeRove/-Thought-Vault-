package com.example.thoughtvault.presentation.settings

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
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val api: WebdavApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        val config = settingsDataStore.getConfig()
        if (config != null) {
            _uiState.update {
                it.copy(
                    baseUrl = config.baseUrl,
                    username = config.username,
                    password = config.password,
                    hasExistingConfig = true,
                )
            }
        }
    }

    fun onBaseUrlChange(url: String) {
        _uiState.update { it.copy(baseUrl = url, testResult = null) }
    }

    fun onUsernameChange(username: String) {
        _uiState.update { it.copy(username = username, testResult = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, testResult = null) }
    }

    fun onSave() {
        val state = _uiState.value
        val url = state.baseUrl.trim()
        val username = state.username.trim()
        val password = state.password

        if (url.isEmpty() || username.isEmpty() || password.isEmpty()) {
            _uiState.update { it.copy(testResult = TestResult.Error("请填写所有字段")) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                settingsDataStore.saveConfig(url, username, password)
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
        val url = state.baseUrl.trim()
        if (url.isEmpty() || state.username.isEmpty()) {
            _uiState.update { it.copy(testResult = TestResult.Error("请先填写地址和用户名")) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, testResult = null) }
            val result = api.testConnection(url, state.username.trim(), state.password)
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
}

data class SettingsUiState(
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val hasExistingConfig: Boolean = false,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val testResult: TestResult? = null,
)

sealed interface TestResult {
    data class Success(val message: String) : TestResult
    data class Error(val message: String) : TestResult
}
