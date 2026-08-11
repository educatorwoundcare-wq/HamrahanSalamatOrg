package com.example.data.auth

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SessionManager(
    val tokenManager: TokenManager
) {

    companion object {
        private const val TAG = "SessionManager"
    }

    suspend fun getValidIdToken(): String? {
        return tokenManager.getValidIdToken()
    }

    suspend fun ensureActiveSession(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val token = tokenManager.getValidIdToken()
            !token.isNullOrEmpty()
        } catch (e: Exception) {
            AuthLogger.logError("EnsureSession", "Failed to ensure active session", e)
            false
        }
    }

    suspend fun onDatabaseRestored(): Boolean = withContext(Dispatchers.IO) {
        AuthLogger.logEvent("DatabaseRestore", "Database restored. Purging stale auth tokens and re-bootstrapping session...")
        return@withContext try {
            tokenManager.clearTokens()
            val newToken = tokenManager.getValidIdToken()
            val success = !newToken.isNullOrEmpty()
            AuthLogger.logEvent("DatabaseRestore", "Session re-bootstrap success: $success")
            success
        } catch (e: Exception) {
            AuthLogger.logError("DatabaseRestore", "Error re-bootstrapping session after database restore", e)
            false
        }
    }

    suspend fun clearSession() = withContext(Dispatchers.IO) {
        tokenManager.clearTokens()
    }
}
