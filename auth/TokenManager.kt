package com.example.data.auth

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray
import android.util.Log
import com.example.data.FirebaseException
import com.example.data.HamrahanDao
import com.example.data.SystemSetting
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit

data class SignUpResponse(
    val idToken: String = "",
    val refreshToken: String = "",
    val expiresIn: String = "",
    val localId: String = ""
)

data class RefreshResponse(
    val id_token: String = "",
    val refresh_token: String = "",
    val expires_in: String = ""
)


class TokenManager(
    private val context: Context,
    private val dao: HamrahanDao,
    private val httpClient: OkHttpClient = defaultOkHttpClient
) {

    companion object {
        private const val TAG = "TokenManager"
        private const val TOKEN_EXPIRY_BUFFER_MS = 300_000L // 5 minutes buffer

        val defaultOkHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()
        }
    }

    private val refreshMutex = Mutex()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val signUpAdapter = moshi.adapter(SignUpResponse::class.java)
    private val refreshAdapter = moshi.adapter(RefreshResponse::class.java)

    /**
     * Resolves Firebase API Key dynamically from google-services.json generated R.string or fallback.
     */
    fun getFirebaseApiKey(): String {
        return try {
            val resId = context.resources.getIdentifier("google_api_key", "string", context.packageName)
            if (resId != 0) {
                val key = context.getString(resId)
                if (key.isNotBlank() && key != "remixed-api-key" && !key.contains("remixed")) return key
            }
            val root = readGoogleServicesJson()
            val clientArr = root?.optJSONArray("client")
            val clientObj = clientArr?.optJSONObject(0)
            val apiKeyArr = clientObj?.optJSONArray("api_key")
            val apiKeyObj = apiKeyArr?.optJSONObject(0)
            val rawKey = apiKeyObj?.optString("current_key", "") ?: ""
            if (rawKey != "remixed-api-key" && !rawKey.contains("remixed")) rawKey else ""
        } catch (e: Exception) {
            AuthLogger.logError("GetApiKey", "Failed to resolve API key from resources or google-services.json", e)
            ""
        }
    }

    /**
     * Resolves Firebase Realtime Database URL dynamically from google-services.json generated R.string or fallback.
     */
    fun getFirebaseDatabaseUrl(): String {
        return try {
            val resId = context.resources.getIdentifier("firebase_database_url", "string", context.packageName)
            if (resId != 0) {
                val url = context.getString(resId)
                if (url.isNotBlank()) return url
            }
            readGoogleServicesJson()?.optJSONObject("project_info")
                ?.optString("firebase_url", "") ?: "https://hamrahan-salamat-prod-default-rtdb.europe-west1.firebasedatabase.app"
        } catch (e: Exception) {
            AuthLogger.logError("GetDatabaseUrl", "Failed to resolve Firebase DB URL", e)
            "https://hamrahan-salamat-prod-default-rtdb.europe-west1.firebasedatabase.app"
        }
    }

    private fun readGoogleServicesJson(): JSONObject? {
        return try {
            val jsonStream = context.assets.open("google-services.json")
            val jsonStr = jsonStream.bufferedReader().use { it.readText() }
            JSONObject(jsonStr)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getValidIdToken(): String? = withContext(Dispatchers.IO) {
        val apiKey = getFirebaseApiKey()
        if (apiKey.isBlank() || apiKey == "remixed-api-key" || apiKey.contains("remixed")) {
            AuthLogger.logEvent("GetToken", "No custom Firebase API key found (or default placeholder key used). Operating in direct Realtime Database mode.")
            return@withContext null
        }

        // 1. Fast path: Read cached token outside lock
        val cachedToken = dao.getSystemSettingByKey("firebase_id_token")
        val cachedExpiryStr = dao.getSystemSettingByKey("firebase_id_token_expiry")
        val cachedExpiry = cachedExpiryStr?.toLongOrNull() ?: 0L
        val now = System.currentTimeMillis()

        if (!cachedToken.isNullOrEmpty() && cachedExpiry > (now + TOKEN_EXPIRY_BUFFER_MS)) {
            return@withContext cachedToken
        }

        // 2. Token is missing or expired: Acquire mutex lock for single-threaded refresh
        refreshMutex.withLock {
            try {
                // Double-check lock pattern
                val reCheckedToken = dao.getSystemSettingByKey("firebase_id_token")
                val reCheckedExpiry = dao.getSystemSettingByKey("firebase_id_token_expiry")?.toLongOrNull() ?: 0L
                if (!reCheckedToken.isNullOrEmpty() && reCheckedExpiry > (System.currentTimeMillis() + TOKEN_EXPIRY_BUFFER_MS)) {
                    return@withLock reCheckedToken
                }

                // Attempt token refresh if refresh token exists
                val cachedRefreshToken = dao.getSystemSettingByKey("firebase_refresh_token")
                if (!cachedRefreshToken.isNullOrEmpty()) {
                    try {
                        val refreshedToken = refreshIdTokenInternal(apiKey, cachedRefreshToken)
                        if (!refreshedToken.isNullOrEmpty()) {
                            AuthLogger.logEvent("TokenRefresh", "Successfully refreshed Firebase ID Token")
                            return@withLock refreshedToken
                        }
                    } catch (e: Exception) {
                        AuthLogger.logError("TokenRefresh", "Refresh token failed or revoked. Purging stale session and attempting anonymous re-authentication...", e)
                        clearTokensInternal()
                    }
                }

                // Attempt anonymous sign-up
                AuthLogger.logEvent("AnonymousSignUp", "Bootstrapping fresh anonymous session...")
                val newToken = signUpAnonymouslyInternal(apiKey)
                return@withLock newToken
            } catch (e: Exception) {
                AuthLogger.logError("GetToken", "Token acquisition failed with exception. Falling back to direct database operation.", e)
                return@withLock null
            }
        }
    }

    private suspend fun signUpAnonymouslyInternal(apiKey: String): String? = withContext(Dispatchers.IO) {
        val url = "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$apiKey"
        val requestJson = "{\"returnSecureToken\":true}"
        val request = Request.Builder()
            .url(url)
            .post(requestJson.toRequestBody(jsonMediaType))
            .build()

        val startTime = System.currentTimeMillis()
        return@withContext AuthenticationRetryPolicy.executeWithRetry(
            maxAttempts = 3,
            actionName = "AnonymousSignUp"
        ) {
            httpClient.newCall(request).execute().use { response ->
                val code = response.code
                val rawBody = response.body?.string()
                val duration = System.currentTimeMillis() - startTime

                AuthLogger.logResponse("POST", url, code, duration, 0L, response.isSuccessful, rawBody)

                if (response.isSuccessful && !rawBody.isNullOrEmpty()) {
                    val res = signUpAdapter.fromJson(rawBody)
                        ?: throw FirebaseException.InvalidResponse("پاسخ ساختاریافته از سرور دریافت نشد.")

                    val expiryTime = System.currentTimeMillis() + (res.expiresIn.toLongOrNull() ?: 3600L) * 1000L

                    // Atomically insert session credentials
                    dao.insertSystemSetting(SystemSetting("firebase_id_token", res.idToken))
                    dao.insertSystemSetting(SystemSetting("firebase_refresh_token", res.refreshToken))
                    dao.insertSystemSetting(SystemSetting("firebase_id_token_expiry", expiryTime.toString()))

                    // Persist active_device_id if missing or update
                    val existingDevId = dao.getSystemSettingByKey("active_device_id")
                    if (existingDevId.isNullOrEmpty() && res.localId.isNotBlank()) {
                        dao.insertSystemSetting(SystemSetting("active_device_id", res.localId))
                        AuthLogger.logEvent("AnonymousSignUp", "Set active_device_id to auth.uid: ${res.localId}")
                    }

                    res.idToken
                } else {
                    throw AuthenticationErrorMapper.mapHttpResponse(response, rawBody)
                }
            }
        }
    }

    private suspend fun refreshIdTokenInternal(apiKey: String, refreshToken: String): String? = withContext(Dispatchers.IO) {
        val url = "https://securetoken.googleapis.com/v1/token?key=$apiKey"
        val formBody = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        val startTime = System.currentTimeMillis()
        httpClient.newCall(request).execute().use { response ->
            val code = response.code
            val rawBody = response.body?.string()
            val duration = System.currentTimeMillis() - startTime

            AuthLogger.logResponse("POST", url, code, duration, 0L, response.isSuccessful, rawBody)

            if (response.isSuccessful && !rawBody.isNullOrEmpty()) {
                val res = refreshAdapter.fromJson(rawBody)
                    ?: throw FirebaseException.InvalidResponse("پاسخ تمدید توکن معتبر نیست.")

                val expiryTime = System.currentTimeMillis() + (res.expires_in.toLongOrNull() ?: 3600L) * 1000L

                dao.insertSystemSetting(SystemSetting("firebase_id_token", res.id_token))
                dao.insertSystemSetting(SystemSetting("firebase_refresh_token", res.refresh_token))
                dao.insertSystemSetting(SystemSetting("firebase_id_token_expiry", expiryTime.toString()))

                res.id_token
            } else {
                throw AuthenticationErrorMapper.mapHttpResponse(response, rawBody)
            }
        }
    }

    suspend fun clearTokens() = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            clearTokensInternal()
        }
    }

    private suspend fun clearTokensInternal() {
        try {
            dao.deleteSystemSettingByKey("firebase_id_token")
            dao.deleteSystemSettingByKey("firebase_refresh_token")
            dao.deleteSystemSettingByKey("firebase_id_token_expiry")
            AuthLogger.logEvent("ClearTokens", "Successfully purged cached Firebase auth tokens.")
        } catch (e: Exception) {
            AuthLogger.logError("ClearTokens", "Error purging token settings from Room DB", e)
        }
    }

    suspend fun getTokenAgeSeconds(): Long = withContext(Dispatchers.IO) {
        val expiryStr = dao.getSystemSettingByKey("firebase_id_token_expiry")?.toLongOrNull() ?: 0L
        if (expiryStr <= 0L) return@withContext -1L
        val issuedAt = expiryStr - 3600_000L
        val ageMs = System.currentTimeMillis() - issuedAt
        (ageMs / 1000L).coerceAtLeast(0L)
    }
}
