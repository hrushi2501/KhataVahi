package com.farmeraccounts.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CustomerDto(
    @SerialName("id") val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("name") val name: String,
    @SerialName("nickname") val nickname: String? = null,
    @SerialName("father_name") val fatherName: String? = null,
    @SerialName("village") val village: String? = null,
    @SerialName("phone") val phone: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("version") val version: Long = 1L,
    @SerialName("is_deleted") val isDeleted: Boolean = false
)

@Serializable
data class TractorDto(
    @SerialName("id") val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("model") val model: String? = null,
    @SerialName("type") val type: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("version") val version: Long = 1L,
    @SerialName("is_deleted") val isDeleted: Boolean = false
)

@Serializable
data class WorkEntryDto(
    @SerialName("id") val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("tractor_id") val tractorId: String,
    @SerialName("work_type") val workType: String,
    @SerialName("date") val date: String,
    @SerialName("quantity_scaled") val quantityScaled: Long,
    @SerialName("quantity_unit") val quantityUnit: String,
    @SerialName("rate_per_unit_paise") val ratePerUnitPaise: Long,
    @SerialName("total_paise") val totalPaise: Long,
    @SerialName("notes") val notes: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("version") val version: Long = 1L,
    @SerialName("is_deleted") val isDeleted: Boolean = false
)

@Serializable
data class PaymentDto(
    @SerialName("id") val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("work_entry_id") val workEntryId: String? = null,
    @SerialName("amount_paise") val amountPaise: Long,
    @SerialName("payment_date") val paymentDate: String,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("notes") val notes: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("version") val version: Long = 1L,
    @SerialName("is_deleted") val isDeleted: Boolean = false
)

@Serializable
data class ExpenseDto(
    @SerialName("id") val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("tractor_id") val tractorId: String? = null,
    @SerialName("category") val category: String,
    @SerialName("amount_paise") val amountPaise: Long,
    @SerialName("expense_date") val expenseDate: String,
    @SerialName("notes") val notes: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("version") val version: Long = 1L,
    @SerialName("is_deleted") val isDeleted: Boolean = false
)

@Serializable
data class AuditLogDto(
    @SerialName("id") val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("entity_type") val entityType: String,
    @SerialName("entity_id") val entityId: String,
    @SerialName("action") val action: String,
    @SerialName("timestamp") val timestamp: String,
    @SerialName("details") val details: String? = null
)

