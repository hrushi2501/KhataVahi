package com.farmeraccounts.domain.repository

import com.farmeraccounts.domain.model.Payment
import com.farmeraccounts.domain.model.WorkEntry
import kotlinx.coroutines.flow.Flow

interface WorkRepository {
    fun getAllActiveWorkEntries(): Flow<List<WorkEntry>>
    fun getWorkEntriesForCustomer(customerId: String): Flow<List<WorkEntry>>
    suspend fun getWorkEntryById(id: String): WorkEntry?

    suspend fun addWorkEntry(workEntry: WorkEntry)
    suspend fun addWorkEntryWithInitialPayment(workEntry: WorkEntry, initialPayment: Payment)
    suspend fun updateWorkEntry(workEntry: WorkEntry, reason: String? = null)
    suspend fun softDeleteWorkEntry(id: String, reason: String? = null)

    fun getTotalWorkRevenueFlow(): Flow<Long>
    suspend fun getTotalWorkPaiseForCustomer(customerId: String): Long
}

