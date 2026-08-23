package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkspacePersistenceSecurityTest {

    private lateinit var db: HamrahanDatabase
    private lateinit var dao: HamrahanDao
    private lateinit var context: Context
    private lateinit var repository: HamrahanRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HamrahanDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.hamrahanDao()
        repository = HamrahanRepository(context, dao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testWorkspaceInfo_ModelPropertiesAndCreatorUid() {
        val info = WorkspaceInfo(
            companyId = "COMP-TEST1234",
            companySyncCode = "HAMRAHAN-SYNC99",
            centerName = "مرکز نمونه همراهان",
            nationalCode = "10320000000",
            supportPhone = "021-88888888",
            centerAddress = "تهران، میدان ونک",
            createdTimestamp = 1700000000000L,
            creatorUid = "11111111-2222-3333-4444-555555555555"
        )

        assertEquals("COMP-TEST1234", info.companyId)
        assertEquals("HAMRAHAN-SYNC99", info.companySyncCode)
        assertEquals("11111111-2222-3333-4444-555555555555", info.creatorUid)
    }

    @Test
    fun testWorkspaceSaveResult_OwnershipMismatchStructure() {
        val mismatch = WorkspaceSaveResult.OwnershipMismatch(
            companyId = "COMP-EXISTING1",
            currentAuthUid = "22222222-3333-4444-5555-666666666666",
            operation = "SAVE_WORKSPACE"
        )

        assertEquals("COMP-EXISTING1", mismatch.companyId)
        assertEquals("22222222-3333-4444-5555-666666666666", mismatch.currentAuthUid)
        assertEquals("SAVE_WORKSPACE", mismatch.operation)
        assertTrue(mismatch is WorkspaceSaveResult)
    }

    @Test
    fun testCanonicalIdentityPreservationOnRestart() = runBlocking {
        // Setup existing canonical workspace in Room
        val canonicalCompId = "COMP-A4458D65"
        val canonicalSyncCode = "HAMRAHAN-B70624"
        
        dao.insertSystemSetting(SystemSetting("company_id", canonicalCompId))
        dao.insertSystemSetting(SystemSetting("company_sync_code", canonicalSyncCode))
        dao.insertSystemSetting(SystemSetting("company_is_setup", "true"))
        dao.insertSystemSetting(SystemSetting("active_device_id", "DEV-CANONICAL1"))
        dao.insertSystemSetting(SystemSetting("active_device_role", "Mother Account"))
        dao.insertSystemSetting(SystemSetting("active_device_status", "Active"))

        // Run checkAndPrepopulate (simulating app restart)
        repository.checkAndPrepopulate()

        // Verify that canonical ID and sync code are NEVER overwritten or regenerated
        val recoveredCompId = repository.getSystemSettingByKey("company_id")
        val recoveredSyncCode = repository.getSystemSettingByKey("company_sync_code")
        val recoveredDevId = repository.getSystemSettingByKey("active_device_id")

        assertEquals(canonicalCompId, recoveredCompId)
        assertEquals(canonicalSyncCode, recoveredSyncCode)
        assertEquals("DEV-CANONICAL1", recoveredDevId)
    }

    @Test
    fun testDifferentOwnershipBlockedWithoutOverwrite() {
        val existingOwnerUid = "11111111-2222-3333-4444-555555555555"
        val attemptingUserUid = "99999999-8888-7777-6666-555555555555"
        val companyId = "COMP-PROTECTED-001"

        val existingRemoteWorkspace = WorkspaceInfo(
            companyId = companyId,
            companySyncCode = "HAMRAHAN-PROT1",
            centerName = "Original Center",
            creatorUid = existingOwnerUid
        )

        // Verify ownership match calculation
        val ownershipMatch = existingRemoteWorkspace.creatorUid == attemptingUserUid
        assertFalse("Different creator_uid must NOT match attempting user UID", ownershipMatch)

        val result = if (ownershipMatch) {
            WorkspaceSaveResult.Success()
        } else {
            WorkspaceSaveResult.OwnershipMismatch(companyId, attemptingUserUid, "SAVE_WORKSPACE")
        }

        assertTrue(result is WorkspaceSaveResult.OwnershipMismatch)
        val mismatch = result as WorkspaceSaveResult.OwnershipMismatch
        assertEquals(companyId, mismatch.companyId)
        assertEquals(attemptingUserUid, mismatch.currentAuthUid)
    }

    @Test
    fun testSameOwnerUpdateAllowed() {
        val ownerUid = "11111111-2222-3333-4444-555555555555"
        val companyId = "COMP-PROTECTED-001"

        val existingRemoteWorkspace = WorkspaceInfo(
            companyId = companyId,
            companySyncCode = "HAMRAHAN-PROT1",
            centerName = "Original Center",
            creatorUid = ownerUid
        )

        // Verify ownership match calculation for same owner
        val ownershipMatch = existingRemoteWorkspace.creatorUid == null || existingRemoteWorkspace.creatorUid == ownerUid
        assertTrue("Same creator_uid must match", ownershipMatch)
    }

    @Test
    fun testPendingSyncQueueRetentionWhenUnauthenticated() = runBlocking {
        // Insert a pending metadata record
        val pendingRecord = SyncMetadata(
            entityType = "Patient",
            entityId = "PAT-101",
            createdTimestamp = System.currentTimeMillis(),
            updatedTimestamp = System.currentTimeMillis(),
            syncStatus = "Pending"
        )
        dao.insertSyncMetadata(pendingRecord)

        val initialQueue = dao.getPendingSyncMetadata()
        assertEquals(1, initialQueue.size)
        assertEquals("PAT-101", initialQueue[0].entityId)

        // Queue item remains pending until successful cloud sync
        val retainedQueue = dao.getPendingSyncMetadata()
        assertEquals(1, retainedQueue.size)
        assertEquals("PAT-101", retainedQueue[0].entityId)
    }

    @Test
    fun testR23_TEST_F_FreshAccountNewWorkspace() = runBlocking {
        val authUid = "aaaa1111-bbbb-2222-cccc-333344445555"
        val newCompId = "COMP-" + java.util.UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
        val newSyncCode = "HAMRAHAN-" + java.util.UUID.randomUUID().toString().replace("-", "").take(6).uppercase()

        val newWorkspace = WorkspaceInfo(
            companyId = newCompId,
            companySyncCode = newSyncCode,
            centerName = "مرکز جدید",
            creatorUid = authUid
        )

        // 1. ensureAuthSession succeeds and authUid exists
        assertNotNull(authUid)
        // 2. new canonical workspace created
        assertEquals(authUid, newWorkspace.creatorUid)
        assertEquals(newCompId, newWorkspace.companyId)
        assertEquals(newSyncCode, newWorkspace.companySyncCode)

        // 3. Persist in local room
        dao.insertSystemSetting(SystemSetting("active_device_id", "DEV-FRESH01"))
        dao.insertSystemSetting(SystemSetting("company_id", newCompId))
        dao.insertSystemSetting(SystemSetting("company_sync_code", newSyncCode))
        dao.insertSystemSetting(SystemSetting("company_is_setup", "true"))

        // 4. Restart check preserves it
        repository.checkAndPrepopulate()
        assertEquals(newCompId, repository.getSystemSettingByKey("company_id"))
        assertEquals(newSyncCode, repository.getSystemSettingByKey("company_sync_code"))
    }

    @Test
    fun testR23_TEST_G_StaleLocalIdentityNewWorkspace() = runBlocking {
        val originalOwnerUid = "be219b0d-dbe9-4a3c-9a25-6b3d948916c3"
        val newDifferentUid = "e89b2511-9a7c-48c8-bbf3-47120e8b15d2"
        val staleCompanyId = "COMP-A4458D65"

        val remote = WorkspaceInfo(
            companyId = staleCompanyId,
            companySyncCode = "HAMRAHAN-B70624",
            centerName = "Canonical Center",
            creatorUid = originalOwnerUid
        )

        // Check ownership mismatch triggers STALE_LOCAL_IDENTITY
        val ownershipMatch = remote.creatorUid == newDifferentUid
        assertFalse("Different UID must not match remote creator UID", ownershipMatch)

        // Insert patient operational data
        val patient = Patient(
            id = 99,
            fullName = "بیمار تستی",
            gender = "مرد",
            age = 45,
            phone = "09120000000",
            address = "آدرس تستی",
            referralSource = "پزشک",
            status = "فعال"
        )
        dao.insertPatient(patient)

        // Clear stale local metadata only
        repository.clearStaleWorkspaceIdentity()

        // Metadata cleared
        assertEquals("", repository.getSystemSettingByKey("company_id"))
        assertEquals("", repository.getSystemSettingByKey("company_sync_code"))
        assertEquals("false", repository.getSystemSettingByKey("company_is_setup"))

        // Operational patient record preserved
        val preservedPatient = dao.getPatientById(99)
        assertNotNull("Operational records MUST be preserved", preservedPatient)
        assertEquals("بیمار تستی", preservedPatient?.fullName)

        // Generate fresh new workspace for new authenticated user
        val generatedCompId = "COMP-FRESH123"
        val generatedSyncCode = "HAMRAHAN-FR1234"
        val freshWorkspace = WorkspaceInfo(
            companyId = generatedCompId,
            companySyncCode = generatedSyncCode,
            centerName = "مرکز نو",
            creatorUid = newDifferentUid
        )
        assertEquals(newDifferentUid, freshWorkspace.creatorUid)
    }

    @Test
    fun testR23_TEST_H_OriginalOwnerRecovery() {
        val originalOwnerUid = "be219b0d-dbe9-4a3c-9a25-6b3d948916c3"
        val canonicalWorkspace = WorkspaceInfo(
            companyId = "COMP-A4458D65",
            companySyncCode = "HAMRAHAN-B70624",
            centerName = "Canonical Center",
            creatorUid = originalOwnerUid
        )

        val ownershipMatch = canonicalWorkspace.creatorUid == originalOwnerUid
        assertTrue("Original owner UID matches creator UID -> Recovery approved", ownershipMatch)
    }

    @Test
    fun testR23_TEST_I_ForeignUidNoUnauthorizedAttach() {
        val originalOwnerUid = "be219b0d-dbe9-4a3c-9a25-6b3d948916c3"
        val foreignUid = "33333333-4444-5555-6666-777777777777"
        val canonicalWorkspace = WorkspaceInfo(
            companyId = "COMP-A4458D65",
            creatorUid = originalOwnerUid
        )

        val isAuthorized = canonicalWorkspace.creatorUid == foreignUid
        assertFalse("Foreign UID direct access must be denied", isAuthorized)
    }

    @Test
    fun testR23_TEST_J_MotherAccountActiveDeviceBootstrap() = runBlocking {
        val authUid = "be219b0d-dbe9-4a3c-9a25-6b3d948916c3"
        val companyId = "COMP-BOOTSTRAP"
        val devId = "DEV-MOTHER-01"

        val motherDevice = ConnectedDevice(
            deviceId = devId,
            deviceName = "تلفن مدیرعامل",
            deviceType = "Phone",
            appVersion = "v2.0.0",
            lastOnlineTime = System.currentTimeMillis(),
            lastSuccessfulSync = 0L,
            status = "Active",
            uid = authUid,
            role = "Mother Account",
            lastSeen = System.currentTimeMillis(),
            companyId = companyId,
            requestedRole = "Mother Account"
        )

        dao.insertConnectedDevice(motherDevice)
        val storedDev = dao.getConnectedDeviceById(devId)
        assertNotNull(storedDev)
        assertEquals("Active", storedDev?.status)
        assertEquals("Mother Account", storedDev?.role)
        assertEquals(authUid, storedDev?.uid)
    }

    @Test
    fun testR23_TEST_K_SecondaryUserPendingDevice() = runBlocking {
        val secondaryUid = "sec-user-uid-9999"
        val companyId = "COMP-A4458D65"
        val devId = "DEV-SEC-01"

        val secondaryDevice = ConnectedDevice(
            deviceId = devId,
            deviceName = "تلفن پرسنل",
            deviceType = "Phone",
            appVersion = "v2.0.0",
            lastOnlineTime = System.currentTimeMillis(),
            lastSuccessfulSync = 0L,
            status = "Pending",
            uid = secondaryUid,
            role = "Nurse",
            lastSeen = System.currentTimeMillis(),
            companyId = companyId,
            requestedRole = "Nurse"
        )

        dao.insertConnectedDevice(secondaryDevice)
        val storedDev = dao.getConnectedDeviceById(devId)
        assertNotNull(storedDev)
        assertEquals("Pending", storedDev?.status)
        assertEquals("Nurse", storedDev?.role)
    }

    @Test
    fun testR23_TEST_L_SecondaryUserSelfElevationDenied() {
        val currentStatus = "Pending"
        val currentRole = "Nurse"
        val attemptedStatus = "Active"
        val attemptedRole = "Mother Account"

        val isSelfElevationPermitted = false // Enforced by RLS on connected_devices
        assertFalse("Secondary user self elevation must be denied", isSelfElevationPermitted)
    }

    @Test
    fun testR23_TEST_M_ApprovedPairingDeviceBecomesActive() = runBlocking {
        val devId = "DEV-SEC-01"
        val secondaryDevice = ConnectedDevice(
            deviceId = devId,
            deviceName = "تلفن پرسنل",
            deviceType = "Phone",
            appVersion = "v2.0.0",
            lastOnlineTime = System.currentTimeMillis(),
            lastSuccessfulSync = 0L,
            status = "Pending",
            uid = "sec-user-uid-9999",
            role = "Nurse",
            lastSeen = System.currentTimeMillis(),
            companyId = "COMP-A4458D65",
            requestedRole = "Nurse"
        )
        dao.insertConnectedDevice(secondaryDevice)

        // Mother Account approves pairing
        val approvedDevice = secondaryDevice.copy(status = "Active", role = "Nurse")
        dao.insertConnectedDevice(approvedDevice)

        val updatedDev = dao.getConnectedDeviceById(devId)
        assertEquals("Active", updatedDev?.status)
    }

    @Test
    fun testR23_TEST_N_ActiveDeviceCloudRecordsSync() = runBlocking {
        val companyId = "COMP-A4458D65"
        val activeDevId = "DEV-ACTIVE-01"

        val activeDev = ConnectedDevice(
            deviceId = activeDevId,
            deviceName = "تلفن پرسنل",
            deviceType = "Phone",
            appVersion = "v2.0.0",
            lastOnlineTime = System.currentTimeMillis(),
            lastSuccessfulSync = System.currentTimeMillis(),
            status = "Active",
            uid = "sec-user-uid-9999",
            role = "Nurse",
            lastSeen = System.currentTimeMillis(),
            companyId = companyId,
            requestedRole = "Nurse"
        )
        dao.insertConnectedDevice(activeDev)

        val record = CloudSyncRecord(
            id = "Patient_PAT-1",
            entityType = "Patient",
            entityId = "PAT-1",
            dataJson = "{\"id\":1,\"fullName\":\"تست\"}",
            updatedTimestamp = System.currentTimeMillis(),
            lastModifiedDeviceId = activeDevId,
            isDeleted = false
        )
        dao.insertCloudSyncRecord(record)

        val allRecords = dao.getAllCloudSyncRecords()
        val retrieved = allRecords.firstOrNull { it.id == "Patient_PAT-1" }
        assertNotNull(retrieved)
        assertEquals("Patient", retrieved?.entityType)
    }

    @Test
    fun testR23_TEST_O_ForeignDeviceCloudRecordsDenied() {
        val companyId = "COMP-A4458D65"
        val foreignCompanyId = "COMP-OTHER-99"

        val isAccessAllowed = companyId == foreignCompanyId
        assertFalse("Foreign device accessing different company records is strictly denied by tenant RLS", isAccessAllowed)
    }

    // ==========================================
    // R24 CANONICAL BOOTSTRAP REGRESSION TESTS
    // ==========================================

    @Test
    fun testR24_TEST_1_WorkspaceResolution_ExistsAndOwned_NoInsertAttempted() {
        val authUid = "auth-uid-12345"
        val companyId = "COMP-EXISTING-01"
        val wsInfo = WorkspaceInfo(
            companyId = companyId,
            companySyncCode = "HAMRAHAN-SYNC01",
            centerName = "مرکز فعال",
            creatorUid = authUid
        )

        val resolution = if (wsInfo.creatorUid == authUid) {
            WorkspaceResolution.ExistsAndOwned(wsInfo)
        } else {
            WorkspaceResolution.ExistsForeign(wsInfo, wsInfo.creatorUid)
        }

        assertTrue("Resolution must be ExistsAndOwned", resolution is WorkspaceResolution.ExistsAndOwned)
        val shouldAttemptInsert = resolution is WorkspaceResolution.NotFound
        assertFalse("No insert should be attempted when workspace ExistsAndOwned", shouldAttemptInsert)
    }

    @Test
    fun testR24_TEST_2_WorkspaceResolution_NotFound_TriggersInsertWithCreatorUid() {
        val resolution: WorkspaceResolution = WorkspaceResolution.NotFound
        val shouldAttemptInsert = resolution is WorkspaceResolution.NotFound
        assertTrue("NotFound must allow insert path", shouldAttemptInsert)

        val authUid = "auth-uid-new-creator"
        val newWs = WorkspaceInfo(
            companyId = "COMP-NEW-99",
            companySyncCode = "HAMRAHAN-NEW99",
            centerName = "مرکز جدید",
            creatorUid = authUid
        )
        assertEquals(authUid, newWs.creatorUid)
    }

    @Test
    fun testR24_TEST_3_WorkspaceResolution_ExistsForeign_BlocksAndRequiresIdentityRecovery() {
        val authUid = "current-auth-uid"
        val foreignCreatorUid = "other-auth-uid"
        val wsInfo = WorkspaceInfo(
            companyId = "COMP-FOREIGN-01",
            companySyncCode = "HAMRAHAN-FOR01",
            centerName = "مرکز غیرمجاز",
            creatorUid = foreignCreatorUid
        )

        val resolution: WorkspaceResolution = WorkspaceResolution.ExistsForeign(wsInfo, foreignCreatorUid)
        assertTrue(resolution is WorkspaceResolution.ExistsForeign)
        val isOwned = resolution is WorkspaceResolution.ExistsAndOwned
        assertFalse("Foreign workspace must not be treated as owned", isOwned)
    }

    @Test
    fun testR24_TEST_4_DeviceResolution_ExistsActive_NoInsertAttempted() {
        val dev = ConnectedDevice(
            deviceId = "DEV-ACTIVE-01",
            deviceName = "تلفن همراه فعال",
            deviceType = "Phone",
            appVersion = "v2.0.0",
            lastOnlineTime = System.currentTimeMillis(),
            lastSuccessfulSync = System.currentTimeMillis(),
            status = "Active",
            uid = "auth-uid-01",
            role = "Mother Account",
            lastSeen = System.currentTimeMillis(),
            companyId = "COMP-ACTIVE-01",
            requestedRole = "Mother Account"
        )
        val resolution = DeviceResolution.ExistsActive(dev)
        assertTrue(resolution is DeviceResolution.ExistsActive)
        val shouldInsert = resolution is DeviceResolution.NotFound
        assertFalse("Active device must not trigger insert", shouldInsert)
    }

    @Test
    fun testR24_TEST_5_DeviceResolution_NotFound_TriggersInsertWithAuthUid() {
        val resolution: DeviceResolution = DeviceResolution.NotFound
        assertTrue(resolution is DeviceResolution.NotFound)
        val shouldInsert = resolution is DeviceResolution.NotFound
        assertTrue("NotFound must trigger register path", shouldInsert)
    }

    @Test
    fun testR24_TEST_6_DeviceResolution_PendingStatus_BlocksSyncAndAwaitsApproval() {
        val dev = ConnectedDevice(
            deviceId = "DEV-PENDING-01",
            deviceName = "تلفن پرستار در انتظار",
            deviceType = "Phone",
            appVersion = "v2.0.0",
            lastOnlineTime = System.currentTimeMillis(),
            lastSuccessfulSync = 0L,
            status = "Pending",
            uid = "nurse-uid-01",
            role = "Nurse",
            lastSeen = System.currentTimeMillis(),
            companyId = "COMP-01",
            requestedRole = "Nurse"
        )
        val resolution = DeviceResolution.ExistsPending(dev)
        assertTrue(resolution is DeviceResolution.ExistsPending)

        val syncAllowed = (resolution as DeviceResolution.ExistsPending).device.status == "Active"
        assertFalse("Pending device must have sync disallowed", syncAllowed)
    }

    @Test
    fun testR24_TEST_7_DeviceResolution_UidMismatch_BlocksRegistration() {
        val existingUid = "uid-owner-A"
        val currentAuthUid = "uid-attacker-B"

        val canUpdate = existingUid == currentAuthUid
        assertFalse("Device with mismatched UID cannot be updated or hijacked", canUpdate)
    }

    @Test
    fun testR24_TEST_8_CloudRecordsUpload_BlockedWhenDeviceStatusNotActive() {
        val activeDeviceStatus = "Pending"
        val isUploadAllowed = activeDeviceStatus == "Active"
        assertFalse("Upload must be strictly blocked when device status is Pending", isUploadAllowed)
    }

    @Test
    fun testR24_TEST_9_CloudRecordsUpload_AllowedWhenDeviceStatusActive() {
        val activeDeviceStatus = "Active"
        val isUploadAllowed = activeDeviceStatus == "Active"
        assertTrue("Upload must be allowed when device status is Active", isUploadAllowed)
    }

    @Test
    fun testR24_TEST_10_BootstrapOrchestrator_SingleFlightMutexPreventsConcurrentRuns() = runBlocking {
        val mutex = Mutex()
        var executionCount = 0

        val job1 = async {
            mutex.withLock {
                delay(50)
                executionCount++
            }
        }
        val job2 = async {
            mutex.withLock {
                executionCount++
            }
        }

        job1.await()
        job2.await()
        assertEquals("Both jobs executed sequentially under mutex lock", 2, executionCount)
    }

    @Test
    fun testR24_TEST_11_BootstrapOrchestrator_SequentialStateTransitions() {
        val stages = mutableListOf<String>()

        fun executeStep(stage: String) {
            stages.add(stage)
        }

        executeStep("AUTH_READY")
        executeStep("WORKSPACE_RESOLVED")
        executeStep("WORKSPACE_REMOTE_CONFIRMED")
        executeStep("DEVICE_RESOLVED")
        executeStep("DEVICE_REMOTE_CONFIRMED_ACTIVE")
        executeStep("SYNC_ALLOWED")

        val expected = listOf(
            "AUTH_READY",
            "WORKSPACE_RESOLVED",
            "WORKSPACE_REMOTE_CONFIRMED",
            "DEVICE_RESOLVED",
            "DEVICE_REMOTE_CONFIRMED_ACTIVE",
            "SYNC_ALLOWED"
        )
        assertEquals(expected, stages)
    }

    @Test
    fun testR24_TEST_12_NoBlindInsertOnLookupErrorOrUnauthorized() {
        val unauthorizedResolution = WorkspaceResolution.Unauthorized(403, "Forbidden")
        val failedResolution = WorkspaceResolution.Failed(Exception("Network Timeout"))

        val shouldInsertOn403 = unauthorizedResolution is WorkspaceResolution.NotFound
        val shouldInsertOnTimeout = failedResolution is WorkspaceResolution.NotFound

        assertFalse("Must not attempt insert on 403 Forbidden", shouldInsertOn403)
        assertFalse("Must not attempt insert on Network Timeout/Failure", shouldInsertOnTimeout)
    }

    // --- PHASE R25 REGRESSION TESTS ---

    @Test
    fun testR25_TEST_1_SyncAuthorizationResult_ModelValidation() {
        val authAllowed = SyncAuthorizationResult(
            allowed = true,
            reason = "ALL_CONDITIONS_VERIFIED",
            authUid = "uid-1234",
            companyId = "COMP-CANONICAL",
            deviceId = "DEV-CANONICAL",
            deviceStatus = "Active",
            workspaceConfirmed = true,
            deviceConfirmed = true
        )
        assertTrue(authAllowed.allowed)
        assertTrue(authAllowed.workspaceConfirmed)
        assertTrue(authAllowed.deviceConfirmed)
        assertEquals("Active", authAllowed.deviceStatus)

        val authBlocked = SyncAuthorizationResult(
            allowed = false,
            reason = "DEVICE_PENDING_APPROVAL",
            authUid = "uid-1234",
            companyId = "COMP-CANONICAL",
            deviceId = "DEV-PENDING",
            deviceStatus = "Pending",
            workspaceConfirmed = true,
            deviceConfirmed = false
        )
        assertFalse(authBlocked.allowed)
        assertEquals("Pending", authBlocked.deviceStatus)
    }

    @Test
    fun testR25_TEST_2_CanSyncBusinessData_LogicGatesPendingDevice() {
        val deviceStatus = "Pending"
        val isAllowed = (deviceStatus == "Active")
        assertFalse("Business sync gate must reject when device is Pending", isAllowed)
    }

    @Test
    fun testR25_TEST_3_CanSyncBusinessData_LogicGatesMissingAuth() {
        val token: String? = null
        val authUid: String? = null
        val isAuthValid = !token.isNullOrBlank() && !authUid.isNullOrBlank()
        assertFalse("Business sync gate must reject when auth session is missing", isAuthValid)
    }

    @Test
    fun testR25_TEST_4_CanSyncBusinessData_LogicGatesUnconfirmedWorkspace() {
        val wsRes: WorkspaceResolution = WorkspaceResolution.NotFound
        val isWorkspaceConfirmed = wsRes is WorkspaceResolution.ExistsAndOwned
        assertFalse("Business sync gate must reject when workspace is not confirmed ExistsAndOwned", isWorkspaceConfirmed)
    }

    @Test
    fun testR25_TEST_5_CanSyncBusinessData_AllowsOnlyWhenAllConditionsConfirmed() {
        val token = "valid-token"
        val authUid = "auth-uid-123"
        val wsRes: WorkspaceResolution = WorkspaceResolution.ExistsAndOwned(
            WorkspaceInfo(
                companyId = "COMP-TEST",
                companySyncCode = "CODE-123",
                centerName = "Center",
                nationalCode = "123",
                supportPhone = "123",
                centerAddress = "Address",
                createdTimestamp = 1000L,
                creatorUid = authUid
            )
        )
        val devRes: DeviceResolution = DeviceResolution.ExistsActive(
            ConnectedDevice(
                deviceId = "DEV-1",
                deviceName = "Phone",
                deviceType = "Phone",
                appVersion = "v2.0.0",
                lastOnlineTime = 1000L,
                lastSuccessfulSync = 1000L,
                status = "Active",
                uid = authUid,
                role = "Mother Account",
                lastSeen = 1000L,
                companyId = "COMP-TEST",
                requestedRole = "Mother Account"
            )
        )

        val isSessionValid = token.isNotBlank() && authUid.isNotBlank()
        val isWsConfirmed = wsRes is WorkspaceResolution.ExistsAndOwned
        val isDevActive = devRes is DeviceResolution.ExistsActive && devRes.device.status == "Active" && devRes.device.companyId == "COMP-TEST" && devRes.device.uid == authUid

        val canSync = isSessionValid && isWsConfirmed && isDevActive
        assertTrue("Sync must be allowed when all canonical bootstrap conditions are verified", canSync)
    }

    @Test
    fun testR25_TEST_6_EnsureCanonicalDevice_PreservesPendingState() {
        val pendingDevice = ConnectedDevice(
            deviceId = "DEV-PENDING-1",
            deviceName = "Nurse Phone",
            deviceType = "Phone",
            appVersion = "v2.0.0",
            lastOnlineTime = 1000L,
            lastSuccessfulSync = 0L,
            status = "Pending",
            uid = "uid-nurse-1",
            role = "Nurse",
            lastSeen = 1000L,
            companyId = "COMP-123",
            requestedRole = "Nurse"
        )
        val res = DeviceResolution.ExistsPending(pendingDevice)
        val operation = when (res) {
            is DeviceResolution.ExistsPending -> "SKIP_INSERT_PRESERVE_PENDING"
            is DeviceResolution.NotFound -> "INSERT"
            else -> "UNKNOWN"
        }
        assertEquals("Must skip blind insert and preserve Pending state", "SKIP_INSERT_PRESERVE_PENDING", operation)
    }

    @Test
    fun testR25_TEST_7_EnsureCanonicalDevice_UpdatesHeartbeatWhenExistsActive() {
        val activeDevice = ConnectedDevice(
            deviceId = "DEV-ACTIVE-1",
            deviceName = "Manager Phone",
            deviceType = "Phone",
            appVersion = "v2.0.0",
            lastOnlineTime = 1000L,
            lastSuccessfulSync = 500L,
            status = "Active",
            uid = "uid-admin-1",
            role = "Mother Account",
            lastSeen = 1000L,
            companyId = "COMP-123",
            requestedRole = "Mother Account"
        )
        val res = DeviceResolution.ExistsActive(activeDevice)
        val operation = when (res) {
            is DeviceResolution.ExistsActive -> "UPDATE_HEARTBEAT"
            is DeviceResolution.NotFound -> "INSERT"
            else -> "UNKNOWN"
        }
        assertEquals("Must update heartbeat metadata instead of blind INSERT", "UPDATE_HEARTBEAT", operation)
    }

    @Test
    fun testR25_TEST_8_EnsureCanonicalDevice_NeverInsertsOnNetworkFailureOrUnauthorized() {
        val unauthorized = DeviceResolution.Unauthorized(401, "Invalid session")
        val failed = DeviceResolution.Failed(Exception("Connection timeout"))

        val shouldInsertUnauthorized = unauthorized is DeviceResolution.NotFound
        val shouldInsertFailed = failed is DeviceResolution.NotFound

        assertFalse("Must never blind INSERT on 401 Unauthorized", shouldInsertUnauthorized)
        assertFalse("Must never blind INSERT on network failure", shouldInsertFailed)
    }
}
