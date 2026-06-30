package com.example.thoughtvault.presentation.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.thoughtvault.data.local.SettingsDataStore
import com.example.thoughtvault.data.repository.TodoRepository
import com.example.thoughtvault.domain.model.TodoItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TodoViewModel @Inject constructor(
    private val repo: TodoRepository,
    private val settings: SettingsDataStore,
) : ViewModel() {
    private val _state = MutableStateFlow(TodoState())
    val state: StateFlow<TodoState> = _state.asStateFlow()

    init { load() }

    fun load() {
        val cfg = settings.getConfig() ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            repo.load(cfg.baseUrl, cfg.username, cfg.password).fold(
                onSuccess = { items ->
                    _state.update { it.copy(items = items, isLoading = false) }
                },
                onFailure = { _state.update { it.copy(isLoading = false) } }
            )
        }
    }

    fun markDone(item: TodoItem) {
        val cfg = settings.getConfig() ?: return
        val newItems = _state.value.items.map {
            if (it.content == item.content && it.date == item.date)
                it.copy(isDone = true)
            else it
        }
        _state.update { it.copy(items = newItems) }
        viewModelScope.launch {
            repo.save(cfg.baseUrl, cfg.username, cfg.password, newItems)
        }
    }

    fun addTodo(content: String, isLong: Boolean) {
        val cfg = settings.getConfig() ?: return
        val today = java.time.LocalDate.now().toString()
        val newItems = _state.value.items + TodoItem(
            content = content, date = today, isLongTerm = isLong
        )
        _state.update { it.copy(items = newItems) }
        viewModelScope.launch {
            repo.save(cfg.baseUrl, cfg.username, cfg.password, newItems)
        }
    }

    fun deleteTodo(item: TodoItem) {
        val cfg = settings.getConfig() ?: return
        val newItems = _state.value.items.filter {
            !(it.content == item.content && it.date == item.date)
        }
        _state.update { it.copy(items = newItems) }
        viewModelScope.launch {
            repo.save(cfg.baseUrl, cfg.username, cfg.password, newItems)
        }
    }

    fun showAddDialog() { _state.update { it.copy(showAddDialog = true) } }
    fun hideAddDialog() { _state.update { it.copy(showAddDialog = false) } }
    fun setAddText(t: String) { _state.update { it.copy(addText = t) } }
    fun setAddLong(l: Boolean) { _state.update { it.copy(addIsLong = l) } }
}

data class TodoState(
    val items: List<TodoItem> = emptyList(),
    val isLoading: Boolean = false,
    val showAddDialog: Boolean = false,
    val addText: String = "",
    val addIsLong: Boolean = false,
)
