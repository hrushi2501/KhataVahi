package com.farmeraccounts.domain.repository

import com.farmeraccounts.domain.model.Customer
import kotlinx.coroutines.flow.Flow

interface CustomerRepository {
    fun getAllActiveCustomers(): Flow<List<Customer>>
    fun searchActiveCustomers(query: String): Flow<List<Customer>>
    suspend fun getCustomerById(id: String): Customer?
    fun getCustomerByIdFlow(id: String): Flow<Customer?>
    suspend fun addCustomer(customer: Customer)
    suspend fun updateCustomer(customer: Customer, reason: String? = null)
    suspend fun softDeleteCustomer(id: String, reason: String? = null)
}

