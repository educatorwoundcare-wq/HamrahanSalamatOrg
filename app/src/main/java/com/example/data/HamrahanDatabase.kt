package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Patient::class,
        Employee::class,
        Service::class,
        ServiceRegistration::class,
        FinancialTransaction::class,
        Cashbox::class,
        CommissionSettlement::class,
        Expense::class,
        ExpenseCategory::class,
        FixedExpenseTemplate::class,
        FinancialReport::class,
        SystemSetting::class,
        AuditLog::class,
        UserPermission::class,
        FinancialEditHistory::class,
        JournalEntry::class,
        SyncMetadata::class,
        CloudSyncRecord::class,
        ConnectedDevice::class,
        Referral::class,
        ReferralCommission::class,
        Alert::class,
        Contract::class,
        StaffProfile::class,
        ServiceSchedule::class,
        NursingReport::class,
        VitalSigns::class,
        WoundRecord::class,
        ConsentForm::class,
        Prescription::class,
        DashboardCache::class,
        SyncQueue::class,
        DiagnosticEvent::class
    ],
    version = 23,
    exportSchema = false,
    autoMigrations = []
)
abstract class HamrahanDatabase : RoomDatabase() {
    abstract fun hamrahanDao(): HamrahanDao
    // abstract fun syncQueueDao(): SyncQueueDao // Will add later

    companion object {
        @Volatile
        private var INSTANCE: HamrahanDatabase? = null

        private val CDCCallback = object : RoomDatabase.Callback() {
            override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                super.onOpen(db)
                val tables = listOf("patients", "services", "financial_transactions", "expenses", "employees", "service_registrations")
                
                tables.forEach { tableName ->
                    // Insert Trigger
                    db.execSQL("""
                        CREATE TRIGGER IF NOT EXISTS ${tableName}_insert_trigger AFTER INSERT ON $tableName
                        BEGIN
                            INSERT INTO sync_queue (tableName, recordId, operationType, timestamp, status, retryCount)
                            VALUES ('$tableName', NEW.id, 'INSERT', strftime('%s','now') * 1000, 'PENDING', 0);
                        END;
                    """.trimIndent())

                    // Update Trigger
                    db.execSQL("""
                        CREATE TRIGGER IF NOT EXISTS ${tableName}_update_trigger AFTER UPDATE ON $tableName
                        BEGIN
                            INSERT INTO sync_queue (tableName, recordId, operationType, timestamp, status, retryCount)
                            VALUES ('$tableName', NEW.id, 'UPDATE', strftime('%s','now') * 1000, 'PENDING', 0);
                        END;
                    """.trimIndent())

                    // Delete Trigger
                    db.execSQL("""
                        CREATE TRIGGER IF NOT EXISTS ${tableName}_delete_trigger AFTER DELETE ON $tableName
                        BEGIN
                            INSERT INTO sync_queue (tableName, recordId, operationType, timestamp, status, retryCount)
                            VALUES ('$tableName', OLD.id, 'DELETE', strftime('%s','now') * 1000, 'PENDING', 0);
                        END;
                    """.trimIndent())
                }
            }
        }

