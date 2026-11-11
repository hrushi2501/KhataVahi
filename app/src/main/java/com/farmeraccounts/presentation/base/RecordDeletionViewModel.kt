package com.farmeraccounts.presentation.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farmeraccounts.domain.usecase.CorrectEntryUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RecordKind { CUSTOMER, WORK, PAYMENT, EXPENSE }

data class RecordDeletionState(
    val isDeleting: Boolean = false,
    val deletedKind: RecordKind? = null,
    val deletedId: String? = null,
    val hasError: Boolean = false
)

class RecordDeletionViewModel(private val corrections: CorrectEntryUseCase) : ViewModel() {
    private val _state = MutableStateFlow(RecordDeletionState())
    val state = _state.asStateFlow()

    fun prepare() {
        if (!_state.value.isDeleting) _state.value = RecordDeletionState()
    }

    fun remove(kind: RecordKind, id: String, reason: String) {
        if (_state.value.isDeleting || _state.value.deletedId != null || id.isBlank() || reason.isBlank()) return
        _state.value = RecordDeletionState(isDeleting = true)
        viewModelScope.launch {
            try {
                when (kind) {
                    RecordKind.CUSTOMER -> corrections.softDeleteCustomer(id, reason.trim())
                    RecordKind.WORK -> corrections.softDeleteWorkEntry(id, reason.trim())
                    RecordKind.PAYMENT -> corrections.softDeletePayment(id, reason.trim())
                    RecordKind.EXPENSE -> corrections.softDeleteExpense(id, reason.trim())
                }
                _state.value = RecordDeletionState(deletedKind = kind, deletedId = id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.value = RecordDeletionState(hasError = true)
            }
        }
    }
}
