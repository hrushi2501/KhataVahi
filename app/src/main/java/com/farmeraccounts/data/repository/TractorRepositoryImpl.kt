package com.farmeraccounts.data.repository

import androidx.room.withTransaction
import com.farmeraccounts.data.local.db.AppDatabase
import com.farmeraccounts.data.local.db.entity.AuditLogEntity
import com.farmeraccounts.data.local.db.entity.OutboxEntity
import com.farmeraccounts.data.mapper.toDomain
import com.farmeraccounts.data.mapper.toEntity
import com.farmeraccounts.domain.model.Tractor
import com.farmeraccounts.domain.model.TractorType
import com.farmeraccounts.domain.repository.TractorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class TractorRepositoryImpl(
    private val database: AppDatabase
) : TractorRepository {

    private val tractorDao = database.tractorDao()
    private val auditDao = database.auditLogDao()
    private val outboxDao = database.outboxDao()

    override fun getAllActiveTractors(): Flow<List<Tractor>> {
        return tractorDao.getAllActiveFlow()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun getTractorById(id: String): Tractor? {
        return withContext(Dispatchers.IO) {
            tractorDao.getById(id)?.toDomain()
        }
    }

    override suspend fun getTractorByType(type: TractorType): Tractor? {
        return withContext(Dispatchers.IO) {
            tractorDao.getByType(type.name)?.toDomain()
        }
    }

    override suspend fun addTractor(tractor: Tractor) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val entity = tractor.toEntity().copy(
                    version = 1L,
                    isDeleted = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                tractorDao.insert(entity)

                auditDao.insert(
                    AuditLogEntity(
                        id = UUID.randomUUID().toString(),
                        entityType = "TRACTOR",
                        entityId = entity.id,
                        action = "INSERT",
                        details = "Added tractor: ${entity.displayName}",
                        timestamp = entity.createdAt
                    )
                )

                outboxDao.insert(
                    OutboxEntity(
                        entityType = "TRACTOR",
                        entityId = entity.id,
                        operationType = "INSERT",
                        createdAt = entity.createdAt
                    )
                )
            }
        }
    }

    override suspend fun updateTractor(tractor: Tractor, reason: String?) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = tractorDao.getById(tractor.id)
                    ?: throw IllegalArgumentException("Tractor with id ${tractor.id} not found")

                val updatedEntity = tractor.toEntity().copy(
                    version = existing.version + 1L,
                    updatedAt = System.currentTimeMillis(),
                    createdAt = existing.createdAt,
                    isDeleted = existing.isDeleted
                )
                tractorDao.update(updatedEntity)

                auditDao.insert(
                    AuditLogEntity(
                        id = UUID.randomUUID().toString(),
                        entityType = "TRACTOR",
                        entityId = updatedEntity.id,
                        action = "UPDATE",
                        details = reason ?: "Updated tractor: ${updatedEntity.displayName}",
                        timestamp = updatedEntity.updatedAt
                    )
                )

                outboxDao.insert(
                    OutboxEntity(
                        entityType = "TRACTOR",
                        entityId = updatedEntity.id,
                        operationType = "UPDATE",
                        createdAt = updatedEntity.updatedAt
                    )
                )
            }
        }
    }

    override suspend fun softDeleteTractor(id: String, reason: String?) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val deletionReason = reason?.trim()
                require(!deletionReason.isNullOrBlank()) { "A deletion reason is required" }
                val existing = tractorDao.getByIdIncludeDeleted(id)
                    ?: throw IllegalArgumentException("Tractor with id $id not found")

                if (existing.isDeleted) return@withTransaction

                val deletedEntity = existing.copy(
                    isDeleted = true,
                    version = existing.version + 1L,
                    updatedAt = System.currentTimeMillis()
                )
                tractorDao.update(deletedEntity)

                database.recordDeletion("TRACTOR", id, deletionSnapshot(existing), deletionReason, deletedEntity.updatedAt)

                outboxDao.insert(
                    OutboxEntity(
                        entityType = "TRACTOR",
                        entityId = id,
                        operationType = "SOFT_DELETE",
                        createdAt = deletedEntity.updatedAt
                    )
                )
            }
        }
    }
}

