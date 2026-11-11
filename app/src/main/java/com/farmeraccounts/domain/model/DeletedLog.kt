package com.farmeraccounts.domain.model

data class DeletedLog(
    val id: String,
    val entityType: String,
    val entityId: String,
    val snapshotJson: String,
    val reason: String,
    val timestamp: Long
)
