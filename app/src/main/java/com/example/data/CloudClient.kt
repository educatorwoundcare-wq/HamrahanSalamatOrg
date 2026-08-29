package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.supabase.SupabaseClientManager
import com.example.data.WorkspaceManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val createdTimestamp: Long = System.currentTimeMillis(),
    val creatorUid: String? = null
)

sealed class WorkspaceResolution {
    data class ExistsAndOwned(val workspace: WorkspaceInfo) : WorkspaceResolution()
    data class ExistsForeign(val workspace: WorkspaceInfo, val remoteCreatorUid: String?) : WorkspaceResolution()
    object NotFound : WorkspaceResolution()
    data class Unauthorized(val code: Int, val message: String) : WorkspaceResolution()
    data class Failed(val exception: Exception) : WorkspaceResolution()
}

sealed class DeviceResolution {
    data class ExistsActive(val device: ConnectedDevice) : DeviceResolution()
    data class ExistsPending(val device: ConnectedDevice) : DeviceResolution()
    data class ExistsOther(val device: ConnectedDevice) : DeviceResolution()
    object NotFound : DeviceResolution()
    data class Unauthorized(val code: Int, val message: String) : DeviceResolution()
    data class Failed(val exception: Exception) : DeviceResolution()
}

sealed class BootstrapResult {
    data class SyncAllowed(
        val companyId: String,
        val authUid: String,
        val device: ConnectedDevice,
        val workspace: WorkspaceInfo
    ) : BootstrapResult()

    data class PendingApproval(
        val companyId: String,
        val deviceId: String,
        val device: ConnectedDevice? = null,
        val message: String
    ) : BootstrapResult()

    data class IdentityRecoveryRequired(
        val companyId: String?,
        val reason: String,
        val remoteCreatorUid: String?
    ) : BootstrapResult()

    data class Blocked(
        val reason: String,
        val code: Int = 0
    ) : BootstrapResult()

    data class Error(
        val message: String,
        val exception: Exception? = null
    ) : BootstrapResult()
}

data class AuthenticatedMutationContext(
    val allowed: Boolean,
    val reason: String,
    val authUid: String? = null,
    val token: String? = null,
    val companyId: String? = null,
    val deviceId: String? = null,
    val workspaceState: String = "UNKNOWN",
    val deviceState: String = "UNKNOWN"
)

data class SyncAuthorizationResult(
    val allowed: Boolean,
    val reason: String,
    val authUid: String? = null,
    val companyId: String? = null,
    val deviceId: String? = null,
    val deviceStatus: String? = null,
    val workspaceConfirmed: Boolean = false,
    val deviceConfirmed: Boolean = false
)

sealed class WorkspaceLookupResult {
    data class Success(val workspace: WorkspaceInfo, val httpStatus: Int = 200) : WorkspaceLookupResult()
    data class NotFound(val syncCode: String, val httpStatus: Int = 404) : WorkspaceLookupResult()
    data class Error(val code: Int, val message: String, val syncCode: String) : WorkspaceLookupResult()
    data class NetworkError(val exception: Exception, val syncCode: String) : WorkspaceLookupResult()
    data class NoToken(val syncCode: String) : WorkspaceLookupResult()
}

sealed class WorkspaceSaveResult {
    data class Success(val httpStatus: Int = 200, val companyId: String = "", val syncCode: String = "") : WorkspaceSaveResult()
    data class OwnershipMismatch(
        val companyId: String,
        val currentAuthUid: String,
        val operation: String = "SAVE_WORKSPACE",
        val remoteCreatorUid: String? = null,
        val httpStatus: Int = 403
    ) : WorkspaceSaveResult()
    data class Error(val code: Int, val message: String, val companyId: String = "", val syncCode: String = "") : WorkspaceSaveResult()
    data class NetworkError(val exception: Exception, val companyId: String = "", val syncCode: String = "") : WorkspaceSaveResult()
    data class NoToken(val companyId: String = "", val syncCode: String = "") : WorkspaceSaveResult()
}

sealed class DeviceRegistrationResult {
    data class Success(val httpStatus: Int = 200) : DeviceRegistrationResult()
    data class PendingAccepted(val httpStatus: Int = 202, val message: String = "درخواست اتصال دستگاه ارسال شد و منتظر تأیید سرپرست مرکز است.") : DeviceRegistrationResult()
    data class Error(val code: Int, val message: String) : DeviceRegistrationResult()
    data class NetworkError(val exception: Exception) : DeviceRegistrationResult()
}

sealed class UploadRecordResult {
    data class Success(val httpCode: Int) : UploadRecordResult()
    data class Blocked(val reason: String, val authUid: String?, val companyId: String) : UploadRecordResult()
    data class HttpError(val httpCode: Int, val body: String) : UploadRecordResult()
    data class NetworkError(val exception: Exception) : UploadRecordResult()
}

