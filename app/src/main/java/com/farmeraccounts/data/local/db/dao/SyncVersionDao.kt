package com.farmeraccounts.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.farmeraccounts.data.local.db.entity.SyncVersionEntity

@Dao
interface SyncVersionDao {
    @Query("SELECT server_version FROM sync_versions WHERE entity_type = :entityType AND entity_id = :entityId")
    suspend fun get(entityType: String, entityId: String): Long?

    @Upsert
    suspend fun set(version: SyncVersionEntity)
}
