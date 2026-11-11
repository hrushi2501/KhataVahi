package com.farmeraccounts.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.farmeraccounts.data.local.db.AppDatabase
import com.farmeraccounts.data.local.db.entity.OutboxEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class OutboxSyncProcessorTest {
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun teardown() = db.close()

    private suspend fun enqueue(id: String, type: String = "CUSTOMER", status: String = "PENDING") =
        db.outboxDao().insert(OutboxEntity(entityType = type, entityId = id, operationType = "UPDATE", syncStatus = status))

    @Test
    fun `coalesces captured edits and leaves new edit pending after server response`() = runBlocking {
        enqueue("customer")
        val latestCaptured = enqueue("customer")
        var calls = 0
        var newSequence = 0L
        val processor = OutboxSyncProcessor(db) { item, _ ->
            calls++
            assertEquals(latestCaptured, item.sequenceId)
            newSequence = enqueue("customer")
            assertEquals(3, db.outboxDao().getUnconfirmed().size)
        }
        val result = processor.processPending("owner")
        assertEquals(1, calls)
        assertEquals(2, result.processedCount)
        assertTrue(result.hasRetryableWork)
        assertEquals(listOf(newSequence), db.outboxDao().getPending().map { it.sequenceId })
    }

    @Test
    fun `batch prioritizes parents before limit and bounds work to 200 mutations`() = runBlocking {
        enqueue("payment", "PAYMENT")
        repeat(201) { enqueue("customer-$it") }
        val uploaded = mutableListOf<String>()
        val result = OutboxSyncProcessor(db) { item, _ -> uploaded += item.entityType }.processPending("owner")
        assertEquals(200, result.processedCount)
        assertEquals(200, uploaded.size)
        assertTrue(uploaded.all { it == "CUSTOMER" })
        assertTrue(result.hasRetryableWork)
        assertEquals(2, db.outboxDao().getPendingCount())
    }

    @Test
    fun `confirmed deletes upload children before parents while unseen tombstones keep parent order`() = runBlocking {
        val types = listOf("CUSTOMER", "WORK_ENTRY", "PAYMENT")
        for (type in types) {
            db.outboxDao().insert(OutboxEntity(entityType = type, entityId = "confirmed-$type", operationType = "SOFT_DELETE"))
            db.syncVersionDao().set(com.farmeraccounts.data.local.db.entity.SyncVersionEntity(type, "confirmed-$type", 1))
            db.outboxDao().insert(OutboxEntity(entityType = type, entityId = "unseen-$type", operationType = "SOFT_DELETE"))
        }
        enqueue("confirmed-CUSTOMER", "CUSTOMER") // pending correction coalesces into tombstone
        val ordered = db.outboxDao().getPendingBatch(200).map { it.entityId }
        assertEquals(listOf("unseen-CUSTOMER", "unseen-WORK_ENTRY", "unseen-PAYMENT",
            "confirmed-PAYMENT", "confirmed-WORK_ENTRY", "confirmed-CUSTOMER", "confirmed-CUSTOMER"), ordered)
    }

    @Test
    fun `rejected foreign key request retries behind its queued parent`() = runBlocking {
        db.customerDao().insert(com.farmeraccounts.data.local.db.entity.CustomerEntity("c", "Test"))
        db.tractorDao().insert(com.farmeraccounts.data.local.db.entity.TractorEntity(
            id = "t", displayName = "Test", type = "BIG"))
        db.workEntryDao().insert(com.farmeraccounts.data.local.db.entity.WorkEntryEntity(
            id = "w", customerId = "c", tractorId = "t", workType = "Test", date = 1,
            quantityScaled = 1000, quantityUnit = "BIGHA", ratePerUnitPaise = 100, totalPaise = 100))
        val child = enqueue("w", "WORK_ENTRY", "FAILED")
        db.outboxDao().updateStatusAndError(child, "FAILED", 1,
            "insert violates foreign key constraint work_entries_customer_owner_fk")
        db.uploadAttemptDao().insert(com.farmeraccounts.data.local.db.entity.UploadAttemptEntity(
            "rejected", "WORK_ENTRY", "w", "{}", null, 1, "[$child]"))
        enqueue("c")
        val uploaded = mutableListOf<String>()
        val result = OutboxSyncProcessor(db) { item, _ -> uploaded += item.entityId }.processPending("owner")
        assertEquals(listOf("c", "w"), uploaded)
        assertEquals(0, result.failedCount)
        assertTrue(db.uploadAttemptDao().getAll().isEmpty())
        assertEquals(0, db.outboxDao().getUnconfirmedCount())
    }

    @Test
    fun `transient failure stays pending without immediate retry or child upload`() = runBlocking {
        enqueue("customer", status = "IN_PROGRESS")
        enqueue("payment", "PAYMENT")
        var calls = 0
        val result = OutboxSyncProcessor(db) { _, _ -> calls++; throw IOException("offline") }.processPending("owner")
        assertEquals(1, calls)
        assertEquals(0, result.processedCount)
        assertTrue(result.hasRetryableWork)
        assertEquals(2, db.outboxDao().getPendingCount())
        assertEquals(1, db.outboxDao().getPending().first().retryCount)
    }

    @Test
    fun `permanent failure blocks future passes and preserves mutation`() = runBlocking {
        enqueue("customer")
        var calls = 0
        val processor = OutboxSyncProcessor(db) { _, _ -> calls++; error("missing local entity") }
        val first = processor.processPending("owner")
        val second = processor.processPending("owner")
        assertEquals(1, calls)
        assertFalse(first.hasRetryableWork)
        assertFalse(second.hasRetryableWork)
        assertEquals(1, db.outboxDao().getFailedCount())
    }

    @Test
    fun `new correction requeues failed edits and uploads entity once`() = runBlocking {
        val oldSequence = enqueue("customer")
        val processor = OutboxSyncProcessor(db) { item, _ ->
            if (item.sequenceId == oldSequence) error("invalid record")
        }
        assertEquals(1, processor.processPending("owner").failedCount)
        val correctionSequence = enqueue("customer")
        var calls = 0
        val recovered = OutboxSyncProcessor(db) { item, _ ->
            calls++
            assertEquals(correctionSequence, item.sequenceId)
            val previous = db.outboxDao().getUnconfirmed().first { it.sequenceId == oldSequence }
            assertEquals(0, previous.retryCount)
            assertEquals(null, previous.lastError)
        }.processPending("owner")
        assertEquals(1, calls)
        assertEquals(2, recovered.processedCount)
        assertEquals(0, recovered.failedCount)
        assertTrue(db.outboxDao().getUnconfirmed().isEmpty())
    }

    @Test
    fun `older pending edit and unrelated entity cannot requeue permanent failure`() = runBlocking {
        enqueue("customer")
        val failed = enqueue("customer")
        db.outboxDao().updateStatusAndError(failed, "FAILED", 1, "invalid")
        enqueue("unrelated")
        db.outboxDao().requeueCorrectedFailures()
        assertEquals(failed, db.outboxDao().getFailed().single().sequenceId)
    }

    @Test
    fun `cancellation is propagated and interrupted claim recovers on next pass`() = runBlocking {
        enqueue("customer")
        try {
            OutboxSyncProcessor(db) { _, _ -> throw CancellationException("stopped") }.processPending("owner")
            throw AssertionError("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals("IN_PROGRESS", db.outboxDao().getUnconfirmed().single().syncStatus)
        }
        val result = OutboxSyncProcessor(db) { _, _ -> }.processPending("owner")
        assertEquals(1, result.processedCount)
        assertTrue(db.outboxDao().getUnconfirmed().isEmpty())
    }
}
