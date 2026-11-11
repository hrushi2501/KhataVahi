package com.farmeraccounts.data.repository

import com.farmeraccounts.data.local.db.AppDatabase
import com.farmeraccounts.data.local.db.entity.AuditLogEntity
import com.farmeraccounts.data.local.db.entity.DeletedLogEntity
import com.farmeraccounts.data.local.db.entity.OutboxEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private val snapshotFormat = Json { encodeDefaults = true }
internal inline fun <reified T> deletionSnapshot(entity: T): String = snapshotFormat.encodeToString(entity)

/** Must be called inside the same Room transaction as the soft deletion. */
internal suspend fun AppDatabase.recordDeletion(
    entityType: String, entityId: String, snapshot: String, reason: String, timestamp: Long
) {
    val logId = UUID.randomUUID().toString()
    deletedLogDao().insert(DeletedLogEntity(logId, entityType, entityId, snapshot, reason, timestamp))
    val auditId = UUID.randomUUID().toString()
    auditLogDao().insert(AuditLogEntity(auditId, entityType, entityId, "SOFT_DELETE", reason, timestamp))
    outboxDao().insert(OutboxEntity(entityType = "DELETED_LOG", entityId = logId, operationType = "INSERT", createdAt = timestamp))
    outboxDao().insert(OutboxEntity(entityType = "AUDIT_LOG", entityId = auditId, operationType = "INSERT", createdAt = timestamp))
}
