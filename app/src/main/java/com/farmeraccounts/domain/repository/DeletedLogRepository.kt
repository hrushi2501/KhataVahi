package com.farmeraccounts.domain.repository

import com.farmeraccounts.domain.model.DeletedLog
import kotlinx.coroutines.flow.Flow

interface DeletedLogRepository {
    fun getAllLogs(): Flow<List<DeletedLog>>
}
