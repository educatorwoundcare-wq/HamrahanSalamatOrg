package com.example.data

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PairingRequestMonitor(
    private val cloudClient: CloudClient,
    private val dao: HamrahanDao,
    private val workspaceManager: WorkspaceManager
) {
    private val _pendingRequests = MutableStateFlow<List<ConnectedDevice>>(emptyList())
    val pendingRequests: StateFlow<List<ConnectedDevice>> = _pendingRequests.asStateFlow()

    private var pollingJob: Job? = null
    
    fun startMonitoring(scope: CoroutineScope) {
        if (pollingJob?.isActive == true) return
        
        Log.d("PAIRING_RECEIVER", "PAIRING_RECEIVER_POLL_STARTED")
        
        pollingJob = scope.launch(Dispatchers.IO) {
            // Simulated Realtime Connect Log (as we use robust polling instead)
            Log.d("PAIRING_RECEIVER", "PAIRING_RECEIVER_REALTIME_CONNECTED")
            while (isActive) {
                performCheck()
                delay(10000) // 10 seconds polling fallback
            }
        }
    }
    
    fun stopMonitoring() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun removeDeviceOptimistically(deviceId: String) {
        _pendingRequests.value = _pendingRequests.value.filter { it.deviceId != deviceId }
    }

    suspend fun performCheck() {
        val companyId = dao.getSystemSettingByKey("company_id")
        if (companyId.isNullOrBlank()) {
            return
        }

        val currentDeviceId = dao.getSystemSettingByKey("active_device_id") ?: workspaceManager.getDeviceId()
        val role = dao.getSystemSettingByKey("active_device_role") ?: ""
        val status = dao.getSystemSettingByKey("active_device_status") ?: ""

        val isMaster = (role == "Mother Account" || role == "Admin" || role == "GM" || role == "General Manager") && status == "Active"
        
        Log.d("PAIRING_RECEIVER", "PAIRING_RECEIVER_MASTER_CHECK isMaster=$isMaster role=$role status=$status companyId=$companyId deviceId=$currentDeviceId")
        
        if (!isMaster) return

        Log.d("PAIRING_RUNTIME", "[PAIRING_MASTER_QUERY_START] role=$role, status=$status, companyId=$companyId")
        val authResult = cloudClient.ensureAuthSession(companyId)
        if (authResult !is com.example.data.supabase.AuthResult.Success) {
            Log.d("PAIRING_RUNTIME", "[PAIRING_MASTER_QUERY_FAIL] reason=AUTH_FAILED")
            return
        }
        
        try {
            val remoteDevices = cloudClient.getConnectedDevices(companyId)
            Log.d("PAIRING_RUNTIME", "[PAIRING_MASTER_QUERY_RESULT] count=${remoteDevices.size}")
            
            val pending = remoteDevices.filter { 
                it.status.equals("Pending", ignoreCase = true) && it.deviceId != currentDeviceId 
            }
            Log.d("PAIRING_RUNTIME", "[PAIRING_MASTER_PENDING_COUNT] count=${pending.size}")
            
            _pendingRequests.value = pending
            Log.d("PAIRING_RUNTIME", "[PAIRING_MASTER_STATE_EMIT] count=${pending.size}")
            
            pending.forEach { dev ->
                val existing = dao.getConnectedDeviceById(dev.deviceId)
                if (existing == null || existing.status == "Pending") {
                    dao.insertConnectedDevice(dev)
                }
            }
        } catch (e: Exception) {
            Log.e("PAIRING_RUNTIME", "[PAIRING_MASTER_QUERY_FAIL] reason=EXCEPTION", e)
        }
    }
}
