package com.farmeraccounts.presentation.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farmeraccounts.domain.model.Customer
import com.farmeraccounts.domain.model.CustomerBalance
import com.farmeraccounts.domain.repository.CustomerRepository
import com.farmeraccounts.domain.usecase.CalculateCustomerBalanceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview

data class CustomerWithBalanceUiItem(
    val customer: Customer,
    val balance: CustomerBalance
)

data class CustomerListUiState(
    val customers: List<CustomerWithBalanceUiItem> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val hasError: Boolean = false
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class CustomerListViewModel(
    private val customerRepository: CustomerRepository,
    private val calculateCustomerBalanceUseCase: CalculateCustomerBalanceUseCase,
    private val ledgerChanges: kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.flowOf(Unit)
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    private val retrySignal = MutableStateFlow(0)

    fun retry() { retrySignal.value += 1 }

    val uiState: StateFlow<CustomerListUiState> = retrySignal.flatMapLatest { combine(
        customerRepository.getAllActiveCustomers(),
        searchQuery.debounce(200),
        ledgerChanges
    ) { customerList, query, _ ->
        val filtered = com.farmeraccounts.core.util.CustomerMatching.search(customerList, query)

        val items = filtered.map { customer ->
            val balance = calculateCustomerBalanceUseCase(customer.id)
            CustomerWithBalanceUiItem(customer, balance)
        }

        CustomerListUiState(
            customers = items,
            searchQuery = query,
            isLoading = false
        )
    }.catch { emit(CustomerListUiState(isLoading = false, hasError = true)) } }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CustomerListUiState()
    )

    fun onSearchQueryChanged(newQuery: String) {
        searchQuery.value = newQuery
    }
}

