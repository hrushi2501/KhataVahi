package com.farmeraccounts.data.local.db

import android.content.Context
import com.farmeraccounts.data.local.db.entity.UploadAttemptEntity
import com.farmeraccounts.data.local.db.dao.UploadAttemptDao
import com.farmeraccounts.data.local.db.entity.DeletedLogEntity
import com.farmeraccounts.data.local.db.entity.SyncVersionEntity
import com.farmeraccounts.data.local.db.dao.DeletedLogDao
import com.farmeraccounts.data.local.db.dao.SyncVersionDao
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.farmeraccounts.data.local.db.dao.AuditLogDao
import com.farmeraccounts.data.local.db.dao.CustomerDao
import com.farmeraccounts.data.local.db.dao.ExpenseDao
import com.farmeraccounts.data.local.db.dao.OutboxDao
import com.farmeraccounts.data.local.db.dao.PaymentDao
import com.farmeraccounts.data.local.db.dao.TractorDao
import com.farmeraccounts.data.local.db.dao.WorkEntryDao
import com.farmeraccounts.data.local.db.entity.AuditLogEntity
import com.farmeraccounts.data.local.db.entity.CustomerEntity
import com.farmeraccounts.data.local.db.entity.ExpenseEntity
import com.farmeraccounts.data.local.db.entity.OutboxEntity
import com.farmeraccounts.data.local.db.entity.PaymentEntity
import com.farmeraccounts.data.local.db.entity.TractorEntity
import com.farmeraccounts.data.local.db.entity.WorkEntryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Room ledger projection with durable offline mutations; confirmed records come from Supabase.
 * Version 2: Complete local accounting engine (Customers, Tractors, Work Entries, Payments, Expenses, Outbox).
 */
