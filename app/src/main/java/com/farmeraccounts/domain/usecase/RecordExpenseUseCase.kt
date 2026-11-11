package com.farmeraccounts.domain.usecase

import com.farmeraccounts.domain.model.Expense
import com.farmeraccounts.domain.model.ExpenseCategory
import com.farmeraccounts.domain.repository.ExpenseRepository
import java.util.UUID

class RecordExpenseUseCase(
    private val expenseRepository: ExpenseRepository
) {
    suspend operator fun invoke(
        category: ExpenseCategory,
        amountPaise: Long,
        expenseDate: Long = System.currentTimeMillis(),
        tractorId: String? = null,
        notes: String? = null
    ): Expense {
        require(amountPaise > 0L) { "Expense amount must be greater than zero" }

        val expense = Expense(
            id = UUID.randomUUID().toString(),
            tractorId = tractorId,
            category = category,
            amountPaise = amountPaise,
            expenseDate = expenseDate,
            notes = notes?.trim()?.ifEmpty { null }
        )
        expenseRepository.recordExpense(expense)
        return expense
    }
}

