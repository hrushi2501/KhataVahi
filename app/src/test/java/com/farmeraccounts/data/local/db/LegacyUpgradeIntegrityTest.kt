package com.farmeraccounts.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class LegacyUpgradeIntegrityTest {
    @Test fun `v2 financial rows migrate with foreign keys enabled`() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="legacy_complete_upgrade.db"
        context.deleteDatabase(name)
        val schemaPath=listOf(File("../schemas/com.farmeraccounts.data.local.db.AppDatabase/2.json"), File("schemas/com.farmeraccounts.data.local.db.AppDatabase/2.json")).first { it.exists() }
        val schema=Json.parseToJsonElement(schemaPath.readText()).jsonObject.getValue("database").jsonObject
        val helper=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object:SupportSQLiteOpenHelper.Callback(2) {
                override fun onConfigure(db:SupportSQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
                override fun onCreate(db:SupportSQLiteDatabase) {
                    for(e in schema.getValue("entities").jsonArray) {
                        val entity=e.jsonObject;val table=entity.getValue("tableName").jsonPrimitive.content
                        db.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}",table))
                        entity.getValue("indices").jsonArray.forEach { db.execSQL(it.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}",table)) }
                    }
                    db.execSQL("INSERT INTO customers VALUES ('c','Owner',NULL,NULL,NULL,NULL,1,1,1,0)")
                    db.execSQL("INSERT INTO tractors VALUES ('tractor_big_mahindra_sarpanch','Big','Mahindra Sarpanch','BIG',1,1,1,0)")
                    db.execSQL("INSERT INTO work_entries VALUES ('w','c','tractor_big_mahindra_sarpanch','PLOUGHING',1,1000,'BIGHA',10000,10000,NULL,1,1,1,0)")
                    db.execSQL("INSERT INTO expenses VALUES ('e','tractor_big_mahindra_sarpanch','DIESEL',2000,1,NULL,1,1,1,0)")
                    db.execSQL("INSERT INTO audit_logs (id,entity_type,entity_id,action,details,timestamp) VALUES ('a','TRACTOR','tractor_big_mahindra_sarpanch','INSERT','Original history',1)")
                    db.execSQL("INSERT INTO outbox (entity_type,entity_id,operation_type,created_at,retry_count,sync_status) VALUES ('TRACTOR','tractor_big_mahindra_sarpanch','INSERT',1,0,'PENDING')")
                }
                override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=Unit
            }).build())
        helper.writableDatabase;helper.close()
        val db=Room.databaseBuilder(context,AppDatabase::class.java,name).addMigrations(AppDatabase.MIGRATION_2_3,AppDatabase.MIGRATION_3_4).allowMainThreadQueries().build()
        try {
            assertEquals(AppDatabase.SEED_TRACTOR_BIG_ID,db.workEntryDao().getById("w")!!.tractorId)
            assertEquals(10000L,db.workEntryDao().getById("w")!!.totalPaise)
            assertEquals(AppDatabase.SEED_TRACTOR_BIG_ID,db.expenseDao().getById("e")!!.tractorId)
            assertEquals("Original history",db.auditLogDao().getById("a")!!.details)
            assertEquals(AppDatabase.SEED_TRACTOR_BIG_ID,db.auditLogDao().getById("a")!!.entityId)
            assertEquals(AppDatabase.SEED_TRACTOR_BIG_ID,db.outboxDao().getPending().single().entityId)
            db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally {db.close();context.deleteDatabase(name)}
    }
}
