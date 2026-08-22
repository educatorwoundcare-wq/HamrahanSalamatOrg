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

sealed class WorkspaceSaveResult {
    object Success : WorkspaceSaveResult()
    data class OwnershipMismatch(
        val companyId: String,
        val currentAuthUid: String,
        val operation: String = "SAVE_WORKSPACE",
        val remoteCreatorUid: String? = null
    ) : WorkspaceSaveResult()
    data class Error(val code: Int, val message: String) : WorkspaceSaveResult()
    data class NetworkError(val exception: Exception) : WorkspaceSaveResult()
    object NoToken : WorkspaceSaveResult()
}

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

            val targetDevId = deviceId ?: dao?.getSystemSettingByKey("active_device_id")
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
                            companyId = companyId,
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
            return@withContext WorkspaceSaveResult.NoToken
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
                            WorkspaceSaveResult.Success
                        } else if (httpCode == 409 || body.contains("23505")) {
                            val freshRes = resolveWorkspace(companyId)
                            if (freshRes is WorkspaceResolution.ExistsAndOwned) {
                                Log.i("WORKSPACE_RESOLUTION", "[WORKSPACE_RESOLUTION] companyId=$companyId httpCode=$httpCode result=ExistsAndOwned operation=INSERT_CONFLICT_RECOVERED")
                                WorkspaceSaveResult.Success
                            } else if (freshRes is WorkspaceResolution.ExistsForeign) {
                                Log.w("WORKSPACE_RESOLUTION", "[WORKSPACE_RESOLUTION] companyId=$companyId httpCode=$httpCode result=ExistsForeign operation=INSERT_CONFLICT_OWNERSHIP_MISMATCH")
                                WorkspaceSaveResult.OwnershipMismatch(companyId, authUid, "INSERT", freshRes.remoteCreatorUid)
                            } else {
                                Log.w("WORKSPACE_RESOLUTION", "[WORKSPACE_RESOLUTION] companyId=$companyId httpCode=$httpCode result=Conflict operation=INSERT_CONFLICT")
                                WorkspaceSaveResult.OwnershipMismatch(companyId, authUid, "INSERT", null)
                            }
                        } else {
                            Log.e("WORKSPACE_RESOLUTION", "[WORKSPACE_RESOLUTION] companyId=$companyId httpCode=$httpCode result=Failed operation=INSERT body=$body")
                            WorkspaceSaveResult.Error(httpCode, body)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WORKSPACE_RESOLUTION", "[WORKSPACE_RESOLUTION] companyId=$companyId httpCode=0 result=Failed operation=INSERT", e)
                    WorkspaceSaveResult.NetworkError(e)
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
                            WorkspaceSaveResult.Success
                        } else {
                            Log.e("WORKSPACE_RESOLUTION", "[WORKSPACE_RESOLUTION] companyId=$companyId httpCode=$httpCode result=Failed operation=UPDATE body=$body")
                            WorkspaceSaveResult.Error(httpCode, body)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WORKSPACE_RESOLUTION", "[WORKSPACE_RESOLUTION] companyId=$companyId httpCode=0 result=Failed operation=UPDATE", e)
                    WorkspaceSaveResult.NetworkError(e)
                }
            }
            is WorkspaceResolution.ExistsForeign -> {
                Log.w("WORKSPACE_RESOLUTION", "[WORKSPACE_RESOLUTION] companyId=$companyId httpCode=403 result=ExistsForeign operation=REJECT_OWNERSHIP_MISMATCH")
                WorkspaceSaveResult.OwnershipMismatch(companyId, authUid, "SAVE_WORKSPACE", resolution.remoteCreatorUid)
            }
            is WorkspaceResolution.Unauthorized -> {
                WorkspaceSaveResult.Error(resolution.code, resolution.message)
            }
            is WorkspaceResolution.Failed -> {
                WorkspaceSaveResult.NetworkError(resolution.exception)
            }
        }
    }

    suspend fun saveWorkspaceInfo(companyId: String, info: WorkspaceInfo): Boolean = withContext(Dispatchers.IO) {
        saveWorkspaceInfoDetailed(companyId, info) is WorkspaceSaveResult.Success
    }

    suspend fun getWorkspaceBySyncCode(syncCode: String): WorkspaceInfo? = withContext(Dispatchers.IO) {
        val jsonPayload = JSONObject().apply { put("p_sync_code", syncCode) }.toString()
        val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl/rpc/find_workspace_by_sync_code")
            .post(requestBody)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                Log.i("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [GET_WORKSPACE_BY_SYNC_CODE] syncCode=$syncCode httpStatus=${response.code} responseBody=$bodyString")
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
                            createdTimestamp = json.optLong("created_timestamp", 0L)
                        )
                        Log.i("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [WORKSPACE_FOUND] syncCode=$syncCode canonicalCompanyId=${info.companyId} centerName=${info.centerName}")
                        return@use info
                    } else {
                        Log.w("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [WORKSPACE_NOT_FOUND] syncCode=$syncCode empty array returned")
                    }
                } else {
                    Log.e("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [WORKSPACE_LOOKUP_ERROR] syncCode=$syncCode httpStatus=${response.code} responseBody=$bodyString")
                }
            }
        } catch (e: Exception) { Log.e("CloudClient", "Error fetching workspace by sync code $syncCode", e) }
        null
    }

    suspend fun ensureAuthSession(targetCompanyId: String? = null, targetSyncCode: String? = null) {
        val currentToken = workspaceManager.currentAuthToken
        val isExpired = workspaceManager.isTokenExpired(currentToken)
        if (currentToken.isNullOrBlank() || isExpired) {
            val companyId = targetCompanyId ?: dao?.getSystemSettingByKey("company_id") ?: ""
            val syncCode = targetSyncCode ?: workspaceManager.currentSyncCode ?: dao?.getSystemSettingByKey("company_sync_code") ?: ""
            if (companyId.isNotBlank() && syncCode.isNotBlank()) {
                Log.i("AUTH_TRACE", "Token missing or expired. Authenticating anonymously for companyId=$companyId")
                val authRepo = com.example.data.supabase.SupabaseAuthRepository(supabaseManager, workspaceManager)
                authRepo.signInAnonymously(companyId, syncCode)
            }
        }
        val authUid = workspaceManager.currentAuthUid ?: workspaceManager.extractSubFromJwt(workspaceManager.currentAuthToken)
        val localCompany = targetCompanyId ?: workspaceManager.currentTenantId ?: dao?.getSystemSettingByKey("company_id")
        val localSync = targetSyncCode ?: workspaceManager.currentSyncCode ?: dao?.getSystemSettingByKey("company_sync_code")
        Log.i("IDENTITY_RECOVERY", "[IDENTITY_RECOVERY] authUid=$authUid localCompanyId=$localCompany localSyncCode=$localSync remoteCreatorUid=N/A decision=ENSURE_AUTH_SESSION")
    }

    private var lastAuthUid: String? = null
    private var cachedBootstrapResult: BootstrapResult? = null

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
                Log.w("DEVICE_WRITE", "[DEVICE_WRITE]\nauthUid=$authUid\ncompanyId=$companyId\ndeviceId=$deviceId\nresolution=Unauthorized\noperation=SKIP\nreason=${authContext.reason}")
                return@withLock DeviceResolution.Unauthorized(401, authContext.reason)
            }

            val currentAuthUid = authContext.authUid ?: authUid

            // Step A: Resolve first
            val resolution = resolveDevice(companyId, deviceId)

            when (resolution) {
                is DeviceResolution.ExistsActive -> {
                    val existing = resolution.device
                    // B) If ExistsActive: verify ownership/company consistency
                    if (existing.companyId == companyId && (existing.uid.isBlank() || existing.uid == currentAuthUid)) {
                        Log.i("DEVICE_WRITE", "[DEVICE_WRITE]\nauthUid=$currentAuthUid\ncompanyId=$companyId\ndeviceId=$deviceId\nresolution=ExistsActive\noperation=UPDATE\nreason=REUSE_EXISTING_ACTIVE_DEVICE")
                        // update permitted heartbeat metadata only
                        val updateJson = JSONObject().apply {
                            put("device_name", deviceName)
                            put("device_type", deviceType)
                            put("app_version", "v2.0.0")
                            put("last_online_time", System.currentTimeMillis())
                            put("last_seen", System.currentTimeMillis())
                        }.toString()

                        val updateRequest = Request.Builder()
                            .url("$baseUrl/connected_devices?device_id=eq.$deviceId")
                            .patch(updateJson.toRequestBody(jsonMediaType))
                            .build()

                        try {
                            client.newCall(updateRequest).execute().use { response ->
                                Log.i("AUTH_MUTATION_RESULT", "[AUTH_MUTATION_RESULT]\noperation=ENSURE_DEVICE_PATCH\nhttpCode=${response.code}\nsuccess=${response.isSuccessful}")
                            }
                        } catch (e: Exception) {
                            Log.w("CloudClient", "Heartbeat patch failed for active device $deviceId", e)
                            Log.i("AUTH_MUTATION_RESULT", "[AUTH_MUTATION_RESULT]\noperation=ENSURE_DEVICE_PATCH\nhttpCode=0\nsuccess=false")
                        }
                        DeviceResolution.ExistsActive(existing)
                    } else {
                        Log.w("DEVICE_WRITE", "[DEVICE_WRITE]\nauthUid=$currentAuthUid\ncompanyId=$companyId\ndeviceId=$deviceId\nresolution=ExistsActive\noperation=SKIP\nreason=OWNERSHIP_OR_COMPANY_MISMATCH")
                        DeviceResolution.Unauthorized(403, "Device belongs to different identity or company")
                    }
                }
                is DeviceResolution.ExistsPending -> {
                    // C) If ExistsPending: preserve Pending state, DO NOT INSERT, DO NOT silently self-promote to Active
                    Log.i("DEVICE_WRITE", "[DEVICE_WRITE]\nauthUid=$currentAuthUid\ncompanyId=$companyId\ndeviceId=$deviceId\nresolution=ExistsPending\noperation=SKIP\nreason=PRESERVE_PENDING_STATE")
                    DeviceResolution.ExistsPending(resolution.device)
                }
                is DeviceResolution.ExistsOther -> {
                    // D) If ExistsOther: stop, return explicit resolution, never overwrite foreign identity
                    Log.w("DEVICE_WRITE", "[DEVICE_WRITE]\nauthUid=$currentAuthUid\ncompanyId=$companyId\ndeviceId=$deviceId\nresolution=ExistsOther\noperation=SKIP\nreason=STATUS_${resolution.device.status}")
                    resolution
                }
                is DeviceResolution.NotFound -> {
                    // E) If NotFound: perform the initial INSERT with on_conflict=device_id & merge-duplicates header
                    Log.i("DEVICE_WRITE", "[DEVICE_WRITE]\nauthUid=$currentAuthUid\ncompanyId=$companyId\ndeviceId=$deviceId\nresolution=NotFound\noperation=INSERT\nreason=NEW_DEVICE_REGISTRATION")
                    val insertJson = JSONObject().apply {
                        put("device_id", deviceId)
                        put("company_id", companyId)
                        put("device_name", deviceName)
                        put("device_type", deviceType)
                        put("app_version", "v2.0.0")
                        put("last_online_time", System.currentTimeMillis())
                        put("last_successful_sync", 0L)
                        put("status", initialStatus)
                        put("uid", currentAuthUid)
                        put("role", deviceRole)
                        put("last_seen", System.currentTimeMillis())
                        put("requested_role", requestedRole)
                    }.toString()

                    val insertRequest = Request.Builder()
                        .url("$baseUrl/connected_devices?on_conflict=device_id")
                        .header("Prefer", "resolution=merge-duplicates")
                        .post(insertJson.toRequestBody(jsonMediaType))
                        .build()

                    try {
                        client.newCall(insertRequest).execute().use { response ->
                            val httpCode = response.code
                            Log.i("AUTH_MUTATION_RESULT", "[AUTH_MUTATION_RESULT]\noperation=ENSURE_DEVICE_INSERT\nhttpCode=$httpCode\nsuccess=${response.isSuccessful}")
                            Log.i("DEVICE_WRITE", "[DEVICE_WRITE]\nauthUid=$currentAuthUid\ncompanyId=$companyId\ndeviceId=$deviceId\nresolution=NotFound\noperation=INSERT_HTTP_$httpCode\nreason=INSERT_RESPONSE")
                        }
                    } catch (e: Exception) {
                        Log.e("DEVICE_WRITE", "[DEVICE_WRITE]\nauthUid=$currentAuthUid\ncompanyId=$companyId\ndeviceId=$deviceId\nresolution=NotFound\noperation=INSERT_ERROR\nreason=${e.message}", e)
                        Log.i("AUTH_MUTATION_RESULT", "[AUTH_MUTATION_RESULT]\noperation=ENSURE_DEVICE_INSERT\nhttpCode=0\nsuccess=false")
                    }

                    // Immediately re-resolve
                    val freshRes = resolveDevice(companyId, deviceId)
                    Log.i("DEVICE_WRITE", "[DEVICE_WRITE]\nauthUid=$currentAuthUid\ncompanyId=$companyId\ndeviceId=$deviceId\nresolution=${freshRes.javaClass.simpleName}\noperation=RE_RESOLVE\nreason=POST_INSERT_CONFIRMATION")
                    freshRes
                }
                is DeviceResolution.Unauthorized -> {
                    // F) If Unauthorized: stop bootstrap
                    Log.w("DEVICE_WRITE", "[DEVICE_WRITE]\nauthUid=$currentAuthUid\ncompanyId=$companyId\ndeviceId=$deviceId\nresolution=Unauthorized\noperation=SKIP\nreason=UNAUTHORIZED_CODE_${resolution.code}")
                    resolution
                }
                is DeviceResolution.Failed -> {
                    // G) If Failed: stop bootstrap
                    Log.e("DEVICE_WRITE", "[DEVICE_WRITE]\nauthUid=$currentAuthUid\ncompanyId=$companyId\ndeviceId=$deviceId\nresolution=Failed\noperation=SKIP\nreason=LOOKUP_FAILURE", resolution.exception)
                    resolution
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

        val targetDeviceId = deviceId
            ?: dao?.getSystemSettingByKey("active_device_id")

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
        val workspaceConfirmed = wsRes is WorkspaceResolution.ExistsAndOwned
        if (!workspaceConfirmed) {
            val reason = when (wsRes) {
                is WorkspaceResolution.ExistsForeign -> "Workspace owned by another identity (${wsRes.remoteCreatorUid})"
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

            val devId = forcedDeviceId
                ?: dao?.getSystemSettingByKey("active_device_id")
                ?: "DEV-" + java.util.UUID.randomUUID().toString().replace("-", "").take(8).uppercase()

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
    suspend fun registerDevice(companyId: String, device: ConnectedDevice): Boolean = withContext(Dispatchers.IO) {
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
        res is DeviceResolution.ExistsActive || res is DeviceResolution.ExistsPending
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

        val json = JSONObject().apply {
            put("last_online_time", System.currentTimeMillis())
            put("last_successful_sync", device.lastSuccessfulSync)
            put("last_seen", System.currentTimeMillis())
        }.toString()

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

        val json = JSONObject().apply {
            put("status", status)
            put("role", role)
        }.toString()

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
    suspend fun uploadRecord(companyId: String, record: CloudSyncRecord): Boolean = withContext(Dispatchers.IO) {
        val authContext = requireAuthenticatedMutationContext(
            operation = "UPLOAD_RECORD",
            companyId = companyId,
            deviceId = record.lastModifiedDeviceId,
            requireActiveDevice = true
        )
        if (!authContext.allowed) {
            Log.w("MUTATION_BLOCKED", "[MUTATION_BLOCKED]\noperation=UPLOAD_RECORD\nreason=${authContext.reason}")
            return@withContext false
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
        try {
            client.newCall(request).execute().use { response ->
                val isSuccess = response.isSuccessful
                Log.i("AUTH_MUTATION_RESULT", "[AUTH_MUTATION_RESULT]\noperation=UPLOAD_RECORD\nhttpCode=${response.code}\nsuccess=$isSuccess")
                if (!isSuccess) {
                    val body = response.body?.string() ?: ""
                    Log.e("CLOUD_RECORD_FORENSIC", "[CLOUD_RECORD_FORENSIC] uploadRecord failed httpStatus=${response.code} body=$body")
                }
                isSuccess
            }
        } catch (e: Exception) {
            Log.e("CLOUD_RECORD_FORENSIC", "[CLOUD_RECORD_FORENSIC] uploadRecord exception for record ${record.id}", e)
            Log.i("AUTH_MUTATION_RESULT", "[AUTH_MUTATION_RESULT]\noperation=UPLOAD_RECORD\nhttpCode=0\nsuccess=false")
            false
        }
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