@Database(
    entities = [
        AuditLogEntity::class,
        CustomerEntity::class,
        TractorEntity::class,
        WorkEntryEntity::class,
        PaymentEntity::class,
        ExpenseEntity::class,
        OutboxEntity::class,
        DeletedLogEntity::class,
        UploadAttemptEntity::class,
        SyncVersionEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun auditLogDao(): AuditLogDao
    abstract fun customerDao(): CustomerDao
    abstract fun tractorDao(): TractorDao
    abstract fun workEntryDao(): WorkEntryDao
    abstract fun paymentDao(): PaymentDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun uploadAttemptDao(): UploadAttemptDao
    abstract fun deletedLogDao(): DeletedLogDao
    abstract fun syncVersionDao(): SyncVersionDao
    abstract fun outboxDao(): OutboxDao

    companion object {
        const val DATABASE_NAME = "farmer_accounts.db"

        // Deterministic valid RFC 4122 UUIDs for seed tractors (UUID v5 / namespace standard)
        const val SEED_TRACTOR_BIG_ID = "097d55a2-5687-53b5-acf5-df685e2549c1"
        const val SEED_TRACTOR_MINI_ID = "a939418c-eb1e-56dd-abe7-eefd2b09e8fe"

        // Legacy non-UUID seed IDs to migrate from
        private const val LEGACY_TRACTOR_BIG_ID = "tractor_big_mahindra_sarpanch"
        private const val LEGACY_TRACTOR_MINI_ID = "tractor_mini_unknown"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Customers Table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `customers` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `nickname` TEXT,
                        `father_name` TEXT,
                        `village` TEXT,
                        `phone` TEXT,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        `version` INTEGER NOT NULL,
                        `is_deleted` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_name` ON `customers` (`name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_village` ON `customers` (`village`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_phone` ON `customers` (`phone`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_is_deleted` ON `customers` (`is_deleted`)")

                // 2. Tractors Table
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
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tractors_type` ON `tractors` (`type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tractors_is_deleted` ON `tractors` (`is_deleted`)")

                // Seed Initial Tractors
                db.execSQL("""
                    INSERT OR IGNORE INTO `tractors` (`id`, `display_name`, `model`, `type`, `created_at`, `updated_at`, `version`, `is_deleted`)
                    VALUES ('$SEED_TRACTOR_BIG_ID', 'મોટો ટ્રેક્ટર (Big Tractor)', 'Mahindra Sarpanch', 'BIG', 1700000000000, 1700000000000, 1, 0)
                """.trimIndent())
                db.execSQL("""
                    INSERT OR IGNORE INTO `tractors` (`id`, `display_name`, `model`, `type`, `created_at`, `updated_at`, `version`, `is_deleted`)
                    VALUES ('$SEED_TRACTOR_MINI_ID', 'નાનો ટ્રેક્ટર (Mini Tractor)', NULL, 'MINI', 1700000000000, 1700000000000, 1, 0)
                """.trimIndent())

                // 3. Work Entries Table
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
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`customer_id`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`tractor_id`) REFERENCES `tractors`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_entries_customer_id` ON `work_entries` (`customer_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_entries_tractor_id` ON `work_entries` (`tractor_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_entries_date` ON `work_entries` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_work_entries_is_deleted` ON `work_entries` (`is_deleted`)")

                // 4. Payments Table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `payments` (
                        `id` TEXT NOT NULL,
                        `customer_id` TEXT NOT NULL,
                        `work_entry_id` TEXT,
                        `amount_paise` INTEGER NOT NULL,
                        `payment_date` INTEGER NOT NULL,
                        `payment_method` TEXT NOT NULL,
                        `notes` TEXT,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        `version` INTEGER NOT NULL,
                        `is_deleted` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`customer_id`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`work_entry_id`) REFERENCES `work_entries`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_customer_id` ON `payments` (`customer_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_work_entry_id` ON `payments` (`work_entry_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_payment_date` ON `payments` (`payment_date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_is_deleted` ON `payments` (`is_deleted`)")

                // 5. Expenses Table
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
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`tractor_id`) REFERENCES `tractors`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_tractor_id` ON `expenses` (`tractor_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_expense_date` ON `expenses` (`expense_date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_category` ON `expenses` (`category`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_is_deleted` ON `expenses` (`is_deleted`)")

                // 6. Outbox Table
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
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_outbox_sync_status` ON `outbox` (`sync_status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_outbox_created_at` ON `outbox` (`created_at`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Room wraps migration in a transaction. Defer parent/child UUID
                // checks until both sides have been updated; never disable FKs.
                db.execSQL("PRAGMA defer_foreign_keys = ON")
                // 1. Add last_error column to outbox table for persistent failure inspection
                db.execSQL("ALTER TABLE `outbox` ADD COLUMN `last_error` TEXT DEFAULT NULL")

                // 2. Resolve legacy non-UUID tractor identifiers to valid RFC 4122 UUIDs
                db.execSQL("UPDATE `tractors` SET `id` = '$SEED_TRACTOR_BIG_ID' WHERE `id` = '$LEGACY_TRACTOR_BIG_ID'")
                db.execSQL("UPDATE `tractors` SET `id` = '$SEED_TRACTOR_MINI_ID' WHERE `id` = '$LEGACY_TRACTOR_MINI_ID'")

                // 3. Update referencing foreign keys in work_entries
                db.execSQL("UPDATE `work_entries` SET `tractor_id` = '$SEED_TRACTOR_BIG_ID' WHERE `tractor_id` = '$LEGACY_TRACTOR_BIG_ID'")
                db.execSQL("UPDATE `work_entries` SET `tractor_id` = '$SEED_TRACTOR_MINI_ID' WHERE `tractor_id` = '$LEGACY_TRACTOR_MINI_ID'")

                // 4. Update referencing foreign keys in expenses
                db.execSQL("UPDATE `expenses` SET `tractor_id` = '$SEED_TRACTOR_BIG_ID' WHERE `tractor_id` = '$LEGACY_TRACTOR_BIG_ID'")
                db.execSQL("UPDATE `expenses` SET `tractor_id` = '$SEED_TRACTOR_MINI_ID' WHERE `tractor_id` = '$LEGACY_TRACTOR_MINI_ID'")

                // Referential identifiers change; preserve original event details/snapshots.
                db.execSQL("UPDATE audit_logs SET entity_id = '$SEED_TRACTOR_BIG_ID' WHERE entity_type = 'TRACTOR' AND entity_id = '$LEGACY_TRACTOR_BIG_ID'")
                db.execSQL("UPDATE audit_logs SET entity_id = '$SEED_TRACTOR_MINI_ID' WHERE entity_type = 'TRACTOR' AND entity_id = '$LEGACY_TRACTOR_MINI_ID'")

                // 5. Update any outbox entries referencing old tractor IDs
                db.execSQL("UPDATE `outbox` SET `entity_id` = '$SEED_TRACTOR_BIG_ID' WHERE `entity_id` = '$LEGACY_TRACTOR_BIG_ID'")
                db.execSQL("UPDATE `outbox` SET `entity_id` = '$SEED_TRACTOR_MINI_ID' WHERE `entity_id` = '$LEGACY_TRACTOR_MINI_ID'")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `deleted_logs` (
                    `id` TEXT NOT NULL, `entity_type` TEXT NOT NULL, `entity_id` TEXT NOT NULL,
                    `snapshot_json` TEXT NOT NULL, `reason` TEXT NOT NULL, `timestamp` INTEGER NOT NULL,
                    PRIMARY KEY(`id`))""")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_deleted_logs_timestamp` ON `deleted_logs` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_deleted_logs_entity_type_entity_id` ON `deleted_logs` (`entity_type`, `entity_id`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `upload_attempts` (
                    `request_id` TEXT NOT NULL, `entity_type` TEXT NOT NULL, `entity_id` TEXT NOT NULL,
                    `payload_json` TEXT NOT NULL, `expected_server_version` INTEGER,
                    `submitted_version` INTEGER NOT NULL, `sequence_ids_json` TEXT NOT NULL,
                    PRIMARY KEY(`request_id`))""")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `sync_versions` (
                    `entity_type` TEXT NOT NULL, `entity_id` TEXT NOT NULL, `server_version` INTEGER NOT NULL,
                    PRIMARY KEY(`entity_type`, `entity_id`))""")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Room invokes onCreate inside its creation transaction. Seed parents
                            // and their outbox entries together before any form can use them.
                            for ((id, name, model, type) in listOf(
                                listOf(SEED_TRACTOR_BIG_ID, "મોટો ટ્રેક્ટર (Big Tractor)", "Mahindra Sarpanch", "BIG"),
                                listOf(SEED_TRACTOR_MINI_ID, "નાનો ટ્રેક્ટર (Mini Tractor)", null, "MINI")
                            )) {
                                val now = System.currentTimeMillis()
                                db.execSQL("INSERT INTO tractors (id, display_name, model, type, created_at, updated_at, version, is_deleted) VALUES (?, ?, ?, ?, ?, ?, 1, 0)", arrayOf(id, name, model, type, now, now))
                                db.execSQL("INSERT INTO outbox (entity_type, entity_id, operation_type, created_at, retry_count, sync_status) VALUES ('TRACTOR', ?, 'INSERT', ?, 0, 'PENDING')", arrayOf(id, now))
                            }
                        }
                    })
                    .build().also { instance = it }
            }
        }
    }
}
