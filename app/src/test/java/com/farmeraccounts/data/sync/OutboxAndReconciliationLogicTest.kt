package com.farmeraccounts.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.farmeraccounts.data.local.db.AppDatabase
import com.farmeraccounts.data.local.db.entity.CustomerEntity
import com.farmeraccounts.data.local.db.entity.OutboxEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class OutboxAndReconciliationLogicTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `dependency sorting orders Customer before WorkEntry before Payment`() {
        // Dependency rank: CUSTOMER (1), TRACTOR (2), WORK_ENTRY (3), PAYMENT (4), EXPENSE (5), AUDIT_LOG (6)
        fun getRank(type: String) = when (type) {
            "CUSTOMER" -> 1
            "TRACTOR" -> 2
            "WORK_ENTRY" -> 3
            "PAYMENT" -> 4
            "EXPENSE" -> 5
            "AUDIT_LOG" -> 6
            else -> 99
        }

        val outboxList = listOf(
            OutboxEntity(sequenceId = 1, entityType = "PAYMENT", entityId = "p1", operationType = "INSERT"),
            OutboxEntity(sequenceId = 2, entityType = "WORK_ENTRY", entityId = "w1", operationType = "INSERT"),
            OutboxEntity(sequenceId = 3, entityType = "CUSTOMER", entityId = "c1", operationType = "INSERT"),
            OutboxEntity(sequenceId = 4, entityType = "AUDIT_LOG", entityId = "a1", operationType = "INSERT")
        )

        val sorted = outboxList.sortedWith(
            compareBy<OutboxEntity> { getRank(it.entityType) }.thenBy { it.sequenceId }
        )

        assertEquals("CUSTOMER", sorted[0].entityType)
        assertEquals("WORK_ENTRY", sorted[1].entityType)
        assertEquals("PAYMENT", sorted[2].entityType)
        assertEquals("AUDIT_LOG", sorted[3].entityType)
    }

    @Test
    fun `reconciliation preserves local pending modifications against stale cloud overwrite`() = runBlocking {
        val customerId = UUID.randomUUID().toString()

        // Local state with pending offline edit
        val localCustomer = CustomerEntity(
            id = customerId,
            name = "સ્થાનિક નવો સુધારો (Local Pending Name)",
            nickname = "કાનાભાઈ",
            createdAt = 1000L,
            updatedAt = 2000L,
            version = 2L,
            isDeleted = false
        )
        db.customerDao().insert(localCustomer)

        // Pending outbox entry exists
        db.outboxDao().insert(
            OutboxEntity(
                entityType = "CUSTOMER",
                entityId = customerId,
                operationType = "UPDATE"
            )
        )

        val pendingIds = db.outboxDao().getPending().map { it.entityId }.toSet()
        assertTrue("Pending outbox must contain customer ID", pendingIds.contains(customerId))

        // Cloud record attempting to overwrite
        val staleCloudName = "જૂનું નામ (Stale Cloud Name)"
        val cloudCustomer = CustomerEntity(
            id = customerId,
            name = staleCloudName,
            nickname = "કાનાભાઈ",
            createdAt = 1000L,
            updatedAt = 1500L,
            version = 1L,
            isDeleted = false
        )

        // Reconciliation rule: if pendingIds contains ID, skip cloud overwrite
        if (!pendingIds.contains(cloudCustomer.id)) {
            db.customerDao().upsert(cloudCustomer)
        }

        // Verify local pending edit was preserved
        val currentLocal = db.customerDao().getById(customerId)
        assertNotNull(currentLocal)
        assertEquals("સ્થાનિક નવો સુધારો (Local Pending Name)", currentLocal?.name)
    }

    @Test
    fun `reconciliation applies newer cloud records when local has no pending outbox changes`() = runBlocking {
        val customerId = UUID.randomUUID().toString()

        // Existing local record without any pending outbox
        val localCustomer = CustomerEntity(
            id = customerId,
            name = "મૂળ નામ (Original Name)",
            createdAt = 1000L,
            updatedAt = 1000L,
            version = 1L,
            isDeleted = false
        )
        db.customerDao().insert(localCustomer)

        val pendingIds = db.outboxDao().getPending().map { it.entityId }.toSet()
        assertTrue("No pending outbox entries should exist", pendingIds.isEmpty())

        // Newer cloud record
        val cloudCustomer = CustomerEntity(
            id = customerId,
            name = "ક્લાઉડ સુધારેલ નામ (Cloud Updated Name)",
            createdAt = 1000L,
            updatedAt = 2000L,
            version = 2L,
            isDeleted = false
        )

        // Reconciliation rule: local has no pending outbox and cloud version > local version
        val local = db.customerDao().getByIdIncludeDeleted(cloudCustomer.id)
        if (local == null || cloudCustomer.version >= local.version) {
            db.customerDao().upsert(cloudCustomer)
        }

        // Verify cloud update was applied
        val updated = db.customerDao().getById(customerId)
        assertNotNull(updated)
        assertEquals("ક્લાઉડ સુધારેલ નામ (Cloud Updated Name)", updated?.name)
        assertEquals(2L, updated?.version)
    }

    @Test
    fun `failed mutations remain visible for reconciliation and repair`() = runBlocking {
        val seqId = db.outboxDao().insert(
            OutboxEntity(entityType = "CUSTOMER", entityId = "c1", operationType = "INSERT")
        )
        db.outboxDao().updateStatusAndError(seqId, "FAILED", 1, "Invalid record")
        assertTrue(db.outboxDao().getPending().isEmpty())
        assertEquals(seqId, db.outboxDao().getFailed().single().sequenceId)
        assertEquals(seqId, db.outboxDao().getUnconfirmed().single().sequenceId)
    }
}
