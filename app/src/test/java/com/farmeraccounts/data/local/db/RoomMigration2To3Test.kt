package com.farmeraccounts.data.local.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RoomMigration2To3Test {

    @Test
    fun `migration 2 to 3 adds last_error column and migrates legacy tractor IDs to valid UUIDs`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("test_migration_2_3.db")
        dbFile.delete()

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("test_migration_2_3.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE audit_logs (id TEXT PRIMARY KEY NOT NULL, entity_type TEXT NOT NULL, entity_id TEXT NOT NULL, action TEXT NOT NULL, details TEXT, timestamp INTEGER NOT NULL)")
                    // Create v2 tables
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `tractors` (
                            `id` TEXT NOT NULL,
                            `display_name` TEXT NOT NULL,
                            `model` TEXT,
                            `type` TEXT NOT NULL,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            `version` INTEGER NOT NULL,
                            `is_deleted` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `work_entries` (
                            `id` TEXT NOT NULL,
                            `customer_id` TEXT NOT NULL,
                            `tractor_id` TEXT NOT NULL,
                            `work_type` TEXT NOT NULL,
                            `date` INTEGER NOT NULL,
                            `quantity_scaled` INTEGER NOT NULL,
                            `quantity_unit` TEXT NOT NULL,
                            `rate_per_unit_paise` INTEGER NOT NULL,
                            `total_paise` INTEGER NOT NULL,
                            `notes` TEXT,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            `version` INTEGER NOT NULL,
                            `is_deleted` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `expenses` (
                            `id` TEXT NOT NULL,
                            `tractor_id` TEXT,
                            `category` TEXT NOT NULL,
                            `amount_paise` INTEGER NOT NULL,
                            `expense_date` INTEGER NOT NULL,
                            `notes` TEXT,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            `version` INTEGER NOT NULL,
                            `is_deleted` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `outbox` (
                            `sequence_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `entity_type` TEXT NOT NULL,
                            `entity_id` TEXT NOT NULL,
                            `operation_type` TEXT NOT NULL,
                            `created_at` INTEGER NOT NULL,
                            `retry_count` INTEGER NOT NULL,
                            `sync_status` TEXT NOT NULL
                        )
                    """.trimIndent())

                    // Insert legacy seed tractors and dependent entries
                    db.execSQL("INSERT INTO `tractors` VALUES ('tractor_big_mahindra_sarpanch', 'Big Tractor', 'Mahindra Sarpanch', 'BIG', 1000, 1000, 1, 0)")
                    db.execSQL("INSERT INTO `tractors` VALUES ('tractor_mini_unknown', 'Mini Tractor', NULL, 'MINI', 1000, 1000, 1, 0)")
                    db.execSQL("INSERT INTO `work_entries` VALUES ('w1', 'c1', 'tractor_big_mahindra_sarpanch', 'Ploughing', 1000, 2000, 'BIGHA', 50000, 100000, NULL, 1000, 1000, 1, 0)")
                    db.execSQL("INSERT INTO `expenses` VALUES ('e1', 'tractor_big_mahindra_sarpanch', 'DIESEL', 200000, 1000, NULL, 1000, 1000, 1, 0)")
                    db.execSQL("INSERT INTO `outbox` VALUES (1, 'TRACTOR', 'tractor_big_mahindra_sarpanch', 'INSERT', 1000, 0, 'PENDING')")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val writableDb = helper.writableDatabase

        // Execute Migration 2 to 3
        AppDatabase.MIGRATION_2_3.migrate(writableDb)

        // Verify column last_error exists in outbox
        val cursorOutbox = writableDb.query("PRAGMA table_info(outbox)")
        var hasLastErrorColumn = false
        while (cursorOutbox.moveToNext()) {
            val colName = cursorOutbox.getString(cursorOutbox.getColumnIndexOrThrow("name"))
            if (colName == "last_error") {
                hasLastErrorColumn = true
                break
            }
        }
        cursorOutbox.close()
        assertTrue("outbox table must have last_error column", hasLastErrorColumn)

        // Verify tractor IDs migrated to valid UUIDs
        val cursorTractor = writableDb.query("SELECT id, model FROM tractors WHERE id = '${AppDatabase.SEED_TRACTOR_BIG_ID}'")
        assertTrue("Big tractor must exist with new UUID", cursorTractor.moveToFirst())
        assertEquals("Mahindra Sarpanch", cursorTractor.getString(cursorTractor.getColumnIndexOrThrow("model")))
        cursorTractor.close()

        val cursorMini = writableDb.query("SELECT id FROM tractors WHERE id = '${AppDatabase.SEED_TRACTOR_MINI_ID}'")
        assertTrue("Mini tractor must exist with new UUID", cursorMini.moveToFirst())
        cursorMini.close()

        // Verify foreign key in work_entries was updated
        val cursorWork = writableDb.query("SELECT tractor_id FROM work_entries WHERE id = 'w1'")
        assertTrue(cursorWork.moveToFirst())
        assertEquals(AppDatabase.SEED_TRACTOR_BIG_ID, cursorWork.getString(0))
        cursorWork.close()

        // Verify outbox entity_id was updated
        val cursorOutboxItem = writableDb.query("SELECT entity_id FROM outbox WHERE sequence_id = 1")
        assertTrue(cursorOutboxItem.moveToFirst())
        assertEquals(AppDatabase.SEED_TRACTOR_BIG_ID, cursorOutboxItem.getString(0))
        cursorOutboxItem.close()

        writableDb.close()
    }
}

