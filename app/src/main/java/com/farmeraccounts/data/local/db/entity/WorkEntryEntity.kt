package com.farmeraccounts.data.local.db.entity

import kotlinx.serialization.Serializable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Serializable
@Entity(
    tableName = "work_entries",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customer_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = TractorEntity::class,
            parentColumns = ["id"],
            childColumns = ["tractor_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["customer_id"]),
        Index(value = ["tractor_id"]),
        Index(value = ["date"]),
        Index(value = ["is_deleted"])
    ]
)
data class WorkEntryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "customer_id")
    val customerId: String,

    @ColumnInfo(name = "tractor_id")
    val tractorId: String,

    @ColumnInfo(name = "work_type")
    val workType: String,

    @ColumnInfo(name = "date")
    val date: Long,

    @ColumnInfo(name = "quantity_scaled")
    val quantityScaled: Long,

    @ColumnInfo(name = "quantity_unit")
    val quantityUnit: String,

    @ColumnInfo(name = "rate_per_unit_paise")
    val ratePerUnitPaise: Long,

    @ColumnInfo(name = "total_paise")
    val totalPaise: Long,

    @ColumnInfo(name = "notes")
    val notes: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "version")
    val version: Long = 1L,

    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false
)

