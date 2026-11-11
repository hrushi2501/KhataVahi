package com.farmeraccounts.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.farmeraccounts.data.local.db.entity.WorkEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkEntryDao {

    @Query("SELECT EXISTS(SELECT 1 FROM work_entries WHERE customer_id = :customerId AND is_deleted = 0)")
    suspend fun hasActiveForCustomer(customerId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(workEntry: WorkEntryEntity)

    @Upsert
    suspend fun upsert(workEntry: WorkEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(workEntries: List<WorkEntryEntity>)

    @Update
    suspend fun update(workEntry: WorkEntryEntity)

    @Query("SELECT * FROM work_entries WHERE id = :id AND is_deleted = 0 LIMIT 1")
    suspend fun getById(id: String): WorkEntryEntity?

    @Query("SELECT * FROM work_entries WHERE id = :id LIMIT 1")
    suspend fun getByIdIncludeDeleted(id: String): WorkEntryEntity?

    @Query("SELECT * FROM work_entries WHERE customer_id = :customerId AND is_deleted = 0 ORDER BY date DESC, created_at DESC")
    fun getByCustomerIdFlow(customerId: String): Flow<List<WorkEntryEntity>>

    @Query("SELECT * FROM work_entries WHERE customer_id = :customerId AND is_deleted = 0 ORDER BY date ASC, created_at ASC")
    suspend fun getByCustomerId(customerId: String): List<WorkEntryEntity>

    @Query("SELECT * FROM work_entries WHERE is_deleted = 0 ORDER BY date DESC, created_at DESC")
    fun getAllActiveFlow(): Flow<List<WorkEntryEntity>>

    @Query("SELECT * FROM work_entries WHERE is_deleted = 0 ORDER BY date DESC, created_at DESC")
    suspend fun getAllActive(): List<WorkEntryEntity>

    @Query("SELECT COALESCE(SUM(total_paise), 0) FROM work_entries WHERE customer_id = :customerId AND is_deleted = 0")
    suspend fun getTotalWorkPaiseForCustomer(customerId: String): Long

    @Query("SELECT COALESCE(SUM(total_paise), 0) FROM work_entries WHERE customer_id = :customerId AND is_deleted = 0")
    fun getTotalWorkPaiseForCustomerFlow(customerId: String): Flow<Long>

    @Query("SELECT COALESCE(SUM(total_paise), 0) FROM work_entries WHERE is_deleted = 0")
    suspend fun getTotalWorkRevenue(): Long

    @Query("SELECT COALESCE(SUM(total_paise), 0) FROM work_entries WHERE is_deleted = 0")
    fun getTotalWorkRevenueFlow(): Flow<Long>

    @Query("SELECT COUNT(*) FROM work_entries WHERE is_deleted = 0")
    suspend fun getActiveCount(): Int
}

