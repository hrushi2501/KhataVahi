package com.farmeraccounts.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.farmeraccounts.data.local.db.entity.TractorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TractorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tractor: TractorEntity)

    @Upsert
    suspend fun upsert(tractor: TractorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tractors: List<TractorEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tractors: List<TractorEntity>)

    @Update
    suspend fun update(tractor: TractorEntity)

    @Query("SELECT * FROM tractors WHERE id = :id AND is_deleted = 0 LIMIT 1")
    suspend fun getById(id: String): TractorEntity?

    @Query("SELECT * FROM tractors WHERE id = :id LIMIT 1")
    suspend fun getByIdIncludeDeleted(id: String): TractorEntity?

    @Query("SELECT * FROM tractors WHERE is_deleted = 0 ORDER BY display_name ASC")
    fun getAllActiveFlow(): Flow<List<TractorEntity>>

    @Query("SELECT * FROM tractors WHERE is_deleted = 0 ORDER BY display_name ASC")
    suspend fun getAllActive(): List<TractorEntity>

    @Query("SELECT * FROM tractors WHERE type = :type AND is_deleted = 0 LIMIT 1")
    suspend fun getByType(type: String): TractorEntity?

    @Query("SELECT COUNT(*) FROM tractors WHERE is_deleted = 0")
    suspend fun getCount(): Int
}

