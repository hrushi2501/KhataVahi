package com.farmeraccounts.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.farmeraccounts.data.local.db.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(customer: CustomerEntity)

    @Upsert
    suspend fun upsert(customer: CustomerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(customers: List<CustomerEntity>)

    @Update
    suspend fun update(customer: CustomerEntity)

    @Query("SELECT * FROM customers WHERE id = :id AND is_deleted = 0 LIMIT 1")
    suspend fun getById(id: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getByIdIncludeDeleted(id: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE id = :id AND is_deleted = 0 LIMIT 1")
    fun getByIdFlow(id: String): Flow<CustomerEntity?>

    @Query("SELECT * FROM customers WHERE is_deleted = 0 ORDER BY name ASC")
    fun getAllActiveFlow(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE is_deleted = 0 ORDER BY name ASC")
    suspend fun getAllActive(): List<CustomerEntity>

    @Query("""
        SELECT * FROM customers 
        WHERE is_deleted = 0 
          AND (name LIKE '%' || :query || '%' 
               OR nickname LIKE '%' || :query || '%' 
               OR village LIKE '%' || :query || '%' 
               OR phone LIKE '%' || :query || '%')
        ORDER BY name ASC
    """)
    fun searchActiveFlow(query: String): Flow<List<CustomerEntity>>

    @Query("SELECT COUNT(*) FROM customers WHERE is_deleted = 0")
    suspend fun getActiveCount(): Int
}

