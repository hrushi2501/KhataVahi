package com.farmeraccounts.data.repository

import androidx.room.withTransaction
import com.farmeraccounts.data.local.db.AppDatabase
import com.farmeraccounts.data.local.db.entity.AuditLogEntity
import com.farmeraccounts.data.local.db.entity.OutboxEntity
import com.farmeraccounts.data.mapper.toDomain
import com.farmeraccounts.data.mapper.toEntity
import com.farmeraccounts.domain.model.Payment
import com.farmeraccounts.domain.repository.PaymentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class PaymentRepositoryImpl(
    private val database: AppDatabase
) : PaymentRepository {

    private val paymentDao = database.paymentDao()
    private val auditDao = database.auditLogDao()
    private val outboxDao = database.outboxDao()

    override fun getAllActivePayments(): Flow<List<Payment>> {
        return paymentDao.getAllActiveFlow()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    override fun getPaymentsForCustomer(customerId: String): Flow<List<Payment>> {
        return paymentDao.getByCustomerIdFlow(customerId)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun getPaymentById(id: String): Payment? {
        return withContext(Dispatchers.IO) {
            paymentDao.getById(id)?.toDomain()
        }
    }

    override suspend fun recordPayment(payment: Payment) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                validatePayment(payment)
                val entity = payment.toEntity().copy(
                    version = 1L,
                    isDeleted = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                paymentDao.insert(entity)

                auditDao.insert(
                    AuditLogEntity(
                        id = UUID.randomUUID().toString(),
                        entityType = "PAYMENT",
                        entityId = entity.id,
                        action = "INSERT",
                        details = "Recorded payment: ${entity.amountPaise} paise (${entity.paymentMethod}) for customer ${entity.customerId}",
                        timestamp = entity.createdAt
                    )
                )

                outboxDao.insert(
                    OutboxEntity(
                        entityType = "PAYMENT",
                        entityId = entity.id,
                        operationType = "INSERT",
                        createdAt = entity.createdAt
                    )
                )
            }
        }
    }

    override suspend fun updatePayment(payment: Payment, reason: String?) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                validatePayment(payment)
                val existing = paymentDao.getById(payment.id)
                    ?: throw IllegalArgumentException("Payment with id ${payment.id} not found")

                val updatedEntity = payment.toEntity().copy(
                    version = existing.version + 1L,
                    updatedAt = System.currentTimeMillis(),
                    createdAt = existing.createdAt,
                    isDeleted = existing.isDeleted
                )
                paymentDao.update(updatedEntity)

                auditDao.insert(
                    AuditLogEntity(
                        id = UUID.randomUUID().toString(),
                        entityType = "PAYMENT",
                        entityId = updatedEntity.id,
                        action = "UPDATE",
                        details = reason ?: "Updated payment ${updatedEntity.id}",
                        timestamp = updatedEntity.updatedAt
                    )
                )

                outboxDao.insert(
                    OutboxEntity(
                        entityType = "PAYMENT",
                        entityId = updatedEntity.id,
                        operationType = "UPDATE",
                        createdAt = updatedEntity.updatedAt
                    )
                )
            }
        }
    }

    override suspend fun softDeletePayment(id: String, reason: String?) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val deletionReason = reason?.trim()
                require(!deletionReason.isNullOrBlank()) { "A deletion reason is required" }
                val existing = paymentDao.getByIdIncludeDeleted(id)
                    ?: throw IllegalArgumentException("Payment with id $id not found")

                if (existing.isDeleted) return@withTransaction

                val deletedEntity = existing.copy(
                    isDeleted = true,
                    version = existing.version + 1L,
                    updatedAt = System.currentTimeMillis()
                )
                paymentDao.update(deletedEntity)

                database.recordDeletion("PAYMENT", id, deletionSnapshot(existing), deletionReason, deletedEntity.updatedAt)

                outboxDao.insert(
                    OutboxEntity(
                        entityType = "PAYMENT",
                        entityId = id,
                        operationType = "SOFT_DELETE",
                        createdAt = deletedEntity.updatedAt
                    )
                )
            }
        }
    }

    private suspend fun validatePayment(payment: Payment) {
        require(payment.amountPaise > 0L) { "Payment must be greater than zero" }
        require(database.customerDao().getById(payment.customerId) != null) { "Customer is not available" }
        payment.workEntryId?.let { workId ->
            val work = database.workEntryDao().getById(workId)
                ?: throw IllegalArgumentException("Work is not available")
            require(work.customerId == payment.customerId) { "Work belongs to another customer" }
        }
    }

    override fun getTotalPaymentsCollectedFlow(): Flow<Long> {
        return paymentDao.getTotalPaymentsCollectedFlow()
            .flowOn(Dispatchers.IO)
    }

    override suspend fun getTotalPaidPaiseForCustomer(customerId: String): Long {
        return withContext(Dispatchers.IO) {
            paymentDao.getTotalPaidPaiseForCustomer(customerId)
        }
    }
}

