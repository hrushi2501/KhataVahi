package com.farmeraccounts.presentation.customers

import com.farmeraccounts.domain.model.Customer
import com.farmeraccounts.domain.repository.CustomerRepository
import com.farmeraccounts.domain.usecase.AddCustomerUseCase
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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class AddEditCustomerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var oldLocale: Locale

    @Before
    fun setup() {
        oldLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun cleanup() {
        Dispatchers.resetMain()
        Locale.setDefault(oldLocale)
    }

    @Test
    fun `repeated taps during and after local save produce only one write`() = runTest(dispatcher) {
        val repository = SavingRepository()
        val model = AddEditCustomerViewModel(AddCustomerUseCase(repository), repository)
        model.saveCustomer("Ramesh", null, null, null, null) {}
        assertTrue(model.uiState.value.isSaving)
        model.saveCustomer("Ramesh", null, null, null, null) {}
        runCurrent()
        assertEquals(1, repository.writes)
        repository.allowSave.complete(Unit)
        runCurrent()
        assertTrue(model.uiState.value.isSaved)
        assertFalse(model.uiState.value.isSaving)
        model.saveCustomer("Ramesh", null, null, null, null) {}
        runCurrent()
        assertEquals(1, repository.writes)
    }

    @Test
    fun `failed save allows retry and does not expose database error details`() = runTest(dispatcher) {
        val repository = SavingRepository().apply {
            fail = true
            allowSave.complete(Unit)
        }
        val model = AddEditCustomerViewModel(AddCustomerUseCase(repository), repository)
        model.saveCustomer("Ramesh", null, null, null, null) {}
        runCurrent()
        assertFalse(model.uiState.value.isSaving)
        assertFalse(model.uiState.value.isSaved)
        assertNotNull(model.uiState.value.errorMessage)
        assertFalse(model.uiState.value.errorMessage!!.contains("secret"))
        repository.fail = false
        model.saveCustomer("Ramesh", null, null, null, null) {}
        runCurrent()
        assertTrue(model.uiState.value.isSaved)
        assertEquals(2, repository.writes)
    }

    @Test
    fun `missing customer cannot silently save as a new customer`() = runTest(dispatcher) {
        val repository = SavingRepository()
        val model = AddEditCustomerViewModel(AddCustomerUseCase(repository), repository)
        model.loadCustomer("missing-customer")
        runCurrent()
        assertTrue(model.uiState.value.loadFailed)
        model.saveCustomer("Ramesh", null, null, null, null) {}
        runCurrent()
        assertEquals(0, repository.writes)
    }

    @Test
    fun `duplicate customer pauses write until matching draft is explicitly confirmed`() = runTest(dispatcher) {
        val repository = SavingRepository().apply {
            customers = listOf(Customer(id = "existing", name = "Ramesh", phone = "9876543210"))
            allowSave.complete(Unit)
        }
        val model = AddEditCustomerViewModel(AddCustomerUseCase(repository), repository)
        model.saveCustomer("Ramesh", null, null, "Anand", "9876543210") {}
        runCurrent()
        assertEquals(listOf("existing"), model.uiState.value.duplicateCandidates.map { it.id })
        assertEquals(0, repository.writes)
        assertFalse(model.uiState.value.isSaving)
        model.saveCustomer("Ramesh", null, null, "Anand", "9876543210", confirmDuplicate = true) {}
        runCurrent()
        assertEquals(1, repository.writes)
        assertTrue(model.uiState.value.isSaved)
    }

    @Test
    fun `duplicate confirmation cannot approve changed inputs or newly matching accounts`() = runTest(dispatcher) {
        val repository = SavingRepository().apply {
            customers = listOf(Customer(id = "one", name = "Ramesh", phone = "9876543210"))
            allowSave.complete(Unit)
        }
        val model = AddEditCustomerViewModel(AddCustomerUseCase(repository), repository)
        model.saveCustomer("Ramesh", null, null, "Anand", "9876543210") {}
        runCurrent()
        model.saveCustomer("Ramesh", null, null, "Vadodara", "9876543210", confirmDuplicate = true) {}
        runCurrent()
        assertEquals(0, repository.writes)
        assertTrue(model.uiState.value.duplicateCandidates.isNotEmpty())
        repository.customers = repository.customers + Customer(id = "two", name = "Ramesh", phone = "9876543210")
        model.saveCustomer("Ramesh", null, null, "Vadodara", "9876543210", confirmDuplicate = true) {}
        runCurrent()
        assertEquals(0, repository.writes)
        assertEquals(2, model.uiState.value.duplicateCandidates.size)
    }

    @Test
    fun `cancelled warning does not leave an approval to bypass the next warning`() = runTest(dispatcher) {
        val repository = SavingRepository().apply {
            customers = listOf(Customer(id = "existing", name = "Ramesh"))
            allowSave.complete(Unit)
        }
        val model = AddEditCustomerViewModel(AddCustomerUseCase(repository), repository)
        model.saveCustomer("Ramesh", null, null, null, null) {}
        runCurrent()
        model.cancelDuplicateWarning()
        model.saveCustomer("Ramesh", null, null, null, null, confirmDuplicate = true) {}
        runCurrent()
        assertEquals(0, repository.writes)
        assertTrue(model.uiState.value.duplicateCandidates.isNotEmpty())
    }

    @Test
    fun `editing a customer excludes its own account from duplicate checks`() = runTest(dispatcher) {
        val repository = SavingRepository().apply {
            customers = listOf(Customer(id = "self", name = "Ramesh", phone = "9876543210"))
        }
        val model = AddEditCustomerViewModel(AddCustomerUseCase(repository), repository)
        model.loadCustomer("self")
        runCurrent()
        model.saveCustomer("Ramesh", null, null, "Anand", "9876543210") {}
        runCurrent()
        assertEquals(1, repository.updates)
        assertEquals(0, repository.writes)
        assertTrue(model.uiState.value.duplicateCandidates.isEmpty())
        assertTrue(model.uiState.value.isSaved)
    }

    private class SavingRepository : CustomerRepository {
        val allowSave = CompletableDeferred<Unit>()
        var writes = 0
        var fail = false
        var customers: List<Customer> = emptyList()
        var updates = 0
        override fun getAllActiveCustomers(): Flow<List<Customer>> = flowOf(customers)
        override fun searchActiveCustomers(query: String): Flow<List<Customer>> = flowOf(emptyList())
        override suspend fun getCustomerById(id: String): Customer? = customers.find { it.id == id }
        override fun getCustomerByIdFlow(id: String): Flow<Customer?> = flowOf(null)
        override suspend fun addCustomer(customer: Customer) {
            writes++
            allowSave.await()
            if (fail) error("secret database details")
        }
        override suspend fun updateCustomer(customer: Customer, reason: String?) { updates++ }
        override suspend fun softDeleteCustomer(id: String, reason: String?) = error("Not used")
    }
}
