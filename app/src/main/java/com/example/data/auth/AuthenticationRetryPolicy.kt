package com.example.data.auth

import com.example.data.FirebaseException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

object AuthenticationRetryPolicy {

    suspend fun <T> executeWithRetry(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 1000L,
        maxDelayMs: Long = 8000L,
        factor: Double = 2.0,
        actionName: String = "AuthOperation",
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        var lastException: Throwable? = null

        for (attempt in 1..maxAttempts) {
            if (!coroutineContext.isActive) {
                throw FirebaseException.NetworkError("عملیات به علت لغو Coroutine متوقف شد.", Exception("Cancelled"))
            }

            try {
                return block()
            } catch (e: Exception) {
                lastException = e

                val isNonRetryable = isNonRetryableError(e)
                if (isNonRetryable || attempt == maxAttempts) {
                    AuthLogger.logError(actionName, "Attempt $attempt failed with non-retryable or max attempts reached.", e)
                    throw if (e is FirebaseException) e else AuthenticationErrorMapper.mapThrowable(e, "شکست عملیات پس از $attempt تلاش")
                }

                // Calculate exponential delay with random jitter (0.85x to 1.15x)
                val jitter = 0.85 + (Random.nextDouble() * 0.3)
                val delayWithJitter = (currentDelay * jitter).toLong().coerceAtMost(maxDelayMs)

                AuthLogger.logEvent(actionName, "Attempt $attempt/$maxAttempts failed: ${e.message}. Retrying in ${delayWithJitter}ms...")
                delay(delayWithJitter)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
            }
        }

        throw lastException ?: FirebaseException.UnknownError(0, "تلاش‌های مکرر ناموفق بود.")
    }

    private fun isNonRetryableError(e: Exception): Boolean {
        if (e is FirebaseException.AuthenticationError) {
            val msg = (e.firebaseError ?: e.message ?: "").uppercase()
            if (msg.contains("INVALID_KEY") || msg.contains("API KEY NOT VALID") || msg.contains("USER_DISABLED") || msg.contains("OPERATION_NOT_ALLOWED")) {
                return true
            }
        }
        if (e is FirebaseException.WorkspaceNotFound || e is FirebaseException.PermissionDenied) {
            return true
        }
        return false
    }
}
