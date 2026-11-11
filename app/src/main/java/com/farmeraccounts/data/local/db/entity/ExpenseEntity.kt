package com.farmeraccounts.data.local.db.entity

import kotlinx.serialization.Serializable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Serializable
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = TractorEntity::class,
            parentColumns = ["id"],
            childColumns = ["tractor_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["tractor_id"]),
        Index(value = ["expense_date"]),
        Index(value = ["category"]),
        Index(value = ["is_deleted"])
    ]
)
data class ExpenseEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "tractor_id")
    val tractorId: String? = null,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "amount_paise")
    val amountPaise: Long,

    @ColumnInfo(name = "expense_date")
    val expenseDate: Long,

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

