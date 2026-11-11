package com.farmeraccounts.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Durable outbox entry for offline-first synchronization.
 * Outbox records are inserted within the same Room transaction as their corresponding data changes.
 */
@Entity(
    tableName = "outbox",
    indices = [
        Index(value = ["sync_status"]),
        Index(value = ["created_at"])
    ]
)
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "sequence_id")
    val sequenceId: Long = 0L,

    @ColumnInfo(name = "entity_type")
    val entityType: String,

    @ColumnInfo(name = "entity_id")
    val entityId: String,

    @ColumnInfo(name = "operation_type")
    val operationType: String, // INSERT, UPDATE, SOFT_DELETE

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "PENDING", // PENDING, IN_PROGRESS, CONFIRMED, FAILED

    @ColumnInfo(name = "last_error")
    val lastError: String? = null
)

