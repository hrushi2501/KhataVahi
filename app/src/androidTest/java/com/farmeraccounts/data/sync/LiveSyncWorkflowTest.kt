package com.farmeraccounts.data.sync

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.farmeraccounts.FarmerAccountsApp
import com.farmeraccounts.data.local.db.AppDatabase
import com.farmeraccounts.data.remote.dto.CustomerDto
import com.farmeraccounts.data.remote.dto.WorkEntryDto
import com.farmeraccounts.data.remote.dto.PaymentDto
import com.farmeraccounts.domain.model.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlinx.serialization.json.*

/** Explicit opt-in, emulator-only test. Creates marked fixtures, then soft-deletes them.
 * Run stages separately with a real force-stop between them. No credentials in test APK.
 */
class LiveSyncWorkflowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val container get() = (context.applicationContext as FarmerAccountsApp).container
    private val fixtureFile get() = File(context.filesDir, "live-sync-fixture.json")

    private fun optIn() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveSync") == "true")
        check(android.os.Build.MODEL.contains("sdk", true) || android.os.Build.FINGERPRINT.contains("emulator")) {
            "Live sync QA must run on an emulator"
        }
    }

    private fun shell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
    }

    private suspend fun awaitCondition(message: String, block: suspend () -> Boolean) {
        withTimeout(180_000) {
            while (!block()) delay(1500)
        }
        android.util.Log.i("LiveSyncQA", "PASS: $message")
    }

    @Test fun stageOfflineSave(): Unit = runBlocking {
        optIn()
        val client = checkNotNull(container.supabaseClient)
        client.auth.awaitInitialization()
        val credentials = File(context.filesDir, "live-sync-login.json")
        if (credentials.exists()) {
            try {
                val login = JSONObject(credentials.readText())
                check(container.authRepository.signIn(login.getString("email"), login.getString("password")).isSuccess) {
                    "Owner sign-in failed"
                }
            } finally { credentials.delete() }
        }
        checkNotNull(client.auth.currentUserOrNull()) { "Sign in as owner before live QA" }
        // Allow existing owner hydration/seeds to settle before disabling connectivity.
        awaitCondition("initial owner sync") {
            val status = container.syncManager.syncStatus.value
            status is SyncStatus.Idle && status.lastSyncTimestamp > 0
        }
        val tractor = container.database.tractorDao().getAllActive().first { it.type == "BIG" }
        val ids = JSONObject().apply {
            put("customer", UUID.randomUUID().toString())
            put("work", UUID.randomUUID().toString())
            put("payment", UUID.randomUUID().toString())
        }
        fixtureFile.writeText(ids.toString())
        shell("svc wifi disable")
        shell("svc data disable")
        delay(4000)
        val now = System.currentTimeMillis()
        container.customerRepository.addCustomer(Customer(ids.getString("customer"), "QA SYNC ${ids.getString("customer").take(8)}"))
        container.workRepository.addWorkEntry(WorkEntry(
            ids.getString("work"), ids.getString("customer"), tractor.id, "QA sync test", now,
            1000, QuantityUnit.BIGHA, 100, 100, "QA fixture - will be soft deleted"
        ))
        container.paymentRepository.recordPayment(Payment(
            ids.getString("payment"), ids.getString("customer"), ids.getString("work"),
            40, now, PaymentMethod.CASH, "QA partial payment - will be soft deleted"
        ))
        delay(3000)
        assertNotNull(container.database.customerDao().getById(ids.getString("customer")))
        val pending = container.database.outboxDao().getUnconfirmed().map { it.entityId }.toSet()
        assertTrue(pending.containsAll(listOf(ids.getString("customer"), ids.getString("work"), ids.getString("payment"))))
        android.util.Log.i("LiveSyncQA", "PASS: offline customer/work/partial-payment saved with durable outbox")
    }

    @Test fun stageOnlineRestoreAndDelete(): Unit = runBlocking {
        optIn()
        val ids = JSONObject(fixtureFile.readText())
        val customerId = ids.getString("customer")
        val workId = ids.getString("work")
        val paymentId = ids.getString("payment")
        // Runner restores connectivity and restarts app before this stage. No manual sync call.
        val client = checkNotNull(container.supabaseClient)
        client.auth.awaitInitialization()
        val owner = checkNotNull(container.authRepository.getOwnerId())
        assertNotNull(container.database.customerDao().getById(customerId))
        awaitCondition("automatic upload and server-confirmed acknowledgement after restart") {
            container.database.outboxDao().getUnconfirmed().none { it.entityId in setOf(customerId, workId, paymentId) }
        }
        val customers = client.postgrest["customers"].select { filter { eq("id", customerId) } }.decodeList<CustomerDto>()
        val work = client.postgrest["work_entries"].select { filter { eq("id", workId) } }.decodeList<WorkEntryDto>()
        val payments = client.postgrest["payments"].select { filter { eq("id", paymentId) } }.decodeList<PaymentDto>()
        assertEquals(1, customers.size); assertEquals(1, work.size); assertEquals(1, payments.size)
        assertEquals(customerId, work.single().customerId)
        assertEquals(workId, payments.single().workEntryId)
        assertEquals(100L, work.single().totalPaise); assertEquals(40L, payments.single().amountPaise)
        assertEquals(owner, customers.single().ownerId)
        android.util.Log.i("LiveSyncQA", "PASS: API rows unique, owner-scoped, exact amounts and relationships")
        val beforeFeed = Json.parseToJsonElement(client.postgrest["sync_changes"].select {
            filter { eq("record_id", customerId) }
        }.data).jsonArray.size
        val receipt = Json.parseToJsonElement(client.postgrest["mutation_receipts"].select {
            filter { eq("entity_id", customerId) }
        }.data).jsonArray.first().jsonObject
        val envelope = receipt.getValue("request_payload").jsonObject
        val replay = buildJsonObject {
            put("p_request_id", receipt.getValue("request_id"))
            put("p_entity_type", envelope.getValue("entity_type"))
            put("p_entity_id", envelope.getValue("entity_id"))
            put("p_expected_version", envelope.getValue("base_version"))
            put("p_payload", envelope.getValue("payload"))
            put("p_deletion_log_id", envelope.getValue("deletion_log_id"))
            put("p_deletion_reason", envelope.getValue("deletion_reason"))
            put("p_deletion_snapshot", envelope.getValue("deletion_snapshot"))
        }
        repeat(2) {
            val response = Json.parseToJsonElement(client.postgrest.rpc("apply_ledger_mutation", replay).data).jsonObject
            assertEquals(receipt.getValue("request_id"), response.getValue("request_id"))
        }
        val afterFeed = Json.parseToJsonElement(client.postgrest["sync_changes"].select {
            filter { eq("record_id", customerId) }
        }.data).jsonArray.size
        assertEquals(beforeFeed, afterFeed)
        assertTrue(container.authRepository.refreshSession().isSuccess)
        assertEquals(owner, client.auth.currentUserOrNull()?.id)
        val foreignOwnerRows = client.postgrest["customers"].select {
            filter { eq("owner_id", UUID.randomUUID().toString()) }
        }.decodeList<CustomerDto>()
        assertTrue(foreignOwnerRows.isEmpty())
        android.util.Log.i("LiveSyncQA", "PASS: duplicate RPC replay adds no change events; session refresh; foreign-owner filter")


        // Fresh Room projection and fresh cursor, without clearing the real ledger.
        val restoreRoot = File(context.cacheDir, "qa-restore-${UUID.randomUUID()}").apply { mkdirs() }
        val restoreContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = restoreRoot
        }
        val restored = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val restoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val restoreStore = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
            scope = restoreScope, produceFile = { File(restoreRoot, "cursor.preferences_pb") })
        try {
            val hydration = CloudHydrationManager(restored, client, restoreContext,
                SyncCursorPreferences(restoreContext, restoreStore))
            do { val page = hydration.hydrateFromCloud(owner); check(page.skippedConflictCount == 0) }
            while (page.hasMore)
            assertNotNull(restored.customerDao().getById(customerId))
            assertEquals(100L, restored.workEntryDao().getById(workId)?.totalPaise)
            assertEquals(40L, restored.paymentDao().getById(paymentId)?.amountPaise)
            assertEquals(0, restored.outboxDao().getUnconfirmedCount())
            val repeated = hydration.hydrateFromCloud(owner)
            assertEquals(0, repeated.downloadedCount)
            android.util.Log.i("LiveSyncQA", "PASS: empty-device hydration, relationships, no repeated download")

            container.paymentRepository.softDeletePayment(paymentId, "QA workflow completed")
            container.workRepository.softDeleteWorkEntry(workId, "QA workflow completed")
            container.customerRepository.softDeleteCustomer(customerId, "QA workflow completed")
            awaitCondition("automatic soft-delete upload") {
                container.database.outboxDao().getUnconfirmed().none { it.entityId in setOf(customerId, workId, paymentId) }
            }
            val tombstone = client.postgrest["customers"].select { filter { eq("id", customerId) } }.decodeList<CustomerDto>().single()
            assertTrue(tombstone.isDeleted)
            do { val page = hydration.hydrateFromCloud(owner); check(page.skippedConflictCount == 0) }
            while (page.hasMore)
            assertNull(restored.customerDao().getById(customerId))
            assertNull(restored.workEntryDao().getById(workId))
            assertNull(restored.paymentDao().getById(paymentId))
            assertTrue(checkNotNull(restored.customerDao().getByIdIncludeDeleted(customerId)).isDeleted)
            android.util.Log.i("LiveSyncQA", "PASS: cloud tombstones invalidate restored records without hard deletion")
            fixtureFile.delete()
        } finally {
            restored.close()
            restoreScope.cancel()
            restoreRoot.deleteRecursively()
        }
    }
}
