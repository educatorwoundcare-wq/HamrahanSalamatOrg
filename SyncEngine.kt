package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SyncEngine @JvmOverloads constructor(
    private val context: Context,
    private val dao: HamrahanDao,
    private val cloudClient: CloudClient = CloudClient(dao, context)
) {

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Long>(0L)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    private val _pendingChangesCount = MutableStateFlow(0)
    val pendingChangesCount: StateFlow<Int> = _pendingChangesCount.asStateFlow()

    private val _failedSyncCount = MutableStateFlow(0)
    val failedSyncCount: StateFlow<Int> = _failedSyncCount.asStateFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val syncMutex = Mutex()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var currentRetryDelay = 5000L
    private val minRetryDelay = 5000L
    private val maxRetryDelay = 600000L // 10 minutes max backoff

    init {
        // Periodically update the pending count and failed count
        coroutineScope.launch {
            dao.getAllSyncMetadata().collect { list ->
                _pendingChangesCount.value = list.count { it.syncStatus == "Pending" || it.syncStatus == "Failed" }
                _failedSyncCount.value = list.count { it.syncStatus == "Failed" }
            }
        }

        // Realtime change listener: Periodically trigger sync using Exponential Backoff
        coroutineScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(currentRetryDelay)
                if (_isOnline.value && !_syncing.value && isActive) {
                    val companyId = dao.getSystemSettingByKey("company_id")
                    if (companyId.isNullOrEmpty()) {
                        Log.i("SyncEngine", "[Lifecycle] Detected cleared companyId during periodic check. Auto-shutting down to prevent leaks.")
                        shutdown()
                        return@launch
                    }
                    try {
                        sync()
                    } catch (e: Exception) {
                        Log.e("SyncEngine", "Periodic real-time background sync error", e)
                    }
                }
            }
        }

        // Automatic connectivity monitoring
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            val isCurrentlyConnected = if (activeNetwork != null) {
                capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: true
            } else {
                @Suppress("DEPRECATION")
                connectivityManager.activeNetworkInfo?.isConnected ?: true
            }
            _isOnline.value = isCurrentlyConnected

            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    Log.i("SyncEngine", "[NetworkCallback] Internet connectivity is RESTORED!")
                    setOnline(true)
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    Log.i("SyncEngine", "[NetworkCallback] Internet connectivity is LOST!")
                    setOnline(false)
                }
            }
            networkCallback = callback
            connectivityManager.registerNetworkCallback(networkRequest, callback)
        } catch (e: Exception) {
            Log.e("SyncEngine", "Error registering network callback", e)
        }
    }

    fun setOnline(online: Boolean) {
        _isOnline.value = online
        if (online) {
            currentRetryDelay = minRetryDelay // Reset backoff immediately on connection restore
            triggerSync()
        }
    }

    fun triggerSync() {
        if (!_isOnline.value || _syncing.value) return
        coroutineScope.launch {
            sync()
        }
    }

    fun shutdown() {
        Log.i("SyncEngine", "[Shutdown] Cancelling SyncEngine's CoroutineScope and unregistering networkCallback to prevent leaks.")
        try {
            networkCallback?.let { callback ->
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                connectivityManager?.unregisterNetworkCallback(callback)
            }
            networkCallback = null
        } catch (e: Exception) {
            Log.e("SyncEngine", "Error unregistering network callback", e)
        }
        try {
            coroutineScope.cancel()
        } catch (e: Exception) {
            Log.e("SyncEngine", "Error cancelling coroutineScope", e)
        }
    }

    // Main Sync loop
    private suspend fun sync() = withContext(Dispatchers.IO) {
        if (!syncMutex.tryLock()) {
            Log.d("SyncEngine", "Sync operation already in progress, skipping concurrent acquisition.")
            return@withContext
        }
        try {
            _syncing.value = true
            val companyId = dao.getSystemSettingByKey("company_id")
            if (companyId.isNullOrEmpty()) {
                Log.w("SyncEngine", "[Sync Cycle] Sync aborted because company_id is missing. Workspace not configured.")
                return@withContext
            }

            // 1. Fetch valid ID token if available (do not abort sync if unavailable, fallback to unauthenticated REST calls)
            val idToken = try {
                cloudClient.getValidIdToken()
            } catch (e: Exception) {
                Log.w("SyncEngine", "[Sync Cycle] Token fetch failed or unavailable, proceeding with standard REST sync: ${e.message}")
                null
            }

            var activeDeviceId = dao.getSystemSettingByKey("active_device_id")
            if (activeDeviceId.isNullOrEmpty()) {
                activeDeviceId = "DEV-" + java.util.UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
                dao.insertSystemSetting(SystemSetting("active_device_id", activeDeviceId))
            }

            val activeDeviceName = dao.getSystemSettingByKey("active_device_name") ?: "تلفن همراه"
            var localRole = dao.getSystemSettingByKey("active_device_role") ?: "General Manager"
            var localStatus = dao.getSystemSettingByKey("active_device_status") ?: "Active"
            val isDeviceApprovedLocally = dao.getSystemSettingByKey("device_has_been_approved") == "true"

            if (localRole == "Mother Account" || isDeviceApprovedLocally) {
                localStatus = "Active"
                dao.insertSystemSetting(SystemSetting("active_device_status", "Active"))
                dao.insertSystemSetting(SystemSetting("device_has_been_approved", "true"))
            }

            Log.i("SyncEngine", "[Sync Cycle] Starting sync cycle. companyId used: $companyId, activeDeviceId: $activeDeviceId")

            // --- 1. REGISTRATION SYNC PIPELINE ---
            // Fetch current device's authoritative state from Firebase first using ID Token
            val cloudSelf = try {
                cloudClient.getSingleDevice(companyId, activeDeviceId)
            } catch (e: Exception) {
                Log.e("SyncEngine", "[Registration Sync] Failed to poll device status from Firebase", e)
                null
            }

            var activeDeviceRole = localRole
            var activeDeviceStatus = localStatus

            if (cloudSelf != null) {
                activeDeviceRole = cloudSelf.role
                activeDeviceStatus = cloudSelf.status
                if (cloudSelf.status == "Active") {
                    dao.insertSystemSetting(SystemSetting("active_device_status", "Active"))
                    dao.insertSystemSetting(SystemSetting("device_has_been_approved", "true"))
                }
            } else {
                // Cloud fetch failed or device not on cloud yet.
                // Maintain previous local authorization state to prevent false downgrades!
                if (isDeviceApprovedLocally || localStatus == "Active") {
                    activeDeviceStatus = "Active"
                }
            }

            // Explicit Self-Approval Override for Mother Account or locally approved device
            if (localRole == "Mother Account" || cloudSelf?.role == "Mother Account" || cloudSelf?.status == "Active" || isDeviceApprovedLocally) {
                activeDeviceStatus = "Active"
            }

            // Always sync and persist authoritative role and status locally
            dao.insertSystemSetting(SystemSetting("active_device_role", activeDeviceRole))
            dao.insertSystemSetting(SystemSetting("active_device_status", activeDeviceStatus))
            if (activeDeviceStatus == "Active") {
                dao.insertSystemSetting(SystemSetting("device_has_been_approved", "true"))
            }

            val existingLocalDev = dao.getConnectedDeviceById(activeDeviceId)
            val sendRole = if (activeDeviceStatus == "Pending") "Nurse" else activeDeviceRole
            // Heartbeat metadata representation for cloud update
            val currentDevice = ConnectedDevice(
                deviceId = activeDeviceId,
                deviceName = activeDeviceName,
                deviceType = if (activeDeviceName.contains("تبلت")) "Tablet" else "Phone",
                appVersion = "v2.0.0",
                lastOnlineTime = System.currentTimeMillis(),
                lastSuccessfulSync = existingLocalDev?.lastSuccessfulSync ?: 0L,
                status = activeDeviceStatus,
                uid = activeDeviceId,
                role = sendRole,
                lastSeen = System.currentTimeMillis(),
                companyId = companyId,
                requestedRole = existingLocalDev?.requestedRole ?: activeDeviceRole
            )

            // Persist locally
            dao.insertConnectedDevice(currentDevice)

            val updateType = if (cloudSelf == null && localStatus == "Pending" && !isDeviceApprovedLocally) "FULL_REGISTER" else "HEARTBEAT_ONLY"
            Log.i("SyncEngine", "[DEVICE SYNC]\nid=$activeDeviceId\nlocal=$localStatus\nremote=${cloudSelf?.status ?: "UNKNOWN"}\naction=$updateType")

            // Registration/heartbeat upload to the cloud
            try {
                if (updateType == "FULL_REGISTER") {
                    Log.i("SyncEngine", "[UPLOAD]\n\nFirebase path=companies/$companyId/devices/$activeDeviceId (Initial Register)")
                    val success = cloudClient.registerDevice(companyId, currentDevice)
                    if (success) {
                        Log.i("SyncEngine", "[Registration Sync] Successfully registered device in cloud.")
                    } else {
                        Log.w("SyncEngine", "[Registration Sync] Failed to register device in cloud.")
                    }
                } else {
                    Log.i("SyncEngine", "[UPLOAD]\n\nFirebase path=companies/$companyId/devices/$activeDeviceId (Heartbeat PATCH)")
                    val success = cloudClient.patchDeviceHeartbeat(companyId, currentDevice)
                    if (success) {
                        Log.i("SyncEngine", "[Registration Sync] Successfully updated registration heartbeat in cloud.")
                    } else {
                        Log.w("SyncEngine", "[Registration Sync] Failed to heartbeat in cloud.")
                    }
                }
            } catch (e: Exception) {
                Log.e("SyncEngine", "[Registration Sync] Error sending registration heartbeat", e)
            }

            // Administrative Registration Sync: approved administrators poll other device metadata/join requests
            if (activeDeviceStatus == "Active" && (activeDeviceRole == "Mother Account" || activeDeviceRole == "Admin" || activeDeviceRole == "GM" || activeDeviceRole == "General Manager")) {
                try {
                    val cloudDevices = cloudClient.getConnectedDevices(companyId)
                    for (dev in cloudDevices) {
                        dao.insertConnectedDevice(dev)
                        if (dev.status == "Pending") {
                            val existingAlerts = dao.getAlertsList()
                            val hasAlert = existingAlerts.any { it.entityId == dev.deviceId && it.status == "PENDING" }
                            if (!hasAlert) {
                                dao.insertAlert(
                                    Alert(
                                        title = "درخواست اتصال دستگاه جدید",
                                        description = "دستگاه «${dev.deviceName}» با نقش «${dev.requestedRole}» درخواست اتصال به این مرکز را دارد.",
                                        type = "pending_approvals",
                                        relatedScreen = "CompanyProfile",
                                        entityId = dev.deviceId,
                                        alertType = "device_approval",
                                        status = "PENDING"
                                    )
                                )
                            }
                        }
                    }
                    val pendingRequestsCount = cloudDevices.count { it.status == "Pending" }
                    Log.i("SyncEngine", "[DOWNLOAD]\n\nPending requests found=$pendingRequestsCount")
                    Log.i("SyncEngine", "[ADMIN]\n\nRequests received=${cloudDevices.size}")
                    Log.i("SyncEngine", "[Registration Sync] Successfully pulled connected devices list. Total count: ${cloudDevices.size}, Pending: $pendingRequestsCount")
                } catch (e: Exception) {
                    Log.e("SyncEngine", "[Registration Sync] Error fetching connected devices list", e)
                }
            }

            // --- 2. BUSINESS DATA SYNC PIPELINE ---
            // Business sync is blocked entirely unless cloud confirms that the device status is Active
            if (activeDeviceStatus != "Active") {
                Log.w("SyncEngine", "[Sync Cycle] Business sync pipeline is BLOCKED. Device status is $activeDeviceStatus")
                return@withContext
            }

            Log.i("SyncEngine", "[Business Sync] Device is Active. Running business synchronization pipeline.")

            // --- APPROVAL FULL SNAPSHOT PIPELINE ---
            val wasPending = localStatus == "Pending" || dao.getSystemSettingByKey("full_snapshot_needed_$companyId") == "true"
            if (wasPending) {
                Log.i("SyncEngine", "[Approval Full Snapshot] Device transitioned from Pending to Active! Initiating full snapshot synchronization for company: $companyId")
                dao.insertSystemSetting(SystemSetting("full_snapshot_needed_$companyId", "false"))
                try {
                    val snapshotRecords = cloudClient.getCloudRecords(companyId)
                    Log.i("SyncEngine", "[Approval Full Snapshot] Downloaded ${snapshotRecords.size} records. Merging into local Room DB...")
                    for (cloudRec in snapshotRecords) {
                        dao.insertCloudSyncRecord(cloudRec)
                        applyCloudRecordToLocal(cloudRec)
                    }
                    Log.i("SyncEngine", "[Approval Full Snapshot] Full snapshot merge completed successfully for $companyId.")
                } catch (e: Exception) {
                    Log.e("SyncEngine", "[Approval Full Snapshot] Error fetching or merging full snapshot", e)
                }
            }

            // Startup Self-Healing Logic: Verify Remote Consistency of Workspace Info Node (Only allowed for active Admin/Mother Account)
            if (activeDeviceRole == "Mother Account" || activeDeviceRole == "Admin" || activeDeviceRole == "GM") {
                try {
                    val cloudInfo = cloudClient.getWorkspaceInfo(companyId)
                    val isCompanySetup = dao.getSystemSettingByKey("company_is_setup")?.toBoolean() ?: false
                    
                    if (cloudInfo == null && isCompanySetup) {
                        Log.w("SyncEngine", "[Self-Healing] Detected missing /info node on Firebase for an active workspace! Starting healing...")
                        
                        val localCode = dao.getSystemSettingByKey("company_sync_code") ?: "HAMRAHAN-HEAL-RECOVERY"
                        val localName = dao.getSystemSettingByKey("center_name") ?: "نرم افزار مدیریت دفاتر خدمات پرستاری"
                        val localNationalCode = dao.getSystemSettingByKey("national_code") ?: "۱۰۳۲۰۰۰۰۰۰۰"
                        val localPhone = dao.getSystemSettingByKey("support_phone") ?: "۰۲۱-۸۸۸۸۸۸۸۸"
                        val localAddress = dao.getSystemSettingByKey("center_address") ?: "دفتر مرکزی"
                        
                        val recoveredInfo = WorkspaceInfo(
                            companyId = companyId,
                            companySyncCode = localCode,
                            centerName = localName,
                            nationalCode = localNationalCode,
                            supportPhone = localPhone,
                            centerAddress = localAddress,
                            createdTimestamp = System.currentTimeMillis()
                        )
                        val infoHealSuccess = cloudClient.saveWorkspaceInfo(companyId, recoveredInfo)
                        if (infoHealSuccess) {
                            Log.i("SyncEngine", "[Self-Healing] Workspace info node healed successfully in cloud!")
                        } else {
                            Log.e("SyncEngine", "[Self-Healing] Failed to heal workspace info node in cloud")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SyncEngine", "[Self-Healing] Error verifying workspace info node consistency", e)
                }
            }

            // 2. Upload pending local changes to the Real Cloud Database
            val pendingMetadata = dao.getPendingSyncMetadata()
            if (pendingMetadata.isNotEmpty()) {
                Log.i("SyncEngine", "[Sync Cycle] Found ${pendingMetadata.size} pending local changes to upload for company $companyId")
            }
            var consecutiveFailures = 0
            for (meta in pendingMetadata) {
                if (consecutiveFailures >= 3) {
                    Log.w("SyncEngine", "[Upload] Fast-breaking remaining ${pendingMetadata.size} upload items due to consecutive failures (network or permission issue).")
                    currentRetryDelay = (currentRetryDelay * 1.5).toLong().coerceAtMost(maxRetryDelay)
                    break
                }
                try {
                    // Fetch local record
                    val localDataJson = fetchLocalRecordJson(meta.entityType, meta.entityId)
                    if (localDataJson != null || meta.deletedStatus) {
                        val cloudRecord = CloudSyncRecord(
                            id = "${meta.entityType}_${meta.entityId}",
                            entityType = meta.entityType,
                            entityId = meta.entityId,
                            dataJson = localDataJson ?: "",
                            updatedTimestamp = meta.updatedTimestamp,
                            lastModifiedDeviceId = activeDeviceId,
                            isDeleted = meta.deletedStatus
                        )
                        // Real cloud upload
                        Log.i("SyncEngine", "[Upload] Uploading record ${cloudRecord.id} of type ${cloudRecord.entityType} to company $companyId")
                        val success = cloudClient.uploadRecord(companyId, cloudRecord)
                        if (success) {
                            Log.i("SyncEngine", "[Upload] Successfully uploaded record ${cloudRecord.id}")
                            consecutiveFailures = 0
                            // Mark local metadata as Synced
                            dao.insertSyncMetadata(meta.copy(syncStatus = "Synced"))
                            // Also update local cache for representation
                            dao.insertCloudSyncRecord(cloudRecord)
                        } else {
                            Log.e("SyncEngine", "[Upload] Failed to upload record ${cloudRecord.id} (Security Rule failure, revoked device, or network)")
                            consecutiveFailures++
                            dao.insertSyncMetadata(meta.copy(syncStatus = "Failed"))
                        }
                    } else {
                        // Orphan metadata or already deleted
                        dao.deleteSyncMetadata(meta.entityType, meta.entityId)
                    }
                } catch (e: Exception) {
                    Log.e("SyncEngine", "[Upload] Failed to upload local record: ${meta.entityType}_${meta.entityId}", e)
                    consecutiveFailures++
                    dao.insertSyncMetadata(meta.copy(syncStatus = "Failed"))
                }
            }

            // 3. Pull remote changes from the Real Cloud Database
            Log.i("SyncEngine", "[Download] Fetching cloud records for company $companyId")
            val cloudRecords = cloudClient.getCloudRecords(companyId)
            Log.i("SyncEngine", "[Download] Fetched ${cloudRecords.size} cloud records from company $companyId")
            for (cloudRec in cloudRecords) {
                // Save/update our local cache of cloud records
                dao.insertCloudSyncRecord(cloudRec)

                if (cloudRec.lastModifiedDeviceId == activeDeviceId) continue // Skip our own uploads

                val localMeta = dao.getSyncMetadata(cloudRec.entityType, cloudRec.entityId)
                
                if (localMeta == null) {
                    // Record does not exist locally: insert it!
                    applyCloudRecordToLocal(cloudRec)
                } else {
                    // Record exists locally: check for conflicts using LWW
                    if (cloudRec.updatedTimestamp > localMeta.updatedTimestamp) {
                        // Cloud version is newer, override local
                        
                        // Archive previous local version in FinancialEditHistory if it's financial
                        if (cloudRec.entityType == "Expense" || cloudRec.entityType == "ServiceRegistration") {
                            archiveLocalVersion(localMeta.entityType, localMeta.entityId, "Sync Conflict Resolution Override")
                        }

                        applyCloudRecordToLocal(cloudRec)
                    } else if (cloudRec.updatedTimestamp < localMeta.updatedTimestamp) {
                        // Local version is newer: resolve conflict by marking local metadata as Pending to re-upload on next turn
                        dao.insertSyncMetadata(localMeta.copy(syncStatus = "Pending"))
                    } else {
                        // Identical timestamp: just mark local metadata as Synced
                        dao.insertSyncMetadata(localMeta.copy(syncStatus = "Synced"))
                    }
                }
            }

            _lastSyncTime.value = System.currentTimeMillis()
            currentRetryDelay = minRetryDelay // Reset backoff on success!
            
            // Also update last sync time in active device in cloud and local
            val updatedDevice = currentDevice.copy(lastSuccessfulSync = System.currentTimeMillis())
            dao.insertConnectedDevice(updatedDevice)
            cloudClient.patchDeviceHeartbeat(companyId, updatedDevice)

        } catch (e: Exception) {
            Log.e("SyncEngine", "Sync error: ${e.localizedMessage}", e)
            val isNetworkError = e is java.io.IOException || 
                    e.message?.contains("503") == true || 
                    e.message?.contains("timeout") == true || 
                    e.message?.contains("network") == true || 
                    e.message?.contains("unavailable") == true
            if (isNetworkError) {
                currentRetryDelay = (currentRetryDelay * 1.5).toLong().coerceAtMost(maxRetryDelay)
                Log.w("SyncEngine", "[Backoff] Network issue detected. Increasing sync interval to $currentRetryDelay ms.")
            }
        } finally {
            _syncing.value = false
            syncMutex.unlock()
        }
    }

    // Helper to fetch local database record as JSON
    private suspend fun fetchLocalRecordJson(entityType: String, entityId: String): String? {
        return try {
            when (entityType) {
                "Patient" -> dao.getPatientByUuid(entityId)?.let { SyncSerializer.serialize(entityType, it) }
                "Employee" -> dao.getEmployeeByUuid(entityId)?.let { SyncSerializer.serialize(entityType, it) }
                "Service" -> dao.getServiceByUuid(entityId)?.let { SyncSerializer.serialize(entityType, it) }
                "ServiceRegistration" -> dao.getServiceRegistrationByUuid(entityId)?.let { SyncSerializer.serialize(entityType, it) }
                "FinancialTransaction" -> dao.getFinancialTransactionByUuid(entityId)?.let { SyncSerializer.serialize(entityType, it) }
                "Cashbox" -> dao.getCashboxByUuid(entityId)?.let { SyncSerializer.serialize(entityType, it) }
                "Expense" -> dao.getExpenseByUuid(entityId)?.let { SyncSerializer.serialize(entityType, it) }
                "CommissionSettlement" -> dao.getCommissionSettlementByUuid(entityId)?.let { SyncSerializer.serialize(entityType, it) }
                "SystemSetting" -> {
                    // SystemSetting uses string key
                    dao.getSystemSettingByKey(entityId)?.let { value ->
                        SyncSerializer.serialize(entityType, SystemSetting(entityId, value))
                    }
                }
                "AuditLog" -> {
                    val idInt = entityId.toIntOrNull() ?: 0
                    val log = dao.getAllAuditLogs().first().find { it.id == idInt }
                    log?.let { SyncSerializer.serialize(entityType, it) }
                }
                "UserPermission" -> {
                    val list = dao.getAllUserPermissions().first()
                    val item = list.find { it.permissionName == entityId }
                    item?.let { SyncSerializer.serialize(entityType, it) }
                }
                "FinancialEditHistory" -> {
                    val idInt = entityId.toIntOrNull() ?: 0
                    val list = dao.getAllEditHistories().first()
                    val item = list.find { it.id == idInt }
                    item?.let { SyncSerializer.serialize(entityType, it) }
                }
                "JournalEntry" -> {
                    val idInt = entityId.toIntOrNull() ?: 0
                    val list = dao.getJournalEntriesList()
                    val item = list.find { it.id == idInt }
                    item?.let { SyncSerializer.serialize(entityType, it) }
                }
                "Referral" -> {
                    dao.getReferralByUuid(entityId)?.let { SyncSerializer.serialize(entityType, it) }
                }
                "ReferralCommission" -> {
                    dao.getReferralCommissionByUuid(entityId)?.let { SyncSerializer.serialize(entityType, it) }
                }
                "Alert" -> {
                    dao.getAlertByUuid(entityId)?.let { SyncSerializer.serialize(entityType, it) }
                }
                "Contract" -> {
                    dao.getContractByUuid(entityId)?.let { SyncSerializer.serialize(entityType, it) }
                }
                "StaffProfile" -> {
                    dao.getStaffProfileByUuid(entityId)?.let { SyncSerializer.serialize(entityType, it) }
                }
                "ServiceSchedule" -> {
                    dao.getServiceScheduleByUuid(entityId)?.let { SyncSerializer.serialize(entityType, it) }
                }
                "NursingReport" -> {
                    dao.getNursingReportByUuid(entityId)?.let { SyncSerializer.serialize(entityType, it) }
                }
                "VitalSigns" -> {
                    dao.getVitalSignsByUuid(entityId)?.let { SyncSerializer.serialize(entityType, it) }
                }
                "WoundRecord" -> {
                    dao.getWoundRecordByUuid(entityId)?.let { SyncSerializer.serialize(entityType, it) }
                }
                "ConsentForm" -> {
                    dao.getConsentFormByUuid(entityId)?.let { SyncSerializer.serialize(entityType, it) }
                }
                "Prescription" -> {
                    dao.getPrescriptionByUuid(entityId)?.let { SyncSerializer.serialize(entityType, it) }
                }
                "DashboardCache" -> {
                    dao.getDashboardCacheByUuid(entityId)?.let { SyncSerializer.serialize(entityType, it) }
                }
                "FixedExpenseTemplate" -> {
                    dao.getFixedExpenseTemplateByUuid(entityId)?.let { SyncSerializer.serialize(entityType, it) }
                }
                "ExpenseCategory" -> {
                    dao.getExpenseCategoryByUuid(entityId)?.let { SyncSerializer.serialize(entityType, it) }
                }
                "ConnectedDevice" -> {
                    dao.getAllConnectedDevicesList().find { it.deviceId == entityId }?.let { SyncSerializer.serialize(entityType, it) }
                }
                "FinancialReport" -> {
                    dao.getFinancialReportsList().find { it.id.toString() == entityId }?.let { SyncSerializer.serialize(entityType, it) }
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e("SyncEngine", "Error serializing local record $entityType with ID $entityId", e)
            null
        }
    }

    // Helper to apply cloud record changes locally
    private suspend fun applyCloudRecordToLocal(cloudRec: CloudSyncRecord) {
        if (cloudRec.isDeleted) {
            // Delete locally
            deleteLocalRecord(cloudRec.entityType, cloudRec.entityId)
            dao.insertSyncMetadata(
                SyncMetadata(
                    entityType = cloudRec.entityType,
                    entityId = cloudRec.entityId,
                    updatedTimestamp = cloudRec.updatedTimestamp,
                    deletedStatus = true,
                    lastModifiedDeviceId = cloudRec.lastModifiedDeviceId,
                    syncStatus = "Synced"
                )
            )
            return
        }

        try {
            val obj = SyncSerializer.deserialize(cloudRec.entityType, cloudRec.dataJson)
            insertOrUpdateLocalRecord(cloudRec.entityType, cloudRec.entityId, obj)

            dao.insertSyncMetadata(
                SyncMetadata(
                    entityType = cloudRec.entityType,
                    entityId = cloudRec.entityId,
                    updatedTimestamp = cloudRec.updatedTimestamp,
                    deletedStatus = false,
                    lastModifiedDeviceId = cloudRec.lastModifiedDeviceId,
                    syncStatus = "Synced"
                )
            )
        } catch (e: Exception) {
            Log.e("SyncEngine", "Failed to deserialize cloud record: ${cloudRec.entityType}_${cloudRec.entityId}", e)
        }
    }

    private suspend fun deleteLocalRecord(entityType: String, entityId: String) {
        val idInt = entityId.toIntOrNull() ?: 0
        when (entityType) {
            "Patient" -> dao.getPatientByUuid(entityId)?.let { dao.deletePatient(it) }
            "Employee" -> dao.getEmployeeByUuid(entityId)?.let { dao.deleteEmployee(it) }
            "Service" -> dao.getServiceByUuid(entityId)?.let { dao.deleteService(it) }
            "ServiceRegistration" -> dao.getServiceRegistrationByUuid(entityId)?.let { dao.deleteServiceRegistration(it) }
            "FinancialTransaction" -> dao.getFinancialTransactionByUuid(entityId)?.let { dao.deleteFinancialTransaction(it) }
            "Cashbox" -> dao.getCashboxByUuid(entityId)?.let { dao.deleteCashbox(it) }
            "CommissionSettlement" -> dao.getCommissionSettlementByUuid(entityId)?.let { dao.deleteCommissionSettlement(it) }
            "Expense" -> dao.getExpenseByUuid(entityId)?.let { dao.deleteExpense(it) }
            "SystemSetting" -> dao.deleteSystemSettingByKey(entityId)
            "JournalEntry" -> {
                val item = dao.getJournalEntriesList().find { it.id == idInt }
                item?.let { dao.deleteJournalEntry(it) }
            }
            "Referral" -> dao.getReferralByUuid(entityId)?.let { dao.deleteReferral(it) }
            "ReferralCommission" -> dao.getReferralCommissionByUuid(entityId)?.let { dao.deleteReferralCommission(it) }
            "Alert" -> dao.getAlertByUuid(entityId)?.let { dao.deleteAlert(it) }
            "Contract" -> dao.getContractByUuid(entityId)?.let { dao.deleteContract(it) }
            "StaffProfile" -> dao.getStaffProfileByUuid(entityId)?.let { dao.deleteStaffProfile(it) }
            "ServiceSchedule" -> dao.getServiceScheduleByUuid(entityId)?.let { dao.deleteServiceSchedule(it) }
            "NursingReport" -> dao.getNursingReportByUuid(entityId)?.let { dao.deleteNursingReport(it) }
            "VitalSigns" -> dao.getVitalSignsByUuid(entityId)?.let { dao.deleteVitalSigns(it) }
            "WoundRecord" -> dao.getWoundRecordByUuid(entityId)?.let { dao.deleteWoundRecord(it) }
            "ConsentForm" -> dao.getConsentFormByUuid(entityId)?.let { dao.deleteConsentForm(it) }
            "Prescription" -> dao.getPrescriptionByUuid(entityId)?.let { dao.deletePrescription(it) }
            "DashboardCache" -> dao.deleteDashboardCacheByUuid(entityId)
            "FixedExpenseTemplate" -> dao.getFixedExpenseTemplateByUuid(entityId)?.let { dao.deleteFixedExpenseTemplate(it) }
            "ExpenseCategory" -> dao.getExpenseCategoryByUuid(entityId)?.let { dao.deleteExpenseCategory(it) }
            "ConnectedDevice" -> dao.deleteConnectedDevice(entityId)
            "FinancialReport" -> dao.deleteFinancialReportById(idInt)
            "AuditLog" -> dao.deleteAuditLogById(idInt)
            "UserPermission" -> dao.deleteUserPermissionByName(entityId)
            "FinancialEditHistory" -> dao.deleteEditHistoryById(idInt)
        }
    }

    private suspend fun insertOrUpdateLocalRecord(entityType: String, entityId: String, obj: Any) {
        when (entityType) {
            "Patient" -> {
                val patient = obj as Patient
                val existing = dao.getPatientByUuid(patient.uuid)
                if (existing != null) {
                    dao.insertPatient(patient.copy(id = existing.id))
                } else {
                    val conflict = dao.getPatientById(patient.id)
                    if (conflict != null) {
                        dao.insertPatient(patient.copy(id = 0))
                    } else {
                        dao.insertPatient(patient)
                    }
                }
            }
            "Employee" -> {
                val employee = obj as Employee
                val existing = dao.getEmployeeByUuid(employee.uuid)
                if (existing != null) {
                    dao.insertEmployee(employee.copy(id = existing.id))
                } else {
                    val conflict = dao.getEmployeeById(employee.id)
                    if (conflict != null) {
                        dao.insertEmployee(employee.copy(id = 0))
                    } else {
                        dao.insertEmployee(employee)
                    }
                }
            }
            "Service" -> {
                val service = obj as Service
                val existing = dao.getServiceByUuid(service.uuid)
                if (existing != null) {
                    dao.insertService(service.copy(id = existing.id))
                } else {
                    val conflict = dao.getServiceById(service.id)
                    if (conflict != null) {
                        dao.insertService(service.copy(id = 0))
                    } else {
                        dao.insertService(service)
                    }
                }
            }
            "ServiceRegistration" -> {
                val reg = obj as ServiceRegistration
                val existing = dao.getServiceRegistrationByUuid(reg.uuid)
                if (existing != null) {
                    dao.insertServiceRegistration(reg.copy(id = existing.id))
                } else {
                    val conflict = dao.getServiceRegistrationById(reg.id)
                    if (conflict != null) {
                        dao.insertServiceRegistration(reg.copy(id = 0))
                    } else {
                        dao.insertServiceRegistration(reg)
                    }
                }
            }
            "FinancialTransaction" -> {
                val tx = obj as FinancialTransaction
                val existing = dao.getFinancialTransactionByUuid(tx.uuid)
                if (existing != null) {
                    dao.insertFinancialTransaction(tx.copy(id = existing.id))
                } else {
                    val conflict = dao.getFinancialTransactionsList().find { it.id == tx.id }
                    if (conflict != null) {
                        dao.insertFinancialTransaction(tx.copy(id = 0))
                    } else {
                        dao.insertFinancialTransaction(tx)
                    }
                }
            }
            "Cashbox" -> {
                val cashbox = obj as Cashbox
                val existing = dao.getCashboxByUuid(cashbox.uuid)
                if (existing != null) {
                    dao.insertCashbox(cashbox.copy(id = existing.id))
                } else {
                    val conflict = dao.getCashboxById(cashbox.id)
                    if (conflict != null) {
                        dao.insertCashbox(cashbox.copy(id = 0))
                    } else {
                        dao.insertCashbox(cashbox)
                    }
                }
            }
            "CommissionSettlement" -> {
                val settlement = obj as CommissionSettlement
                val existing = dao.getCommissionSettlementByUuid(settlement.uuid)
                if (existing != null) {
                    dao.insertCommissionSettlement(settlement.copy(id = existing.id))
                } else {
                    val conflict = dao.getCommissionSettlementsList().find { it.id == settlement.id }
                    if (conflict != null) {
                        dao.insertCommissionSettlement(settlement.copy(id = 0))
                    } else {
                        dao.insertCommissionSettlement(settlement)
                    }
                }
            }
            "Expense" -> {
                val expense = obj as Expense
                val existing = dao.getExpenseByUuid(expense.uuid)
                if (existing != null) {
                    dao.insertExpense(expense.copy(id = existing.id))
                } else {
                    val conflict = dao.getExpenseById(expense.id)
                    if (conflict != null) {
                        dao.insertExpense(expense.copy(id = 0))
                    } else {
                        dao.insertExpense(expense)
                    }
                }
            }
            "SystemSetting" -> dao.insertSystemSetting(obj as SystemSetting)
            "AuditLog" -> dao.insertAuditLog(obj as AuditLog)
            "UserPermission" -> dao.insertUserPermission(obj as UserPermission)
            "FinancialEditHistory" -> dao.insertEditHistory(obj as FinancialEditHistory)
            "JournalEntry" -> dao.insertJournalEntry(obj as JournalEntry)
            "Referral" -> {
                val referral = obj as Referral
                val existing = dao.getReferralByUuid(referral.uuid)
                if (existing != null) {
                    dao.insertReferral(referral.copy(id = existing.id))
                } else {
                    val conflict = dao.getReferralById(referral.id)
                    if (conflict != null) {
                        dao.insertReferral(referral.copy(id = 0))
                    } else {
                        dao.insertReferral(referral)
                    }
                }
            }
            "ReferralCommission" -> {
                val commission = obj as ReferralCommission
                val existing = dao.getReferralCommissionByUuid(commission.uuid)
                if (existing != null) {
                    dao.insertReferralCommission(commission.copy(id = existing.id))
                } else {
                    val conflict = dao.getReferralCommissionById(commission.id)
                    if (conflict != null) {
                        dao.insertReferralCommission(commission.copy(id = 0))
                    } else {
                        dao.insertReferralCommission(commission)
                    }
                }
            }
            "Alert" -> {
                val alert = obj as Alert
                val existing = dao.getAlertByUuid(alert.uuid)
                if (existing != null) {
                    dao.insertAlert(alert.copy(id = existing.id))
                } else {
                    val conflict = dao.getAlertById(alert.id)
                    if (conflict != null) {
                        dao.insertAlert(alert.copy(id = 0))
                    } else {
                        dao.insertAlert(alert)
                    }
                }
            }
            "Contract" -> {
                val contract = obj as Contract
                val existing = dao.getContractByUuid(contract.uuid)
                if (existing != null) {
                    dao.insertContract(contract.copy(id = existing.id))
                } else {
                    val conflict = dao.getContractById(contract.id)
                    if (conflict != null) {
                        dao.insertContract(contract.copy(id = 0))
                    } else {
                        dao.insertContract(contract)
                    }
                }
            }
            "StaffProfile" -> {
                val profile = obj as StaffProfile
                val existing = dao.getStaffProfileByUuid(profile.uuid)
                if (existing != null) {
                    dao.insertStaffProfile(profile.copy(id = existing.id))
                } else {
                    val conflict = dao.getStaffProfileById(profile.id)
                    if (conflict != null) {
                        dao.insertStaffProfile(profile.copy(id = 0))
                    } else {
                        dao.insertStaffProfile(profile)
                    }
                }
            }
            "ServiceSchedule" -> {
                val schedule = obj as ServiceSchedule
                val existing = dao.getServiceScheduleByUuid(schedule.uuid)
                if (existing != null) {
                    dao.insertServiceSchedule(schedule.copy(id = existing.id))
                } else {
                    val conflict = dao.getServiceScheduleById(schedule.id)
                    if (conflict != null) {
                        dao.insertServiceSchedule(schedule.copy(id = 0))
                    } else {
                        dao.insertServiceSchedule(schedule)
                    }
                }
            }
            "NursingReport" -> {
                val report = obj as NursingReport
                val existing = dao.getNursingReportByUuid(report.uuid)
                if (existing != null) {
                    dao.insertNursingReport(report.copy(id = existing.id))
                } else {
                    val conflict = dao.getNursingReportById(report.id)
                    if (conflict != null) {
                        dao.insertNursingReport(report.copy(id = 0))
                    } else {
                        dao.insertNursingReport(report)
                    }
                }
            }
            "VitalSigns" -> {
                val signs = obj as VitalSigns
                val existing = dao.getVitalSignsByUuid(signs.uuid)
                if (existing != null) {
                    dao.insertVitalSigns(signs.copy(id = existing.id))
                } else {
                    val conflict = dao.getVitalSignsById(signs.id)
                    if (conflict != null) {
                        dao.insertVitalSigns(signs.copy(id = 0))
                    } else {
                        dao.insertVitalSigns(signs)
                    }
                }
            }
            "WoundRecord" -> {
                val record = obj as WoundRecord
                val existing = dao.getWoundRecordByUuid(record.uuid)
                if (existing != null) {
                    dao.insertWoundRecord(record.copy(id = existing.id))
                } else {
                    val conflict = dao.getWoundRecordById(record.id)
                    if (conflict != null) {
                        dao.insertWoundRecord(record.copy(id = 0))
                    } else {
                        dao.insertWoundRecord(record)
                    }
                }
            }
            "ConsentForm" -> {
                val form = obj as ConsentForm
                val existing = dao.getConsentFormByUuid(form.uuid)
                if (existing != null) {
                    dao.insertConsentForm(form.copy(id = existing.id))
                } else {
                    val conflict = dao.getConsentFormById(form.id)
                    if (conflict != null) {
                        dao.insertConsentForm(form.copy(id = 0))
                    } else {
                        dao.insertConsentForm(form)
                    }
                }
            }
            "Prescription" -> {
                val prescription = obj as Prescription
                val existing = dao.getPrescriptionByUuid(prescription.uuid)
                if (existing != null) {
                    dao.insertPrescription(prescription.copy(id = existing.id))
                } else {
                    val conflict = dao.getPrescriptionById(prescription.id)
                    if (conflict != null) {
                        dao.insertPrescription(prescription.copy(id = 0))
                    } else {
                        dao.insertPrescription(prescription)
                    }
                }
            }
            "DashboardCache" -> {
                val cache = obj as DashboardCache
                dao.insertDashboardCache(cache)
            }
            "FixedExpenseTemplate" -> {
                val template = obj as FixedExpenseTemplate
                val existing = dao.getFixedExpenseTemplateByUuid(template.uuid)
                if (existing != null) {
                    dao.updateFixedExpenseTemplate(template.copy(id = existing.id))
                } else {
                    val conflict = dao.getAllFixedExpenseTemplates().first().find { it.id == template.id }
                    if (conflict != null) {
                        dao.insertFixedExpenseTemplate(template.copy(id = 0))
                    } else {
                        dao.insertFixedExpenseTemplate(template)
                    }
                }
            }
            "ExpenseCategory" -> {
                val category = obj as ExpenseCategory
                val existing = dao.getExpenseCategoryByUuid(category.uuid)
                if (existing != null) {
                    dao.updateExpenseCategory(category.copy(id = existing.id))
                } else {
                    val conflict = dao.getExpenseCategoriesList().find { it.id == category.id }
                    if (conflict != null) {
                        dao.insertExpenseCategory(category.copy(id = 0))
                    } else {
                        dao.insertExpenseCategory(category)
                    }
                }
            }
            "ConnectedDevice" -> {
                val dev = obj as ConnectedDevice
                dao.insertConnectedDevice(dev)
            }
            "FinancialReport" -> {
                val report = obj as FinancialReport
                val conflict = dao.getFinancialReportsList().find { it.id == report.id }
                if (conflict != null) {
                    dao.insertFinancialReport(report.copy(id = conflict.id))
                } else {
                    dao.insertFinancialReport(report)
                }
            }
        }
    }

    private suspend fun archiveLocalVersion(entityType: String, entityId: String, reason: String) {
        try {
            when (entityType) {
                "Expense" -> {
                    val exp = dao.getExpenseByUuid(entityId)
                    if (exp != null) {
                        dao.insertEditHistory(
                            FinancialEditHistory(
                                entityType = "Expense",
                                entityId = exp.id,
                                previousValue = "مبلغ: ${exp.amount}، عنوان: ${exp.title}، دسته: ${exp.category}",
                                newValue = "همگام‌سازی و بازنویسی تعارضی از فضای ابری",
                                differenceAmount = 0.0,
                                editedBy = "موتور همگام‌سازی",
                                userRole = "سیستم",
                                reason = reason,
                                comment = "تغییرات همپوشانی‌شده در همگام‌سازی چند دستگاهی"
                            )
                        )
                    }
                }
                "ServiceRegistration" -> {
                    val reg = dao.getServiceRegistrationByUuid(entityId)
                    if (reg != null) {
                        dao.insertEditHistory(
                            FinancialEditHistory(
                                entityType = "ServiceRegistration",
                                entityId = reg.id,
                                previousValue = "مبلغ توافقی: ${reg.sellingPrice}، دستمزد: ${reg.employeeCost}، نهایی: ${reg.finalPrice}",
                                newValue = "همگام‌سازی و بازنویسی تعارضی از فضای ابری",
                                differenceAmount = 0.0,
                                editedBy = "موتور همگام‌سازی",
                                userRole = "سیستم",
                                reason = reason,
                                comment = "تغییرات همپوشانی‌شده در همگام‌سازی چند دستگاهی"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SyncEngine", "Error archiving version for conflict recovery", e)
        }
    }
}
