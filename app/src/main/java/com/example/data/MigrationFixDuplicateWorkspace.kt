package com.example.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Fix duplicate workspace collision
        db.execSQL("UPDATE system_settings SET value = 'COMP-A4458D65' WHERE key = 'company_id' AND value = 'COMP-5938C8A0'")
        db.execSQL("UPDATE system_settings SET value = 'HAMRAHAN-8D08C4' WHERE key = 'company_sync_code' AND value = 'HAMRAHAN-F1A485'")
        db.execSQL("UPDATE connected_devices SET companyId = 'COMP-A4458D65' WHERE companyId = 'COMP-5938C8A0'")
        db.execSQL("UPDATE cloud_sync_records SET companyId = 'COMP-A4458D65' WHERE companyId = 'COMP-5938C8A0'")
    }
}
