package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.data.AuditLog
import com.example.data.ConnectedDevice
import com.example.data.HamrahanDao
import com.example.data.SyncQueue
import com.example.data.WorkspaceManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SyncManagementUiState(
    val syncStatus: WorkInfo.State? = null,
    val whitelistedDevices: List<ConnectedDevice> = emptyList(),
    val blacklistedDevices: List<ConnectedDevice> = emptyList(),
    val auditLogs: List<AuditLog> = emptyList(),
    val syncQueueEvents: List<SyncQueue> = emptyList(),
    val isLoading: Boolean = false,
    val tenantId: String? = null
)

class SyncManagementViewModel(
    private val dao: HamrahanDao,
    private val workspaceManager: WorkspaceManager,
    private val workManager: WorkManager
) : ViewModel() {

    // Note: The prompt requested @HiltViewModel, but since Hilt is not set up in the current project,
    // we use manual dependency injection via ViewModelProvider.Factory to ensure the project compiles.

    private val _workInfosFlow = workManager.getWorkInfosForUniqueWorkFlow("HamrahanBackgroundSync")
    
    val uiState: StateFlow<SyncManagementUiState> = combine(
        dao.getAllConnectedDevices(),
        dao.getAllAuditLogs(),
        dao.getAllSyncQueues(),
        _workInfosFlow,
        workspaceManager.getTenantIdFlow()
    ) { devices, auditLogs, syncQueues, workInfos, tenantId ->
        
        val whitelisted = devices.filter { it.status == "Active" }
        val blacklisted = devices.filter { it.status == "Revoked" || it.status == "Suspended" }
        
        val currentWorkInfoState = workInfos.firstOrNull()?.state

        SyncManagementUiState(
            syncStatus = currentWorkInfoState,
            whitelistedDevices = whitelisted,
            blacklistedDevices = blacklisted,
            auditLogs = auditLogs,
            syncQueueEvents = syncQueues,
            isLoading = false,
            tenantId = tenantId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SyncManagementUiState(isLoading = true)
    )

    fun toggleDeviceStatus(device: ConnectedDevice) {
        viewModelScope.launch {
            val newStatus = if (device.status == "Active") "Revoked" else "Active"
            dao.insertConnectedDevice(device.copy(status = newStatus))
        }
    }

    fun forceSync() {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()
        val syncRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.data.SyncWorker>()
            .setConstraints(constraints)
            .build()
        workManager.enqueueUniqueWork("ManualSync", androidx.work.ExistingWorkPolicy.REPLACE, syncRequest)
    }

    companion object {
        fun provideFactory(
            dao: HamrahanDao,
            workspaceManager: WorkspaceManager,
            workManager: WorkManager
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(SyncManagementViewModel::class.java)) {
                    return SyncManagementViewModel(dao, workspaceManager, workManager) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }
}
