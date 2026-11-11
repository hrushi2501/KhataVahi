package com.farmeraccounts.presentation.work

import com.farmeraccounts.core.util.DisplayLanguage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farmeraccounts.domain.model.WorkEntry
import com.farmeraccounts.domain.repository.CustomerRepository
import com.farmeraccounts.domain.repository.TractorRepository
import com.farmeraccounts.domain.repository.WorkRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi

data class WorkEntryUiItem(
    val workEntry: WorkEntry,
    val customerName: String,
    val tractorName: String
)

data class WorkListUiState(
    val workEntries: List<WorkEntryUiItem> = emptyList(),
    val isLoading: Boolean = true,
    val hasError: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class WorkListViewModel(
    private val workRepository: WorkRepository,
    private val customerRepository: CustomerRepository,
    private val tractorRepository: TractorRepository
) : ViewModel() {

    private val retrySignal = MutableStateFlow(0)

    fun retry() { retrySignal.value += 1 }

    val uiState: StateFlow<WorkListUiState> = retrySignal.flatMapLatest { combine(
        workRepository.getAllActiveWorkEntries(),
        customerRepository.getAllActiveCustomers(),
        tractorRepository.getAllActiveTractors()
    ) { workList, customers, tractors ->
        val customerMap = customers.associateBy { it.id }
        val tractorMap = tractors.associateBy { it.id }

        val items = workList.map { entry ->
            val customer = customerMap[entry.customerId]
            val tractor = tractorMap[entry.tractorId]
            WorkEntryUiItem(
                workEntry = entry,
                customerName = customer?.getDisplayName() ?: DisplayLanguage.text("ગ્રાહક", "Customer"),
                tractorName = tractor?.localizedDisplayName() ?: DisplayLanguage.text("ટ્રેક્ટર", "Tractor")
            )
        }

        WorkListUiState(
            workEntries = items,
            isLoading = false
        )
    }.catch { emit(WorkListUiState(isLoading = false, hasError = true)) } }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = WorkListUiState()
    )
}

