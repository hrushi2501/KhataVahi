package com.farmeraccounts.presentation.expense

import com.farmeraccounts.core.util.DisplayLanguage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farmeraccounts.core.util.NumericInputNormalizer
import com.farmeraccounts.domain.model.ExpenseCategory
import com.farmeraccounts.domain.model.Tractor
import com.farmeraccounts.domain.repository.TractorRepository
import com.farmeraccounts.domain.usecase.RecordExpenseUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecordExpenseViewModel(
    private val recordExpenseUseCase: RecordExpenseUseCase,
    private val tractorRepository: TractorRepository
) : ViewModel() {

    val tractors: StateFlow<List<Tractor>> = tractorRepository.getAllActiveTractors()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    private val _saveCompleted = MutableStateFlow(false)
    val saveCompleted: StateFlow<Boolean> = _saveCompleted.asStateFlow()

    fun saveExpense(
        category: ExpenseCategory,
        tractorId: String?,
        amountStr: String,
        expenseDate: Long,
        notes: String?,
        onSuccess: () -> Unit
    ) {
        if (_isSaving.value || _saveCompleted.value) return
        val amountPaise = NumericInputNormalizer.parseRupeesToPaise(amountStr)
        if (amountPaise <= 0L) {
            _saveError.value = DisplayLanguage.text("યોગ્ય રકમ દાખલ કરો", "Please enter a valid expense amount")
            return
        }

        _isSaving.value = true
        viewModelScope.launch {
            try {
                recordExpenseUseCase(
                    category = category,
                    amountPaise = amountPaise,
                    expenseDate = expenseDate,
                    tractorId = tractorId?.ifBlank { null },
                    notes = notes
                )
                _saveCompleted.value = true
                onSuccess()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _saveError.value = DisplayLanguage.text("સાચવી શકાયું નથી. તમારી વિગતો અહીં જ છે. ફરી પ્રયત્ન કરો.", "Could not save. Your entries are still here. Please try again.")
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearError() {
        _saveError.value = null
    }
}

