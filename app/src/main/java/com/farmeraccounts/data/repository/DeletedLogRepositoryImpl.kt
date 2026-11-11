package com.farmeraccounts.data.repository

import com.farmeraccounts.data.local.db.dao.DeletedLogDao
import com.farmeraccounts.domain.model.DeletedLog
import com.farmeraccounts.domain.repository.DeletedLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DeletedLogRepositoryImpl(private val dao: DeletedLogDao) : DeletedLogRepository {
    override fun getAllLogs(): Flow<List<DeletedLog>> = dao.getAllFlow().map { logs ->
        logs.map { DeletedLog(it.id, it.entityType, it.entityId, it.snapshotJson, it.reason, it.timestamp) }
    }
}
