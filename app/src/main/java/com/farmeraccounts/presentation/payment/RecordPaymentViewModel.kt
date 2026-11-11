package com.farmeraccounts.presentation.payment

import com.farmeraccounts.core.util.DisplayLanguage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farmeraccounts.core.util.NumericInputNormalizer
import com.farmeraccounts.domain.model.Customer
import com.farmeraccounts.domain.model.PaymentMethod
import com.farmeraccounts.domain.model.WorkEntry
import com.farmeraccounts.domain.repository.CustomerRepository
import com.farmeraccounts.domain.repository.WorkRepository
import com.farmeraccounts.domain.usecase.RecordPaymentUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecordPaymentViewModel(
    private val recordPaymentUseCase: RecordPaymentUseCase,
    private val customerRepository: CustomerRepository,
    private val workRepository: WorkRepository
) : ViewModel() {

    val customers: StateFlow<List<Customer>> = customerRepository.getAllActiveCustomers()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _customerWorkEntries = MutableStateFlow<List<WorkEntry>>(emptyList())
    val customerWorkEntries: StateFlow<List<WorkEntry>> = _customerWorkEntries.asStateFlow()

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    private val _saveCompleted = MutableStateFlow(false)
    val saveCompleted: StateFlow<Boolean> = _saveCompleted.asStateFlow()

    private var workLoadJob: kotlinx.coroutines.Job? = null

    fun loadWorkForCustomer(customerId: String) {
        workLoadJob?.cancel()
        _customerWorkEntries.value = emptyList()
        if (customerId.isBlank()) return
        workLoadJob = viewModelScope.launch {
            val entries = workRepository.getWorkEntriesForCustomer(customerId).first()
            _customerWorkEntries.value = entries
        }
    }

    fun savePayment(
        customerId: String,
        workEntryId: String?,
        amountStr: String,
        paymentDate: Long,
        paymentMethod: PaymentMethod,
        notes: String?,
        onSuccess: () -> Unit
    ) {
        if (_isSaving.value || _saveCompleted.value) return
        if (customerId.isBlank()) {
            _saveError.value = DisplayLanguage.text("ગ્રાહક પસંદ કરો", "Please select a customer")
            return
        }

        val amountPaise = NumericInputNormalizer.parseRupeesToPaise(amountStr)
        if (amountPaise <= 0L) {
            _saveError.value = DisplayLanguage.text("યોગ્ય રકમ દાખલ કરો", "Please enter a valid payment amount")
            return
        }

        _isSaving.value = true
        viewModelScope.launch {
            try {
                recordPaymentUseCase(
                    customerId = customerId,
                    amountPaise = amountPaise,
                    paymentDate = paymentDate,
                    paymentMethod = paymentMethod,
                    workEntryId = workEntryId?.ifBlank { null },
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

