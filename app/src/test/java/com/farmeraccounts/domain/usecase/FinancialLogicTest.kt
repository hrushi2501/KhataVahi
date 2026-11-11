package com.farmeraccounts.domain.usecase

import com.farmeraccounts.core.util.ScaledQuantity
import com.farmeraccounts.domain.model.Customer
import com.farmeraccounts.domain.model.Expense
import com.farmeraccounts.domain.model.ExpenseCategory
import com.farmeraccounts.domain.model.Payment
import com.farmeraccounts.domain.model.PaymentMethod
import com.farmeraccounts.domain.model.QuantityUnit
import com.farmeraccounts.domain.model.WorkEntry
import com.farmeraccounts.domain.repository.CustomerRepository
import com.farmeraccounts.domain.repository.ExpenseRepository
import com.farmeraccounts.domain.repository.PaymentRepository
import com.farmeraccounts.domain.repository.TractorRepository
import com.farmeraccounts.domain.repository.WorkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class FinancialLogicTest {

    private lateinit var customerRepository: FakeCustomerRepository
    private lateinit var workRepository: FakeWorkRepository
    private lateinit var paymentRepository: FakePaymentRepository
    private lateinit var expenseRepository: FakeExpenseRepository

    private lateinit var calculateCustomerBalanceUseCase: CalculateCustomerBalanceUseCase
    private lateinit var calculateDashboardTotalsUseCase: CalculateDashboardTotalsUseCase
    private lateinit var getCustomerLedgerUseCase: GetCustomerLedgerUseCase

    @Before
    fun setup() {
        customerRepository = FakeCustomerRepository()
        workRepository = FakeWorkRepository()
        paymentRepository = FakePaymentRepository()
        expenseRepository = FakeExpenseRepository()

        calculateCustomerBalanceUseCase = CalculateCustomerBalanceUseCase(
            workRepository = workRepository,
            paymentRepository = paymentRepository
        )

        calculateDashboardTotalsUseCase = CalculateDashboardTotalsUseCase(
            customerRepository = customerRepository,
            workRepository = workRepository,
            paymentRepository = paymentRepository,
            expenseRepository = expenseRepository
        )

        getCustomerLedgerUseCase = GetCustomerLedgerUseCase(
            customerRepository = customerRepository,
            workRepository = workRepository,
            paymentRepository = paymentRepository
        )
    }

    @Test
    fun `partial payment leaves remaining outstanding balance`() = runBlocking {
        val customer = createSampleCustomer("Ramesh Patel")
        customerRepository.addCustomer(customer)

        // Work: 5.0 Bigha at ₹600.00 = ₹3,000.00 (300,000 paise)
        val totalPaise = ScaledQuantity.calculateTotalPaise(5000L, 60000L)
        val work = WorkEntry(
            id = UUID.randomUUID().toString(),
            customerId = customer.id,
            tractorId = "tractor-1",
            workType = "Plowing",
            date = 1000L,
            quantityScaled = 5000L,
            quantityUnit = QuantityUnit.BIGHA,
            ratePerUnitPaise = 60000L,
            totalPaise = totalPaise
        )
        workRepository.addWorkEntry(work)

        // Partial payment: ₹1,000.00 (100,000 paise)
        val partialPayment = Payment(
            id = UUID.randomUUID().toString(),
            customerId = customer.id,
            workEntryId = work.id,
            amountPaise = 100000L,
            paymentDate = 2000L,
            paymentMethod = PaymentMethod.CASH
        )
        paymentRepository.recordPayment(partialPayment)

        val balance = calculateCustomerBalanceUseCase(customer.id)
        assertEquals(300000L, balance.totalWorkPaise)
        assertEquals(100000L, balance.totalPaidPaise)
        assertEquals(200000L, balance.outstandingPaise)
        assertTrue(balance.isDue)
        assertFalse(balance.isAdvance)
        assertFalse(balance.isSettled)
    }

    @Test
    fun `multiple jobs per customer aggregate correctly into outstanding balance`() = runBlocking {
        val customer = createSampleCustomer("Hareshbhai")
        customerRepository.addCustomer(customer)

        // Job 1: 3 Bigha Plowing = ₹1,800.00 (180,000 paise)
        workRepository.addWorkEntry(
            WorkEntry(
                id = UUID.randomUUID().toString(),
                customerId = customer.id,
                tractorId = "tractor-1",
                workType = "Plowing",
                date = 1000L,
                quantityScaled = 3000L,
                quantityUnit = QuantityUnit.BIGHA,
                ratePerUnitPaise = 60000L,
                totalPaise = 180000L
            )
        )

        // Job 2: 2 Hours Rotavator = ₹2,400.00 (240,000 paise)
        workRepository.addWorkEntry(
            WorkEntry(
                id = UUID.randomUUID().toString(),
                customerId = customer.id,
                tractorId = "tractor-1",
                workType = "Rotavator",
                date = 2000L,
                quantityScaled = 2000L,
                quantityUnit = QuantityUnit.HOUR,
                ratePerUnitPaise = 120000L,
                totalPaise = 240000L
            )
        )

        // Job 3: 4 Trips Haulage = ₹1,200.00 (120,000 paise)
        workRepository.addWorkEntry(
            WorkEntry(
                id = UUID.randomUUID().toString(),
                customerId = customer.id,
                tractorId = "tractor-1",
                workType = "Haulage",
                date = 3000L,
                quantityScaled = 4000L,
                quantityUnit = QuantityUnit.TRIP,
                ratePerUnitPaise = 30000L,
                totalPaise = 120000L
            )
        )

        val balance = calculateCustomerBalanceUseCase(customer.id)
        // 180,000 + 240,000 + 120,000 = 540,000 paise (₹5,400.00)
        assertEquals(540000L, balance.totalWorkPaise)
        assertEquals(0L, balance.totalPaidPaise)
        assertEquals(540000L, balance.outstandingPaise)
    }

    @Test
    fun `customer-level payments without linking to specific work entry apply to overall balance`() = runBlocking {
        val customer = createSampleCustomer("Bhavesh Patel")
        customerRepository.addCustomer(customer)

        // Work: ₹5,000.00 (500,000 paise)
        workRepository.addWorkEntry(
            WorkEntry(
                id = UUID.randomUUID().toString(),
                customerId = customer.id,
                tractorId = "tractor-1",
                workType = "Harvesting",
                date = 1000L,
                quantityScaled = 10000L,
                quantityUnit = QuantityUnit.BIGHA,
                ratePerUnitPaise = 50000L,
                totalPaise = 500000L
            )
        )

        // Customer-level lump-sum payment (workEntryId is null)
        paymentRepository.recordPayment(
            Payment(
                id = UUID.randomUUID().toString(),
                customerId = customer.id,
                workEntryId = null,
                amountPaise = 500000L,
                paymentDate = 2000L,
                paymentMethod = PaymentMethod.ONLINE_UPI
            )
        )

        val balance = calculateCustomerBalanceUseCase(customer.id)
        assertEquals(0L, balance.outstandingPaise)
        assertTrue(balance.isSettled)
    }

    @Test
    fun `customer advance payment produces negative outstanding balance`() = runBlocking {
        val customer = createSampleCustomer("Jayeshbhai Advance")
        customerRepository.addCustomer(customer)

        // Customer pays ₹10,000.00 advance before any work is done
        paymentRepository.recordPayment(
            Payment(
                id = UUID.randomUUID().toString(),
                customerId = customer.id,
                workEntryId = null,
                amountPaise = 1000000L,
                paymentDate = 1000L,
                paymentMethod = PaymentMethod.BANK_TRANSFER,
                notes = "Advance payment before sowing season"
            )
        )

        // Later: 5 Bigha Sowing done = ₹3,000.00 (300,000 paise)
        workRepository.addWorkEntry(
            WorkEntry(
                id = UUID.randomUUID().toString(),
                customerId = customer.id,
                tractorId = "tractor-1",
                workType = "Sowing",
                date = 2000L,
                quantityScaled = 5000L,
                quantityUnit = QuantityUnit.BIGHA,
                ratePerUnitPaise = 60000L,
                totalPaise = 300000L
            )
        )

        val balance = calculateCustomerBalanceUseCase(customer.id)
        // 300,000 - 1,000,000 = -700,000 paise (Farmer owes customer ₹7,000.00 advance)
        assertEquals(300000L, balance.totalWorkPaise)
        assertEquals(1000000L, balance.totalPaidPaise)
        assertEquals(-700000L, balance.outstandingPaise)
        assertTrue(balance.isAdvance)
        assertFalse(balance.isDue)
        assertFalse(balance.isSettled)
    }

    @Test
    fun `soft-deleted records are excluded from balance and revenue calculations`() = runBlocking {
        val customer = createSampleCustomer("Deleted Test")
        customerRepository.addCustomer(customer)

        val workId = UUID.randomUUID().toString()
        workRepository.addWorkEntry(
            WorkEntry(
                id = workId,
                customerId = customer.id,
                tractorId = "tractor-1",
                workType = "Mistake Entry",
                date = 1000L,
                quantityScaled = 1000L,
                quantityUnit = QuantityUnit.BIGHA,
                ratePerUnitPaise = 50000L,
                totalPaise = 50000L
            )
        )

        // Customer balance before soft delete
        assertEquals(50000L, calculateCustomerBalanceUseCase(customer.id).outstandingPaise)

        // Soft delete the entry
        workRepository.softDeleteWorkEntry(workId, "Correction of duplicate entry")

        // Customer balance after soft delete must be 0
        val balanceAfterDelete = calculateCustomerBalanceUseCase(customer.id)
        assertEquals(0L, balanceAfterDelete.totalWorkPaise)
        assertEquals(0L, balanceAfterDelete.outstandingPaise)
        assertTrue(balanceAfterDelete.isSettled)
    }

    @Test
    fun `customer ledger displays chronological running balance`() = runBlocking {
        val customer = createSampleCustomer("Ledger Chronology Test")
        customerRepository.addCustomer(customer)

        // 1. Work done on Day 1: ₹1,000.00 (100,000 paise)
        workRepository.addWorkEntry(
            WorkEntry(
                id = "w1",
                customerId = customer.id,
                tractorId = "tractor-1",
                workType = "Plowing",
                date = 1000L,
                quantityScaled = 2000L,
                quantityUnit = QuantityUnit.BIGHA,
                ratePerUnitPaise = 50000L,
                totalPaise = 100000L,
                createdAt = 1000L
            )
        )

        // 2. Work done on Day 2: ₹2,000.00 (200,000 paise) -> Balance = ₹3,000.00
        workRepository.addWorkEntry(
            WorkEntry(
                id = "w2",
                customerId = customer.id,
                tractorId = "tractor-1",
                workType = "Rotavator",
                date = 2000L,
                quantityScaled = 2000L,
                quantityUnit = QuantityUnit.HOUR,
                ratePerUnitPaise = 100000L,
                totalPaise = 200000L,
                createdAt = 2000L
            )
        )

        // 3. Payment received on Day 3: ₹1,500.00 (150,000 paise) -> Balance = ₹1,500.00
        paymentRepository.recordPayment(
            Payment(
                id = "p1",
                customerId = customer.id,
                workEntryId = "w1",
                amountPaise = 150000L,
                paymentDate = 3000L,
                paymentMethod = PaymentMethod.CASH,
                createdAt = 3000L
            )
        )

        val ledger = getCustomerLedgerUseCase(customer.id)
        assertEquals(3, ledger.entries.size)
        assertEquals(100000L, ledger.entries[0].runningBalancePaise)
        assertEquals(300000L, ledger.entries[1].runningBalancePaise)
        assertEquals(150000L, ledger.entries[2].runningBalancePaise)
        assertEquals(150000L, ledger.balance.outstandingPaise)
    }

    @Test
    fun `dashboard totals aggregates receivables, advances, expenses and net cash flow`() = runBlocking {
        // Customer 1: Owes ₹4,000.00 (400,000 paise)
        val c1 = createSampleCustomer("Customer 1")
        customerRepository.addCustomer(c1)
        workRepository.addWorkEntry(
            WorkEntry(
                id = UUID.randomUUID().toString(),
                customerId = c1.id,
                tractorId = "t1",
                workType = "Plowing",
                date = 1000L,
                quantityScaled = 4000L,
                quantityUnit = QuantityUnit.BIGHA,
                ratePerUnitPaise = 100000L,
                totalPaise = 400000L
            )
        )

        // Customer 2: Paid advance of ₹2,000.00 (200,000 paise)
        val c2 = createSampleCustomer("Customer 2 Advance")
        customerRepository.addCustomer(c2)
        paymentRepository.recordPayment(
            Payment(
                id = UUID.randomUUID().toString(),
                customerId = c2.id,
                workEntryId = null,
                amountPaise = 200000L,
                paymentDate = 1000L,
                paymentMethod = PaymentMethod.ONLINE_UPI
            )
        )

        // Farm Expenses: Diesel = ₹1,500.00 (150,000 paise)
        expenseRepository.recordExpense(
            Expense(
                id = UUID.randomUUID().toString(),
                tractorId = "t1",
                category = ExpenseCategory.DIESEL,
                amountPaise = 150000L,
                expenseDate = 1000L
            )
        )

        val totals = calculateDashboardTotalsUseCase()
        assertEquals(400000L, totals.totalReceivablesPaise)
        assertEquals(200000L, totals.totalAdvancesPaise)
        assertEquals(400000L, totals.totalWorkRevenuePaise)
        assertEquals(200000L, totals.totalPaymentsCollectedPaise)
        assertEquals(150000L, totals.totalExpensesPaise)
        // Net Cash Flow: 200,000 collected - 150,000 spent = 50,000 paise (₹500.00)
        assertEquals(50000L, totals.netCashFlowPaise)
    }

    private fun createSampleCustomer(name: String) = Customer(
        id = UUID.randomUUID().toString(),
        name = name,
        village = "Gondal",
        phone = "9876543210"
    )

    // In-memory test fakes for fast, isolated business logic verification
    private class FakeCustomerRepository : CustomerRepository {
        private val list = mutableListOf<Customer>()
        private val flow = MutableStateFlow<List<Customer>>(emptyList())

        override fun getAllActiveCustomers(): Flow<List<Customer>> = flow
        override fun searchActiveCustomers(query: String): Flow<List<Customer>> =
            flow.map { l -> l.filter { it.name.contains(query, true) } }

        override suspend fun getCustomerById(id: String): Customer? = list.firstOrNull { it.id == id && !it.isDeleted }
        override fun getCustomerByIdFlow(id: String): Flow<Customer?> =
            flow.map { l -> l.firstOrNull { it.id == id && !it.isDeleted } }

        override suspend fun addCustomer(customer: Customer) {
            list.add(customer)
            flow.value = list.filter { !it.isDeleted }
        }

        override suspend fun updateCustomer(customer: Customer, reason: String?) {
            val idx = list.indexOfFirst { it.id == customer.id }
            if (idx >= 0) list[idx] = customer
            flow.value = list.filter { !it.isDeleted }
        }

        override suspend fun softDeleteCustomer(id: String, reason: String?) {
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0) list[idx] = list[idx].copy(isDeleted = true)
            flow.value = list.filter { !it.isDeleted }
        }
    }

    private class FakeWorkRepository : WorkRepository {
        private val list = mutableListOf<WorkEntry>()
        private val flow = MutableStateFlow<List<WorkEntry>>(emptyList())

        override fun getAllActiveWorkEntries(): Flow<List<WorkEntry>> = flow
        override fun getWorkEntriesForCustomer(customerId: String): Flow<List<WorkEntry>> =
            flow.map { l -> l.filter { it.customerId == customerId } }

        override suspend fun getWorkEntryById(id: String): WorkEntry? = list.firstOrNull { it.id == id && !it.isDeleted }

        override suspend fun addWorkEntry(workEntry: WorkEntry) {
            list.add(workEntry)
            flow.value = list.filter { !it.isDeleted }
        }

        override suspend fun addWorkEntryWithInitialPayment(workEntry: WorkEntry, initialPayment: Payment) {
            list.add(workEntry)
            flow.value = list.filter { !it.isDeleted }
        }

        override suspend fun updateWorkEntry(workEntry: WorkEntry, reason: String?) {
            val idx = list.indexOfFirst { it.id == workEntry.id }
            if (idx >= 0) list[idx] = workEntry
            flow.value = list.filter { !it.isDeleted }
        }

        override suspend fun softDeleteWorkEntry(id: String, reason: String?) {
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0) list[idx] = list[idx].copy(isDeleted = true)
            flow.value = list.filter { !it.isDeleted }
        }

        override fun getTotalWorkRevenueFlow(): Flow<Long> =
            flow.map { l -> l.sumOf { it.totalPaise } }

        override suspend fun getTotalWorkPaiseForCustomer(customerId: String): Long =
            list.filter { it.customerId == customerId && !it.isDeleted }.sumOf { it.totalPaise }
    }

    private class FakePaymentRepository : PaymentRepository {
        private val list = mutableListOf<Payment>()
        private val flow = MutableStateFlow<List<Payment>>(emptyList())

        override fun getAllActivePayments(): Flow<List<Payment>> = flow
        override fun getPaymentsForCustomer(customerId: String): Flow<List<Payment>> =
            flow.map { l -> l.filter { it.customerId == customerId } }

        override suspend fun getPaymentById(id: String): Payment? = list.firstOrNull { it.id == id && !it.isDeleted }

        override suspend fun recordPayment(payment: Payment) {
            list.add(payment)
            flow.value = list.filter { !it.isDeleted }
        }

        override suspend fun updatePayment(payment: Payment, reason: String?) {
            val idx = list.indexOfFirst { it.id == payment.id }
            if (idx >= 0) list[idx] = payment
            flow.value = list.filter { !it.isDeleted }
        }

        override suspend fun softDeletePayment(id: String, reason: String?) {
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0) list[idx] = list[idx].copy(isDeleted = true)
            flow.value = list.filter { !it.isDeleted }
        }

        override fun getTotalPaymentsCollectedFlow(): Flow<Long> =
            flow.map { l -> l.sumOf { it.amountPaise } }

        override suspend fun getTotalPaidPaiseForCustomer(customerId: String): Long =
            list.filter { it.customerId == customerId && !it.isDeleted }.sumOf { it.amountPaise }
    }

    private class FakeExpenseRepository : ExpenseRepository {
        private val list = mutableListOf<Expense>()
        private val flow = MutableStateFlow<List<Expense>>(emptyList())

        override fun getAllActiveExpenses(): Flow<List<Expense>> = flow
        override fun getExpensesForTractor(tractorId: String): Flow<List<Expense>> =
            flow.map { l -> l.filter { it.tractorId == tractorId } }

        override suspend fun getExpenseById(id: String): Expense? = list.firstOrNull { it.id == id && !it.isDeleted }

        override suspend fun recordExpense(expense: Expense) {
            list.add(expense)
            flow.value = list.filter { !it.isDeleted }
        }

        override suspend fun updateExpense(expense: Expense, reason: String?) {
            val idx = list.indexOfFirst { it.id == expense.id }
            if (idx >= 0) list[idx] = expense
            flow.value = list.filter { !it.isDeleted }
        }

        override suspend fun softDeleteExpense(id: String, reason: String?) {
            val idx = list.indexOfFirst { it.id == id }
            if (idx >= 0) list[idx] = list[idx].copy(isDeleted = true)
            flow.value = list.filter { !it.isDeleted }
        }

        override fun getTotalExpensesFlow(): Flow<Long> =
            flow.map { l -> l.sumOf { it.amountPaise } }
    }
}

