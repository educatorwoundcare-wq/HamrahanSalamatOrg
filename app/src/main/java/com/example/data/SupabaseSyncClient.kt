package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class SupabaseSyncClient(
    private val workspaceManager: WorkspaceManager,
    private val dao: HamrahanDao
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .addInterceptor(TenantInterceptor(workspaceManager))
        // Add auth interceptor for Supabase API key (anon key)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("apikey", "SUPABASE_ANON_KEY_PLACEHOLDER")
                .build()
            chain.proceed(request)
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://your-supabase-project.supabase.co/rest/v1"

    suspend fun pushBatchedChanges(tenantId: String, changes: List<SyncQueue>): SyncResult = withContext(Dispatchers.IO) {
        val payload = changes.map { queueItem ->
            val dataJson = fetchEntityJson(queueItem.tableName, queueItem.recordId)
            mapOf(
                "sync_id" to queueItem.id,
                "table_name" to queueItem.tableName,
                "record_id" to queueItem.recordId,
                "operation_type" to queueItem.operationType,
                "timestamp" to queueItem.timestamp,
                "payload" to dataJson
            )
        }

        val json = moshi.adapter(List::class.java).toJson(payload)
        val request = Request.Builder()
            .url("$baseUrl/rpc/push_sync_batch")
            .post(json.toRequestBody(jsonMediaType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200, 201, 204 -> SyncResult.Success
                    401, 403 -> SyncResult.AuthError
                    else -> SyncResult.ServerError(response.code, response.body?.string() ?: "Unknown error")
                }
            }
        } catch (e: Exception) {
            SyncResult.NetworkError(e)
        }
    }

    suspend fun pullRemoteChanges(tenantId: String, lastSyncTimestamp: Long): RemoteSyncResponse? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/rpc/pull_sync_batch?last_sync=$lastSyncTimestamp")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    if (!bodyString.isNullOrEmpty()) {
                        // Assuming RPC returns List<Map<String, Any>> representing remote changes
                        return@withContext RemoteSyncResponse(changes = emptyList()) // Placeholder
                    }
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchEntityJson(tableName: String, recordId: String): String? {
        // Here we would dynamically query the DAO to get the entity based on tableName and recordId.
        // For simplicity in this architecture demo, we return a mock JSON.
        // In a real scenario, we'd use a switch/when on tableName, call dao.get...ById(), and serialize it.
        return "{ \"id\": \"$recordId\", \"info\": \"mock_data\" }"
    }
}

sealed class SyncResult {
    object Success : SyncResult()
    object AuthError : SyncResult()
    data class ServerError(val code: Int, val message: String) : SyncResult()
    data class NetworkError(val exception: Exception) : SyncResult()
}

data class RemoteSyncResponse(
    val changes: List<RemoteChange>
)

data class RemoteChange(
    val tableName: String,
    val recordId: String,
    val operationType: String,
    val timestamp: Long,
    val payloadJson: String
)
