package com.farmeraccounts.domain.model

data class WorkEntry(
    val id: String,
    val customerId: String,
    val tractorId: String,
    val workType: String,
    val date: Long,
    val quantityScaled: Long,
    val quantityUnit: QuantityUnit,
    val ratePerUnitPaise: Long,
    val totalPaise: Long,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Long = 1L,
    val isDeleted: Boolean = false
)

