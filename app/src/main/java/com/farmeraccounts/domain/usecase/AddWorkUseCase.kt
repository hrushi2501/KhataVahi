package com.farmeraccounts.domain.usecase

import com.farmeraccounts.core.util.ScaledQuantity
import com.farmeraccounts.domain.model.Payment
import com.farmeraccounts.domain.model.PaymentMethod
import com.farmeraccounts.domain.model.QuantityUnit
import com.farmeraccounts.domain.model.WorkEntry
import com.farmeraccounts.domain.repository.WorkRepository
import java.util.UUID

class AddWorkUseCase(
    private val workRepository: WorkRepository
) {
    suspend operator fun invoke(
        customerId: String,
        tractorId: String,
        workType: String,
        date: Long,
        quantityScaled: Long,
        quantityUnit: QuantityUnit,
        ratePerUnitPaise: Long,
        notes: String? = null,
        initialPaymentPaise: Long? = null,
        initialPaymentMethod: PaymentMethod? = null
    ): WorkEntry {
        require(customerId.isNotBlank()) { "Customer ID is required" }
        require(tractorId.isNotBlank()) { "Tractor ID is required" }
        require(quantityScaled > 0L) { "Quantity must be greater than zero" }
        require(ratePerUnitPaise >= 0L) { "Rate cannot be negative" }

        val totalPaise = ScaledQuantity.calculateTotalPaise(quantityScaled, ratePerUnitPaise)
        val workId = UUID.randomUUID().toString()

        val workEntry = WorkEntry(
            id = workId,
            customerId = customerId,
            tractorId = tractorId,
            workType = workType,
            date = date,
            quantityScaled = quantityScaled,
            quantityUnit = quantityUnit,
            ratePerUnitPaise = ratePerUnitPaise,
            totalPaise = totalPaise,
            notes = notes?.trim()?.ifEmpty { null }
        )

        if (initialPaymentPaise != null && initialPaymentPaise > 0L) {
            val payment = Payment(
                id = UUID.randomUUID().toString(),
                customerId = customerId,
                workEntryId = workId,
                amountPaise = initialPaymentPaise,
                paymentDate = date,
                paymentMethod = initialPaymentMethod ?: PaymentMethod.CASH,
                notes = "Initial payment for $workType"
            )
            workRepository.addWorkEntryWithInitialPayment(workEntry, payment)
        } else {
            workRepository.addWorkEntry(workEntry)
        }

        return workEntry
    }
}

