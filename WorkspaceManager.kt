package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.workspaceDataStore by preferencesDataStore(name = "workspace_prefs")

class WorkspaceManager private constructor(private val context: Context) {

    private val TENANT_ID_KEY = stringPreferencesKey("tenant_id")
    private val SYNC_CODE_KEY = stringPreferencesKey("sync_code")
    private val AUTH_TOKEN_KEY = stringPreferencesKey("auth_token")

    @Volatile var currentTenantId: String? = null
        private set
    @Volatile var currentSyncCode: String? = null
        private set
    @Volatile var currentAuthToken: String? = null
        private set

    init {
        runBlocking {
            val prefs = context.workspaceDataStore.data.first()
            currentTenantId = prefs[TENANT_ID_KEY]
            currentSyncCode = prefs[SYNC_CODE_KEY]
            currentAuthToken = prefs[AUTH_TOKEN_KEY]
        }
    }

    suspend fun saveIdentity(tenantId: String, syncCode: String, authToken: String) {
        context.workspaceDataStore.edit { prefs ->
            prefs[TENANT_ID_KEY] = tenantId
            prefs[SYNC_CODE_KEY] = syncCode
            prefs[AUTH_TOKEN_KEY] = authToken
        }
        currentTenantId = tenantId
        currentSyncCode = syncCode
        currentAuthToken = authToken
    }

    suspend fun clearIdentity() {
        context.workspaceDataStore.edit { prefs ->
            prefs.clear()
        }
        currentTenantId = null
        currentSyncCode = null
        currentAuthToken = null
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
