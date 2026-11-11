package com.farmeraccounts.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.farmeraccounts.data.local.db.entity.UploadAttemptEntity

@Dao
interface UploadAttemptDao {
    @Query("SELECT * FROM upload_attempts ORDER BY request_id ASC")
    suspend fun getAll(): List<UploadAttemptEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(attempt: UploadAttemptEntity)

    @Query("DELETE FROM upload_attempts WHERE request_id = :requestId")
    suspend fun delete(requestId: String)
}
