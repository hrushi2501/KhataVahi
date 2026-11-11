package com.farmeraccounts.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farmeraccounts.domain.model.DeletedLog
import com.farmeraccounts.domain.model.Expense
import com.farmeraccounts.domain.repository.DeletedLogRepository
import com.farmeraccounts.domain.repository.ExpenseRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryUiState(
    val expenses: List<Expense> = emptyList(),
    val deletedLogs: List<DeletedLog> = emptyList(),
    val isLoading: Boolean = true,
    val hasError: Boolean = false
)

class HistoryViewModel(
    private val expenses: ExpenseRepository,
    private val deletedLogs: DeletedLogRepository
) : ViewModel() {
    private val _state = MutableStateFlow(HistoryUiState())
    val state = _state.asStateFlow()
    private var loadJob: Job? = null

    fun load(deleted: Boolean) {
        loadJob?.cancel()
        _state.value = _state.value.copy(isLoading = true, hasError = false)
        loadJob = viewModelScope.launch {
            try {
                if (deleted) deletedLogs.getAllLogs().collect { _state.value = HistoryUiState(deletedLogs = it, isLoading = false) }
                else expenses.getAllActiveExpenses().collect { _state.value = HistoryUiState(expenses = it, isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = _state.value.copy(isLoading = false, hasError = true)
            }
        }
    }
}
