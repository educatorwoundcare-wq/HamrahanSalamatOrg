package com.example.data

import android.util.Log

object WorkspaceSanitizer {
    const val CANONICAL_COMPANY_ID = "COMP-A4458D65"
    const val CANONICAL_SYNC_CODE = "HAMRAHAN-8D08C4"
    const val DUPLICATE_COMPANY_ID = "COMP-5938C8A0"
    const val DUPLICATE_SYNC_CODE = "HAMRAHAN-F1A485"

    fun getCanonicalCompanyId(current: String?): String? {
        if (current == DUPLICATE_COMPANY_ID) {
            Log.i("WorkspaceSanitizer", "Sanitized duplicate company_id to canonical")
            return CANONICAL_COMPANY_ID
        }
        return current
    }

    fun getCanonicalSyncCode(current: String?): String? {
        if (current == DUPLICATE_SYNC_CODE) {
            Log.i("WorkspaceSanitizer", "Sanitized duplicate sync_code to canonical")
            return CANONICAL_SYNC_CODE
        }
        return current
    }
}
