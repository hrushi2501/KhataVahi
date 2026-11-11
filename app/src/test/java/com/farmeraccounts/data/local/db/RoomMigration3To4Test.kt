package com.farmeraccounts.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.farmeraccounts.data.local.db.entity.DeletedLogEntity
import com.farmeraccounts.data.local.db.entity.SyncVersionEntity
import com.farmeraccounts.data.local.db.entity.UploadAttemptEntity
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RoomMigration3To4Test {
    @Test
    fun `actual Room migration preserves ledger and pending outbox and validates v4 schema`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration_3_4_test.db"
        context.deleteDatabase(name)
        val schemaFile = listOf(File("../schemas/com.farmeraccounts.data.local.db.AppDatabase/3.json"),
            File("schemas/com.farmeraccounts.data.local.db.AppDatabase/3.json")).first { it.exists() }
        val schema = Json.parseToJsonElement(schemaFile.readText()).jsonObject.getValue("database").jsonObject
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    for (entry in schema.getValue("entities").jsonArray) {
                        val entity = entry.jsonObject
                        val table = entity.getValue("tableName").jsonPrimitive.content
                        db.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                        for (index in entity.getValue("indices").jsonArray) {
                            db.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                        }
                    }
                    db.execSQL("INSERT INTO customers VALUES ('c','Farmer',NULL,NULL,NULL,NULL,1,1,1,0)")
                    db.execSQL("INSERT INTO outbox (entity_type,entity_id,operation_type,created_at,retry_count,sync_status) VALUES ('CUSTOMER','c','INSERT',1,0,'PENDING')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        helper.writableDatabase
        helper.close()
        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_3_4).allowMainThreadQueries().build()
        try {
            assertEquals("Farmer", migrated.customerDao().getById("c")!!.name)
            assertEquals(1, migrated.outboxDao().getPendingCount())
            val log = DeletedLogEntity("d", "CUSTOMER", "c", "{}", "test", 2)
            migrated.deletedLogDao().insert(log)
            assertEquals(log, migrated.deletedLogDao().getById("d"))
            assertNull(migrated.syncVersionDao().get("CUSTOMER", "c"))
            migrated.syncVersionDao().set(SyncVersionEntity("CUSTOMER", "c", 3))
            migrated.syncVersionDao().set(SyncVersionEntity("CUSTOMER", "c", 4))
            assertEquals(4L, migrated.syncVersionDao().get("CUSTOMER", "c"))
            val attempt = UploadAttemptEntity("request", "CUSTOMER", "c", "{}", null, 1, "[1]")
            migrated.uploadAttemptDao().insert(attempt)
            assertEquals(listOf(attempt), migrated.uploadAttemptDao().getAll())
            migrated.uploadAttemptDao().delete("request")
            assertTrue(migrated.uploadAttemptDao().getAll().isEmpty())
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }
}
