package com.farmeraccounts.domain.model

data class Expense(
    val id: String,
    val tractorId: String? = null,
    val category: ExpenseCategory,
    val amountPaise: Long,
    val expenseDate: Long,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Long = 1L,
    val isDeleted: Boolean = false
)

