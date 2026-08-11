package com.example.data

import android.util.Log

class ConflictResolver(
    private val dao: HamrahanDao
) {
    /**
     * Resolves a conflict using Last-Write-Wins (LWW) strategy based on timestamps.
     * Optionally could be Server-Wins.
     * 
     * @return true if the local change should be applied (local wins or server wins but we merge), 
     *         false if the local change is rejected (server wins and overwrites local).
     */
    suspend fun resolveConflict(
        tableName: String,
        recordId: String,
        localTimestamp: Long,
        remoteTimestamp: Long,
        deviceId: String,
        user: String
    ): Boolean {
        // Last-Write-Wins strategy
        if (localTimestamp >= remoteTimestamp) {
            // Local change is newer, so it wins.
            return true
        } else {
            // Server change is newer (or same). Server wins.
            // Reject local change and log to AuditLog so management staff can review.
            logConflict(tableName, recordId, localTimestamp, remoteTimestamp, deviceId, user)
            return false
        }
    }

    private suspend fun logConflict(
        tableName: String,
        recordId: String,
        localTimestamp: Long,
        remoteTimestamp: Long,
        deviceId: String,
        user: String
    ) {
        try {
            val auditLog = AuditLog(
                timestamp = System.currentTimeMillis(),
                relatedScreen = "Sync",
                user = user,
                device = deviceId,
                action = "Conflict Rejected",
                affectedModule = mapTableNameToModule(tableName),
                details = "تغییر محلی رکورد $recordId در جدول $tableName رد شد. نسخه سرور جدیدتر است (زمان سرور: $remoteTimestamp، زمان محلی: $localTimestamp)."
            )
            dao.insertAuditLog(auditLog)
        } catch (e: Exception) {
            Log.e("ConflictResolver", "Error logging conflict", e)
        }
    }

    private fun mapTableNameToModule(tableName: String): String {
        return when (tableName) {
            "financial_transactions", "expenses", "journal_entries" -> "Finance"
            "patients", "consent_forms", "prescriptions", "vital_signs", "wound_records" -> "Patients"
            "employees", "staff_profiles", "contracts" -> "Personnel"
            "services", "service_registrations", "service_schedules" -> "Services"
            else -> "System"
        }
    }
}
