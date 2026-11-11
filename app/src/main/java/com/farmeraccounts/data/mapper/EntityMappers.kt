package com.farmeraccounts.data.mapper

import com.farmeraccounts.data.local.db.entity.CustomerEntity
import com.farmeraccounts.data.local.db.entity.ExpenseEntity
import com.farmeraccounts.data.local.db.entity.PaymentEntity
import com.farmeraccounts.data.local.db.entity.TractorEntity
import com.farmeraccounts.data.local.db.entity.WorkEntryEntity
import com.farmeraccounts.domain.model.Customer
import com.farmeraccounts.domain.model.Expense
import com.farmeraccounts.domain.model.ExpenseCategory
import com.farmeraccounts.domain.model.Payment
import com.farmeraccounts.domain.model.PaymentMethod
import com.farmeraccounts.domain.model.QuantityUnit
import com.farmeraccounts.domain.model.Tractor
import com.farmeraccounts.domain.model.TractorType
import com.farmeraccounts.domain.model.WorkEntry

fun CustomerEntity.toDomain(): Customer = Customer(
    id = id,
    name = name,
    nickname = nickname,
    fatherName = fatherName,
    village = village,
    phone = phone,
    createdAt = createdAt,
    updatedAt = updatedAt,
    version = version,
    isDeleted = isDeleted
)

fun Customer.toEntity(): CustomerEntity = CustomerEntity(
    id = id,
    name = name,
    nickname = nickname,
    fatherName = fatherName,
    village = village,
    phone = phone,
    createdAt = createdAt,
    updatedAt = updatedAt,
    version = version,
    isDeleted = isDeleted
)

fun TractorEntity.toDomain(): Tractor = Tractor(
    id = id,
    displayName = displayName,
    model = model,
    type = TractorType.fromString(type),
    createdAt = createdAt,
    updatedAt = updatedAt,
    version = version,
    isDeleted = isDeleted
)

fun Tractor.toEntity(): TractorEntity = TractorEntity(
    id = id,
    displayName = displayName,
    model = model,
    type = type.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    version = version,
    isDeleted = isDeleted
)

fun WorkEntryEntity.toDomain(): WorkEntry = WorkEntry(
    id = id,
    customerId = customerId,
    tractorId = tractorId,
    workType = workType,
    date = date,
    quantityScaled = quantityScaled,
    quantityUnit = QuantityUnit.fromString(quantityUnit),
    ratePerUnitPaise = ratePerUnitPaise,
    totalPaise = totalPaise,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    version = version,
    isDeleted = isDeleted
)

fun WorkEntry.toEntity(): WorkEntryEntity = WorkEntryEntity(
    id = id,
    customerId = customerId,
    tractorId = tractorId,
    workType = workType,
    date = date,
    quantityScaled = quantityScaled,
    quantityUnit = quantityUnit.name,
    ratePerUnitPaise = ratePerUnitPaise,
    totalPaise = totalPaise,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    version = version,
    isDeleted = isDeleted
)

fun PaymentEntity.toDomain(): Payment = Payment(
    id = id,
    customerId = customerId,
    workEntryId = workEntryId,
    amountPaise = amountPaise,
    paymentDate = paymentDate,
    paymentMethod = PaymentMethod.fromString(paymentMethod),
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    version = version,
    isDeleted = isDeleted
)

fun Payment.toEntity(): PaymentEntity = PaymentEntity(
    id = id,
    customerId = customerId,
    workEntryId = workEntryId,
    amountPaise = amountPaise,
    paymentDate = paymentDate,
    paymentMethod = paymentMethod.name,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    version = version,
    isDeleted = isDeleted
)

fun ExpenseEntity.toDomain(): Expense = Expense(
    id = id,
    tractorId = tractorId,
    category = ExpenseCategory.fromString(category),
    amountPaise = amountPaise,
    expenseDate = expenseDate,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    version = version,
    isDeleted = isDeleted
)

fun Expense.toEntity(): ExpenseEntity = ExpenseEntity(
    id = id,
    tractorId = tractorId,
    category = category.name,
    amountPaise = amountPaise,
    expenseDate = expenseDate,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    version = version,
    isDeleted = isDeleted
)

