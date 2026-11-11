package com.farmeraccounts.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.farmeraccounts.data.local.db.AppDatabase
import com.farmeraccounts.data.local.db.entity.CustomerEntity
import com.farmeraccounts.data.local.db.entity.OutboxEntity
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MutationReceiptReplayTest {
    @Test fun `lost confirmation replays frozen request before a newer edit`() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(),AppDatabase::class.java).build()
        try {
            db.customerDao().insert(CustomerEntity(id="c1",name="Original",createdAt=1L,updatedAt=1L))
            db.outboxDao().insert(OutboxEntity(entityType="CUSTOMER",entityId="c1",operationType="INSERT"))
            val received=mutableListOf<JsonObject>()
            val receipts=mutableMapOf<String,String>()
            val submit: suspend (JsonObject)->String = { params ->
                received.add(params)
                val id=params.getValue("p_request_id").jsonPrimitive.content
                val receipt=receipts.getOrPut(id) {
                    buildJsonObject {
                        put("request_id",id)
                        put("version",params.getValue("p_payload").jsonObject.getValue("version"))
                    }.toString()
                }
                if(received.size==1) throw IOException("Response lost after commit")
                receipt
            }
            val processor=OutboxSyncProcessor(db,submit)
            assertTrue(processor.processPending("owner").failedCount>0)
            assertEquals(1,db.uploadAttemptDao().getAll().size)
            db.customerDao().update(db.customerDao().getById("c1")!!.copy(name="New edit",version=2,updatedAt=2L))
            db.outboxDao().insert(OutboxEntity(entityType="CUSTOMER",entityId="c1",operationType="UPDATE"))
            assertEquals(0,processor.processPending("owner").failedCount)
            assertEquals(received[0],received[1])
            assertNotEquals(received[1].getValue("p_request_id"),received[2].getValue("p_request_id"))
            assertEquals(1L,received[2].getValue("p_expected_version").jsonPrimitive.long)
            assertEquals("New edit",received[2].getValue("p_payload").jsonObject.getValue("name").jsonPrimitive.content)
            assertEquals(2,receipts.size)
            assertEquals(0,db.outboxDao().getPendingCount())
            assertTrue(db.uploadAttemptDao().getAll().isEmpty())
            assertEquals(2L,db.syncVersionDao().get("CUSTOMER","c1"))
        } finally { db.close() }
    }
}
