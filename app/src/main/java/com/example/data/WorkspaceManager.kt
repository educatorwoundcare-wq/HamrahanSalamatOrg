package com.example.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

private val Context.workspaceDataStore by preferencesDataStore(name = "workspace_prefs")

class WorkspaceManager private constructor(private val context: Context) {

    private val TENANT_ID_KEY = stringPreferencesKey("tenant_id")
    private val SYNC_CODE_KEY = stringPreferencesKey("sync_code")
    private val AUTH_TOKEN_KEY = stringPreferencesKey("auth_token")
    private val AUTH_UID_KEY = stringPreferencesKey("auth_uid")
    private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")

    @Volatile var currentTenantId: String? = null
        private set
    @Volatile var currentSyncCode: String? = null
        private set
    @Volatile var currentAuthToken: String? = null
        private set
    @Volatile var currentAuthUid: String? = null
        private set
    @Volatile var currentRefreshToken: String? = null
        private set

    init {
        runBlocking {
            val prefs = context.workspaceDataStore.data.first()
            currentTenantId = WorkspaceSanitizer.getCanonicalCompanyId(prefs[TENANT_ID_KEY])
            currentSyncCode = WorkspaceSanitizer.getCanonicalSyncCode(prefs[SYNC_CODE_KEY])
            currentAuthToken = prefs[AUTH_TOKEN_KEY]
            currentAuthUid = prefs[AUTH_UID_KEY] ?: extractSubFromJwt(currentAuthToken)
            currentRefreshToken = prefs[REFRESH_TOKEN_KEY]
        }
    }

    suspend fun saveIdentity(tenantId: String, syncCode: String, authToken: String, authUid: String? = null, refreshToken: String? = null) {
        val resolvedUid = authUid ?: extractSubFromJwt(authToken)
        val effectiveTenantId = if (tenantId.isNotBlank()) tenantId else (currentTenantId ?: "")
        val effectiveSyncCode = if (syncCode.isNotBlank()) syncCode else (currentSyncCode ?: "")
        context.workspaceDataStore.edit { prefs ->
            if (effectiveTenantId.isNotBlank()) {
                prefs[TENANT_ID_KEY] = effectiveTenantId
            }
            if (effectiveSyncCode.isNotBlank()) {
                prefs[SYNC_CODE_KEY] = effectiveSyncCode
            }
            prefs[AUTH_TOKEN_KEY] = authToken
            if (refreshToken != null) prefs[REFRESH_TOKEN_KEY] = refreshToken
            if (!resolvedUid.isNullOrBlank()) {
                prefs[AUTH_UID_KEY] = resolvedUid
            } else {
                prefs.remove(AUTH_UID_KEY)
            }
        }
        currentTenantId = effectiveTenantId.takeIf { it.isNotBlank() }
        currentSyncCode = effectiveSyncCode.takeIf { it.isNotBlank() }
        currentAuthToken = authToken
        if (refreshToken != null) currentRefreshToken = refreshToken
        currentAuthUid = resolvedUid
    }

    suspend fun clearIdentity() {
        context.workspaceDataStore.edit { prefs ->
            prefs.clear()
        }
        currentTenantId = null
        currentSyncCode = null
        currentAuthToken = null
        currentAuthUid = null
    }

    suspend fun clearWorkspaceTenantOnly() {
        context.workspaceDataStore.edit { prefs ->
            prefs.remove(TENANT_ID_KEY)
            prefs.remove(SYNC_CODE_KEY)
        }
        currentTenantId = null
        currentSyncCode = null
    }

    suspend fun updateTenantAndSyncCode(tenantId: String, syncCode: String) {
        context.workspaceDataStore.edit { prefs ->
            prefs[TENANT_ID_KEY] = tenantId
            prefs[SYNC_CODE_KEY] = syncCode
        }
        currentTenantId = tenantId
        currentSyncCode = syncCode
    }

    fun getDeviceId(): String = DeviceIdentityProvider.getDeviceId(context)

    fun isTokenExpired(token: String?): Boolean {
        if (token.isNullOrBlank()) return true
        return try {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val payloadBytes = Base64.decode(
                    parts[1],
                    Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                )
                val payloadJson = String(payloadBytes, Charsets.UTF_8)
                val exp = JSONObject(payloadJson).optLong("exp")
                if (exp > 0) {
                    val expMs = exp * 1000
                    // Consider it expired if it expires in less than 5 minutes
                    System.currentTimeMillis() + (5 * 60 * 1000) > expMs
                } else false
            } else true
        } catch (e: Exception) {
            true
        }
    }

    fun extractSubFromJwt(token: String?): String? {
        if (token.isNullOrBlank()) return null
        return try {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val payloadBytes = Base64.decode(
                    parts[1],
                    Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                )
                val payloadJson = String(payloadBytes, Charsets.UTF_8)
                val sub = JSONObject(payloadJson).optString("sub")
                if (sub.isNotBlank()) sub else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun getTenantIdFlow(): Flow<String?> = context.workspaceDataStore.data.map { it[TENANT_ID_KEY] }

    companion object {
        @Volatile private var INSTANCE: WorkspaceManager? = null

        fun getInstance(context: Context): WorkspaceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WorkspaceManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
