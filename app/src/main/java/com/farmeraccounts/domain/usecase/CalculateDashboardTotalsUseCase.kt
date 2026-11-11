package com.farmeraccounts.domain.usecase

import com.farmeraccounts.domain.model.DashboardTotals
import com.farmeraccounts.domain.repository.CustomerRepository
import com.farmeraccounts.domain.repository.ExpenseRepository
import com.farmeraccounts.domain.repository.PaymentRepository
import com.farmeraccounts.domain.repository.WorkRepository
import kotlinx.coroutines.flow.first

class CalculateDashboardTotalsUseCase(
    private val customerRepository: CustomerRepository,
    private val workRepository: WorkRepository,
    private val paymentRepository: PaymentRepository,
    private val expenseRepository: ExpenseRepository
) {
    suspend operator fun invoke(): DashboardTotals {
        val customers = customerRepository.getAllActiveCustomers().first()
        var totalReceivables = 0L
        var totalAdvances = 0L

        for (customer in customers) {
            val workPaise = workRepository.getTotalWorkPaiseForCustomer(customer.id)
            val paidPaise = paymentRepository.getTotalPaidPaiseForCustomer(customer.id)
            val balance = workPaise - paidPaise

            if (balance > 0L) {
                totalReceivables += balance
            } else if (balance < 0L) {
                totalAdvances += -balance
            }
        }

        val totalWorkRevenue = workRepository.getTotalWorkRevenueFlow().first()
        val totalPaymentsCollected = paymentRepository.getTotalPaymentsCollectedFlow().first()
        val totalExpenses = expenseRepository.getTotalExpensesFlow().first()
        val netCashFlow = totalPaymentsCollected - totalExpenses

        return DashboardTotals(
            totalReceivablesPaise = totalReceivables,
            totalAdvancesPaise = totalAdvances,
            totalWorkRevenuePaise = totalWorkRevenue,
            totalPaymentsCollectedPaise = totalPaymentsCollected,
            totalExpensesPaise = totalExpenses,
            netCashFlowPaise = netCashFlow
        )
    }
}

