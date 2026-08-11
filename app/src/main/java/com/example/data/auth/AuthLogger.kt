package com.example.data.auth

import android.util.Log

object AuthLogger {
    private const val TAG = "FirebaseAuth_Diag"

    fun logRequest(
        method: String,
        url: String,
        hasAuthToken: Boolean,
        hasAuthParam: Boolean,
        anonymousUid: String?,
        workspaceId: String?
    ) {
        val sanitizedUrl = sanitizeUrl(url)
        Log.d(TAG, "--- START Auth Request ---")
        Log.d(TAG, "Method: $method | URL: $sanitizedUrl")
        Log.d(TAG, "Token Exists: $hasAuthToken | Auth Param Appended: $hasAuthParam")
        Log.d(TAG, "Anonymous UID: ${anonymousUid ?: "N/A"} | Workspace: ${workspaceId ?: "N/A"}")
    }

    fun logResponse(
        method: String,
        url: String,
        statusCode: Int,
        executionTimeMs: Long,
        tokenAgeSeconds: Long,
        isSuccess: Boolean,
        firebaseError: String? = null
    ) {
        val sanitizedUrl = sanitizeUrl(url)
        Log.d(TAG, "HTTP Response Code: $statusCode | Duration: ${executionTimeMs}ms | Token Age: ${tokenAgeSeconds}s | Success: $isSuccess")
        if (!isSuccess && !firebaseError.isNullOrBlank()) {
            Log.w(TAG, "Firebase Error Reason: $firebaseError")
        }
        Log.d(TAG, "--- END Auth Request ($sanitizedUrl) ---")
    }

    fun logEvent(event: String, details: String = "") {
        if (details.isNotBlank()) {
            Log.i(TAG, "[$event] $details")
        } else {
            Log.i(TAG, "[$event]")
        }
    }

    fun logError(event: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(TAG, "[$event] $message (${throwable.message})")
        } else {
            Log.w(TAG, "[$event] $message")
        }
    }

    fun sanitizeUrl(url: String): String {
        return url.replace(Regex("auth=[^&]+"), "auth=[REDACTED_TOKEN]")
            .replace(Regex("key=[^&]+"), "key=[REDACTED_API_KEY]")
    }
}
