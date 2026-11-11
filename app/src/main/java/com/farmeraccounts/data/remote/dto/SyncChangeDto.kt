package com.farmeraccounts.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SyncChangeDto(
    @SerialName("sequence_id") val sequenceId: Long,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("table_name") val tableName: String,
    @SerialName("record_id") val recordId: String,
    @SerialName("operation") val operation: String
)
