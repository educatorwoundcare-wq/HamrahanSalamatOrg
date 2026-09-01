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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class SyncSummary(
    val total: Int,
    val successful: Int,
    val pending: Int,
    val retrying: Int,
    val failed: Int,
    val blocked: Int,
    val details: List<SyncItemDetail> = emptyList()
)

data class SyncItemDetail(
    val operationUuid: String,
    val entityType: String,
    val operationType: String,
    val companyId: String,
    val retryCount: Int,
    val lastAttemptTimestamp: Long,
    val lastError: String,
    val httpStatus: Int?,
    val serverResponse: String?,
    val classification: SyncClassification,
    val failureReasonDescription: String
)

enum class SyncClassification(val code: String, val title: String, val descriptionFa: String) {
    A("A", "Waiting / Never Attempted", "در انتظار اجرا / هنوز تلاش نشده"),
    B("B", "Retryable Network Failure", "خطای موقت شبکه قابل تلاش مجدد"),
    C("C", "Authentication Failure", "خطای احراز هویت / عدم وجود توکن"),
    D("D", "RLS Failure", "خطای سطح دسترسی RLS"),
    E("E", "Duplicate / Conflict", "تکراری یا تداخل داده"),
    F("F", "Invalid Workspace / Company ID", "شناسه دفتر/شرکت نامعتبر یا قدیمی"),
    G("G", "Serialization / Parsing Failure", "خطای سریال‌سازی یا خواندن داده محلی"),
    H("H", "Permanent Server Error", "خطای دائمی سرور")
}

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

    private val _syncSummary = MutableStateFlow<SyncSummary?>(null)
    val syncSummary: StateFlow<SyncSummary?> = _syncSummary.asStateFlow()

    private val pairingDialogsShown = mutableSetOf<String>()
    val pairingApprovalEvents = MutableSharedFlow<ConnectedDevice>(extraBufferCapacity = 16)

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val syncMutex = Mutex()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    suspend fun logDiagnostic(
        category: String,
        level: String,
        summary: String,
        details: String = "",
        entityType: String = "",
        entityId: String = ""
    ) {
        try {
            dao.insertDiagnosticEvent(
                DiagnosticEvent(
                    category = category,
                    level = level,
                    summary = summary,
                    details = details,
                    entityType = entityType,
                    entityId = entityId
                )
            )
            if (Math.random() < 0.05) {
                dao.pruneDiagnosticEvents()
            }
        } catch (e: Exception) {
            Log.e("SyncEngine", "Failed to log diagnostic event", e)
        }
    }

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
                    val wm = WorkspaceManager.getInstance(context)
                    val companyId = wm.currentTenantId?.takeIf { it.isNotBlank() } ?: dao.getSystemSettingByKey("company_id")?.takeIf { it.isNotBlank() } ?: dao.getSystemSettingByKey("pending_company_id")
                    if (!companyId.isNullOrEmpty()) {
                        try {
                            sync()
                        } catch (e: Exception) {
                            Log.e("SyncEngine", "Periodic real-time background sync error", e)
                        }
                    } else {
                        Log.d("SyncEngine", "[Lifecycle] No company_id configured yet. Connectivity monitoring remains ACTIVE while sync operations are skipped.")
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
    suspend fun sync(): Boolean = withContext(Dispatchers.IO) {
        if (!syncMutex.tryLock()) {
            Log.d("SyncEngine", "Sync operation already in progress, skipping concurrent acquisition.")
            return@withContext true
        }
        var success = false
        var syncCompanyId: String? = null
        var syncDeviceId: String? = null
        var syncAuthUid: String? = null
        try {
            _syncing.value = true
            val wm = WorkspaceManager.getInstance(context)
            val companyId = wm.currentTenantId?.takeIf { it.isNotBlank() } ?: dao.getSystemSettingByKey("company_id")?.takeIf { it.isNotBlank() } ?: dao.getSystemSettingByKey("pending_company_id")
            syncCompanyId = companyId
            val initialAuthUid = wm.currentAuthUid ?: wm.extractSubFromJwt(wm.currentAuthToken)
            syncAuthUid = initialAuthUid
            val syncCode = wm.currentSyncCode?.takeIf { it.isNotBlank() } ?: dao.getSystemSettingByKey("company_sync_code")?.takeIf { it.isNotBlank() } ?: dao.getSystemSettingByKey("pending_sync_code")
            Log.i("IDENTITY_RECOVERY", "[IDENTITY_RECOVERY] authUid=$initialAuthUid localCompanyId=$companyId localSyncCode=$syncCode remoteCreatorUid=N/A decision=SYNC_ENGINE_STARTUP")
            if (companyId.isNullOrEmpty()) {
                Log.w("SyncEngine", "[Sync Cycle] Sync aborted because company_id is missing. Workspace not configured.")
                return@withContext true
            }

            val activeDeviceId = DeviceIdentityProvider.syncWithRoomDatabase(context, dao)
            syncDeviceId = activeDeviceId
            val localStatus = dao.getSystemSettingByKey("active_device_status") ?: "Pending"

            // Canonical Bootstrap Orchestration (R24/R25)
            val bootstrapResult = cloudClient.ensureCanonicalCloudBootstrap(
                companyId = companyId,
                syncCode = syncCode,
                forcedDeviceId = activeDeviceId
            )

            if (bootstrapResult !is BootstrapResult.SyncAllowed) {
                when (bootstrapResult) {
                    is BootstrapResult.PendingApproval -> {
                        Log.w("SYNC_GATE_BLOCKED", "[SYNC_GATE_BLOCKED] companyId=$companyId deviceId=$activeDeviceId deviceStatus=Pending allowed=false reason=DEVICE_PENDING_APPROVAL")
                        dao.insertSystemSetting(SystemSetting("active_device_status", "Pending"))
                        dao.insertSystemSetting(SystemSetting("device_has_been_approved", "false"))
                    }
                    is BootstrapResult.IdentityRecoveryRequired -> {
                        Log.w("SYNC_GATE_BLOCKED", "[SYNC_GATE_BLOCKED] companyId=$companyId deviceId=$activeDeviceId allowed=false reason=IDENTITY_RECOVERY_REQUIRED (${bootstrapResult.reason})")
                    }
                    is BootstrapResult.Blocked -> {
                        Log.w("SYNC_GATE_BLOCKED", "[SYNC_GATE_BLOCKED] companyId=$companyId deviceId=$activeDeviceId allowed=false reason=BLOCKED_${bootstrapResult.reason}")
                    }
                    is BootstrapResult.Error -> {
                        Log.e("SYNC_GATE_BLOCKED", "[SYNC_GATE_BLOCKED] companyId=$companyId deviceId=$activeDeviceId allowed=false reason=ERROR_${bootstrapResult.message}", bootstrapResult.exception)
                    }
                    else -> {}
                }
                return@withContext true
            }

            val confirmedDevice = bootstrapResult.device
            val confirmedWorkspace = bootstrapResult.workspace
            val confirmedAuthUid = bootstrapResult.authUid
            val activeDeviceRole = confirmedDevice.role
                        val activeDeviceStatus = confirmedDevice.status

            // Promote pending target to active workspace if applicable
            val pendingCompId = dao.getSystemSettingByKey("pending_company_id")
            if (!pendingCompId.isNullOrBlank() && pendingCompId == confirmedWorkspace.companyId && activeDeviceStatus == "Active") {
                dao.insertSystemSetting(SystemSetting("company_id", confirmedWorkspace.companyId))
                dao.insertSystemSetting(SystemSetting("company_sync_code", confirmedWorkspace.companySyncCode))
                val pName = dao.getSystemSettingByKey("pending_company_name") ?: confirmedWorkspace.centerName
                dao.insertSystemSetting(SystemSetting("company_name", pName))
                dao.insertSystemSetting(SystemSetting("center_name", pName))
                
                dao.insertSystemSetting(SystemSetting("pending_company_id", ""))
                dao.insertSystemSetting(SystemSetting("pending_sync_code", ""))
                dao.insertSystemSetting(SystemSetting("pending_company_name", ""))
                
                wm.saveIdentity(confirmedWorkspace.companyId, confirmedWorkspace.companySyncCode, wm.currentAuthToken ?: "", confirmedAuthUid)
                
                // Reindex local workspace
                (context.applicationContext as? com.example.HamrahanApplication)?.container?.repository?.reindexWorkspaceData(confirmedWorkspace.companyId)
            }


            // Sync and persist authoritative role and status locally
            dao.insertSystemSetting(SystemSetting("active_device_role", activeDeviceRole))
            dao.insertSystemSetting(SystemSetting("active_device_status", activeDeviceStatus))
            dao.insertSystemSetting(SystemSetting("device_has_been_approved", "true"))
            dao.insertConnectedDevice(confirmedDevice)

            // Administrative Registration Sync: approved administrators poll other device metadata/join requests
            if (activeDeviceStatus == "Active" && (activeDeviceRole == "Mother Account" || activeDeviceRole == "Admin" || activeDeviceRole == "GM" || activeDeviceRole == "General Manager")) {
                try {
                    val cloudDevices = cloudClient.getConnectedDevices(companyId)
                    val pendingRequestsCount = cloudDevices.count { it.status == "Pending" }
                    Log.i("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [DISCOVERY] companyId=$companyId remoteCount=${cloudDevices.size} pendingCount=$pendingRequestsCount deviceIds=${cloudDevices.map { it.deviceId }}")
                    for (dev in cloudDevices) {
                        dao.insertConnectedDevice(dev)
                        Log.i("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [ROOM_INSERT] deviceId=${dev.deviceId} companyId=${dev.companyId} status=${dev.status} role=${dev.role}")
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
                            if (pairingDialogsShown.add(dev.deviceId)) {
                                Log.d("PAIRING_POPUP", "[PAIRING_POPUP] Pending pairing detected deviceId=${dev.deviceId}")
                                Log.d("PAIRING_POPUP", "[PAIRING_POPUP] Event emitted deviceId=${dev.deviceId}")
                                pairingApprovalEvents.tryEmit(dev)
                            } else {
                                Log.d("PAIRING_POPUP", "[PAIRING_POPUP] Duplicate event suppressed deviceId=${dev.deviceId}")
                            }
                        } else {
                            pairingDialogsShown.remove(dev.deviceId)
                        }
                    }
                    Log.i("SyncEngine", "[DOWNLOAD]\n\nPending requests found=$pendingRequestsCount")
                    Log.i("SyncEngine", "[ADMIN]\n\nRequests received=${cloudDevices.size}")
                    Log.i("SyncEngine", "[Registration Sync] Successfully pulled connected devices list. Total count: ${cloudDevices.size}, Pending: $pendingRequestsCount")
                } catch (e: Exception) {
                    Log.e("SyncEngine", "[Registration Sync] Error fetching connected devices list", e)
                }
            }

            // --- 2. BUSINESS DATA SYNC PIPELINE ---
            val syncAuthCheck = cloudClient.canSyncBusinessData(companyId, activeDeviceId)
            if (!syncAuthCheck.allowed) {
                Log.w("SYNC_GATE_BLOCKED", "[SYNC_GATE_BLOCKED] Business sync blocked. Reason: ${syncAuthCheck.reason}")
                return@withContext true
            }

            Log.i("SyncEngine", "[Business Sync] Canonical bootstrap confirmed Active. Running business synchronization pipeline.")

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
                        Log.w("SyncEngine", "[Self-Healing] Detected missing workspace record in Supabase for an active workspace! Starting healing...")
                        
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

            // 2. Upload pending local changes to the Real Cloud Database (Only for Active devices)
            if (activeDeviceStatus != "Active") {
                Log.i("SyncEngine", "[Business Sync] Skipping cloud record upload/download because device status is '$activeDeviceStatus' (must be 'Active')")
                return@withContext true
            }

            val pendingMetadata = dao.getPendingSyncMetadata()
            val totalPendingCount = pendingMetadata.size
            if (pendingMetadata.isNotEmpty()) {
                Log.i("SyncEngine", "[Sync Cycle] Found $totalPendingCount pending local changes to upload for company $companyId")
            }

            var successfulUploads = 0
            var failedUploads = 0
            var retryingUploads = 0
            var blockedUploads = 0
            val syncDetails = mutableListOf<SyncItemDetail>()

            // Retrieve or validate canonical companyId
            val canonicalCompanyId = companyId.takeIf { it.isNotBlank() && it != "COMP-LOCAL" }
                ?: WorkspaceManager.getInstance(context).currentTenantId?.takeIf { it.isNotBlank() && it != "COMP-LOCAL" }
                ?: dao.getSystemSettingByKey("company_id")?.takeIf { it.isNotBlank() && it != "COMP-LOCAL" }
                ?: ""

            for (staleMeta in pendingMetadata) {
                val meta = dao.getSyncMetadata(staleMeta.entityType, staleMeta.entityId) ?: continue

                val operationUuid = meta.entityId
                val entityType = meta.entityType
                val operationType = if (meta.deletedStatus) "DELETE" else "UPSERT"
                val lastAttemptTs = System.currentTimeMillis()

                if (entityType == "FixedExpenseTemplate") {
                    Log.i("SYNC_FIXED_EXPENSE", "SYNC_FIXED_EXPENSE_OPERATION operation=$operationType entityType=$entityType entityId=$operationUuid")
                }

                try {
                    // Fetch local record only for UPSERT. For DELETE, we must not query the local DB.
                    val localDataJson = if (!meta.deletedStatus) {
                        val json = fetchLocalRecordJson(meta.entityType, meta.entityId)
                        if (entityType == "FixedExpenseTemplate") {
                            Log.i("SYNC_FIXED_EXPENSE", "SYNC_FIXED_EXPENSE_LOCAL_RECORD_FOUND=${json != null}")
                        }
                        json
                    } else {
                        if (entityType == "FixedExpenseTemplate") {
                            Log.i("SYNC_FIXED_EXPENSE", "SYNC_FIXED_EXPENSE_DELETE_NO_LOCAL_LOOKUP")
                        }
                        null
                    }

                    if (localDataJson == null && !meta.deletedStatus) {
                        // G. Serialization / parsing failure (or missing local record)
                        val lastError = "Local record data could not be found or serialized for $entityType with ID $operationUuid"
                        val classification = SyncClassification.G
                        Log.w("SYNC_QUEUE_TRACE", """
                            [SYNC_QUEUE_ITEM]
                            operation UUID: $operationUuid
                            entity/table type: $entityType
                            operation type: $operationType
                            company/workspace ID: $canonicalCompanyId
                            retry count: 1
                            last attempt timestamp: $lastAttemptTs
                            last error: $lastError
                            HTTP status: N/A
                            server response: NONE
                            classification: ${classification.code} - ${classification.title}
                        """.trimIndent())

                        failedUploads++
                        dao.insertSyncMetadata(meta.copy(syncStatus = "Failed"))
                        syncDetails.add(
                            SyncItemDetail(
                                operationUuid = operationUuid,
                                entityType = entityType,
                                operationType = operationType,
                                companyId = canonicalCompanyId,
                                retryCount = 1,
                                lastAttemptTimestamp = lastAttemptTs,
                                lastError = lastError,
                                httpStatus = null,
                                serverResponse = null,
                                classification = classification,
                                failureReasonDescription = classification.descriptionFa + ": " + lastError
                            )
                        )
                        continue
                    }

                    if (meta.deletedStatus) {
                        val existingCloudRec = dao.getAllCloudSyncRecords().find { it.id == "${meta.entityType}_${meta.entityId}" }
                        if (existingCloudRec == null) {
                            Log.i("SYNC_QUEUE", "Skipping DELETE for ${meta.entityType}_${meta.entityId} because it never existed in cloud.")
                            dao.deleteSyncMetadata(meta.entityType, meta.entityId)
                            successfulUploads++
                            continue
                        }
                    }

                    if (canonicalCompanyId.isBlank()) {
                        // F. Invalid workspace / company ID
                        val lastError = "Company ID is empty or invalid (COMP-LOCAL / unassigned)"
                        val classification = SyncClassification.F
                        Log.w("SYNC_QUEUE_TRACE", """
                            [SYNC_QUEUE_ITEM]
                            operation UUID: $operationUuid
                            entity/table type: $entityType
                            operation type: $operationType
                            company/workspace ID: NONE
                            retry count: 1
                            last attempt timestamp: $lastAttemptTs
                            last error: $lastError
                            HTTP status: N/A
                            server response: NONE
                            classification: ${classification.code} - ${classification.title}
                        """.trimIndent())

                        blockedUploads++
                        dao.insertSyncMetadata(meta.copy(syncStatus = "Failed"))
                        syncDetails.add(
                            SyncItemDetail(
                                operationUuid = operationUuid,
                                entityType = entityType,
                                operationType = operationType,
                                companyId = "",
                                retryCount = 1,
                                lastAttemptTimestamp = lastAttemptTs,
                                lastError = lastError,
                                httpStatus = null,
                                serverResponse = null,
                                classification = classification,
                                failureReasonDescription = classification.descriptionFa
                            )
                        )
                        continue
                    }

                    val cloudRecord = CloudSyncRecord(
                        id = "${meta.entityType}_${meta.entityId}",
                        entityType = meta.entityType,
                        entityId = meta.entityId,
                        dataJson = localDataJson ?: "",
                        updatedTimestamp = meta.updatedTimestamp,
                        lastModifiedDeviceId = activeDeviceId,
                        isDeleted = meta.deletedStatus
                    )

                    // Real cloud upload using detailed caller
                    Log.e(
                        "SYNC_DIAGNOSTIC",
                        "UPLOAD START | entityType=${meta.entityType} | entityId=${meta.entityId}"
                    )
                    Log.i("SyncEngine", "[Upload] Uploading record ${cloudRecord.id} of type ${cloudRecord.entityType} to company $canonicalCompanyId")
                    val uploadResult = cloudClient.uploadRecordDetailed(canonicalCompanyId, cloudRecord)
                    when (uploadResult) {
                        is UploadRecordResult.Success -> {
                            Log.e(
                                "SYNC_DIAGNOSTIC",
                                "UPLOAD RESULT | entity=${meta.entityType} | id=${meta.entityId} | success=true | code=${uploadResult.httpCode} | body=OK"
                            )
                            Log.i("SYNC_QUEUE_TRACE", """
                                [SYNC_QUEUE_ITEM]
                                operation UUID: $operationUuid
                                entity/table type: $entityType
                                operation type: $operationType
                                company/workspace ID: $canonicalCompanyId
                                retry count: 0
                                last attempt timestamp: $lastAttemptTs
                                last error: NONE
                                HTTP status: ${uploadResult.httpCode}
                                server response: SUCCESS
                                classification: SUCCESS
                            """.trimIndent())

                            successfulUploads++
                            dao.insertSyncMetadata(meta.copy(syncStatus = "Synced"))
                            dao.insertCloudSyncRecord(cloudRecord)
                        }
                        is UploadRecordResult.Blocked -> {
                            Log.e(
                                "SYNC_DIAGNOSTIC",
                                "UPLOAD RESULT | entity=${meta.entityType} | id=${meta.entityId} | success=false | code=403 | body=MUTATION_BLOCKED_${uploadResult.reason}"
                            )
                            val classification = if (uploadResult.reason.contains("TOKEN") || uploadResult.reason.contains("AUTH")) {
                                SyncClassification.C
                            } else if (uploadResult.reason.contains("COMPANY") || uploadResult.reason.contains("WORKSPACE")) {
                                SyncClassification.F
                            } else {
                                SyncClassification.D
                            }
                            Log.w("SYNC_QUEUE_TRACE", """
                                [SYNC_QUEUE_ITEM]
                                operation UUID: $operationUuid
                                entity/table type: $entityType
                                operation type: $operationType
                                company/workspace ID: $canonicalCompanyId
                                retry count: 1
                                last attempt timestamp: $lastAttemptTs
                                last error: Blocked: ${uploadResult.reason}
                                HTTP status: 403
                                server response: MUTATION_BLOCKED
                                classification: ${classification.code} - ${classification.title}
                            """.trimIndent())

                            blockedUploads++
                            dao.insertSyncMetadata(meta.copy(syncStatus = "Failed"))
                            syncDetails.add(
                                SyncItemDetail(
                                    operationUuid = operationUuid,
                                    entityType = entityType,
                                    operationType = operationType,
                                    companyId = canonicalCompanyId,
                                    retryCount = 1,
                                    lastAttemptTimestamp = lastAttemptTs,
                                    lastError = "Blocked: ${uploadResult.reason}",
                                    httpStatus = 403,
                                    serverResponse = "MUTATION_BLOCKED",
                                    classification = classification,
                                    failureReasonDescription = classification.descriptionFa + " (${uploadResult.reason})"
                                )
                            )
                        }
                        is UploadRecordResult.HttpError -> {
                            Log.e(
                                "SYNC_DIAGNOSTIC",
                                "UPLOAD RESULT | entity=${meta.entityType} | id=${meta.entityId} | success=false | code=${uploadResult.httpCode} | body=${uploadResult.body}"
                            )
                            val classification = when (uploadResult.httpCode) {
                                401, 403 -> SyncClassification.D
                                409 -> SyncClassification.E
                                500, 502, 503, 504 -> SyncClassification.B
                                else -> SyncClassification.H
                            }
                            Log.e("SYNC_QUEUE_TRACE", """
                                [SYNC_QUEUE_ITEM]
                                operation UUID: $operationUuid
                                entity/table type: $entityType
                                operation type: $operationType
                                company/workspace ID: $canonicalCompanyId
                                retry count: 1
                                last attempt timestamp: $lastAttemptTs
                                last error: HTTP ${uploadResult.httpCode} ${uploadResult.body}
                                HTTP status: ${uploadResult.httpCode}
                                server response: ${uploadResult.body}
                                classification: ${classification.code} - ${classification.title}
                            """.trimIndent())

                            if (classification == SyncClassification.B) {
                                retryingUploads++
                            } else {
                                failedUploads++
                            }
                            dao.insertSyncMetadata(meta.copy(syncStatus = "Failed"))
                            syncDetails.add(
                                SyncItemDetail(
                                    operationUuid = operationUuid,
                                    entityType = entityType,
                                    operationType = operationType,
                                    companyId = canonicalCompanyId,
                                    retryCount = 1,
                                    lastAttemptTimestamp = lastAttemptTs,
                                    lastError = "HTTP ${uploadResult.httpCode}: ${uploadResult.body}",
                                    httpStatus = uploadResult.httpCode,
                                    serverResponse = uploadResult.body,
                                    classification = classification,
                                    failureReasonDescription = classification.descriptionFa + " (HTTP ${uploadResult.httpCode})"
                                )
                            )
                        }
                        is UploadRecordResult.NetworkError -> {
                            Log.e(
                                "SYNC_DIAGNOSTIC",
                                "UPLOAD RESULT | entity=${meta.entityType} | id=${meta.entityId} | success=false | code=0 | exception=${uploadResult.exception.javaClass.simpleName}: ${uploadResult.exception.message}",
                                uploadResult.exception
                            )
                            val classification = SyncClassification.B
                            val errMsg = uploadResult.exception.message ?: "Network error"
                            Log.e("SYNC_QUEUE_TRACE", """
                                [SYNC_QUEUE_ITEM]
                                operation UUID: $operationUuid
                                entity/table type: $entityType
                                operation type: $operationType
                                company/workspace ID: $canonicalCompanyId
                                retry count: 1
                                last attempt timestamp: $lastAttemptTs
                                last error: $errMsg
                                HTTP status: 0
                                server response: NETWORK_FAILURE
                                classification: ${classification.code} - ${classification.title}
                            """.trimIndent())

                            retryingUploads++
                            dao.insertSyncMetadata(meta.copy(syncStatus = "Failed"))
                            syncDetails.add(
                                SyncItemDetail(
                                    operationUuid = operationUuid,
                                    entityType = entityType,
                                    operationType = operationType,
                                    companyId = canonicalCompanyId,
                                    retryCount = 1,
                                    lastAttemptTimestamp = lastAttemptTs,
                                    lastError = errMsg,
                                    httpStatus = 0,
                                    serverResponse = "NETWORK_FAILURE",
                                    classification = classification,
                                    failureReasonDescription = classification.descriptionFa + ": " + errMsg
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(
                        "SYNC_DIAGNOSTIC",
                        "UPLOAD FAILED | entity=${meta.entityType} | id=${meta.entityId}",
                        e
                    )
                    val classification = SyncClassification.G
                    val errMsg = e.message ?: "Unexpected exception"
                    Log.e("SYNC_QUEUE_TRACE", """
                        [SYNC_QUEUE_ITEM]
                        operation UUID: $operationUuid
                        entity/table type: $entityType
                        operation type: $operationType
                        company/workspace ID: $canonicalCompanyId
                        retry count: 1
                        last attempt timestamp: $lastAttemptTs
                        last error: $errMsg
                        HTTP status: 0
                        server response: EXCEPTION
                        classification: ${classification.code} - ${classification.title}
                    """.trimIndent(), e)

                    failedUploads++
                    dao.insertSyncMetadata(meta.copy(syncStatus = "Failed"))
                    syncDetails.add(
                        SyncItemDetail(
                            operationUuid = operationUuid,
                            entityType = entityType,
                            operationType = operationType,
                            companyId = canonicalCompanyId,
                            retryCount = 1,
                            lastAttemptTimestamp = lastAttemptTs,
                            lastError = errMsg,
                            httpStatus = 0,
                            serverResponse = "EXCEPTION",
                            classification = classification,
                            failureReasonDescription = classification.descriptionFa + ": " + errMsg
                        )
                    )
                }

                if (entityType == "FixedExpenseTemplate") {
                    val finalState = dao.getSyncMetadata(entityType, operationUuid)
                    Log.i("SYNC_FIXED_EXPENSE", "SYNC_FIXED_EXPENSE_QUEUE_STATE entityId=$operationUuid status=${finalState?.syncStatus} isDeleted=${finalState?.deletedStatus}")
                }
            }

            val currentPendingMetadata = dao.getPendingSyncMetadata()
            val remainingPendingCount = currentPendingMetadata.size

            val summary = SyncSummary(
                total = totalPendingCount,
                successful = successfulUploads,
                pending = remainingPendingCount,
                retrying = retryingUploads,
                failed = failedUploads,
                blocked = blockedUploads,
                details = syncDetails
            )
            _syncSummary.value = summary
            
            val authState = WorkspaceManager.getInstance(context).currentAuthToken != null
            val failuresGrouped = syncDetails.groupBy { it.classification.name }.map { "${it.key}: ${it.value.size}" }.joinToString(", ")
            
            Log.i("SYNC_SUMMARY", "SYNC_SUMMARY | totalPending=$totalPendingCount | uploaded=$successfulUploads | failed=${syncDetails.size} | remainingPending=$remainingPendingCount | groups: $failuresGrouped")

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
            val updatedDevice = confirmedDevice.copy(lastSuccessfulSync = System.currentTimeMillis())
            dao.insertConnectedDevice(updatedDevice)
            cloudClient.patchDeviceHeartbeat(companyId, updatedDevice)
            success = true
            Log.i("SYNC", "[SYNC] companyId=$companyId deviceId=$activeDeviceId authUid=$confirmedAuthUid result=SUCCESS")

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
            success = false
            Log.e("SYNC", "[SYNC] companyId=${syncCompanyId ?: "NULL"} deviceId=${syncDeviceId ?: "NULL"} authUid=${syncAuthUid ?: "NULL"} result=FAILED")
        } finally {
            _syncing.value = false
            syncMutex.unlock()
        }
        return@withContext success
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
                    (dao.getDashboardCacheByKey(entityId) ?: dao.getDashboardCacheByUuid(entityId))?.let { SyncSerializer.serialize(entityType, it) }
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
            if (obj != null) {
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
            } else {
                Log.e("SyncEngine", "Deserialized object is null for entity: ${cloudRec.entityType}_${cloudRec.entityId}")
            }
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
            "DashboardCache" -> {
                dao.deleteDashboardCacheByKey(entityId)
                dao.deleteDashboardCacheByUuid(entityId)
            }
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
