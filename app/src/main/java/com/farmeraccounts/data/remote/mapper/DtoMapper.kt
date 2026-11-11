package com.farmeraccounts.data.remote.mapper

import com.farmeraccounts.data.local.db.entity.AuditLogEntity
import com.farmeraccounts.data.local.db.entity.CustomerEntity
import com.farmeraccounts.data.local.db.entity.ExpenseEntity
import com.farmeraccounts.data.local.db.entity.PaymentEntity
import com.farmeraccounts.data.local.db.entity.TractorEntity
import com.farmeraccounts.data.local.db.entity.WorkEntryEntity
import com.farmeraccounts.data.remote.dto.AuditLogDto
import com.farmeraccounts.data.remote.dto.CustomerDto
import com.farmeraccounts.data.remote.dto.ExpenseDto
import com.farmeraccounts.data.remote.dto.PaymentDto
import com.farmeraccounts.data.remote.dto.TractorDto
import com.farmeraccounts.data.remote.dto.WorkEntryDto

fun CustomerEntity.toDto(ownerId: String): CustomerDto {
    return CustomerDto(
        id = id,
        ownerId = ownerId,
        name = name,
        nickname = nickname,
        fatherName = fatherName,
        village = village,
        phone = phone,
        createdAt = TimestampIsoConverter.toIsoString(createdAt),
        updatedAt = TimestampIsoConverter.toIsoString(updatedAt),
        version = version,
        isDeleted = isDeleted
    )
}

fun CustomerDto.toEntity(): CustomerEntity {
    return CustomerEntity(
        id = id,
        name = name,
        nickname = nickname,
        fatherName = fatherName,
        village = village,
        phone = phone,
        createdAt = TimestampIsoConverter.toEpochMillis(createdAt),
        updatedAt = TimestampIsoConverter.toEpochMillis(updatedAt),
        version = version,
        isDeleted = isDeleted
    )
}

fun TractorEntity.toDto(ownerId: String): TractorDto {
    return TractorDto(
        id = id,
        ownerId = ownerId,
        displayName = displayName,
        model = model,
        type = type,
        createdAt = TimestampIsoConverter.toIsoString(createdAt),
        updatedAt = TimestampIsoConverter.toIsoString(updatedAt),
        version = version,
        isDeleted = isDeleted
    )
}

fun TractorDto.toEntity(): TractorEntity {
    return TractorEntity(
        id = id,
        displayName = displayName,
        model = model,
        type = type,
        createdAt = TimestampIsoConverter.toEpochMillis(createdAt),
        updatedAt = TimestampIsoConverter.toEpochMillis(updatedAt),
        version = version,
        isDeleted = isDeleted
    )
}

fun WorkEntryEntity.toDto(ownerId: String): WorkEntryDto {
    return WorkEntryDto(
        id = id,
        ownerId = ownerId,
        customerId = customerId,
        tractorId = tractorId,
        workType = workType,
        date = TimestampIsoConverter.toIsoString(date),
        quantityScaled = quantityScaled,
        quantityUnit = quantityUnit,
        ratePerUnitPaise = ratePerUnitPaise,
        totalPaise = totalPaise,
        notes = notes,
        createdAt = TimestampIsoConverter.toIsoString(createdAt),
        updatedAt = TimestampIsoConverter.toIsoString(updatedAt),
        version = version,
        isDeleted = isDeleted
    )
}

fun WorkEntryDto.toEntity(): WorkEntryEntity {
    return WorkEntryEntity(
        id = id,
        customerId = customerId,
        tractorId = tractorId,
        workType = workType,
        date = TimestampIsoConverter.toEpochMillis(date),
        quantityScaled = quantityScaled,
        quantityUnit = quantityUnit,
        ratePerUnitPaise = ratePerUnitPaise,
        totalPaise = totalPaise,
        notes = notes,
        createdAt = TimestampIsoConverter.toEpochMillis(createdAt),
        updatedAt = TimestampIsoConverter.toEpochMillis(updatedAt),
        version = version,
        isDeleted = isDeleted
    )
}

fun PaymentEntity.toDto(ownerId: String): PaymentDto {
    return PaymentDto(
        id = id,
        ownerId = ownerId,
        customerId = customerId,
        workEntryId = workEntryId,
        amountPaise = amountPaise,
        paymentDate = TimestampIsoConverter.toIsoString(paymentDate),
        paymentMethod = paymentMethod,
        notes = notes,
        createdAt = TimestampIsoConverter.toIsoString(createdAt),
        updatedAt = TimestampIsoConverter.toIsoString(updatedAt),
        version = version,
        isDeleted = isDeleted
    )
}

fun PaymentDto.toEntity(): PaymentEntity {
    return PaymentEntity(
        id = id,
        customerId = customerId,
        workEntryId = workEntryId,
        amountPaise = amountPaise,
        paymentDate = TimestampIsoConverter.toEpochMillis(paymentDate),
        paymentMethod = paymentMethod,
        notes = notes,
        createdAt = TimestampIsoConverter.toEpochMillis(createdAt),
        updatedAt = TimestampIsoConverter.toEpochMillis(updatedAt),
        version = version,
        isDeleted = isDeleted
    )
}

fun ExpenseEntity.toDto(ownerId: String): ExpenseDto {
    return ExpenseDto(
        id = id,
        ownerId = ownerId,
        tractorId = tractorId,
        category = category,
        amountPaise = amountPaise,
        expenseDate = TimestampIsoConverter.toIsoString(expenseDate),
        notes = notes,
        createdAt = TimestampIsoConverter.toIsoString(createdAt),
        updatedAt = TimestampIsoConverter.toIsoString(updatedAt),
        version = version,
        isDeleted = isDeleted
    )
}

fun ExpenseDto.toEntity(): ExpenseEntity {
    return ExpenseEntity(
        id = id,
        tractorId = tractorId,
        category = category,
        amountPaise = amountPaise,
        expenseDate = TimestampIsoConverter.toEpochMillis(expenseDate),
        notes = notes,
        createdAt = TimestampIsoConverter.toEpochMillis(createdAt),
        updatedAt = TimestampIsoConverter.toEpochMillis(updatedAt),
        version = version,
        isDeleted = isDeleted
    )
}

fun AuditLogEntity.toDto(ownerId: String): AuditLogDto {
    return AuditLogDto(
        id = id,
        ownerId = ownerId,
        entityType = entityType,
        entityId = entityId,
        action = action,
        timestamp = TimestampIsoConverter.toIsoString(timestamp),
        details = details
    )
}

fun AuditLogDto.toEntity(): AuditLogEntity {
    return AuditLogEntity(
        id = id,
        entityType = entityType,
        entityId = entityId,
        action = action,
        timestamp = TimestampIsoConverter.toEpochMillis(timestamp),
        details = details
    )
}

