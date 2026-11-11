package com.farmeraccounts.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "sync_versions", primaryKeys = ["entity_type", "entity_id"])
data class SyncVersionEntity(
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "server_version") val serverVersion: Long
)
