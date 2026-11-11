package com.farmeraccounts.data.repository

import com.farmeraccounts.data.local.db.dao.AuditLogDao
import com.farmeraccounts.data.local.db.entity.AuditLogEntity
import com.farmeraccounts.domain.model.AuditRecord
import com.farmeraccounts.domain.repository.AuditRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AuditRepositoryImpl(
    private val auditLogDao: AuditLogDao
) : AuditRepository {

    override fun getAllLogs(): Flow<List<AuditRecord>> {
        return auditLogDao.getAllLogs().map { list ->
            list.map { entity ->
                AuditRecord(
                    id = entity.id,
                    entityType = entity.entityType,
                    entityId = entity.entityId,
                    action = entity.action,
                    details = entity.details,
                    timestamp = entity.timestamp
                )
            }
        }
    }

    override suspend fun recordLog(record: AuditRecord) {
        auditLogDao.insert(
            AuditLogEntity(
                id = record.id,
                entityType = record.entityType,
                entityId = record.entityId,
                action = record.action,
                details = record.details,
                timestamp = record.timestamp
            )
        )
    }
}

