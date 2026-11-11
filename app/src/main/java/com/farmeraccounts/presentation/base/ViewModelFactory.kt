package com.farmeraccounts.presentation.base

import com.farmeraccounts.data.local.db.ledgerInvalidations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.farmeraccounts.core.di.AppContainer
import com.farmeraccounts.presentation.customers.AddEditCustomerViewModel
import com.farmeraccounts.presentation.customers.CustomerDetailViewModel
import com.farmeraccounts.presentation.customers.CustomerListViewModel
import com.farmeraccounts.presentation.expense.RecordExpenseViewModel
import com.farmeraccounts.presentation.home.HomeViewModel
import com.farmeraccounts.presentation.payment.RecordPaymentViewModel
import com.farmeraccounts.presentation.settings.SettingsViewModel
import com.farmeraccounts.presentation.work.AddWorkViewModel
import com.farmeraccounts.presentation.work.WorkListViewModel

/**
 * Factory providing ViewModels using the application's manual DI container.
 */
class ViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(com.farmeraccounts.presentation.history.HistoryViewModel::class.java) ->
                com.farmeraccounts.presentation.history.HistoryViewModel(container.expenseRepository,
                    com.farmeraccounts.data.repository.DeletedLogRepositoryImpl(container.database.deletedLogDao())) as T
            modelClass.isAssignableFrom(RecordDeletionViewModel::class.java) -> RecordDeletionViewModel(container.correctEntryUseCase) as T
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(
                    container.calculateDashboardTotalsUseCase,
                    container.customerRepository,
                    container.workRepository,
                    container.paymentRepository,
                    container.expenseRepository
                ) as T
            }
            modelClass.isAssignableFrom(CustomerListViewModel::class.java) -> {
                CustomerListViewModel(
                    container.customerRepository,
                    container.calculateCustomerBalanceUseCase,
                    container.database.ledgerInvalidations()
                ) as T
            }
            modelClass.isAssignableFrom(AddEditCustomerViewModel::class.java) -> {
                AddEditCustomerViewModel(
                    container.addCustomerUseCase,
                    container.customerRepository
                ) as T
            }
            modelClass.isAssignableFrom(CustomerDetailViewModel::class.java) -> {
                CustomerDetailViewModel(
                    container.getCustomerLedgerUseCase,
                    container.database.ledgerInvalidations()
                ) as T
            }
            modelClass.isAssignableFrom(WorkListViewModel::class.java) -> {
                WorkListViewModel(
                    container.workRepository,
                    container.customerRepository,
                    container.tractorRepository
                ) as T
            }
            modelClass.isAssignableFrom(AddWorkViewModel::class.java) -> {
                AddWorkViewModel(
                    container.addWorkUseCase,
                    container.customerRepository,
                    container.tractorRepository
                ) as T
            }
            modelClass.isAssignableFrom(RecordPaymentViewModel::class.java) -> {
                RecordPaymentViewModel(
                    container.recordPaymentUseCase,
                    container.customerRepository,
                    container.workRepository
                ) as T
            }
            modelClass.isAssignableFrom(RecordExpenseViewModel::class.java) -> {
                RecordExpenseViewModel(
                    container.recordExpenseUseCase,
                    container.tractorRepository
                ) as T
            }
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(
                    container.themePreferencesRepository,
                    container.authRepository,
                    container.syncManager
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

