package com.farmeraccounts.domain.usecase

import com.farmeraccounts.domain.model.Payment
import com.farmeraccounts.domain.model.PaymentMethod
import com.farmeraccounts.domain.repository.PaymentRepository
import java.util.UUID

class RecordPaymentUseCase(
    private val paymentRepository: PaymentRepository
) {
    suspend operator fun invoke(
        customerId: String,
        amountPaise: Long,
        paymentDate: Long = System.currentTimeMillis(),
        paymentMethod: PaymentMethod = PaymentMethod.CASH,
        workEntryId: String? = null,
        notes: String? = null
    ): Payment {
        require(customerId.isNotBlank()) { "Customer ID is required" }
        require(amountPaise > 0L) { "Payment amount must be greater than zero" }

        val payment = Payment(
            id = UUID.randomUUID().toString(),
            customerId = customerId,
            workEntryId = workEntryId,
            amountPaise = amountPaise,
            paymentDate = paymentDate,
            paymentMethod = paymentMethod,
            notes = notes?.trim()?.ifEmpty { null }
        )
        paymentRepository.recordPayment(payment)
        return payment
    }
}

