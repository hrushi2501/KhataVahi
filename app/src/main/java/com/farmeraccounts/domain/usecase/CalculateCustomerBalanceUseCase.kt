package com.farmeraccounts.domain.usecase

import com.farmeraccounts.domain.model.CustomerBalance
import com.farmeraccounts.domain.repository.PaymentRepository
import com.farmeraccounts.domain.repository.WorkRepository

/**
 * Calculates a customer's current financial standing.
 * Per project rules:
 * - Customer outstanding = active work totals - active payment totals.
 * - Supports partial payments and customer advances (negative balance).
 * - Soft-deleted records are automatically excluded from the sums.
 */
class CalculateCustomerBalanceUseCase(
    private val workRepository: WorkRepository,
    private val paymentRepository: PaymentRepository
) {
    suspend operator fun invoke(customerId: String): CustomerBalance {
        val totalWorkPaise = workRepository.getTotalWorkPaiseForCustomer(customerId)
        val totalPaidPaise = paymentRepository.getTotalPaidPaiseForCustomer(customerId)
        val outstandingPaise = totalWorkPaise - totalPaidPaise

        return CustomerBalance(
            customerId = customerId,
            totalWorkPaise = totalWorkPaise,
            totalPaidPaise = totalPaidPaise,
            outstandingPaise = outstandingPaise
        )
    }
}

