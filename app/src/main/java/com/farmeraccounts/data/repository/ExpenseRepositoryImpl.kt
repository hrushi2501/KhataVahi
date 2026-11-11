package com.farmeraccounts.data.repository

import androidx.room.withTransaction
import com.farmeraccounts.data.local.db.AppDatabase
import com.farmeraccounts.data.local.db.entity.AuditLogEntity
import com.farmeraccounts.data.local.db.entity.OutboxEntity
import com.farmeraccounts.data.mapper.toDomain
import com.farmeraccounts.data.mapper.toEntity
import com.farmeraccounts.domain.model.Expense
import com.farmeraccounts.domain.repository.ExpenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class ExpenseRepositoryImpl(
    private val database: AppDatabase
) : ExpenseRepository {

    private val expenseDao = database.expenseDao()
    private val auditDao = database.auditLogDao()
    private val outboxDao = database.outboxDao()

    override fun getAllActiveExpenses(): Flow<List<Expense>> {
        return expenseDao.getAllActiveFlow()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    override fun getExpensesForTractor(tractorId: String): Flow<List<Expense>> {
        return expenseDao.getByTractorIdFlow(tractorId)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun getExpenseById(id: String): Expense? {
        return withContext(Dispatchers.IO) {
            expenseDao.getById(id)?.toDomain()
        }
    }

    override suspend fun recordExpense(expense: Expense) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val entity = expense.toEntity().copy(
                    version = 1L,
                    isDeleted = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                expenseDao.insert(entity)

                auditDao.insert(
                    AuditLogEntity(
                        id = UUID.randomUUID().toString(),
                        entityType = "EXPENSE",
                        entityId = entity.id,
                        action = "INSERT",
                        details = "Recorded expense: ${entity.amountPaise} paise (${entity.category})",
                        timestamp = entity.createdAt
                    )
                )

                outboxDao.insert(
                    OutboxEntity(
                        entityType = "EXPENSE",
                        entityId = entity.id,
                        operationType = "INSERT",
                        createdAt = entity.createdAt
                    )
                )
            }
        }
    }

    override suspend fun updateExpense(expense: Expense, reason: String?) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existing = expenseDao.getById(expense.id)
                    ?: throw IllegalArgumentException("Expense with id ${expense.id} not found")

                val updatedEntity = expense.toEntity().copy(
                    version = existing.version + 1L,
                    updatedAt = System.currentTimeMillis(),
                    createdAt = existing.createdAt,
                    isDeleted = existing.isDeleted
                )
                expenseDao.update(updatedEntity)

                auditDao.insert(
                    AuditLogEntity(
                        id = UUID.randomUUID().toString(),
                        entityType = "EXPENSE",
                        entityId = updatedEntity.id,
                        action = "UPDATE",
                        details = reason ?: "Updated expense ${updatedEntity.id}",
                        timestamp = updatedEntity.updatedAt
                    )
                )

                outboxDao.insert(
                    OutboxEntity(
                        entityType = "EXPENSE",
                        entityId = updatedEntity.id,
                        operationType = "UPDATE",
                        createdAt = updatedEntity.updatedAt
                    )
                )
            }
        }
    }

    override suspend fun softDeleteExpense(id: String, reason: String?) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val deletionReason = reason?.trim()
                require(!deletionReason.isNullOrBlank()) { "A deletion reason is required" }
                val existing = expenseDao.getByIdIncludeDeleted(id)
                    ?: throw IllegalArgumentException("Expense with id $id not found")

                if (existing.isDeleted) return@withTransaction

                val deletedEntity = existing.copy(
                    isDeleted = true,
                    version = existing.version + 1L,
                    updatedAt = System.currentTimeMillis()
                )
                expenseDao.update(deletedEntity)

                database.recordDeletion("EXPENSE", id, deletionSnapshot(existing), deletionReason, deletedEntity.updatedAt)

                outboxDao.insert(
                    OutboxEntity(
                        entityType = "EXPENSE",
                        entityId = id,
                        operationType = "SOFT_DELETE",
                        createdAt = deletedEntity.updatedAt
                    )
                )
            }
        }
    }

    override fun getTotalExpensesFlow(): Flow<Long> {
        return expenseDao.getTotalExpensesFlow()
            .flowOn(Dispatchers.IO)
    }
}

