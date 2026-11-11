package com.farmeraccounts.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.farmeraccounts.data.local.db.entity.PaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {

    @Query("SELECT EXISTS(SELECT 1 FROM payments WHERE customer_id = :customerId AND is_deleted = 0)")
    suspend fun hasActiveForCustomer(customerId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(payment: PaymentEntity)

    @Upsert
    suspend fun upsert(payment: PaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(payments: List<PaymentEntity>)

    @Update
    suspend fun update(payment: PaymentEntity)

    @Query("SELECT * FROM payments WHERE id = :id AND is_deleted = 0 LIMIT 1")
    suspend fun getById(id: String): PaymentEntity?

    @Query("SELECT * FROM payments WHERE id = :id LIMIT 1")
    suspend fun getByIdIncludeDeleted(id: String): PaymentEntity?

    @Query("SELECT * FROM payments WHERE customer_id = :customerId AND is_deleted = 0 ORDER BY payment_date DESC, created_at DESC")
    fun getByCustomerIdFlow(customerId: String): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE customer_id = :customerId AND is_deleted = 0 ORDER BY payment_date ASC, created_at ASC")
    suspend fun getByCustomerId(customerId: String): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE work_entry_id = :workEntryId AND is_deleted = 0")
    suspend fun getByWorkEntryId(workEntryId: String): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE is_deleted = 0 ORDER BY payment_date DESC, created_at DESC")
    fun getAllActiveFlow(): Flow<List<PaymentEntity>>

    @Query("SELECT COALESCE(SUM(amount_paise), 0) FROM payments WHERE customer_id = :customerId AND is_deleted = 0")
    suspend fun getTotalPaidPaiseForCustomer(customerId: String): Long

    @Query("SELECT COALESCE(SUM(amount_paise), 0) FROM payments WHERE customer_id = :customerId AND is_deleted = 0")
    fun getTotalPaidPaiseForCustomerFlow(customerId: String): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount_paise), 0) FROM payments WHERE is_deleted = 0")
    suspend fun getTotalPaymentsCollected(): Long

    @Query("SELECT COALESCE(SUM(amount_paise), 0) FROM payments WHERE is_deleted = 0")
    fun getTotalPaymentsCollectedFlow(): Flow<Long>

    @Query("SELECT COUNT(*) FROM payments WHERE is_deleted = 0")
    suspend fun getActiveCount(): Int
}

