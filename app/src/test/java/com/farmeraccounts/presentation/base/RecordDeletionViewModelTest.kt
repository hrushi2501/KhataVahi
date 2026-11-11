package com.farmeraccounts.presentation.base

import com.farmeraccounts.domain.model.Customer
import com.farmeraccounts.domain.repository.CustomerRepository
import com.farmeraccounts.domain.repository.WorkRepository
import com.farmeraccounts.domain.repository.PaymentRepository
import com.farmeraccounts.domain.repository.ExpenseRepository
import com.farmeraccounts.domain.usecase.CorrectEntryUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class RecordDeletionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test
    fun `reason is required and repeated confirmation performs only one removal`() = runTest(dispatcher) {
        val repository = DeletingCustomers()
        val model = model(repository)
        model.remove(RecordKind.CUSTOMER, "customer", " ")
        runCurrent()
        assertEquals(0, repository.calls)
        model.remove(RecordKind.CUSTOMER, "customer", "Duplicate account")
        model.remove(RecordKind.CUSTOMER, "customer", "Duplicate account")
        runCurrent()
        assertTrue(model.state.value.isDeleting)
        assertEquals(1, repository.calls)
        repository.release.complete(Unit)
        runCurrent()
        assertEquals("customer", model.state.value.deletedId)
        model.remove(RecordKind.CUSTOMER, "customer", "Duplicate account")
        runCurrent()
        assertEquals(1, repository.calls)
    }

    @Test
    fun `guard failure remains retryable and never reports a deletion as successful`() = runTest(dispatcher) {
        val repository = DeletingCustomers().apply { fail = true; release.complete(Unit) }
        val model = model(repository)
        model.remove(RecordKind.CUSTOMER, "customer", "Correction")
        runCurrent()
        assertTrue(model.state.value.hasError)
        assertNull(model.state.value.deletedId)
        assertFalse(model.state.value.isDeleting)
        repository.fail = false
        model.remove(RecordKind.CUSTOMER, "customer", "Correction")
        runCurrent()
        assertEquals("customer", model.state.value.deletedId)
        assertEquals(2, repository.calls)
    }

    private fun model(customers: CustomerRepository) = RecordDeletionViewModel(
        CorrectEntryUseCase(customers, unused<WorkRepository>(), unused<PaymentRepository>(), unused<ExpenseRepository>())
    )

    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        error("Unexpected repository call: ${method.name}")
    } as T

    private class DeletingCustomers : CustomerRepository {
        var calls = 0
        var fail = false
        val release = CompletableDeferred<Unit>()
        override fun getAllActiveCustomers(): Flow<List<Customer>> = flowOf(emptyList())
        override fun searchActiveCustomers(query: String): Flow<List<Customer>> = flowOf(emptyList())
        override suspend fun getCustomerById(id: String): Customer? = null
        override fun getCustomerByIdFlow(id: String): Flow<Customer?> = flowOf(null)
        override suspend fun addCustomer(customer: Customer) = error("Not used")
        override suspend fun updateCustomer(customer: Customer, reason: String?) = error("Not used")
        override suspend fun softDeleteCustomer(id: String, reason: String?) {
            calls++
            release.await()
            if (fail) error("Customer has active work")
        }
    }
}
