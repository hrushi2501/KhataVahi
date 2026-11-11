package com.farmeraccounts.data.repository

import androidx.room.withTransaction
import com.farmeraccounts.data.local.db.AppDatabase
import com.farmeraccounts.data.local.db.entity.AuditLogEntity
import com.farmeraccounts.data.local.db.entity.OutboxEntity
import com.farmeraccounts.data.mapper.toDomain
import com.farmeraccounts.data.mapper.toEntity
import com.farmeraccounts.domain.model.Customer
import com.farmeraccounts.domain.repository.CustomerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class CustomerRepositoryImpl(
    private val database: AppDatabase
) : CustomerRepository {

    private val customerDao = database.customerDao()
    private val auditDao = database.auditLogDao()
    private val outboxDao = database.outboxDao()

    override fun getAllActiveCustomers(): Flow<List<Customer>> {
        return customerDao.getAllActiveFlow()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    override fun searchActiveCustomers(query: String): Flow<List<Customer>> {
        return customerDao.searchActiveFlow(query.trim())
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun getCustomerById(id: String): Customer? {
        return withContext(Dispatchers.IO) {
            customerDao.getById(id)?.toDomain()
        }
    }

    override fun getCustomerByIdFlow(id: String): Flow<Customer?> {
        return customerDao.getByIdFlow(id)
            .map { it?.toDomain() }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun addCustomer(customer: Customer) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val entity = customer.toEntity().copy(
                    version = 1L,
                    isDeleted = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                customerDao.insert(entity)

                auditDao.insert(
                    AuditLogEntity(
                        id = UUID.randomUUID().toString(),
                        entityType = "CUSTOMER",
                        entityId = entity.id,
                        action = "INSERT",
                        details = "Added customer: ${entity.name}",
                        timestamp = entity.createdAt
                    )
                )

                outboxDao.insert(
                    OutboxEntity(
                        entityType = "CUSTOMER",
                        entityId = entity.id,
                        operationType = "INSERT",
                        createdAt = entity.createdAt
                    )
                )
            }
        }
    }

    override suspend fun updateCustomer(customer: Customer, reason: String?) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = customerDao.getById(customer.id)
                    ?: throw IllegalArgumentException("Customer with id ${customer.id} not found")

                val updatedEntity = customer.toEntity().copy(
                    version = existing.version + 1L,
                    updatedAt = System.currentTimeMillis(),
                    createdAt = existing.createdAt,
                    isDeleted = existing.isDeleted
                )
                customerDao.update(updatedEntity)

                auditDao.insert(
                    AuditLogEntity(
                        id = UUID.randomUUID().toString(),
                        entityType = "CUSTOMER",
                        entityId = updatedEntity.id,
                        action = "UPDATE",
                        details = reason ?: "Updated customer details for ${updatedEntity.name}",
                        timestamp = updatedEntity.updatedAt
                    )
                )

                outboxDao.insert(
                    OutboxEntity(
                        entityType = "CUSTOMER",
                        entityId = updatedEntity.id,
                        operationType = "UPDATE",
                        createdAt = updatedEntity.updatedAt
                    )
                )
            }
        }
    }

    override suspend fun softDeleteCustomer(id: String, reason: String?) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val deletionReason = reason?.trim()
                require(!deletionReason.isNullOrBlank()) { "A deletion reason is required" }
                val existing = customerDao.getByIdIncludeDeleted(id)
                    ?: throw IllegalArgumentException("Customer with id $id not found")

                if (existing.isDeleted) return@withTransaction
                require(!database.workEntryDao().hasActiveForCustomer(id) &&
                    !database.paymentDao().hasActiveForCustomer(id)) {
                    "Customer has active work or payments"
                }

                val deletedEntity = existing.copy(
                    isDeleted = true,
                    version = existing.version + 1L,
                    updatedAt = System.currentTimeMillis()
                )
                customerDao.update(deletedEntity)

                database.recordDeletion("CUSTOMER", id, deletionSnapshot(existing), deletionReason, deletedEntity.updatedAt)

                outboxDao.insert(
                    OutboxEntity(
                        entityType = "CUSTOMER",
                        entityId = id,
                        operationType = "SOFT_DELETE",
                        createdAt = deletedEntity.updatedAt
                    )
                )
            }
        }
    }
}

