package com.farmeraccounts.domain.repository

import com.farmeraccounts.domain.model.Payment
import kotlinx.coroutines.flow.Flow

interface PaymentRepository {
    fun getAllActivePayments(): Flow<List<Payment>>
    fun getPaymentsForCustomer(customerId: String): Flow<List<Payment>>
    suspend fun getPaymentById(id: String): Payment?
    suspend fun recordPayment(payment: Payment)
    suspend fun updatePayment(payment: Payment, reason: String? = null)
    suspend fun softDeletePayment(id: String, reason: String? = null)

    fun getTotalPaymentsCollectedFlow(): Flow<Long>
    suspend fun getTotalPaidPaiseForCustomer(customerId: String): Long
}

