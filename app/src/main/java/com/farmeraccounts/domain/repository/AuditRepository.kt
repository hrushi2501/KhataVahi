package com.farmeraccounts.domain.repository

import com.farmeraccounts.domain.model.AuditRecord
import kotlinx.coroutines.flow.Flow

interface AuditRepository {
    fun getAllLogs(): Flow<List<AuditRecord>>
    suspend fun recordLog(record: AuditRecord)
}

