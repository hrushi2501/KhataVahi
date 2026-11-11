package com.farmeraccounts.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Frozen request envelope retained until the server receipt is confirmed. */
@Entity(tableName = "upload_attempts")
data class UploadAttemptEntity(
    @PrimaryKey @ColumnInfo(name = "request_id") val requestId: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "expected_server_version") val expectedServerVersion: Long?,
    @ColumnInfo(name = "submitted_version") val submittedVersion: Long,
    @ColumnInfo(name = "sequence_ids_json") val sequenceIdsJson: String
)
