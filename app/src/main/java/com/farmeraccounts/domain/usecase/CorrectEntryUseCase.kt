package com.farmeraccounts.domain.usecase

import com.farmeraccounts.domain.model.Customer
import com.farmeraccounts.domain.model.Expense
import com.farmeraccounts.domain.model.Payment
import com.farmeraccounts.domain.model.WorkEntry
import com.farmeraccounts.domain.repository.CustomerRepository
import com.farmeraccounts.domain.repository.ExpenseRepository
import com.farmeraccounts.domain.repository.PaymentRepository
import com.farmeraccounts.domain.repository.WorkRepository

class CorrectEntryUseCase(
    private val customerRepository: CustomerRepository,
    private val workRepository: WorkRepository,
    private val paymentRepository: PaymentRepository,
    private val expenseRepository: ExpenseRepository
) {
    suspend fun updateWorkEntry(workEntry: WorkEntry, reason: String) {
        workRepository.updateWorkEntry(workEntry, reason)
    }

    suspend fun softDeleteWorkEntry(id: String, reason: String) {
        workRepository.softDeleteWorkEntry(id, reason)
    }

    suspend fun updatePayment(payment: Payment, reason: String) {
        paymentRepository.updatePayment(payment, reason)
    }

    suspend fun softDeletePayment(id: String, reason: String) {
        paymentRepository.softDeletePayment(id, reason)
    }

    suspend fun updateExpense(expense: Expense, reason: String) {
        expenseRepository.updateExpense(expense, reason)
    }

    suspend fun softDeleteExpense(id: String, reason: String) {
        expenseRepository.softDeleteExpense(id, reason)
    }

    suspend fun updateCustomer(customer: Customer, reason: String) {
        customerRepository.updateCustomer(customer, reason)
    }

    suspend fun softDeleteCustomer(id: String, reason: String) {
        customerRepository.softDeleteCustomer(id, reason)
    }
}

