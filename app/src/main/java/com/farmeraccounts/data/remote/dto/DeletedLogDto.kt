package com.farmeraccounts.data.remote.dto

import com.farmeraccounts.data.local.db.entity.DeletedLogEntity
import com.farmeraccounts.data.remote.mapper.TimestampIsoConverter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeletedLogDto(
    @SerialName("id") val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("entity_type") val entityType: String,
    @SerialName("entity_id") val entityId: String,
    @SerialName("snapshot_json") val snapshotJson: String,
    @SerialName("reason") val reason: String,
    @SerialName("timestamp") val timestamp: String
) {
    fun toEntity() = DeletedLogEntity(
        id = id,
        entityType = entityType,
        entityId = entityId,
        snapshotJson = snapshotJson,
        reason = reason,
        timestamp = TimestampIsoConverter.toEpochMillis(timestamp)
    )
}
