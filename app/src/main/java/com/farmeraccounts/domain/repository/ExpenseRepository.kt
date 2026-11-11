package com.farmeraccounts.domain.repository

import com.farmeraccounts.domain.model.Expense
import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    fun getAllActiveExpenses(): Flow<List<Expense>>
    fun getExpensesForTractor(tractorId: String): Flow<List<Expense>>
    suspend fun getExpenseById(id: String): Expense?
    suspend fun recordExpense(expense: Expense)
    suspend fun updateExpense(expense: Expense, reason: String? = null)
    suspend fun softDeleteExpense(id: String, reason: String? = null)
    fun getTotalExpensesFlow(): Flow<Long>
}

