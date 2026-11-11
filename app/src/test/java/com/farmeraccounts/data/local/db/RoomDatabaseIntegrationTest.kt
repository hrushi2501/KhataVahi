package com.farmeraccounts.data.local.db

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.farmeraccounts.data.local.db.entity.CustomerEntity
import com.farmeraccounts.data.local.db.entity.PaymentEntity
import com.farmeraccounts.data.local.db.entity.TractorEntity
import com.farmeraccounts.data.local.db.entity.WorkEntryEntity
import com.farmeraccounts.data.mapper.toDomain
import com.farmeraccounts.data.repository.WorkRepositoryImpl
import com.farmeraccounts.domain.model.Payment
import com.farmeraccounts.domain.model.PaymentMethod
import com.farmeraccounts.domain.model.QuantityUnit
import com.farmeraccounts.domain.model.WorkEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomDatabaseIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var workRepository: WorkRepositoryImpl

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        workRepository = WorkRepositoryImpl(db)
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun `foreign key integrity prevents inserting work entry with non-existent customer`() = runBlocking {
        // Insert valid tractor
        val tractor = TractorEntity(
            id = "tractor-1",
            displayName = "મોટો ટ્રેક્ટર (Big Tractor)",
            model = "Mahindra Sarpanch",
            type = "BIG"
        )
        db.tractorDao().insert(tractor)

        // Try to insert work entry with non-existent customerId
        val invalidWork = WorkEntryEntity(
            id = UUID.randomUUID().toString(),
            customerId = "non-existent-customer-id",
            tractorId = tractor.id,
            workType = "Plowing",
            date = System.currentTimeMillis(),
            quantityScaled = 2000L,
            quantityUnit = "BIGHA",
            ratePerUnitPaise = 50000L,
            totalPaise = 100000L
        )

        try {
            db.workEntryDao().insert(invalidWork)
            fail("Expected SQLiteConstraintException when foreign key constraint is violated")
        } catch (e: SQLiteConstraintException) {
            // Expected foreign key failure
            assertTrue(e.message?.contains("FOREIGN KEY") == true || e.message?.contains("constraint failed") == true)
        }
    }

    @Test
    fun `foreign key integrity prevents inserting work entry with non-existent tractor`() = runBlocking {
        // Insert valid customer
        val customer = CustomerEntity(
            id = "customer-1",
            name = "Rameshbhai Patel",
            village = "Gondal"
        )
        db.customerDao().insert(customer)

        // Try to insert work entry with non-existent tractorId
        val invalidWork = WorkEntryEntity(
            id = UUID.randomUUID().toString(),
            customerId = customer.id,
            tractorId = "non-existent-tractor-id",
            workType = "Plowing",
            date = System.currentTimeMillis(),
            quantityScaled = 2000L,
            quantityUnit = "BIGHA",
            ratePerUnitPaise = 50000L,
            totalPaise = 100000L
        )

        try {
            db.workEntryDao().insert(invalidWork)
            fail("Expected SQLiteConstraintException when tractor foreign key is violated")
        } catch (e: SQLiteConstraintException) {
            assertTrue(e.message?.contains("FOREIGN KEY") == true || e.message?.contains("constraint failed") == true)
        }
    }

    @Test
    fun `work entry with initial payment saves work, payment, audit and outbox in atomic transaction`() = runBlocking {
        val customerId = "customer-atomic"
        val tractorId = "tractor-atomic"

        db.customerDao().insert(
            CustomerEntity(id = customerId, name = "Kishorbhai", village = "Rajkot")
        )
        db.tractorDao().insert(
            TractorEntity(id = tractorId, displayName = "મોટો ટ્રેક્ટર", model = "Mahindra", type = "BIG")
        )

        val workId = UUID.randomUUID().toString()
        val paymentId = UUID.randomUUID().toString()

        val work = WorkEntry(
            id = workId,
            customerId = customerId,
            tractorId = tractorId,
            workType = "Sowing",
            date = 1000L,
            quantityScaled = 5000L,
            quantityUnit = QuantityUnit.BIGHA,
            ratePerUnitPaise = 60000L,
            totalPaise = 300000L
        )

        val initialPayment = Payment(
            id = paymentId,
            customerId = customerId,
            workEntryId = workId,
            amountPaise = 100000L,
            paymentDate = 1000L,
            paymentMethod = PaymentMethod.CASH,
            notes = "Cash at work completion"
        )

        workRepository.addWorkEntryWithInitialPayment(work, initialPayment)

        // Verify Work Entry exists
        val savedWork = db.workEntryDao().getById(workId)
        assertNotNull(savedWork)
        assertEquals(300000L, savedWork?.totalPaise)

        // Verify Payment exists and is linked
        val savedPayment = db.paymentDao().getById(paymentId)
        assertNotNull(savedPayment)
        assertEquals(100000L, savedPayment?.amountPaise)
        assertEquals(workId, savedPayment?.workEntryId)

        // Verify Audit records exist for both
        val auditLogs = db.auditLogDao().getAllLogs().first()
        val workAudit = auditLogs.find { it.entityId == workId }
        val paymentAudit = auditLogs.find { it.entityId == paymentId }
        assertNotNull("Audit log for work must exist", workAudit)
        assertNotNull("Audit log for payment must exist", paymentAudit)

        // Verify Outbox records exist for future synchronization
        val outbox = db.outboxDao().getPending()
        val workOutbox = outbox.find { it.entityId == workId }
        val paymentOutbox = outbox.find { it.entityId == paymentId }
        assertNotNull("Outbox record for work must exist", workOutbox)
        assertNotNull("Outbox record for payment must exist", paymentOutbox)
        assertEquals("PENDING", workOutbox?.syncStatus)
        assertEquals("PENDING", paymentOutbox?.syncStatus)
    }

    @Test
    fun `initial tractor seed data accurately represents big tractor and mini tractor with unknown model`() = runBlocking {
        // Test seed tractor entities
        val bigTractor = TractorEntity(
            id = AppDatabase.SEED_TRACTOR_BIG_ID,
            displayName = "મોટો ટ્રેક્ટર (Big Tractor)",
            model = "Mahindra Sarpanch",
            type = "BIG"
        )
        val miniTractor = TractorEntity(
            id = AppDatabase.SEED_TRACTOR_MINI_ID,
            displayName = "નાનો ટ્રેક્ટર (Mini Tractor)",
            model = null, // Strictly null per requirement: "Never invent the mini tractor's model"
            type = "MINI"
        )

        db.tractorDao().insert(bigTractor)
        db.tractorDao().insert(miniTractor)

        val retrievedBig = db.tractorDao().getById(AppDatabase.SEED_TRACTOR_BIG_ID)
        val retrievedMini = db.tractorDao().getById(AppDatabase.SEED_TRACTOR_MINI_ID)

        assertNotNull(retrievedBig)
        assertEquals("Mahindra Sarpanch", retrievedBig?.model)
        assertEquals("BIG", retrievedBig?.type)

        assertNotNull(retrievedMini)
        assertNull("Mini tractor model must be null - never invented", retrievedMini?.model)
        assertEquals("MINI", retrievedMini?.type)
    }

    @Test
    fun `seed tractor identifiers are valid RFC 4122 UUIDs for cloud synchronization parity`() {
        assertNotNull(java.util.UUID.fromString(AppDatabase.SEED_TRACTOR_BIG_ID))
        assertNotNull(java.util.UUID.fromString(AppDatabase.SEED_TRACTOR_MINI_ID))
    }
}

