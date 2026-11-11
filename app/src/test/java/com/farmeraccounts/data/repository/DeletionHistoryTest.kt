package com.farmeraccounts.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.farmeraccounts.data.local.db.AppDatabase
import com.farmeraccounts.data.local.db.entity.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DeletionHistoryTest {
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun teardown() = db.close()

    private suspend fun seedLedger() {
        db.customerDao().insert(CustomerEntity("c", "Farmer"))
        db.tractorDao().insert(TractorEntity("t", "Tractor", type = "BIG"))
        db.workEntryDao().insert(WorkEntryEntity("w", "c", "t", "PLOUGHING", 1, 1000, "BIGHA", 100, 100))
        db.paymentDao().insert(PaymentEntity("p", "c", "w", 40, 1, "CASH"))
    }

    @Test
    fun `work deletion records exact snapshot and preserves linked payment`() = runBlocking {
        seedLedger()
        val original = db.workEntryDao().getById("w")!!
        WorkRepositoryImpl(db).softDeleteWorkEntry("w", "  Duplicate work  ")
        val log = db.deletedLogDao().getAllFlow().first().single()
        assertEquals(original, Json.decodeFromString<WorkEntryEntity>(log.snapshotJson))
        assertEquals("Duplicate work", log.reason)
        assertEquals("WORK_ENTRY", log.entityType)
        assertTrue(db.workEntryDao().getByIdIncludeDeleted("w")!!.isDeleted)
        assertEquals(2L, db.workEntryDao().getByIdIncludeDeleted("w")!!.version)
        assertEquals(40L, db.paymentDao().getById("p")!!.amountPaise)
        assertEquals("w", db.paymentDao().getById("p")!!.workEntryId)
        assertEquals(setOf("WORK_ENTRY", "DELETED_LOG", "AUDIT_LOG"), db.outboxDao().getPending().map { it.entityType }.toSet())
        WorkRepositoryImpl(db).softDeleteWorkEntry("w", "Repeated tap")
        assertEquals(1, db.deletedLogDao().getAllFlow().first().size)
        assertEquals(3, db.outboxDao().getPendingCount())
    }

    @Test
    fun `customer with active work or payments refuses deletion without side effects`() = runBlocking {
        seedLedger()
        suspend fun expectRefusal() {
            try {
                CustomerRepositoryImpl(db).softDeleteCustomer("c", "Remove customer")
                fail("Active financial records must block customer deletion")
            } catch (_: IllegalArgumentException) { }
        }
        expectRefusal()
        WorkRepositoryImpl(db).softDeleteWorkEntry("w", "Duplicate work")
        expectRefusal() // The payment remains active even after the work was deleted.
        assertNotNull(db.customerDao().getById("c"))
        PaymentRepositoryImpl(db).softDeletePayment("p", "Duplicate payment")
        CustomerRepositoryImpl(db).softDeleteCustomer("c", "Inactive customer")
        TractorRepositoryImpl(db).softDeleteTractor("t", "Retired tractor")
        assertEquals(setOf("CUSTOMER", "TRACTOR", "WORK_ENTRY", "PAYMENT"), db.deletedLogDao().getAllFlow().first().map { it.entityType }.toSet())
        assertEquals(40L, db.paymentDao().getByIdIncludeDeleted("p")!!.amountPaise)
    }

    @Test
    fun `blank reason is refused and expense snapshot preserves integer paise`() = runBlocking {
        val expense = ExpenseEntity("e", category = "DIESEL", amountPaise = 9_007_199_254_740_993L, expenseDate = 1)
        db.expenseDao().insert(expense)
        try {
            ExpenseRepositoryImpl(db).softDeleteExpense("e", " \n ")
            fail("Reason required")
        } catch (_: IllegalArgumentException) { }
        assertTrue(db.deletedLogDao().getAllFlow().first().isEmpty())
        assertNotNull(db.expenseDao().getById("e"))
        ExpenseRepositoryImpl(db).softDeleteExpense("e", "Entered twice")
        val snapshot = db.deletedLogDao().getAllFlow().first().single().snapshotJson
        assertEquals(expense, Json.decodeFromString<ExpenseEntity>(snapshot))
    }

    @Test
    fun `outbox failure rolls back deletion snapshot and audit together`() = runBlocking {
        val expense = ExpenseEntity("e", category = "DIESEL", amountPaise = 100L, expenseDate = 1)
        db.expenseDao().insert(expense)
        db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER fail_deletion_outbox BEFORE INSERT ON outbox
            WHEN NEW.entity_type = 'DELETED_LOG' BEGIN SELECT RAISE(ABORT, 'test failure'); END""")
        try {
            ExpenseRepositoryImpl(db).softDeleteExpense("e", "Duplicate")
            fail("Injected outbox failure must abort transaction")
        } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(expense, db.expenseDao().getById("e"))
        assertTrue(db.deletedLogDao().getAllFlow().first().isEmpty())
        assertEquals(0, db.auditLogDao().getCount())
        assertEquals(0, db.outboxDao().getPendingCount())
    }

    @Test
    fun `remote replay cannot overwrite an existing deletion snapshot`() = runBlocking {
        val log = DeletedLogEntity("log", "PAYMENT", "p", "{\"amountPaise\":100}", "Duplicate", 1)
        db.deletedLogDao().insert(log)
        db.deletedLogDao().insertAll(listOf(log.copy(reason = "Changed", snapshotJson = "{}")))
        assertEquals(log, db.deletedLogDao().getById("log"))
    }
    @Test
    fun `trusted server confirmation replaces provisional snapshot and acknowledges only matching log`() = runBlocking {
        val provisional = DeletedLogEntity("log", "PAYMENT", "p", "{\"amountPaise\":100}", "Duplicate", 1)
        val canonical = provisional.copy(snapshotJson = "{\"amountPaise\":200}", timestamp = 2)
        db.deletedLogDao().insert(provisional)
        db.outboxDao().insert(OutboxEntity(entityType = "DELETED_LOG", entityId = "log", operationType = "INSERT"))
        db.outboxDao().insert(OutboxEntity(entityType = "PAYMENT", entityId = "p", operationType = "SOFT_DELETE"))
        db.outboxDao().insert(OutboxEntity(entityType = "DELETED_LOG", entityId = "other", operationType = "INSERT"))
        db.deletedLogDao().upsertConfirmed(canonical)
        db.outboxDao().acknowledgeDeletedLog("log")
        assertEquals(canonical, db.deletedLogDao().getLatestForEntity("PAYMENT", "p"))
        assertEquals(setOf("p", "other"), db.outboxDao().getPending().map { it.entityId }.toSet())
    }

    @Test
    fun `server tombstone marks matching mutations failed without deleting unrelated work`() = runBlocking {
        db.outboxDao().insert(OutboxEntity(entityType = "PAYMENT", entityId = "p", operationType = "UPDATE"))
        db.outboxDao().insert(OutboxEntity(entityType = "PAYMENT", entityId = "p", operationType = "UPDATE", syncStatus = "IN_PROGRESS"))
        db.outboxDao().insert(OutboxEntity(entityType = "WORK_ENTRY", entityId = "p", operationType = "UPDATE"))
        db.outboxDao().failForEntity("PAYMENT", "p", "Deleted on server")
        assertEquals(2, db.outboxDao().getFailedCount())
        assertEquals(3, db.outboxDao().getUnconfirmed().size)
        assertEquals("WORK_ENTRY", db.outboxDao().getPending().single().entityType)
        assertTrue(db.outboxDao().getFailed().all { it.lastError == "Deleted on server" })
    }

}
