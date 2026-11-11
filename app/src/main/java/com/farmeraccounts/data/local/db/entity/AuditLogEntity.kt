package com.farmeraccounts.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Immutable audit trail record.
 * Per project rules:
 * - Local SQLite/Room is single source of truth.
 * - All financial edits and corrections maintain an auditable history.
 * - UUIDs are locally generated.
 */
@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "entity_type")
    val entityType: String,

    @ColumnInfo(name = "entity_id")
    val entityId: String,

    @ColumnInfo(name = "action")
    val action: String,

    @ColumnInfo(name = "details")
    val details: String? = null,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

