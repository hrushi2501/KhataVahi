package com.farmeraccounts.data.local.db.entity

import kotlinx.serialization.Serializable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Serializable
@Entity(
    tableName = "tractors",
    indices = [
        Index(value = ["type"]),
        Index(value = ["is_deleted"])
    ]
)
data class TractorEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "model")
    val model: String? = null,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "version")
    val version: Long = 1L,

    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false
)

