package com.farmeraccounts.data.local.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.farmeraccounts.data.local.db.entity.CustomerEntity
import com.farmeraccounts.data.local.db.entity.TractorEntity
import com.farmeraccounts.data.local.db.entity.WorkEntryEntity
import com.farmeraccounts.data.repository.PaymentRepositoryImpl
import com.farmeraccounts.domain.model.Payment
import com.farmeraccounts.domain.model.PaymentMethod
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PaymentRelationshipTest {
    @Test fun paymentCannotBeLinkedToAnotherCustomersWork() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        try {
            db.customerDao().insert(CustomerEntity(id = "a", name = "A"))
            db.customerDao().insert(CustomerEntity(id = "b", name = "B"))
            db.tractorDao().insert(TractorEntity(id = "t", displayName = "Tractor", type = "BIG"))
            db.workEntryDao().insert(WorkEntryEntity(id = "w", customerId = "a", tractorId = "t", workType = "Work", date = 1L, quantityScaled = 1000L, quantityUnit = "BIGHA", ratePerUnitPaise = 100L, totalPaise = 100L))
            try {
                PaymentRepositoryImpl(db).recordPayment(Payment(id = "p", customerId = "b", workEntryId = "w", amountPaise = 50L, paymentDate = 1L, paymentMethod = PaymentMethod.CASH))
                fail("Cross-customer payment must fail")
            } catch (_: IllegalArgumentException) { }
            assertNull(db.paymentDao().getById("p"))
            assertEquals(0, db.outboxDao().getPendingCount())
        } finally { db.close() }
    }
}
