package com.farmeraccounts.presentation.home

import com.farmeraccounts.core.util.DisplayLanguage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farmeraccounts.domain.model.DashboardTotals
import com.farmeraccounts.domain.repository.CustomerRepository
import com.farmeraccounts.domain.repository.ExpenseRepository
import com.farmeraccounts.domain.repository.PaymentRepository
import com.farmeraccounts.domain.repository.WorkRepository
import com.farmeraccounts.domain.usecase.CalculateDashboardTotalsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch

data class RecentActivityUiItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val amountPaise: Long,
    val isPayment: Boolean,
    val timestamp: Long
)

data class HomeUiState(
    val totals: DashboardTotals = DashboardTotals(0L, 0L, 0L, 0L, 0L, 0L),
    val recentActivities: List<RecentActivityUiItem> = emptyList(),
    val isLoading: Boolean = true,
    val hasError: Boolean = false
)

class HomeViewModel(
    private val calculateDashboardTotalsUseCase: CalculateDashboardTotalsUseCase,
    private val customerRepository: CustomerRepository,
    private val workRepository: WorkRepository,
    private val paymentRepository: PaymentRepository,
    private val expenseRepository: ExpenseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var observation: Job? = null

    init {
        refreshDashboard()
    }

    fun refreshDashboard() {
        observation?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = true, hasError = false)
        observeDashboardData()
    }

    private fun observeDashboardData() {
        // Observe customers, work entries, payments, and expenses concurrently
        observation = combine(
            customerRepository.getAllActiveCustomers(),
            workRepository.getAllActiveWorkEntries(),
            paymentRepository.getAllActivePayments(),
            expenseRepository.getAllActiveExpenses()
        ) { customers, workEntries, payments, _ ->
            val customerMap = customers.associateBy { it.id }

            val activities = mutableListOf<RecentActivityUiItem>()

            workEntries.take(5).forEach { work ->
                val customerName = customerMap[work.customerId]?.name ?: DisplayLanguage.text("ગ્રાહક", "Customer")
                activities.add(
                    RecentActivityUiItem(
                        id = work.id,
                        title = "$customerName — ${com.farmeraccounts.core.util.DisplayLanguage.workType(work.workType)}",
                        subtitle = DisplayLanguage.text("કામગીરી (${work.quantityUnit.bilingualTitle()})", "Work (${work.quantityUnit.bilingualTitle()})"),
                        amountPaise = work.totalPaise,
                        isPayment = false,
                        timestamp = work.date
                    )
                )
            }

            payments.take(5).forEach { payment ->
                val customerName = customerMap[payment.customerId]?.name ?: DisplayLanguage.text("ગ્રાહક", "Customer")
                activities.add(
                    RecentActivityUiItem(
                        id = payment.id,
                        title = DisplayLanguage.text("$customerName — જમા રકમ", "$customerName — Payment"),
                        subtitle = DisplayLanguage.text("ચુકવણી (${payment.paymentMethod.bilingualTitle()})", "Payment (${payment.paymentMethod.bilingualTitle()})"),
                        amountPaise = payment.amountPaise,
                        isPayment = true,
                        timestamp = payment.paymentDate
                    )
                )
            }

            activities.sortedByDescending { it.timestamp }.take(6)
        }.onEach { recentList ->
            val currentTotals = calculateDashboardTotalsUseCase()

            _uiState.value = HomeUiState(
                totals = currentTotals,
                recentActivities = recentList,
                isLoading = false
            )
        }.catch {
            _uiState.value = _uiState.value.copy(isLoading = false, hasError = true)
        }.launchIn(viewModelScope)
    }
}

