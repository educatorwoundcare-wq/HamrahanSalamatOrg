package com.example.data.supabase

import android.util.Log
import com.example.data.WorkspaceManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Handles administrative login securely using Supabase Auth.
 * Uses Anonymous Authentication with Connection IDs (Sync Codes) to maintain the existing frictionless workflow.
 */
class SupabaseAuthRepository(
    private val clientManager: SupabaseClientManager,
    private val workspaceManager: WorkspaceManager
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun signInAnonymously(tenantId: String, syncCode: String): AuthResult = withContext(Dispatchers.IO) {
        val url = "${clientManager.supabaseUrl}/auth/v1/signup"
        val payload = mapOf("data" to mapOf("tenant_id" to tenantId, "sync_code" to syncCode))
        val json = moshi.adapter(Map::class.java).toJson(payload)
        
        // Note: OkHttp client from clientManager automatically injects the apikey via interceptor
        val request = Request.Builder()
            .url(url)
            .post(json.toRequestBody(jsonMediaType))
            .build()
        
        try {
            clientManager.httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    val authMap = moshi.adapter(Map::class.java).fromJson(body) as? Map<String, Any>
                    val accessToken = authMap?.get("access_token") as? String
                    val userMap = authMap?.get("user") as? Map<String, Any>
                    val uid = userMap?.get("id") as? String
                    
                    if (accessToken != null && uid != null) {
                        workspaceManager.saveIdentity(
                            tenantId = tenantId,
                            syncCode = syncCode,
                            authToken = accessToken
                        )
                        AuthResult.Success
                    } else {
                        AuthResult.Error("Invalid authorization format received from Supabase.")
                    }
                } else {
                    Log.w("SupabaseAuth", "Anonymous login failed: ${response.code} $body")
                    AuthResult.Error(extractErrorMessage(body) ?: "Authentication failed.")
                }
            }
        } catch (e: Exception) {
            Log.e("SupabaseAuth", "Network error during login", e)
            AuthResult.NetworkError(e)
        }
    }
    
    suspend fun logout() {
        // Clear local session securely
        workspaceManager.clearIdentity()
    }

    private fun extractErrorMessage(body: String?): String? {
        if (body.isNullOrEmpty()) return null
        return try {
            val map = moshi.adapter(Map::class.java).fromJson(body) as? Map<String, Any>
            map?.get("error_description") as? String ?: map?.get("msg") as? String
        } catch (e: Exception) {
            null
        }
    }
}

sealed class AuthResult {
    object Success : AuthResult()
    data class Error(val message: String) : AuthResult()
    data class NetworkError(val exception: Exception) : AuthResult()
}
