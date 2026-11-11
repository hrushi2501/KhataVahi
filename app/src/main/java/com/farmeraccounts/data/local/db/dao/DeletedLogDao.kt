package com.farmeraccounts.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.farmeraccounts.data.local.db.entity.DeletedLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeletedLogDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(log: DeletedLogEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(logs: List<DeletedLogEntity>)

    @Query("SELECT * FROM deleted_logs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DeletedLogEntity?

    @Query("SELECT * FROM deleted_logs WHERE entity_type = :entityType AND entity_id = :entityId ORDER BY timestamp DESC, id DESC LIMIT 1")
    suspend fun getLatestForEntity(entityType: String, entityId: String): DeletedLogEntity?

    /** Trusted server projection only: replace the provisional offline snapshot with confirmed OLD. */
    @Upsert
    suspend fun upsertConfirmed(log: DeletedLogEntity)

    @Query("SELECT * FROM deleted_logs ORDER BY timestamp DESC, id DESC")
    fun getAllFlow(): Flow<List<DeletedLogEntity>>
}
