package com.farmeraccounts.domain.model

data class AuditRecord(
    val id: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val details: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

