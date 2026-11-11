package com.farmeraccounts.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.farmeraccounts.data.local.db.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(expense: ExpenseEntity)

    @Upsert
    suspend fun upsert(expense: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(expenses: List<ExpenseEntity>)

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE id = :id AND is_deleted = 0 LIMIT 1")
    suspend fun getById(id: String): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getByIdIncludeDeleted(id: String): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE is_deleted = 0 ORDER BY expense_date DESC, created_at DESC")
    fun getAllActiveFlow(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE tractor_id = :tractorId AND is_deleted = 0 ORDER BY expense_date DESC, created_at DESC")
    fun getByTractorIdFlow(tractorId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT COALESCE(SUM(amount_paise), 0) FROM expenses WHERE is_deleted = 0")
    suspend fun getTotalExpenses(): Long

    @Query("SELECT COALESCE(SUM(amount_paise), 0) FROM expenses WHERE is_deleted = 0")
    fun getTotalExpensesFlow(): Flow<Long>

    @Query("SELECT COUNT(*) FROM expenses WHERE is_deleted = 0")
    suspend fun getActiveCount(): Int
}