class CloudClient @JvmOverloads constructor(
    private val dao: HamrahanDao,
    private val context: Context? = null
) {
    private val resolvedContext: Context
        get() = context ?: com.example.HamrahanApplication.instance
    
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

    // --- Data Sync Record Actions ---
    
// snippet to insert into CloudClient
    suspend fun getMyWorkspaces(): List<WorkspaceInfo> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/workspaces")
            .get()
            .build()
        val list = mutableListOf<WorkspaceInfo>()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: return@use list
                    val array = org.json.JSONArray(bodyString)
                    for (i in 0 until array.length()) {
                        val json = array.getJSONObject(i)
                        list.add(WorkspaceInfo(
                            companyId = json.optString("company_id"),
                            companySyncCode = json.optString("sync_code"),
                            centerName = json.optString("center_name"),
                            nationalCode = json.optString("national_code", ""),
                            supportPhone = json.optString("support_phone", ""),
                            centerAddress = json.optString("center_address", ""),
                            createdTimestamp = json.optLong("created_timestamp", 0L),
                            creatorUid = if (json.isNull("creator_uid")) null else json.optString("creator_uid").takeIf { it.isNotBlank() }
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CloudClient", "getMyWorkspaces Network Exception", e)
        }
        list
    }
    private val bootstrapMutex = Mutex()
    private val deviceWriteMutex = Mutex()

    suspend fun requireAuthenticatedMutationContext(
        operation: String,
        companyId: String,
        deviceId: String? = null,
        requireActiveDevice: Boolean = false
    ): AuthenticatedMutationContext = withContext(Dispatchers.IO) {
        if (companyId.isBlank() || companyId == "COMP-LOCAL") {
            val reason = "COMPANY_ID_INVALID"
            Log.w("MUTATION_BLOCKED", "[MUTATION_BLOCKED]\noperation=$operation\nreason=$reason\nhasSession=false\nhasToken=false\nworkspaceState=NONE\ndeviceState=NONE")
            return@withContext AuthenticatedMutationContext(allowed = false, reason = reason)
        }

        ensureAuthSession(companyId)
        val token = workspaceManager.currentAuthToken
        val authUid = workspaceManager.currentAuthUid ?: workspaceManager.extractSubFromJwt(token)
        val hasSession = !token.isNullOrBlank() && !workspaceManager.isTokenExpired(token)
        val hasToken = !token.isNullOrBlank()
        val uidPresent = !authUid.isNullOrBlank()

        if (!hasSession || !uidPresent || token == null) {
            val reason = if (!hasToken) "NO_TOKEN" else if (workspaceManager.isTokenExpired(token)) "TOKEN_EXPIRED" else "NO_AUTH_UID"
            Log.w("MUTATION_BLOCKED", "[MUTATION_BLOCKED]\noperation=$operation\nreason=$reason\nhasSession=$hasSession\nhasToken=$hasToken\nworkspaceState=UNKNOWN\ndeviceState=UNKNOWN")
            return@withContext AuthenticatedMutationContext(
                allowed = false,
                reason = reason,
                authUid = authUid,
                token = token,
                companyId = companyId,
                deviceId = deviceId
            )
        }

        var wsState = "NOT_CHECKED"
        var devState = "NOT_CHECKED"

        if (requireActiveDevice) {
            val wsRes = resolveWorkspace(companyId)
            wsState = wsRes.javaClass.simpleName
            if (wsRes !is WorkspaceResolution.ExistsAndOwned) {
                val reason = "WORKSPACE_NOT_CONFIRMED_$wsState"
                Log.w("MUTATION_BLOCKED", "[MUTATION_BLOCKED]\noperation=$operation\nreason=$reason\nhasSession=$hasSession\nhasToken=$hasToken\nworkspaceState=$wsState\ndeviceState=$devState")
                return@withContext AuthenticatedMutationContext(
                    allowed = false,
                    reason = reason,
                    authUid = authUid,
                    token = token,
                    companyId = companyId,
                    deviceId = deviceId,
                    workspaceState = wsState,
                    deviceState = devState
                )
            }

            val targetDevId = deviceId?.takeIf { DeviceIdentityProvider.isValidUuidDeviceId(it) }
                ?: (dao?.getSystemSettingByKey("active_device_id")?.takeIf { DeviceIdentityProvider.isValidUuidDeviceId(it) })
                ?: context?.let { DeviceIdentityProvider.getDeviceId(it) }
                ?: workspaceManager.getDeviceId()
            if (targetDevId.isNullOrBlank()) {
                val reason = "NO_DEVICE_ID"
                Log.w("MUTATION_BLOCKED", "[MUTATION_BLOCKED]\noperation=$operation\nreason=$reason\nhasSession=$hasSession\nhasToken=$hasToken\nworkspaceState=$wsState\ndeviceState=MISSING")
                return@withContext AuthenticatedMutationContext(
                    allowed = false,
                    reason = reason,
                    authUid = authUid,
                    token = token,
                    companyId = companyId,
                    deviceId = targetDevId,
                    workspaceState = wsState,
                    deviceState = "MISSING"
                )
            }

            val devRes = resolveDevice(companyId, targetDevId)
            devState = devRes.javaClass.simpleName
            if (devRes !is DeviceResolution.ExistsActive) {
                val reason = when (devRes) {
                    is DeviceResolution.ExistsPending -> "DEVICE_PENDING_APPROVAL"
                    is DeviceResolution.ExistsOther -> "DEVICE_STATUS_${devRes.device.status}"
                    is DeviceResolution.NotFound -> "DEVICE_NOT_FOUND"
                    is DeviceResolution.Unauthorized -> "DEVICE_UNAUTHORIZED"
                    is DeviceResolution.Failed -> "DEVICE_LOOKUP_FAILED"
                    else -> "DEVICE_NOT_ACTIVE"
                }
                Log.w("MUTATION_BLOCKED", "[MUTATION_BLOCKED]\noperation=$operation\nreason=$reason\nhasSession=$hasSession\nhasToken=$hasToken\nworkspaceState=$wsState\ndeviceState=$devState")
                return@withContext AuthenticatedMutationContext(
                    allowed = false,
                    reason = reason,
                    authUid = authUid,
                    token = token,
                    companyId = companyId,
                    deviceId = targetDevId,
                    workspaceState = wsState,
                    deviceState = devState
                )
            }

            val activeDev = devRes.device
            if (activeDev.companyId != companyId || (activeDev.uid.isNotBlank() && activeDev.uid != authUid)) {
                val reason = "DEVICE_IDENTITY_MISMATCH"
                Log.w("MUTATION_BLOCKED", "[MUTATION_BLOCKED]\noperation=$operation\nreason=$reason\nhasSession=$hasSession\nhasToken=$hasToken\nworkspaceState=$wsState\ndeviceState=$devState")
                return@withContext AuthenticatedMutationContext(
                    allowed = false,
                    reason = reason,
                    authUid = authUid,
                    token = token,
                    companyId = companyId,
                    deviceId = targetDevId,
                    workspaceState = wsState,
                    deviceState = devState
                )
            }
        }

        Log.i("AUTH_MUTATION_CONTEXT", "[AUTH_MUTATION_CONTEXT]\noperation=$operation\nhasSession=$hasSession\nhasToken=$hasToken\nuidPresent=$uidPresent\nworkspaceIdPresent=${companyId.isNotBlank()}\ndeviceIdPresent=${!deviceId.isNullOrBlank()}")

        AuthenticatedMutationContext(
            allowed = true,
            reason = "VERIFIED",
            authUid = authUid,
            token = token,
            companyId = companyId,
            deviceId = deviceId,
            workspaceState = wsState,
            deviceState = devState
        )
    }

    suspend fun resolveWorkspace(companyId: String): WorkspaceResolution = withContext(Dispatchers.IO) {
        val token = workspaceManager.currentAuthToken
        val authUid = workspaceManager.currentAuthUid ?: workspaceManager.extractSubFromJwt(token)
        val request = Request.Builder()
            .url("$baseUrl/workspaces?company_id=eq.$companyId")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val httpCode = response.code
                val bodyString = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val array = JSONArray(bodyString)
                    if (array.length() > 0) {
                        val json = array.getJSONObject(0)
                        val info = WorkspaceInfo(
                            companyId = json.optString("company_id"),
                            companySyncCode = json.optString("sync_code"),
                            centerName = json.optString("center_name"),
                            nationalCode = json.optString("national_code", ""),
                            supportPhone = json.optString("support_phone", ""),
                            centerAddress = json.optString("center_address", ""),
                            createdTimestamp = json.optLong("created_timestamp", 0L),
                            creatorUid = if (json.isNull("creator_uid")) null else json.optString("creator_uid").takeIf { it.isNotBlank() }
                        )
                        val isOwned = info.creatorUid == null || info.creatorUid == authUid
                        val result = if (isOwned) WorkspaceResolution.ExistsAndOwned(info) else WorkspaceResolution.ExistsForeign(info, info.creatorUid)
                        Log.i("WORKSPACE_RESOLUTION", "[WORKSPACE_RESOLUTION] companyId=$companyId httpCode=$httpCode result=${result.javaClass.simpleName} operation=QUERY")
                        return@use result
                    } else {
                        Log.i("WORKSPACE_RESOLUTION", "[WORKSPACE_RESOLUTION] companyId=$companyId httpCode=$httpCode result=NotFound operation=QUERY")
                        return@use WorkspaceResolution.NotFound
                    }
                } else if (httpCode == 401 || httpCode == 403 || httpCode == 42501) {
                    Log.w("WORKSPACE_RESOLUTION", "[WORKSPACE_RESOLUTION] companyId=$companyId httpCode=$httpCode result=Unauthorized operation=QUERY")
                    return@use WorkspaceResolution.Unauthorized(httpCode, bodyString)
                } else {
                    Log.e("WORKSPACE_RESOLUTION", "[WORKSPACE_RESOLUTION] companyId=$companyId httpCode=$httpCode result=Failed operation=QUERY")
                    return@use WorkspaceResolution.Failed(Exception("HTTP $httpCode: $bodyString"))
                }
            }
        } catch (e: Exception) {
            Log.e("WORKSPACE_RESOLUTION", "[WORKSPACE_RESOLUTION] companyId=$companyId httpCode=0 result=Failed operation=QUERY", e)
            return@withContext WorkspaceResolution.Failed(e)
        }
    }

    suspend fun resolveDevice(companyId: String, deviceId: String): DeviceResolution = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/connected_devices?device_id=eq.$deviceId")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val httpCode = response.code
                val bodyString = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val array = JSONArray(bodyString)
                    if (array.length() > 0) {
                        val json = array.getJSONObject(0)
                        val device = ConnectedDevice(
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
                            companyId = json.optString("company_id").takeIf { it.isNotBlank() } ?: companyId,
                            requestedRole = json.optString("requested_role")
                        )
                        val result = when (device.status) {
                            "Active" -> DeviceResolution.ExistsActive(device)
                            "Pending" -> DeviceResolution.ExistsPending(device)
                            else -> DeviceResolution.ExistsOther(device)
                        }
                        Log.i("DEVICE_RESOLUTION", "[DEVICE_RESOLUTION] deviceId=$deviceId companyId=$companyId httpCode=$httpCode result=${result.javaClass.simpleName} operation=QUERY")
                        return@use result
                    } else {
                        Log.i("DEVICE_RESOLUTION", "[DEVICE_RESOLUTION] deviceId=$deviceId companyId=$companyId httpCode=$httpCode result=NotFound operation=QUERY")
                        return@use DeviceResolution.NotFound
                    }
                } else if (httpCode == 401 || httpCode == 403 || httpCode == 42501) {
                    Log.w("DEVICE_RESOLUTION", "[DEVICE_RESOLUTION] deviceId=$deviceId companyId=$companyId httpCode=$httpCode result=Unauthorized operation=QUERY")
                    return@use DeviceResolution.Unauthorized(httpCode, bodyString)
                } else {
                    Log.e("DEVICE_RESOLUTION", "[DEVICE_RESOLUTION] deviceId=$deviceId companyId=$companyId httpCode=$httpCode result=Failed operation=QUERY")
                    return@use DeviceResolution.Failed(Exception("HTTP $httpCode: $bodyString"))
                }
            }
        } catch (e: Exception) {
            Log.e("DEVICE_RESOLUTION", "[DEVICE_RESOLUTION] deviceId=$deviceId companyId=$companyId httpCode=0 result=Failed operation=QUERY", e)
            return@withContext DeviceResolution.Failed(e)
        }
    }

    suspend fun getWorkspaceInfo(companyId: String): WorkspaceInfo? = withContext(Dispatchers.IO) {
        when (val res = resolveWorkspace(companyId)) {
            is WorkspaceResolution.ExistsAndOwned -> res.workspace
            is WorkspaceResolution.ExistsForeign -> res.workspace
            else -> null
        }
    }

    suspend fun saveWorkspaceInfoDetailed(companyId: String, info: WorkspaceInfo): WorkspaceSaveResult = withContext(Dispatchers.IO) {
        // STEP A — Ensure authentication FIRST
        ensureAuthSession(companyId, info.companySyncCode)

        val token = workspaceManager.currentAuthToken
        val authUid = workspaceManager.currentAuthUid ?: workspaceManager.extractSubFromJwt(token)
        if (token.isNullOrBlank() || authUid.isNullOrBlank() || workspaceManager.isTokenExpired(token)) {
            Log.e("WORKSPACE_RESOLUTION", "[WORKSPACE_RESOLUTION] companyId=$companyId httpCode=401 result=Unauthorized operation=BLOCKED_NO_TOKEN")
            return@withContext WorkspaceSaveResult.NoToken(companyId, info.companySyncCode)
        }

        val resolution = resolveWorkspace(companyId)
        when (resolution) {
            is WorkspaceResolution.NotFound -> {
                // Pure INSERT
                val insertJson = JSONObject().apply {
                    put("company_id", companyId)
                    put("sync_code", info.companySyncCode)
                    put("center_name", info.centerName)
                    put("national_code", info.nationalCode)
                    put("support_phone", info.supportPhone)
                    put("center_address", info.centerAddress)
                    put("created_timestamp", info.createdTimestamp)
                    put("creator_uid", authUid)
                }.toString()

                val insertRequest = Request.Builder()
                    .url("$baseUrl/workspaces")
                    .post(insertJson.toRequestBody(jsonMediaType))
                    .build()

                try {
                    client.newCall(insertRequest).execute().use { response ->
                        val body = response.body?.string() ?: ""
                        val httpCode = response.code
                        if (response.isSuccessful) {
                            Log.i("WORKSPACE_RESOLUTION", "[WORKSPACE_RESOLUTION] companyId=$companyId httpCode=$httpCode result=Success operation=INSERT")
                            WorkspaceSaveResult.Success(httpCode, companyId, info.companySyncCode)
                        } else if (httpCode == 409 || body.contains("23505")) {
                            val freshRes = resolveWorkspace(companyId)
                            if (freshRes is WorkspaceResolution.ExistsAndOwned) {
                                Log.i("WORKSPACE_RESOLUTION", "[WORKSPACE_RESOLUTION] companyId=$companyId httpCode=$httpCode result=ExistsAndOwned operation=INSERT_CONFLICT_RECOVERED")
                                WorkspaceSaveResult.Success(httpCode, companyId, info.companySyncCode)
                            } else if (freshRes is WorkspaceResolution.ExistsForeign) {
                                Log.w("WORKSPACE_RESOLUTION", "[WORKSPACE_RESOLUTION] companyId=$companyId httpCode=$httpCode result=ExistsForeign operation=INSERT_CONFLICT_OWNERSHIP_MISMATCH")
                                WorkspaceSaveResult.OwnershipMismatch(companyId, authUid, "INSERT", freshRes.remoteCreatorUid, httpCode)
                            } else {
                                Log.w("WORKSPACE_RESOLUTION", "[WORKSPACE_RESOLUTION] companyId=$companyId httpCode=$httpCode result=Conflict operation=INSERT_CONFLICT")
                                WorkspaceSaveResult.OwnershipMismatch(companyId, authUid, "INSERT", null, httpCode)
                            }
                        } else {
                            Log.e("WORKSPACE_RESOLUTION", "[WORKSPACE_RESOLUTION] companyId=$companyId httpCode=$httpCode result=Failed operation=INSERT body=$body")
                            WorkspaceSaveResult.Error(httpCode, body, companyId, info.companySyncCode)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WORKSPACE_RESOLUTION", "[WORKSPACE_RESOLUTION] companyId=$companyId httpCode=0 result=Failed operation=INSERT", e)
                    WorkspaceSaveResult.NetworkError(e, companyId, info.companySyncCode)
                }
            }
            is WorkspaceResolution.ExistsAndOwned -> {
                // Pure UPDATE via PATCH
                val updateJson = JSONObject().apply {
                    put("center_name", info.centerName)
                    put("national_code", info.nationalCode)
                    put("support_phone", info.supportPhone)
                    put("center_address", info.centerAddress)
                }.toString()

                val updateRequest = Request.Builder()
                    .url("$baseUrl/workspaces?company_id=eq.$companyId")
                    .patch(updateJson.toRequestBody(jsonMediaType))
                    .build()

                try {
                    client.newCall(updateRequest).execute().use { response ->
                        val body = response.body?.string() ?: ""
                        val httpCode = response.code
                        if (response.isSuccessful) {
                            Log.i("WORKSPACE_RESOLUTION", "[WORKSPACE_RESOLUTION] companyId=$companyId httpCode=$httpCode result=Success operation=UPDATE")
                            WorkspaceSaveResult.Success(httpCode, companyId, info.companySyncCode)
                        } else {
                            Log.e("WORKSPACE_RESOLUTION", "[WORKSPACE_RESOLUTION] companyId=$companyId httpCode=$httpCode result=Failed operation=UPDATE body=$body")
                            WorkspaceSaveResult.Error(httpCode, body, companyId, info.companySyncCode)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WORKSPACE_RESOLUTION", "[WORKSPACE_RESOLUTION] companyId=$companyId httpCode=0 result=Failed operation=UPDATE", e)
                    WorkspaceSaveResult.NetworkError(e, companyId, info.companySyncCode)
                }
            }
            is WorkspaceResolution.ExistsForeign -> {
                Log.w("WORKSPACE_RESOLUTION", "[WORKSPACE_RESOLUTION] companyId=$companyId httpCode=403 result=ExistsForeign operation=REJECT_OWNERSHIP_MISMATCH")
                WorkspaceSaveResult.OwnershipMismatch(companyId, authUid, "SAVE_WORKSPACE", resolution.remoteCreatorUid, 403)
            }
            is WorkspaceResolution.Unauthorized -> {
                WorkspaceSaveResult.Error(resolution.code, resolution.message, companyId, info.companySyncCode)
            }
            is WorkspaceResolution.Failed -> {
                WorkspaceSaveResult.NetworkError(resolution.exception, companyId, info.companySyncCode)
            }
        }
    }

    suspend fun saveWorkspaceInfo(companyId: String, info: WorkspaceInfo): Boolean = withContext(Dispatchers.IO) {
        saveWorkspaceInfoDetailed(companyId, info) is WorkspaceSaveResult.Success
    }

    suspend fun resolveWorkspaceBySyncCodeDetailed(syncCode: String): WorkspaceLookupResult = withContext(Dispatchers.IO) {
        val normalizedSyncCode = syncCode.trim().uppercase(java.util.Locale.ROOT)
        if (normalizedSyncCode.isBlank()) {
            return@withContext WorkspaceLookupResult.NotFound(normalizedSyncCode, 400)
        }

        // Step 1: Ensure authenticated session exists
        ensureAuthSession()
        val token = workspaceManager.currentAuthToken
        val authUid = workspaceManager.currentAuthUid ?: workspaceManager.extractSubFromJwt(token)
        if (token.isNullOrBlank() || authUid.isNullOrBlank() || workspaceManager.isTokenExpired(token)) {
            return@withContext WorkspaceLookupResult.NoToken(normalizedSyncCode)
        }

        val jsonPayload = JSONObject().apply { put("p_sync_code", normalizedSyncCode) }.toString()
        val requestBody = jsonPayload.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("$baseUrl/rpc/resolve_workspace_by_sync_code")
            .post(requestBody)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                val httpStatus = response.code
                Log.i("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [RESOLVE_WORKSPACE_BY_SYNC_CODE] syncCode=$normalizedSyncCode httpStatus=$httpStatus responseBody=$bodyString")
                if (response.isSuccessful) {
                    val trimmed = bodyString.trim()
                    if (trimmed.startsWith("[")) {
                        val array = JSONArray(trimmed)
                        if (array.length() > 0) {
                            val json = array.getJSONObject(0)
                            val compId = json.optString("company_id")
                            if (compId.isNotBlank()) {
                                val info = WorkspaceInfo(
                                    companyId = compId,
                                    companySyncCode = json.optString("sync_code", normalizedSyncCode).ifBlank { normalizedSyncCode },
                                    centerName = json.optString("center_name", json.optString("name", "")),
                                    nationalCode = json.optString("national_code", ""),
                                    supportPhone = json.optString("support_phone", ""),
                                    centerAddress = json.optString("center_address", ""),
                                    createdTimestamp = json.optLong("created_timestamp", json.optLong("created_at", System.currentTimeMillis()))
                                )
                                Log.i("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [WORKSPACE_FOUND] syncCode=$normalizedSyncCode canonicalCompanyId=${info.companyId} centerName=${info.centerName}")
                                return@use WorkspaceLookupResult.Success(info, httpStatus)
                            } else {
                                Log.w("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [WORKSPACE_NOT_FOUND] syncCode=$normalizedSyncCode empty company_id in row")
                                return@use WorkspaceLookupResult.NotFound(normalizedSyncCode, httpStatus)
                            }
                        } else {
                            Log.w("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [WORKSPACE_NOT_FOUND] syncCode=$normalizedSyncCode empty array returned")
                            return@use WorkspaceLookupResult.NotFound(normalizedSyncCode, httpStatus)
                        }
                    } else if (trimmed.startsWith("{")) {
                        val json = JSONObject(trimmed)
                        val compId = json.optString("company_id")
                        if (compId.isNotBlank()) {
                            val info = WorkspaceInfo(
                                companyId = compId,
                                companySyncCode = json.optString("sync_code", normalizedSyncCode).ifBlank { normalizedSyncCode },
                                centerName = json.optString("center_name", json.optString("name", "")),
                                nationalCode = json.optString("national_code", ""),
                                supportPhone = json.optString("support_phone", ""),
                                centerAddress = json.optString("center_address", ""),
                                createdTimestamp = json.optLong("created_timestamp", json.optLong("created_at", System.currentTimeMillis()))
                            )
                            Log.i("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [WORKSPACE_FOUND] syncCode=$normalizedSyncCode canonicalCompanyId=${info.companyId} centerName=${info.centerName}")
                            return@use WorkspaceLookupResult.Success(info, httpStatus)
                        } else if (json.has("code") || json.has("message")) {
                            val msg = json.optString("message", json.optString("hint", bodyString))
                            Log.e("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [WORKSPACE_RPC_ERROR] syncCode=$normalizedSyncCode code=$httpStatus msg=$msg")
                            return@use WorkspaceLookupResult.Error(httpStatus, msg, normalizedSyncCode)
                        } else {
                            return@use WorkspaceLookupResult.NotFound(normalizedSyncCode, httpStatus)
                        }
                    } else {
                        return@use WorkspaceLookupResult.NotFound(normalizedSyncCode, httpStatus)
                    }
                } else {
                    Log.e("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [WORKSPACE_LOOKUP_ERROR] syncCode=$normalizedSyncCode httpStatus=$httpStatus responseBody=$bodyString")
                    val msg = try {
                        val errJson = JSONObject(bodyString)
                        errJson.optString("message", errJson.optString("details", bodyString))
                    } catch (_: Exception) {
                        bodyString
                    }
                    return@use WorkspaceLookupResult.Error(httpStatus, msg.ifBlank { "HTTP $httpStatus" }, normalizedSyncCode)
                }
            }
        } catch (e: Exception) {
            Log.e("CloudClient", "Error resolving workspace by sync code $normalizedSyncCode", e)
            return@withContext WorkspaceLookupResult.NetworkError(e, normalizedSyncCode)
        }
    }

    suspend fun getWorkspaceBySyncCode(syncCode: String): WorkspaceInfo? = withContext(Dispatchers.IO) {
        val result = resolveWorkspaceBySyncCodeDetailed(syncCode)
        if (result is WorkspaceLookupResult.Success) result.workspace else null
    }

    suspend fun ensureAuthSession(targetCompanyId: String? = null, targetSyncCode: String? = null): com.example.data.supabase.AuthResult {
        Log.i("AUTH_TRACE", "AUTH_IDENTITY_CHECK_START")
        
        val currentToken = workspaceManager.currentAuthToken
        val isExpired = workspaceManager.isTokenExpired(currentToken)
        val authRepo = com.example.data.supabase.SupabaseAuthRepository(supabaseManager, workspaceManager)
        
        val storedAuthUserId = workspaceManager.currentAuthUid
        val companyId = targetCompanyId ?: dao?.getSystemSettingByKey("company_id") ?: ""
        val deviceId = dao?.getSystemSettingByKey("active_device_id") ?: workspaceManager.getDeviceId()
        
        Log.i("AUTH_TRACE", "AUTH_IDENTITY_CURRENT storedAuthUserId=$storedAuthUserId companyId=$companyId deviceId=$deviceId sessionExists=${!currentToken.isNullOrBlank()} accessTokenValid=${!currentToken.isNullOrBlank() && !isExpired}")

        if (currentToken.isNullOrBlank() || isExpired) {
            var refreshed = false
            var refreshAttempted = false
            var refreshSucceeded = false
            
            if (!workspaceManager.currentRefreshToken.isNullOrBlank()) {
                refreshAttempted = true
                Log.i("AUTH_TRACE", "AUTH_SESSION_REFRESH_START")
                val refreshResult = authRepo.refreshSession()
                if (refreshResult is com.example.data.supabase.AuthResult.Success) {
                    refreshed = true
                    refreshSucceeded = true
                    Log.i("AUTH_TRACE", "AUTH_SESSION_REFRESH_SUCCESS")
                } else {
                    Log.i("AUTH_TRACE", "AUTH_SESSION_REFRESH_FAILURE")
                }
            }
            
            if (!refreshed) {
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
            }
        }
        
        val finalToken = workspaceManager.currentAuthToken
        if (finalToken.isNullOrBlank() || workspaceManager.isTokenExpired(finalToken)) {
            Log.e("AUTH_TRACE", "AUTH_SESSION_UNRECOVERABLE")
            return com.example.data.supabase.AuthResult.Error("Failed to obtain valid session token.")
        }
        
        val currentAuthUserId = workspaceManager.currentAuthUid ?: workspaceManager.extractSubFromJwt(finalToken)
        
        Log.i("AUTH_TRACE", "PAIRING_AUTH_IDENTITY_CHECK storedAuthUserId=$storedAuthUserId currentAuthUserId=$currentAuthUserId companyId=$companyId deviceId=$deviceId sessionExists=${!finalToken.isNullOrBlank()} accessTokenValid=${!workspaceManager.isTokenExpired(finalToken)}")
        
        if (!storedAuthUserId.isNullOrBlank() && currentAuthUserId != storedAuthUserId) {
            Log.e("AUTH_TRACE", "AUTH_IDENTITY_DRIFT_DETECTED: $storedAuthUserId != $currentAuthUserId")
            return com.example.data.supabase.AuthResult.Error("AUTH_IDENTITY_DRIFT_DETECTED")
        }
        
        Log.i("AUTH_TRACE", "AUTH_IDENTITY_CHECK_SUCCESS")
        Log.i("AUTH_TRACE", "PAIRING_AUTH_READY authUserId=$currentAuthUserId companyId=$companyId deviceId=$deviceId tokenPresent=true")
        
        val localCompany = targetCompanyId ?: workspaceManager.currentTenantId ?: dao?.getSystemSettingByKey("company_id")
        val localSync = targetSyncCode ?: workspaceManager.currentSyncCode ?: dao?.getSystemSettingByKey("company_sync_code")
        Log.i("IDENTITY_RECOVERY", "[IDENTITY_RECOVERY] authUid=$currentAuthUserId localCompanyId=$localCompany localSyncCode=$localSync remoteCreatorUid=N/A decision=ENSURE_AUTH_SESSION")
        
        return com.example.data.supabase.AuthResult.Success
    }

    private var lastAuthUid: String? = null
    private var cachedBootstrapResult: BootstrapResult? = null

    private fun buildDevicePatchPayload(
        deviceName: String? = null,
        deviceType: String? = null,
        appVersion: String? = null,
        lastOnlineTime: Long? = null,
        lastSeen: Long? = null,
        lastSuccessfulSync: Long? = null,
        status: String? = null,
        role: String? = null,
        requestedRole: String? = null
    ): JSONObject {
        val json = JSONObject()
        deviceName?.let { json.put("device_name", it) }
        deviceType?.let { json.put("device_type", it) }
        appVersion?.let { json.put("app_version", it) }
        lastOnlineTime?.let { json.put("last_online_time", it) }
        lastSeen?.let { json.put("last_seen", it) }
        lastSuccessfulSync?.let { json.put("last_successful_sync", it) }
        status?.let { json.put("status", it) }
        role?.let { json.put("role", it) }
        requestedRole?.let { json.put("requested_role", it) }
        return json
    }

    private fun logConnectedDevicePatch(deviceId: String, json: JSONObject) {
        val keysList = mutableListOf<String>()
        val iter = json.keys()
        while (iter.hasNext()) {
            keysList.add(iter.next())
        }
        Log.i("CONNECTED_DEVICE_PATCH", "[CONNECTED_DEVICE_PATCH]\ndeviceId=$deviceId\npayloadKeys=${keysList.joinToString(",")}")
    }

    // --- Canonical Device Write Authority (R25/R26) ---
    suspend fun ensureCanonicalDevice(
        companyId: String,
        authUid: String,
        deviceId: String,
        deviceRole: String = "Nurse",
        deviceName: String = "تلفن همراه",
        deviceType: String = "Phone",
        requestedRole: String = deviceRole,
        initialStatus: String = if (deviceRole == "Mother Account") "Active" else "Pending"
    ): DeviceResolution = withContext(Dispatchers.IO) {
        deviceWriteMutex.withLock {
            val authContext = requireAuthenticatedMutationContext(
                operation = "ENSURE_CANONICAL_DEVICE",
                companyId = companyId,
                deviceId = deviceId,
                requireActiveDevice = false
            )
            if (!authContext.allowed) {
                return@withLock DeviceResolution.Unauthorized(401, authContext.reason)
            }
            val currentAuthUid = authContext.authUid ?: authUid

            if (deviceRole == "Mother Account") {
                val rpcJson = JSONObject().apply {
                    put("p_app_version", "v2.0.0")
                    put("p_company_id", companyId)
                    put("p_device_id", deviceId)
                    put("p_device_name", deviceName)
                    put("p_device_type", deviceType)
                    put("p_requested_role", "Mother Account")
                }.toString()
                
                val rpcRequest = Request.Builder()
                    .url("$baseUrl/rpc/bootstrap_creator_device")
                    .post(rpcJson.toRequestBody(jsonMediaType))
                    .build()
                    
                try {
                    client.newCall(rpcRequest).execute().use { response ->
                        if (!response.isSuccessful) {
                            val code = response.code
                            val errBody = response.body?.string() ?: ""
                            Log.e("DEVICE_WRITE", "RPC failed: HTTP $code $errBody")
                            return@withLock DeviceResolution.Unauthorized(code, "خطای دسترسی در ثبت دستگاه مدیر: $errBody")
                        }
                    }
                } catch (e: Exception) {
                    return@withLock DeviceResolution.Failed(e)
                }
                
                return@withLock resolveDevice(companyId, deviceId)
            }
            
            val resolution = resolveDevice(companyId, deviceId)
            when (resolution) {
                is DeviceResolution.ExistsActive,
                is DeviceResolution.ExistsPending,
                is DeviceResolution.ExistsOther -> {
                    val existing = when (resolution) {
                        is DeviceResolution.ExistsActive -> resolution.device
                        is DeviceResolution.ExistsPending -> resolution.device
                        is DeviceResolution.ExistsOther -> resolution.device
                        else -> throw IllegalStateException()
                    }
                    if (existing.companyId == companyId && existing.uid == currentAuthUid) {
                        return@withLock resolution
                    } else {
                        return@withLock DeviceResolution.Unauthorized(403, "این دستگاه به حساب یا مرکز دیگری متصل است.")
                    }
                }
                is DeviceResolution.NotFound -> {
                    val insertJson = JSONObject().apply {
                        put("device_id", deviceId)
                        put("company_id", companyId)
                        put("device_name", deviceName)
                        put("device_type", deviceType)
                        put("app_version", "v2.0.0")
                        put("last_online_time", System.currentTimeMillis())
                        put("last_successful_sync", 0L)
                        put("status", "Pending")
                        put("uid", currentAuthUid)
                        put("role", deviceRole)
                        put("last_seen", System.currentTimeMillis())
                        put("requested_role", requestedRole)
                    }.toString()

                    val insertRequest = Request.Builder()
                        .url("$baseUrl/connected_devices")
                        .post(insertJson.toRequestBody(jsonMediaType))
                        .build()

                    try {
                        client.newCall(insertRequest).execute().use { response ->
                            if (!response.isSuccessful) {
                                val code = response.code
                                val errBody = response.body?.string() ?: ""
                                return@withLock DeviceResolution.Unauthorized(code, "HTTP $code: $errBody")
                            }
                        }
                    } catch (e: Exception) {
                        return@withLock DeviceResolution.Failed(e)
                    }
                    return@withLock resolveDevice(companyId, deviceId)
                }
                is DeviceResolution.Failed,
                is DeviceResolution.Unauthorized -> {
                    return@withLock resolution
                }
            }
        }
    }

    // --- Hard Business Sync Gate (R25) ---
    suspend fun canSyncBusinessData(
        companyId: String? = null,
        deviceId: String? = null
    ): SyncAuthorizationResult = withContext(Dispatchers.IO) {
        val targetCompanyId = companyId
            ?: workspaceManager.currentTenantId?.takeIf { it.isNotBlank() && it != "COMP-LOCAL" }
            ?: dao?.getSystemSettingByKey("company_id")?.takeIf { it.isNotBlank() && it != "COMP-LOCAL" }

        val targetDeviceId = deviceId?.takeIf { DeviceIdentityProvider.isValidUuidDeviceId(it) }
            ?: (dao?.getSystemSettingByKey("active_device_id")?.takeIf { DeviceIdentityProvider.isValidUuidDeviceId(it) })
            ?: context?.let { DeviceIdentityProvider.getDeviceId(it) }
            ?: workspaceManager.getDeviceId()

        if (targetCompanyId.isNullOrBlank()) {
            Log.w("SYNC_GATE_BLOCKED", "[SYNC_GATE_BLOCKED] allowed=false reason=NO_COMPANY_ID")
            return@withContext SyncAuthorizationResult(
                allowed = false,
                reason = "No company ID configured",
                companyId = targetCompanyId,
                deviceId = targetDeviceId
            )
        }

        if (targetDeviceId.isNullOrBlank()) {
            Log.w("SYNC_GATE_BLOCKED", "[SYNC_GATE_BLOCKED] allowed=false reason=NO_DEVICE_ID")
            return@withContext SyncAuthorizationResult(
                allowed = false,
                reason = "No device ID configured",
                companyId = targetCompanyId,
                deviceId = targetDeviceId
            )
        }

        // 1. Valid authenticated session exists & auth.uid is non-null
        ensureAuthSession(targetCompanyId)
        val token = workspaceManager.currentAuthToken
        val authUid = workspaceManager.currentAuthUid ?: workspaceManager.extractSubFromJwt(token)
        if (token.isNullOrBlank() || authUid.isNullOrBlank() || workspaceManager.isTokenExpired(token)) {
            Log.w("SYNC_GATE_BLOCKED", "[SYNC_GATE_BLOCKED] authUid=$authUid companyId=$targetCompanyId deviceId=$targetDeviceId allowed=false reason=AUTH_INVALID")
            return@withContext SyncAuthorizationResult(
                allowed = false,
                reason = "Authentication session invalid or missing",
                authUid = authUid,
                companyId = targetCompanyId,
                deviceId = targetDeviceId
            )
        }

        // 2. Canonical workspace is remotely confirmed
        val wsRes = resolveWorkspace(targetCompanyId)
        val workspaceConfirmed = wsRes is WorkspaceResolution.ExistsAndOwned || wsRes is WorkspaceResolution.ExistsForeign
        if (!workspaceConfirmed) {
            val reason = when (wsRes) {
                is WorkspaceResolution.NotFound -> "Workspace does not exist on remote"
                is WorkspaceResolution.Unauthorized -> "Unauthorized to access workspace (${wsRes.code})"
                is WorkspaceResolution.Failed -> "Failed to resolve workspace: ${wsRes.exception.message}"
                else -> "Workspace not confirmed"
            }
            Log.w("SYNC_GATE_BLOCKED", "[SYNC_GATE_BLOCKED] authUid=$authUid companyId=$targetCompanyId deviceId=$targetDeviceId workspaceConfirmed=false allowed=false reason=$reason")
            return@withContext SyncAuthorizationResult(
                allowed = false,
                reason = reason,
                authUid = authUid,
                companyId = targetCompanyId,
                deviceId = targetDeviceId,
                workspaceConfirmed = false
            )
        }

        // 3. Canonical device is remotely confirmed & belongs to canonical company & auth.uid & is Active
        val devRes = resolveDevice(targetCompanyId, targetDeviceId)
        val deviceConfirmed = devRes is DeviceResolution.ExistsActive
        val deviceStatus = when (devRes) {
            is DeviceResolution.ExistsActive -> devRes.device.status
            is DeviceResolution.ExistsPending -> devRes.device.status
            is DeviceResolution.ExistsOther -> devRes.device.status
            else -> null
        }

        if (devRes !is DeviceResolution.ExistsActive) {
            val reason = when (devRes) {
                is DeviceResolution.ExistsPending -> "Device registration is Pending approval"
                is DeviceResolution.ExistsOther -> "Device status is ${devRes.device.status}"
                is DeviceResolution.NotFound -> "Device not registered on remote"
                is DeviceResolution.Unauthorized -> "Unauthorized to resolve device (${devRes.code})"
                is DeviceResolution.Failed -> "Failed to resolve device: ${devRes.exception.message}"
                else -> "Device not active"
            }
            Log.w("SYNC_GATE_BLOCKED", "[SYNC_GATE_BLOCKED] authUid=$authUid companyId=$targetCompanyId deviceId=$targetDeviceId deviceStatus=$deviceStatus allowed=false reason=$reason")
            return@withContext SyncAuthorizationResult(
                allowed = false,
                reason = reason,
                authUid = authUid,
                companyId = targetCompanyId,
                deviceId = targetDeviceId,
                deviceStatus = deviceStatus,
                workspaceConfirmed = true,
                deviceConfirmed = false
            )
        }

        val activeDevice = devRes.device
        if (activeDevice.companyId != targetCompanyId || (activeDevice.uid.isNotBlank() && activeDevice.uid != authUid)) {
            val reason = "Device identity/company mismatch (devCompany=${activeDevice.companyId}, devUid=${activeDevice.uid})"
            Log.w("SYNC_GATE_BLOCKED", "[SYNC_GATE_BLOCKED] authUid=$authUid companyId=$targetCompanyId deviceId=$targetDeviceId allowed=false reason=$reason")
            return@withContext SyncAuthorizationResult(
                allowed = false,
                reason = reason,
                authUid = authUid,
                companyId = targetCompanyId,
                deviceId = targetDeviceId,
                deviceStatus = activeDevice.status,
                workspaceConfirmed = true,
                deviceConfirmed = false
            )
        }

        Log.i("SYNC_GATE", "[SYNC_GATE]\nauthUid=$authUid\ncompanyId=$targetCompanyId\ndeviceId=$targetDeviceId\ndeviceStatus=${activeDevice.status}\nworkspaceConfirmed=true\ndeviceConfirmed=true\nallowed=true\nreason=ALL_CONDITIONS_VERIFIED")

        SyncAuthorizationResult(
            allowed = true,
            reason = "All bootstrap conditions verified",
            authUid = authUid,
            companyId = targetCompanyId,
            deviceId = targetDeviceId,
            deviceStatus = activeDevice.status,
            workspaceConfirmed = true,
            deviceConfirmed = true
        )
    }

    // --- Canonical Bootstrap Orchestrator (R24/R25) ---
    suspend fun ensureCanonicalCloudBootstrap(
        companyId: String? = null,
        syncCode: String? = null,
        forcedDeviceId: String? = null
    ): BootstrapResult = withContext(Dispatchers.IO) {
        bootstrapMutex.withLock {
            val targetCompanyId = companyId 
                ?: workspaceManager.currentTenantId?.takeIf { it.isNotBlank() && it != "COMP-LOCAL" }
                ?: dao?.getSystemSettingByKey("company_id")?.takeIf { it.isNotBlank() && it != "COMP-LOCAL" }
                ?: ""

            val targetSyncCode = syncCode 
                ?: workspaceManager.currentSyncCode?.takeIf { it.isNotBlank() && it != "HAMRAHAN-LOCAL-WORK" }
                ?: dao?.getSystemSettingByKey("company_sync_code")?.takeIf { it.isNotBlank() && it != "HAMRAHAN-LOCAL-WORK" }
                ?: ""

            val devId = forcedDeviceId?.takeIf { DeviceIdentityProvider.isValidUuidDeviceId(it) }
                ?: (dao?.getSystemSettingByKey("active_device_id")?.takeIf { DeviceIdentityProvider.isValidUuidDeviceId(it) })
                ?: context?.let { DeviceIdentityProvider.getDeviceId(it) }
                ?: workspaceManager.getDeviceId()

            if (targetCompanyId.isBlank()) {
                Log.i("BOOTSTRAP", "[BOOTSTRAP] stage=AUTH_READY companyId= deviceId=$devId authUidPresent=false result=SKIPPED_NO_WORKSPACE")
                return@withLock BootstrapResult.Blocked("No company ID configured")
            }

            // STAGE 1: AUTH_READY
            ensureAuthSession(targetCompanyId, targetSyncCode)
            val token = workspaceManager.currentAuthToken
            val authUid = workspaceManager.currentAuthUid ?: workspaceManager.extractSubFromJwt(token)
            val authUidPresent = !authUid.isNullOrBlank()

            if (token.isNullOrBlank() || !authUidPresent || workspaceManager.isTokenExpired(token)) {
                Log.w("BOOTSTRAP", "[BOOTSTRAP] stage=AUTH_READY companyId=$targetCompanyId deviceId=$devId authUidPresent=$authUidPresent result=AUTH_FAILED")
                return@withLock BootstrapResult.Blocked("Authentication session unavailable", 401)
            }

            // Invalidate cache if auth UID changed
            if (authUid != lastAuthUid) {
                cachedBootstrapResult = null
                lastAuthUid = authUid
            }

            // Return cached result if already SyncAllowed for this session and same company/device
            val cached = cachedBootstrapResult
            if (cached is BootstrapResult.SyncAllowed && cached.companyId == targetCompanyId && cached.device.deviceId == devId && cached.authUid == authUid) {
                return@withLock cached
            }

            Log.i("BOOTSTRAP", "[BOOTSTRAP] stage=AUTH_READY companyId=$targetCompanyId deviceId=$devId authUidPresent=true result=SUCCESS")

            // STAGE 2: WORKSPACE_RESOLVED
            val wsRes = resolveWorkspace(targetCompanyId)
            val confirmedWorkspace: WorkspaceInfo = when (wsRes) {
                is WorkspaceResolution.ExistsAndOwned -> {
                    Log.i("BOOTSTRAP", "[BOOTSTRAP] stage=WORKSPACE_RESOLVED companyId=$targetCompanyId deviceId=$devId authUidPresent=true result=CONFIRMED_OWNED")
                    wsRes.workspace
                }
                is WorkspaceResolution.ExistsForeign -> {
                    Log.w("BOOTSTRAP", "[BOOTSTRAP] stage=WORKSPACE_RESOLVED companyId=$targetCompanyId deviceId=$devId authUidPresent=true result=IDENTITY_RECOVERY_REQUIRED")
                    return@withLock BootstrapResult.IdentityRecoveryRequired(
                        targetCompanyId,
                        "Workspace belongs to a different owner",
                        wsRes.remoteCreatorUid
                    )
                }
                is WorkspaceResolution.NotFound -> {
                    Log.i("BOOTSTRAP", "[BOOTSTRAP] stage=WORKSPACE_RESOLVED companyId=$targetCompanyId deviceId=$devId authUidPresent=true result=NOT_FOUND_CREATING")
                    val centerName = dao?.getSystemSettingByKey("center_name") ?: dao?.getSystemSettingByKey("company_name") ?: "مرکز خدمات پرستاری"
                    val natCode = dao?.getSystemSettingByKey("national_code") ?: dao?.getSystemSettingByKey("company_national_code") ?: ""
                    val phone = dao?.getSystemSettingByKey("support_phone") ?: dao?.getSystemSettingByKey("company_phone") ?: ""
                    val address = dao?.getSystemSettingByKey("center_address") ?: dao?.getSystemSettingByKey("company_address") ?: ""
                    val newWs = WorkspaceInfo(
                        companyId = targetCompanyId,
                        companySyncCode = targetSyncCode,
                        centerName = centerName,
                        nationalCode = natCode,
                        supportPhone = phone,
                        centerAddress = address,
                        createdTimestamp = System.currentTimeMillis(),
                        creatorUid = authUid
                    )
                    when (val saveRes = saveWorkspaceInfoDetailed(targetCompanyId, newWs)) {
                        is WorkspaceSaveResult.Success -> {
                            newWs
                        }
                        is WorkspaceSaveResult.OwnershipMismatch -> {
                            Log.w("BOOTSTRAP", "[BOOTSTRAP] stage=WORKSPACE_REMOTE_CONFIRMED companyId=$targetCompanyId deviceId=$devId authUidPresent=true result=OWNERSHIP_MISMATCH")
                            return@withLock BootstrapResult.IdentityRecoveryRequired(
                                targetCompanyId,
                                "Save conflict: ownership mismatch",
                                saveRes.remoteCreatorUid
                            )
                        }
                        is WorkspaceSaveResult.Error -> {
                            Log.e("BOOTSTRAP", "[BOOTSTRAP] stage=WORKSPACE_REMOTE_CONFIRMED companyId=$targetCompanyId deviceId=$devId authUidPresent=true result=SAVE_ERROR code=${saveRes.code}")
                            return@withLock BootstrapResult.Blocked("Workspace save failed: ${saveRes.message}", saveRes.code)
                        }
                        is WorkspaceSaveResult.NoToken -> {
                            return@withLock BootstrapResult.Blocked("Authentication token missing", 401)
                        }
                        is WorkspaceSaveResult.NetworkError -> {
                            return@withLock BootstrapResult.Error("Network error saving workspace", saveRes.exception)
                        }
                    }
                }
                is WorkspaceResolution.Unauthorized -> {
                    Log.w("BOOTSTRAP", "[BOOTSTRAP] stage=WORKSPACE_RESOLVED companyId=$targetCompanyId deviceId=$devId authUidPresent=true result=UNAUTHORIZED code=${wsRes.code}")
                    return@withLock BootstrapResult.Blocked("Unauthorized to resolve workspace: ${wsRes.message}", wsRes.code)
                }
                is WorkspaceResolution.Failed -> {
                    Log.e("BOOTSTRAP", "[BOOTSTRAP] stage=WORKSPACE_RESOLVED companyId=$targetCompanyId deviceId=$devId authUidPresent=true result=FAILED", wsRes.exception)
                    return@withLock BootstrapResult.Error("Failed to resolve workspace", wsRes.exception)
                }
            }
            Log.i("BOOTSTRAP", "[BOOTSTRAP] stage=WORKSPACE_REMOTE_CONFIRMED companyId=$targetCompanyId deviceId=$devId authUidPresent=true result=CONFIRMED")

            // STAGE 3: DEVICE_RESOLVED & DEVICE_REMOTE_CONFIRMED_ACTIVE via ensureCanonicalDevice
            val devRole = dao?.getSystemSettingByKey("active_device_role") ?: "Mother Account"
            val devName = dao?.getSystemSettingByKey("active_device_name") ?: "تلفن همراه"
            val isMother = devRole == "Mother Account" || confirmedWorkspace.creatorUid == authUid
            val initialStatus = if (isMother) "Active" else "Pending"

            val devRes = ensureCanonicalDevice(
                companyId = targetCompanyId,
                authUid = authUid,
                deviceId = devId,
                deviceRole = devRole,
                deviceName = devName,
                deviceType = "Phone",
                requestedRole = devRole,
                initialStatus = initialStatus
            )

            val finalDevice: ConnectedDevice = when (devRes) {
                is DeviceResolution.ExistsActive -> {
                    Log.i("BOOTSTRAP", "[BOOTSTRAP] stage=DEVICE_RESOLVED deviceId=$devId companyId=$targetCompanyId authUidPresent=true result=CONFIRMED_ACTIVE")
                    dao?.insertSystemSetting(SystemSetting("active_device_status", "Active"))
                    dao?.insertSystemSetting(SystemSetting("device_has_been_approved", "true"))
                    devRes.device
                }
                is DeviceResolution.ExistsPending -> {
                    Log.i("BOOTSTRAP", "[BOOTSTRAP] stage=DEVICE_RESOLVED deviceId=$devId companyId=$targetCompanyId authUidPresent=true result=PENDING_APPROVAL")
                    dao?.insertSystemSetting(SystemSetting("active_device_status", "Pending"))
                    dao?.insertSystemSetting(SystemSetting("device_has_been_approved", "false"))
                    return@withLock BootstrapResult.PendingApproval(
                        targetCompanyId,
                        devId,
                        devRes.device,
                        "Device registration is pending approval by center administrator"
                    )
                }
                is DeviceResolution.ExistsOther -> {
                    Log.w("BOOTSTRAP", "[BOOTSTRAP] stage=DEVICE_RESOLVED deviceId=$devId companyId=$targetCompanyId authUidPresent=true result=OTHER_STATUS status=${devRes.device.status}")
                    dao?.insertSystemSetting(SystemSetting("active_device_status", devRes.device.status))
                    return@withLock BootstrapResult.Blocked("Device status is ${devRes.device.status}")
                }
                is DeviceResolution.NotFound -> {
                    Log.e("BOOTSTRAP", "[BOOTSTRAP] stage=DEVICE_RESOLVED deviceId=$devId companyId=$targetCompanyId authUidPresent=true result=NOT_FOUND_AFTER_ENSURE")
                    return@withLock BootstrapResult.Blocked("Device registration could not be confirmed on remote")
                }
                is DeviceResolution.Unauthorized -> {
                    Log.w("BOOTSTRAP", "[BOOTSTRAP] stage=DEVICE_RESOLVED deviceId=$devId companyId=$targetCompanyId authUidPresent=true result=UNAUTHORIZED code=${devRes.code}")
                    return@withLock BootstrapResult.Blocked("Unauthorized to resolve device: ${devRes.message}", devRes.code)
                }
                is DeviceResolution.Failed -> {
                    Log.e("BOOTSTRAP", "[BOOTSTRAP] stage=DEVICE_RESOLVED deviceId=$devId companyId=$targetCompanyId authUidPresent=true result=FAILED", devRes.exception)
                    return@withLock BootstrapResult.Error("Failed to resolve device", devRes.exception)
                }
            }

            val authGate = canSyncBusinessData(targetCompanyId, devId)
            if (authGate.allowed) {
                Log.i("BOOTSTRAP_COMPLETE", "[BOOTSTRAP_COMPLETE]\nauthUid=$authUid\ncompanyId=$targetCompanyId\ndeviceId=$devId\ndeviceStatus=${finalDevice.status}\nsyncAllowed=true")
                val syncAllowedRes = BootstrapResult.SyncAllowed(
                    companyId = targetCompanyId,
                    authUid = authUid,
                    device = finalDevice,
                    workspace = confirmedWorkspace
                )
                cachedBootstrapResult = syncAllowedRes
                syncAllowedRes
            } else {
                Log.w("SYNC_GATE_BLOCKED", "[SYNC_GATE_BLOCKED] companyId=$targetCompanyId deviceId=$devId reason=${authGate.reason}")
                BootstrapResult.Blocked(authGate.reason)
            }
        }
    }

    // --- Device Management (Delegates to ensureCanonicalDevice) ---
    suspend fun registerDeviceDetailed(companyId: String, device: ConnectedDevice): DeviceRegistrationResult = withContext(Dispatchers.IO) {
        val authUid = workspaceManager.currentAuthUid 
            ?: workspaceManager.extractSubFromJwt(workspaceManager.currentAuthToken) 
            ?: device.uid
        val res = ensureCanonicalDevice(
            companyId = companyId,
            authUid = authUid,
            deviceId = device.deviceId,
            deviceRole = device.role,
            deviceName = device.deviceName,
            deviceType = device.deviceType,
            requestedRole = device.requestedRole,
            initialStatus = device.status
        )
        val result: DeviceRegistrationResult = when (res) {
            is DeviceResolution.ExistsActive -> DeviceRegistrationResult.Success(200)
            is DeviceResolution.ExistsPending -> {
                if (device.role == "Mother Account") {
                    DeviceRegistrationResult.Success(200)
                } else {
                    DeviceRegistrationResult.PendingAccepted(202, "درخواست اتصال دستگاه ارسال شد و منتظر تأیید سرپرست مرکز است.")
                }
            }
            is DeviceResolution.Unauthorized -> DeviceRegistrationResult.Error(res.code, res.message)
            is DeviceResolution.Failed -> DeviceRegistrationResult.NetworkError(res.exception)
            is DeviceResolution.ExistsOther -> DeviceRegistrationResult.Error(403, "وضعیت دستگاه نامعتبر است (${res.device.status})")
            is DeviceResolution.NotFound -> DeviceRegistrationResult.Error(404, "ثبت دستگاه در سرور ابری تأیید نشد.")
        }
        val httpStatus = when (result) {
            is DeviceRegistrationResult.Success -> result.httpStatus
            is DeviceRegistrationResult.PendingAccepted -> result.httpStatus
            is DeviceRegistrationResult.Error -> result.code
            is DeviceRegistrationResult.NetworkError -> 0
        }
        val responseBodyOrError = when (result) {
            is DeviceRegistrationResult.Success -> "OK"
            is DeviceRegistrationResult.PendingAccepted -> result.message
            is DeviceRegistrationResult.Error -> result.message
            is DeviceRegistrationResult.NetworkError -> result.exception.message ?: "Network failure"
        }
        Log.i("DEVICE_REG_TRACE", "[DEVICE_REG_TRACE]\ndeviceId=${device.deviceId}\ncompanyId=$companyId\nHTTP status=$httpStatus\nresponse body/error=$responseBodyOrError\nfinal local device state=${device.status}")
        result
    }

    suspend fun registerDevice(companyId: String, device: ConnectedDevice): Boolean = withContext(Dispatchers.IO) {
        val res = registerDeviceDetailed(companyId, device)
        res is DeviceRegistrationResult.Success || res is DeviceRegistrationResult.PendingAccepted
    }

    suspend fun patchDeviceHeartbeat(companyId: String, device: ConnectedDevice): Boolean = withContext(Dispatchers.IO) {
        val authContext = requireAuthenticatedMutationContext(
            operation = "PATCH_DEVICE_HEARTBEAT",
            companyId = companyId,
            deviceId = device.deviceId,
            requireActiveDevice = false
        )
        if (!authContext.allowed) {
            Log.w("MUTATION_BLOCKED", "[MUTATION_BLOCKED]\noperation=PATCH_DEVICE_HEARTBEAT\nreason=${authContext.reason}")
            return@withContext false
        }

        val patchPayload = buildDevicePatchPayload(
            lastOnlineTime = System.currentTimeMillis(),
            lastSuccessfulSync = device.lastSuccessfulSync,
            lastSeen = System.currentTimeMillis()
        )
        logConnectedDevicePatch(device.deviceId, patchPayload)
        val json = patchPayload.toString()

        val request = Request.Builder()
            .url("$baseUrl/connected_devices?device_id=eq.${device.deviceId}")
            .patch(json.toRequestBody(jsonMediaType))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val isSuccess = response.isSuccessful
                Log.i("AUTH_MUTATION_RESULT", "[AUTH_MUTATION_RESULT]\noperation=PATCH_DEVICE_HEARTBEAT\nhttpCode=${response.code}\nsuccess=$isSuccess")
                isSuccess
            }
        } catch (e: Exception) {
            Log.i("AUTH_MUTATION_RESULT", "[AUTH_MUTATION_RESULT]\noperation=PATCH_DEVICE_HEARTBEAT\nhttpCode=0\nsuccess=false")
            false
        }
    }

    suspend fun patchDeviceAuthorization(companyId: String, deviceId: String, status: String, role: String): Boolean = withContext(Dispatchers.IO) {
        val authContext = requireAuthenticatedMutationContext(
            operation = "PATCH_DEVICE_AUTH",
            companyId = companyId,
            deviceId = deviceId,
            requireActiveDevice = false
        )
        if (!authContext.allowed) {
            Log.w("MUTATION_BLOCKED", "[MUTATION_BLOCKED]\noperation=PATCH_DEVICE_AUTH\nreason=${authContext.reason}")
            return@withContext false
        }

        val patchPayload = buildDevicePatchPayload(
            status = status,
            role = role
        )
        logConnectedDevicePatch(deviceId, patchPayload)
        val json = patchPayload.toString()

        val request = Request.Builder()
            .url("$baseUrl/connected_devices?device_id=eq.$deviceId&company_id=eq.$companyId")
            .header("Prefer", "return=representation")
            .patch(json.toRequestBody(jsonMediaType))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val isSuccess = response.isSuccessful
                Log.i("AUTH_MUTATION_RESULT", "[AUTH_MUTATION_RESULT]\noperation=PATCH_DEVICE_AUTH\nhttpCode=${response.code}\nsuccess=$isSuccess")
                Log.i("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [SUPABASE_APPROVAL_RESPONSE] patchDeviceAuthorization deviceId=$deviceId companyId=$companyId status=$status HTTP=${response.code}")
                if (isSuccess) {
                    val bodyString = response.body?.string() ?: ""
                    if (bodyString.isNotBlank() && bodyString.startsWith("[")) {
                        try {
                            val array = JSONArray(bodyString)
                            if (array.length() > 0) {
                                val obj = array.getJSONObject(0)
                                val returnedStatus = obj.optString("status")
                                Log.i("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [SUPABASE_APPROVAL_VERIFICATION] deviceId=$deviceId targetStatus=$status returnedStatus=$returnedStatus match=${returnedStatus == status}")
                            }
                        } catch (e: Exception) {
                            Log.w("CloudClient", "Parsing patch representation failed", e)
                        }
                    }
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [SUPABASE_APPROVAL_RESPONSE] patchDeviceAuthorization failed with exception", e)
            Log.i("AUTH_MUTATION_RESULT", "[AUTH_MUTATION_RESULT]\noperation=PATCH_DEVICE_AUTH\nhttpCode=0\nsuccess=false")
            false
        }
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
        val authContext = requireAuthenticatedMutationContext(
            operation = "DELETE_DEVICE",
            companyId = companyId,
            deviceId = deviceId,
            requireActiveDevice = false
        )
        if (!authContext.allowed) {
            Log.w("MUTATION_BLOCKED", "[MUTATION_BLOCKED]\noperation=DELETE_DEVICE\nreason=${authContext.reason}")
            return@withContext false
        }

        val request = Request.Builder()
            .url("$baseUrl/connected_devices?device_id=eq.$deviceId")
            .delete()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val isSuccess = response.isSuccessful
                Log.i("AUTH_MUTATION_RESULT", "[AUTH_MUTATION_RESULT]\noperation=DELETE_DEVICE\nhttpCode=${response.code}\nsuccess=$isSuccess")
                isSuccess
            }
        } catch (e: Exception) {
            Log.i("AUTH_MUTATION_RESULT", "[AUTH_MUTATION_RESULT]\noperation=DELETE_DEVICE\nhttpCode=0\nsuccess=false")
            false
        }
    }

    // --- Record Sync ---
    suspend fun uploadRecordDetailed(companyId: String, record: CloudSyncRecord): UploadRecordResult = withContext(Dispatchers.IO) {
        val authContext = requireAuthenticatedMutationContext(
            operation = "UPLOAD_RECORD",
            companyId = companyId,
            deviceId = record.lastModifiedDeviceId,
            requireActiveDevice = true
        )
        if (!authContext.allowed) {
            Log.w("MUTATION_BLOCKED", "[MUTATION_BLOCKED]\noperation=UPLOAD_RECORD\nreason=${authContext.reason}")
            return@withContext UploadRecordResult.Blocked(authContext.reason, authContext.authUid, companyId)
        }

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
            .url("$baseUrl/cloud_records?on_conflict=id")
            .header("Prefer", "resolution=merge-duplicates")
            .post(json.toRequestBody(jsonMediaType))
            .build()
        val hasAuth = !workspaceManager.currentAuthToken.isNullOrBlank() && !workspaceManager.isTokenExpired(workspaceManager.currentAuthToken)
        val hasApiKey = !supabaseManager.supabaseAnonKey.isNullOrBlank()
        Log.e(
            "SYNC_DIAGNOSTIC",
            "REQUEST | method=${request.method} | url=${request.url} | workspace=$companyId | hasAuth=$hasAuth | hasApiKey=$hasApiKey"
        )
        try {
            client.newCall(request).execute().use { response ->
                val httpCode = response.code
                val body = response.body?.string() ?: ""
                Log.e(
                    "SYNC_DIAGNOSTIC",
                    "RESPONSE | code=$httpCode | body=$body"
                )
                val isSuccess = response.isSuccessful
                Log.i("AUTH_MUTATION_RESULT", "[AUTH_MUTATION_RESULT]\noperation=UPLOAD_RECORD\nhttpCode=$httpCode\nsuccess=$isSuccess")
                if (!isSuccess) {
                    Log.e("CLOUD_RECORD_FORENSIC", "[CLOUD_RECORD_FORENSIC] uploadRecord failed httpStatus=$httpCode body=$body")
                    UploadRecordResult.HttpError(httpCode, body)
                } else {
                    UploadRecordResult.Success(httpCode)
                }
            }
        } catch (e: Exception) {
            Log.e(
                "SYNC_DIAGNOSTIC",
                "REQUEST EXCEPTION | method=${request.method} | url=${request.url} | workspace=$companyId | exception=${e.javaClass.simpleName}: ${e.message}",
                e
            )
            Log.e("CLOUD_RECORD_FORENSIC", "[CLOUD_RECORD_FORENSIC] uploadRecord exception for record ${record.id}", e)
            Log.i("AUTH_MUTATION_RESULT", "[AUTH_MUTATION_RESULT]\noperation=UPLOAD_RECORD\nhttpCode=0\nsuccess=false")
            UploadRecordResult.NetworkError(e)
        }
    }

    suspend fun uploadRecord(companyId: String, record: CloudSyncRecord): Boolean = withContext(Dispatchers.IO) {
        uploadRecordDetailed(companyId, record) is UploadRecordResult.Success
    }

    suspend fun getCloudRecords(companyId: String): List<CloudSyncRecord> = withContext(Dispatchers.IO) {
        ensureAuthSession(companyId)
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
        val authContext = requireAuthenticatedMutationContext(
            operation = "DELETE_RECORD",
            companyId = companyId,
            requireActiveDevice = true
        )
        if (!authContext.allowed) {
            Log.w("MUTATION_BLOCKED", "[MUTATION_BLOCKED]\noperation=DELETE_RECORD\nreason=${authContext.reason}")
            return@withContext false
        }

        val id = "${entityType}_${entityId}"
        val request = Request.Builder()
            .url("$baseUrl/cloud_records?id=eq.$id")
            .delete()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val isSuccess = response.isSuccessful
                Log.i("AUTH_MUTATION_RESULT", "[AUTH_MUTATION_RESULT]\noperation=DELETE_RECORD\nhttpCode=${response.code}\nsuccess=$isSuccess")
                isSuccess
            }
        } catch (e: Exception) {
            Log.i("AUTH_MUTATION_RESULT", "[AUTH_MUTATION_RESULT]\noperation=DELETE_RECORD\nhttpCode=0\nsuccess=false")
            false
        }
    }
}
