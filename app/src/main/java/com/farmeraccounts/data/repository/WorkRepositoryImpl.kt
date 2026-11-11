package com.farmeraccounts.data.repository

import androidx.room.withTransaction
import com.farmeraccounts.core.util.ScaledQuantity
import com.farmeraccounts.data.local.db.AppDatabase
import com.farmeraccounts.data.local.db.entity.AuditLogEntity
import com.farmeraccounts.data.local.db.entity.OutboxEntity
import com.farmeraccounts.data.mapper.toDomain
import com.farmeraccounts.data.mapper.toEntity
import com.farmeraccounts.domain.model.Payment
import com.farmeraccounts.domain.model.WorkEntry
import com.farmeraccounts.domain.repository.WorkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class WorkRepositoryImpl(
    private val database: AppDatabase
) : WorkRepository {

    private val workDao = database.workEntryDao()
    private val paymentDao = database.paymentDao()
    private val auditDao = database.auditLogDao()
    private val outboxDao = database.outboxDao()

    override fun getAllActiveWorkEntries(): Flow<List<WorkEntry>> {
        return workDao.getAllActiveFlow()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    override fun getWorkEntriesForCustomer(customerId: String): Flow<List<WorkEntry>> {
        return workDao.getByCustomerIdFlow(customerId)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun getWorkEntryById(id: String): WorkEntry? {
        return withContext(Dispatchers.IO) {
            workDao.getById(id)?.toDomain()
        }
    }

    override suspend fun addWorkEntry(workEntry: WorkEntry) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                validateWork(workEntry)
                val entity = workEntry.toEntity().copy(
                    version = 1L,
                    isDeleted = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                workDao.insert(entity)

                auditDao.insert(
                    AuditLogEntity(
                        id = UUID.randomUUID().toString(),
                        entityType = "WORK_ENTRY",
                        entityId = entity.id,
                        action = "INSERT",
                        details = "Added work entry (${entity.workType}) for customer ${entity.customerId}, total: ${entity.totalPaise} paise",
                        timestamp = entity.createdAt
                    )
                )

                outboxDao.insert(
                    OutboxEntity(
                        entityType = "WORK_ENTRY",
                        entityId = entity.id,
                        operationType = "INSERT",
                        createdAt = entity.createdAt
                    )
                )
            }
        }
    }

    override suspend fun addWorkEntryWithInitialPayment(workEntry: WorkEntry, initialPayment: Payment) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                validateWork(workEntry)
                require(initialPayment.amountPaise > 0L) { "Payment must be greater than zero" }
                require(initialPayment.customerId == workEntry.customerId && initialPayment.workEntryId == workEntry.id) { "Payment does not match this work" }
                val now = System.currentTimeMillis()

                // 1. Insert Work Entry
                val workEntity = workEntry.toEntity().copy(
                    version = 1L,
                    isDeleted = false,
                    createdAt = now,
                    updatedAt = now
                )
                workDao.insert(workEntity)

                // 2. Insert Initial Payment linked to this work entry
                val paymentEntity = initialPayment.toEntity().copy(
                    workEntryId = workEntity.id,
                    customerId = workEntity.customerId,
                    version = 1L,
                    isDeleted = false,
                    createdAt = now,
                    updatedAt = now
                )
                paymentDao.insert(paymentEntity)

                // 3. Insert Audit Records for both
                auditDao.insert(
                    AuditLogEntity(
                        id = UUID.randomUUID().toString(),
                        entityType = "WORK_ENTRY",
                        entityId = workEntity.id,
                        action = "INSERT",
                        details = "Atomic work entry: ${workEntity.workType}, total: ${workEntity.totalPaise} paise",
                        timestamp = now
                    )
                )
                auditDao.insert(
                    AuditLogEntity(
                        id = UUID.randomUUID().toString(),
                        entityType = "PAYMENT",
                        entityId = paymentEntity.id,
                        action = "INSERT",
                        details = "Atomic initial payment for work ${workEntity.id}: ${paymentEntity.amountPaise} paise",
                        timestamp = now
                    )
                )

                // 4. Insert Outbox Records for both
                outboxDao.insert(
                    OutboxEntity(
                        entityType = "WORK_ENTRY",
                        entityId = workEntity.id,
                        operationType = "INSERT",
                        createdAt = now
                    )
                )
                outboxDao.insert(
                    OutboxEntity(
                        entityType = "PAYMENT",
                        entityId = paymentEntity.id,
                        operationType = "INSERT",
                        createdAt = now
                    )
                )
            }
        }
    }

    override suspend fun updateWorkEntry(workEntry: WorkEntry, reason: String?) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                validateWork(workEntry)
                val existing = workDao.getById(workEntry.id)
                    ?: throw IllegalArgumentException("Work entry with id ${workEntry.id} not found")

                val updatedEntity = workEntry.toEntity().copy(
                    version = existing.version + 1L,
                    updatedAt = System.currentTimeMillis(),
                    createdAt = existing.createdAt,
                    isDeleted = existing.isDeleted
                )
                workDao.update(updatedEntity)

                auditDao.insert(
                    AuditLogEntity(
                        id = UUID.randomUUID().toString(),
                        entityType = "WORK_ENTRY",
                        entityId = updatedEntity.id,
                        action = "UPDATE",
                        details = reason ?: "Updated work entry ${updatedEntity.id}",
                        timestamp = updatedEntity.updatedAt
                    )
                )

                outboxDao.insert(
                    OutboxEntity(
                        entityType = "WORK_ENTRY",
                        entityId = updatedEntity.id,
                        operationType = "UPDATE",
                        createdAt = updatedEntity.updatedAt
                    )
                )
            }
        }
    }

    override suspend fun softDeleteWorkEntry(id: String, reason: String?) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val deletionReason = reason?.trim()
                require(!deletionReason.isNullOrBlank()) { "A deletion reason is required" }
                val existing = workDao.getByIdIncludeDeleted(id)
                    ?: throw IllegalArgumentException("Work entry with id $id not found")

                if (existing.isDeleted) return@withTransaction

                val deletedEntity = existing.copy(
                    isDeleted = true,
                    version = existing.version + 1L,
                    updatedAt = System.currentTimeMillis()
                )
                workDao.update(deletedEntity)

                database.recordDeletion("WORK_ENTRY", id, deletionSnapshot(existing), deletionReason, deletedEntity.updatedAt)

                outboxDao.insert(
                    OutboxEntity(
                        entityType = "WORK_ENTRY",
                        entityId = id,
                        operationType = "SOFT_DELETE",
                        createdAt = deletedEntity.updatedAt
                    )
                )
            }
        }
    }

    override fun getTotalWorkRevenueFlow(): Flow<Long> {
        return workDao.getTotalWorkRevenueFlow()
            .flowOn(Dispatchers.IO)
    }

    override suspend fun getTotalWorkPaiseForCustomer(customerId: String): Long {
        return withContext(Dispatchers.IO) {
            workDao.getTotalWorkPaiseForCustomer(customerId)
        }
    }
    private suspend fun validateWork(work: WorkEntry) {
        require(work.quantityScaled > 0L && work.ratePerUnitPaise >= 0L) { "Invalid quantity or rate" }
        require(work.totalPaise == ScaledQuantity.calculateTotalPaise(work.quantityScaled, work.ratePerUnitPaise)) { "Work total does not match quantity and rate" }
        require(database.customerDao().getById(work.customerId) != null) { "Customer is not available" }
        require(database.tractorDao().getById(work.tractorId) != null) { "Tractor is not available" }
    }

}

