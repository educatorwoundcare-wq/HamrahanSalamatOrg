import sys
with open('app/src/main/java/com/example/data/CloudClient.kt', 'r') as f:
    content = f.read()

target = """            if (!refreshed) {
                if (!storedAuthUserId.isNullOrBlank()) {
                    Log.e("AUTH_TRACE", "AUTH_IDENTITY_DRIFT_DETECTED: Stored UID exists ($storedAuthUserId) but session is unrecoverable.")
                    Log.e("AUTH_TRACE", "AUTH_ANONYMOUS_FALLBACK_BLOCKED: Refusing to create new anonymous identity for existing device.")
                    Log.e("AUTH_TRACE", "AUTH_SESSION_UNRECOVERABLE")
                    return com.example.data.supabase.AuthResult.Error("AUTH_SESSION_UNRECOVERABLE")
                } else {
                    val syncCode = targetSyncCode ?: workspaceManager.currentSyncCode ?: dao?.getSystemSettingByKey("company_sync_code") ?: ""
                    
                    Log.i("AUTH_TRACE", "Token missing/expired. Authenticating anonymously for companyId=$companyId, syncCode=$syncCode")
                    val authResult = authRepo.signInAnonymously(companyId, syncCode)
                    if (authResult !is com.example.data.supabase.AuthResult.Success) {
                        Log.e("AUTH_TRACE", "Anonymous authentication failed: $authResult")
                        return authResult
                    }
                }
            }"""

replacement = """            if (!refreshed) {
                // Strict isolation of anonymous fallback (Initial Bootstrap Only)
                val localCompanyId = dao?.getSystemSettingByKey("company_id")
                val localPendingId = dao?.getSystemSettingByKey("pending_company_id")
                val isExistingDevice = !localCompanyId.isNullOrBlank() || !localPendingId.isNullOrBlank()

                if (!storedAuthUserId.isNullOrBlank() || isExistingDevice) {
                    Log.e("AUTH_TRACE", "AUTH_IDENTITY_DRIFT_DETECTED: Stored UID exists ($storedAuthUserId) or Device is registered (companyId=$localCompanyId, pendingId=$localPendingId).")
                    Log.e("AUTH_TRACE", "AUTH_ANONYMOUS_FALLBACK_BLOCKED: Refusing to create new anonymous identity for existing device.")
                    Log.e("AUTH_TRACE", "AUTH_SESSION_UNRECOVERABLE")
                    return com.example.data.supabase.AuthResult.Error("AUTH_SESSION_UNRECOVERABLE")
                } else {
                    // Exact Allowed Scenario: Fresh installation without any prior workspace configuration
                    val syncCode = targetSyncCode ?: workspaceManager.currentSyncCode ?: dao?.getSystemSettingByKey("company_sync_code") ?: ""
                    
                    Log.i("AUTH_TRACE", "Token missing/expired. Authenticating anonymously (Fresh Bootstrap) for companyId=$companyId, syncCode=$syncCode")
                    val authResult = authRepo.signInAnonymously(companyId, syncCode)
                    if (authResult !is com.example.data.supabase.AuthResult.Success) {
                        Log.e("AUTH_TRACE", "Anonymous authentication failed: $authResult")
                        return authResult
                    }
                }
            }"""

new_content = content.replace(target, replacement)
with open('app/src/main/java/com/example/data/CloudClient.kt', 'w') as f:
    f.write(new_content)
print("Auth patched" if new_content != content else "Not patched")
