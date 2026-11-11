package com.farmeraccounts.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Provisional deletion snapshot until confirmed by the immutable server history. */
@Entity(tableName = "deleted_logs", indices = [Index(value = ["timestamp"]), Index(value = ["entity_type", "entity_id"])])
data class DeletedLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "snapshot_json") val snapshotJson: String,
    val reason: String,
    val timestamp: Long
)
