package com.farmeraccounts.data.local.db.entity

import kotlinx.serialization.Serializable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Serializable
@Entity(
    tableName = "customers",
    indices = [
        Index(value = ["name"]),
        Index(value = ["village"]),
        Index(value = ["phone"]),
        Index(value = ["is_deleted"])
    ]
)
data class CustomerEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "nickname")
    val nickname: String? = null,

    @ColumnInfo(name = "father_name")
    val fatherName: String? = null,

    @ColumnInfo(name = "village")
    val village: String? = null,

    @ColumnInfo(name = "phone")
    val phone: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "version")
    val version: Long = 1L,

    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false
)

