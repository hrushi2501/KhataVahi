package com.farmeraccounts.domain.model

data class Payment(
    val id: String,
    val customerId: String,
    val workEntryId: String? = null,
    val amountPaise: Long,
    val paymentDate: Long,
    val paymentMethod: PaymentMethod,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Long = 1L,
    val isDeleted: Boolean = false
)

