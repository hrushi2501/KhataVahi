package com.farmeraccounts.presentation.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farmeraccounts.domain.model.CustomerLedger
import com.farmeraccounts.domain.usecase.GetCustomerLedgerUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

data class CustomerDetailUiState(
    val ledger: CustomerLedger? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class CustomerDetailViewModel(
    private val getCustomerLedgerUseCase: GetCustomerLedgerUseCase,
    private val ledgerChanges: kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.flowOf(Unit)
) : ViewModel() {

    private val _uiState = MutableStateFlow(CustomerDetailUiState())
    val uiState: StateFlow<CustomerDetailUiState> = _uiState.asStateFlow()

    private var loadJob: kotlinx.coroutines.Job? = null

    fun loadLedger(customerId: String) {
        loadJob?.cancel()
        if (customerId.isBlank()) {
            _uiState.value = CustomerDetailUiState(isLoading = false)
            return
        }
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            ledgerChanges.collectLatest {
            try {
                val ledger = getCustomerLedgerUseCase(customerId)
                _uiState.value = CustomerDetailUiState(
                    ledger = ledger,
                    isLoading = false,
                    errorMessage = null
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = CustomerDetailUiState(
                    ledger = null,
                    isLoading = false,
                    errorMessage = "ledger_load_failed"
                )
            }
            }
        }
    }
}

