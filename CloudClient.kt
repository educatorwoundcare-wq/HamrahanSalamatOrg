package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.auth.SessionManager
import com.example.data.auth.TokenManager
import com.example.data.supabase.SupabaseClientManager
import com.example.data.WorkspaceManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class WorkspaceInfo(
    val companyId: String = "",
    val companySyncCode: String = "",
    val centerName: String = "",
    val nationalCode: String = "",
    val supportPhone: String = "",
    val centerAddress: String = "",
    val createdTimestamp: Long = System.currentTimeMillis()
)

sealed class FirebaseException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NetworkError(message: String, cause: Throwable) : FirebaseException(message, cause)
    class AuthenticationError(val code: Int, val firebaseError: String?, message: String) : FirebaseException(message)
    class PermissionDenied(val code: Int, message: String) : FirebaseException(message)
    class WorkspaceNotFound(message: String) : FirebaseException(message)
    class TokenExpired(message: String) : FirebaseException(message)
    class InvalidResponse(message: String, cause: Throwable? = null) : FirebaseException(message, cause)
    class UnknownError(val code: Int, message: String, cause: Throwable? = null) : FirebaseException(message, cause)
}

class CloudClient @JvmOverloads constructor(
    private val dao: HamrahanDao,
    private val context: Context? = null,
    val sessionManager: SessionManager? = null
) {
    private val resolvedContext: Context
        get() = context ?: com.example.HamrahanApplication.instance
        
    val internalSessionManager: SessionManager by lazy {
        sessionManager ?: SessionManager(TokenManager(resolvedContext, dao))
    }
    
    private val workspaceManager: WorkspaceManager by lazy {
        WorkspaceManager.getInstance(resolvedContext)
    }

    private val supabaseManager = SupabaseClientManager(workspaceManager)
    private val client = supabaseManager.httpClient
    private val baseUrl = "${supabaseManager.supabaseUrl}/rest/v1"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val workspaceAdapter = moshi.adapter(WorkspaceInfo::class.java)
    private val deviceAdapter = moshi.adapter(ConnectedDevice::class.java)
    private val recordAdapter = moshi.adapter(CloudSyncRecord::class.java)
    
    private val recordsListType = Types.newParameterizedType(List::class.java, CloudSyncRecord::class.java)
    private val recordsListAdapter = moshi.adapter<List<CloudSyncRecord>>(recordsListType)
    
    private val devicesListType = Types.newParameterizedType(List::class.java, ConnectedDevice::class.java)
    private val devicesListAdapter = moshi.adapter<List<ConnectedDevice>>(devicesListType)

    // --- Firebase Auth (Deprecated - Handled by SupabaseAuthRepository now) ---
    suspend fun getValidIdToken(): String? = withContext(Dispatchers.IO) {
        // We now rely on Supabase Anon Key + RLS (WorkspaceManager) for requests.
        // Return a dummy token so SyncEngine doesn't fail.
        "SUPABASE_ANON_TOKEN"
    }

    // --- Data Sync Record Actions ---
    
    suspend fun getWorkspaceInfo(companyId: String): WorkspaceInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/workspaces?company_id=eq.$companyId")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: return@use null
                    val array = JSONArray(bodyString)
                    if (array.length() > 0) {
                        val json = array.getJSONObject(0)
                        return@use WorkspaceInfo(
                            companyId = json.optString("company_id"),
                            companySyncCode = json.optString("sync_code"),
                            centerName = json.optString("center_name"),
                            nationalCode = json.optString("national_code"),
                            supportPhone = json.optString("support_phone"),
                            centerAddress = json.optString("center_address"),
                            createdTimestamp = json.optLong("created_timestamp")
                        )
                    }
                }
            }
        } catch (e: Exception) { Log.e("CloudClient", "Error", e) }
        null
    }

    suspend fun saveWorkspaceInfo(companyId: String, info: WorkspaceInfo): Boolean = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("company_id", companyId)
            put("sync_code", info.companySyncCode)
            put("center_name", info.centerName)
            put("national_code", info.nationalCode)
            put("support_phone", info.supportPhone)
            put("center_address", info.centerAddress)
            put("created_timestamp", info.createdTimestamp)
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl/workspaces")
            .header("Prefer", "resolution=merge-duplicates")
            .post(json.toRequestBody(jsonMediaType))
            .build()

        try { client.newCall(request).execute().use { it.isSuccessful } } 
        catch (e: Exception) { false }
    }

    suspend fun getWorkspaceBySyncCode(syncCode: String): WorkspaceInfo? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/workspaces?sync_code=eq.$syncCode")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: return@use null
                    val array = JSONArray(bodyString)
                    if (array.length() > 0) {
                        val json = array.getJSONObject(0)
                        return@use WorkspaceInfo(
                            companyId = json.optString("company_id"),
                            companySyncCode = json.optString("sync_code"),
                            centerName = json.optString("center_name"),
                            nationalCode = json.optString("national_code"),
                            supportPhone = json.optString("support_phone"),
                            centerAddress = json.optString("center_address"),
                            createdTimestamp = json.optLong("created_timestamp")
                        )
                    }
                }
            }
        } catch (e: Exception) { Log.e("CloudClient", "Error", e) }
        null
    }

    // --- Device Management ---
    suspend fun registerDevice(companyId: String, device: ConnectedDevice): Boolean = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("device_id", device.deviceId)
            put("company_id", companyId)
            put("device_name", device.deviceName)
            put("device_type", device.deviceType)
            put("app_version", device.appVersion)
            put("last_online_time", device.lastOnlineTime)
            put("last_successful_sync", device.lastSuccessfulSync)
            put("status", device.status)
            put("uid", device.uid)
            put("role", device.role)
            put("last_seen", device.lastSeen)
            put("requested_role", device.requestedRole)
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl/connected_devices")
            .header("Prefer", "resolution=merge-duplicates")
            .post(json.toRequestBody(jsonMediaType))
            .build()
        try { client.newCall(request).execute().use { it.isSuccessful } } catch (e: Exception) { false }
    }

    suspend fun patchDeviceHeartbeat(companyId: String, device: ConnectedDevice): Boolean = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("last_online_time", System.currentTimeMillis())
            put("last_successful_sync", device.lastSuccessfulSync)
            put("last_seen", System.currentTimeMillis())
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl/connected_devices?device_id=eq.${device.deviceId}")
            .patch(json.toRequestBody(jsonMediaType))
            .build()
        try { client.newCall(request).execute().use { it.isSuccessful } } catch (e: Exception) { false }
    }

    suspend fun patchDeviceAuthorization(companyId: String, deviceId: String, status: String, role: String): Boolean = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("status", status)
            put("role", role)
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl/connected_devices?device_id=eq.$deviceId")
            .patch(json.toRequestBody(jsonMediaType))
            .build()
        try { client.newCall(request).execute().use { it.isSuccessful } } catch (e: Exception) { false }
    }

    suspend fun getSingleDevice(companyId: String, deviceId: String): ConnectedDevice? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/connected_devices?device_id=eq.$deviceId")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: return@use null
                    val array = JSONArray(bodyString)
                    if (array.length() > 0) {
                        val json = array.getJSONObject(0)
                        return@use ConnectedDevice(
                            deviceId = json.optString("device_id"),
                            deviceName = json.optString("device_name"),
                            deviceType = json.optString("device_type"),
                            appVersion = json.optString("app_version"),
                            lastOnlineTime = json.optLong("last_online_time"),
                            lastSuccessfulSync = json.optLong("last_successful_sync"),
                            status = json.optString("status"),
                            uid = json.optString("uid"),
                            role = json.optString("role"),
                            lastSeen = json.optLong("last_seen"),
                            companyId = companyId,
                            requestedRole = json.optString("requested_role")
                        )
                    }
                }
            }
        } catch (e: Exception) { Log.e("CloudClient", "Error", e) }
        null
    }

    suspend fun getConnectedDevices(companyId: String): List<ConnectedDevice> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/connected_devices?company_id=eq.$companyId")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: return@use emptyList<ConnectedDevice>()
                    val array = JSONArray(bodyString)
                    val list = mutableListOf<ConnectedDevice>()
                    for (i in 0 until array.length()) {
                        val json = array.getJSONObject(i)
                        list.add(ConnectedDevice(
                            deviceId = json.optString("device_id"),
                            deviceName = json.optString("device_name"),
                            deviceType = json.optString("device_type"),
                            appVersion = json.optString("app_version"),
                            lastOnlineTime = json.optLong("last_online_time"),
                            lastSuccessfulSync = json.optLong("last_successful_sync"),
                            status = json.optString("status"),
                            uid = json.optString("uid"),
                            role = json.optString("role"),
                            lastSeen = json.optLong("last_seen"),
                            companyId = companyId,
                            requestedRole = json.optString("requested_role")
                        ))
                    }
                    return@use list
                }
            }
        } catch (e: Exception) { Log.e("CloudClient", "Error", e) }
        emptyList()
    }

    suspend fun deleteDevice(companyId: String, deviceId: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/connected_devices?device_id=eq.$deviceId")
            .delete()
            .build()
        try { client.newCall(request).execute().use { it.isSuccessful } } catch (e: Exception) { false }
    }

    // --- Record Sync ---
    suspend fun uploadRecord(companyId: String, record: CloudSyncRecord): Boolean = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("id", record.id)
            put("company_id", companyId)
            put("entity_type", record.entityType)
            put("entity_id", record.entityId)
            put("data_json", record.dataJson)
            put("updated_timestamp", record.updatedTimestamp)
            put("last_modified_device_id", record.lastModifiedDeviceId)
            put("is_deleted", record.isDeleted)
        }.toString()

        val request = Request.Builder()
            .url("$baseUrl/cloud_records")
            .header("Prefer", "resolution=merge-duplicates")
            .post(json.toRequestBody(jsonMediaType))
            .build()
        try { client.newCall(request).execute().use { it.isSuccessful } } catch (e: Exception) { false }
    }

    suspend fun getCloudRecords(companyId: String): List<CloudSyncRecord> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/cloud_records?company_id=eq.$companyId")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: return@use emptyList<CloudSyncRecord>()
                    val array = JSONArray(bodyString)
                    val list = mutableListOf<CloudSyncRecord>()
                    for (i in 0 until array.length()) {
                        val json = array.getJSONObject(i)
                        list.add(CloudSyncRecord(
                            id = json.optString("id"),
                            entityType = json.optString("entity_type"),
                            entityId = json.optString("entity_id"),
                            dataJson = json.optString("data_json"),
                            updatedTimestamp = json.optLong("updated_timestamp"),
                            lastModifiedDeviceId = json.optString("last_modified_device_id"),
                            isDeleted = json.optBoolean("is_deleted")
                        ))
                    }
                    return@use list
                }
            }
        } catch (e: Exception) { Log.e("CloudClient", "Error", e) }
        emptyList()
    }

    suspend fun deleteCloudRecord(companyId: String, entityType: String, entityId: String): Boolean = withContext(Dispatchers.IO) {
        val id = "${entityType}_${entityId}"
        val request = Request.Builder()
            .url("$baseUrl/cloud_records?id=eq.$id")
            .delete()
            .build()
        try { client.newCall(request).execute().use { it.isSuccessful } } catch (e: Exception) { false }
    }
}