        private fun addColumnIfNotExists(db: androidx.sqlite.db.SupportSQLiteDatabase, tableName: String, columnName: String, columnDef: String) {
            try {
                val cursor = db.query("PRAGMA table_info(`$tableName`)")
                var exists = false
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0 && cursor.getString(nameIndex) == columnName) {
                        exists = true
                        break
                    }
                }
                cursor.close()
                if (!exists) {
                    db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `$columnName` $columnDef")
                }
            } catch (e: Exception) {
                android.util.Log.e("HamrahanDatabase", "Error checking/adding column $columnName to $tableName", e)
            }
        }

        private fun runSafeMigrations(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            // Safe column additions
            addColumnIfNotExists(db, "patients", "referralId", "INTEGER DEFAULT NULL")
            addColumnIfNotExists(db, "service_registrations", "scheduledDate", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfNotExists(db, "service_registrations", "serviceDate", "INTEGER NOT NULL DEFAULT 0")

            try {
                db.execSQL("UPDATE service_registrations SET scheduledDate = dateTime WHERE scheduledDate = 0")
                db.execSQL("UPDATE service_registrations SET serviceDate = dateTime WHERE serviceDate = 0")
            } catch (e: Exception) {}

            // Safe table creations for any version update
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `referrals` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                    `name` TEXT NOT NULL, 
                    `type` TEXT NOT NULL, 
                    `phone` TEXT NOT NULL DEFAULT '', 
                    `address` TEXT NOT NULL DEFAULT '', 
                    `commissionPercentage` REAL NOT NULL DEFAULT 0.0, 
                    `commissionFixedAmount` REAL NOT NULL DEFAULT 0.0, 
                    `notes` TEXT NOT NULL DEFAULT '', 
                    `isActive` INTEGER NOT NULL DEFAULT 1, 
                    `uuid` TEXT NOT NULL
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `referral_commissions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                    `referralId` INTEGER NOT NULL, 
                    `patientId` INTEGER NOT NULL, 
                    `serviceRegistrationId` INTEGER NOT NULL, 
                    `serviceName` TEXT NOT NULL, 
                    `serviceAmount` REAL NOT NULL, 
                    `commissionPercentage` REAL NOT NULL, 
                    `commissionAmount` REAL NOT NULL, 
                    `date` INTEGER NOT NULL, 
                    `status` TEXT NOT NULL, 
                    `paymentDate` INTEGER, 
                    `documentNumber` TEXT NOT NULL DEFAULT '', 
                    `notes` TEXT NOT NULL DEFAULT '', 
                    `uuid` TEXT NOT NULL
                )
            """.trimIndent())

            db.execSQL("CREATE TABLE IF NOT EXISTS `dashboard_caches` (`key` TEXT NOT NULL, `dataJson` TEXT NOT NULL, `updatedTimestamp` INTEGER NOT NULL, `uuid` TEXT NOT NULL, PRIMARY KEY(`key`))")
        }

        // Generate non-destructive migrations from ALL previous database versions (1..19) to version 20
        val ALL_MIGRATIONS: List<androidx.room.migration.Migration> = (1..19).map { startVersion ->
            object : androidx.room.migration.Migration(startVersion, 20) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    runSafeMigrations(db)
                }
            }
        }
        
        val MIGRATION_21_22 = object : androidx.room.migration.Migration(21, 22) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("UPDATE system_settings SET value = 'COMP-A4458D65' WHERE key = 'company_id' AND value = 'COMP-5938C8A0'")
                db.execSQL("UPDATE system_settings SET value = 'HAMRAHAN-8D08C4' WHERE key = 'company_sync_code' AND value = 'HAMRAHAN-F1A485'")
                db.execSQL("UPDATE connected_devices SET companyId = 'COMP-A4458D65' WHERE companyId = 'COMP-5938C8A0'")
                try { db.execSQL("UPDATE cloud_sync_records SET companyId = 'COMP-A4458D65' WHERE companyId = 'COMP-5938C8A0'") } catch (e: Exception) {}
            }
        }

        val MIGRATION_20_21 = object : androidx.room.migration.Migration(20, 21) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `sync_queue` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `tableName` TEXT NOT NULL, 
                        `recordId` TEXT NOT NULL, 
                        `operationType` TEXT NOT NULL, 
                        `timestamp` INTEGER NOT NULL, 
                        `status` TEXT NOT NULL, 
                        `retryCount` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        fun getDatabase(context: Context): HamrahanDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HamrahanDatabase::class.java,
                    "hamrahan_salamat_db"
                )
                    .addMigrations(*ALL_MIGRATIONS.toTypedArray())
                    .addMigrations(MIGRATION_20_21, MIGRATION_21_22)
                    .addCallback(CDCCallback)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun resetInstance() {
            synchronized(this) {
                INSTANCE = null
            }
        }
    }
}
