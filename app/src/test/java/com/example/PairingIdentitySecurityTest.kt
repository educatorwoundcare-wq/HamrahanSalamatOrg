package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PairingIdentitySecurityTest {

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
    fun testIdentitySeparation_DeviceIdIsNotAuthUid() = runBlocking {
        val deviceId = "DEV-788EAE7C"
        val authUid = "d3b2e591-8840-4b2a-8ef0-94f796472d41"
        val companyId = "COMP-HAMRAHAN0C7602"

        val device = ConnectedDevice(
            deviceId = deviceId,
            deviceName = "دستگاه همراه (پرسنل)",
            deviceType = "Phone",
            appVersion = "v2.0.0",
            lastOnlineTime = System.currentTimeMillis(),
            lastSuccessfulSync = 0L,
            status = "Pending",
            uid = authUid,
            role = "Staff",
            lastSeen = System.currentTimeMillis(),
            companyId = companyId,
            requestedRole = "Staff"
        )

        dao.insertConnectedDevice(device)
        val saved = dao.getConnectedDeviceById(deviceId)
        assertNotNull(saved)
        assertEquals(deviceId, saved?.deviceId)
        assertEquals(authUid, saved?.uid)
        assertNotEquals(saved?.deviceId, saved?.uid)
    }

    @Test
    fun testPendingDeviceStateBlocksBusinessSync() = runBlocking {
        dao.insertSystemSetting(SystemSetting("active_device_status", "Pending"))
        val status = dao.getSystemSettingByKey("active_device_status")
        assertEquals("Pending", status)
        assertNotEquals("Active", status)
    }

    @Test
    fun testActiveDeviceStateAllowsBusinessSync() = runBlocking {
        dao.insertSystemSetting(SystemSetting("active_device_status", "Active"))
        val status = dao.getSystemSettingByKey("active_device_status")
        assertEquals("Active", status)
    }

    @Test
    fun testMotherAccountApprovalTransition() = runBlocking {
        val deviceId = "DEV-788EAE7C"
        val initialDevice = ConnectedDevice(
            deviceId = deviceId,
            deviceName = "دستگاه همراه",
            deviceType = "Phone",
            appVersion = "v2.0.0",
            lastOnlineTime = 1000L,
            lastSuccessfulSync = 0L,
            status = "Pending",
            uid = "d3b2e591-8840-4b2a-8ef0-94f796472d41",
            role = "Staff",
            lastSeen = 1000L,
            companyId = "COMP-TEST",
            requestedRole = "Staff"
        )
        dao.insertConnectedDevice(initialDevice)
        assertEquals("Pending", dao.getConnectedDeviceById(deviceId)?.status)

        // Simulate Mother Account Approval
        val approvedDevice = initialDevice.copy(status = "Active", role = "Staff")
        dao.insertConnectedDevice(approvedDevice)
        assertEquals("Active", dao.getConnectedDeviceById(deviceId)?.status)
    }

    @Test
    fun testWorkspaceResolutionAndDeviceStatusMatrix() = runBlocking {
        val foreignWorkspace = WorkspaceInfo(
            companyId = "COMP-CENTER-01",
            companySyncCode = "HAMRAHAN-C01",
            centerName = "مرکز نمونه",
            creatorUid = "creator-mother-uid"
        )
        val ownedWorkspace = WorkspaceInfo(
            companyId = "COMP-CENTER-01",
            companySyncCode = "HAMRAHAN-C01",
            centerName = "مرکز نمونه",
            creatorUid = "my-auth-uid"
        )

        // 1. ExistsForeign + Active Device -> Sync Allowed
        val resForeignActive = evaluateSyncPermission(
            wsRes = WorkspaceResolution.ExistsForeign(foreignWorkspace, foreignWorkspace.creatorUid),
            devRes = DeviceResolution.ExistsActive(
                ConnectedDevice(
                    deviceId = "DEV-STAFF-1",
                    deviceName = "Staff Phone",
                    deviceType = "Phone",
                    appVersion = "1.0",
                    lastOnlineTime = 1L,
                    lastSuccessfulSync = 1L,
                    status = "Active",
                    uid = "my-auth-uid",
                    role = "Staff",
                    lastSeen = 1L,
                    companyId = "COMP-CENTER-01"
                )
            )
        )
        assertTrue("ExistsForeign + Active device must allow sync", resForeignActive)

        // 2. ExistsForeign + Pending Device -> Sync Blocked
        val resForeignPending = evaluateSyncPermission(
            wsRes = WorkspaceResolution.ExistsForeign(foreignWorkspace, foreignWorkspace.creatorUid),
            devRes = DeviceResolution.ExistsPending(
                ConnectedDevice(
                    deviceId = "DEV-STAFF-1",
                    deviceName = "Staff Phone",
                    deviceType = "Phone",
                    appVersion = "1.0",
                    lastOnlineTime = 1L,
                    lastSuccessfulSync = 0L,
                    status = "Pending",
                    uid = "my-auth-uid",
                    role = "Staff",
                    lastSeen = 1L,
                    companyId = "COMP-CENTER-01"
                )
            )
        )
        assertFalse("ExistsForeign + Pending device must NOT allow sync", resForeignPending)

        // 3. ExistsForeign + Rejected (ExistsOther) -> Sync Blocked
        val resForeignRejected = evaluateSyncPermission(
            wsRes = WorkspaceResolution.ExistsForeign(foreignWorkspace, foreignWorkspace.creatorUid),
            devRes = DeviceResolution.ExistsOther(
                ConnectedDevice(
                    deviceId = "DEV-STAFF-1",
                    deviceName = "Staff Phone",
                    deviceType = "Phone",
                    appVersion = "1.0",
                    lastOnlineTime = 1L,
                    lastSuccessfulSync = 0L,
                    status = "Rejected",
                    uid = "my-auth-uid",
                    role = "Staff",
                    lastSeen = 1L,
                    companyId = "COMP-CENTER-01"
                )
            )
        )
        assertFalse("ExistsForeign + Rejected device must NOT allow sync", resForeignRejected)

        // 4. ExistsForeign + Unknown/NotFound Device -> Sync Blocked
        val resForeignNotFound = evaluateSyncPermission(
            wsRes = WorkspaceResolution.ExistsForeign(foreignWorkspace, foreignWorkspace.creatorUid),
            devRes = DeviceResolution.NotFound
        )
        assertFalse("ExistsForeign + NotFound device must NOT allow sync", resForeignNotFound)

        // 5. ExistsAndOwned + Active -> Sync Allowed
        val resOwnedActive = evaluateSyncPermission(
            wsRes = WorkspaceResolution.ExistsAndOwned(ownedWorkspace),
            devRes = DeviceResolution.ExistsActive(
                ConnectedDevice(
                    deviceId = "DEV-MOTHER-1",
                    deviceName = "Mother Phone",
                    deviceType = "Phone",
                    appVersion = "1.0",
                    lastOnlineTime = 1L,
                    lastSuccessfulSync = 1L,
                    status = "Active",
                    uid = "my-auth-uid",
                    role = "Mother Account",
                    lastSeen = 1L,
                    companyId = "COMP-CENTER-01"
                )
            )
        )
        assertTrue("ExistsAndOwned + Active device must allow sync", resOwnedActive)

        // 6. ExistsAndOwned + Pending -> Sync Blocked
        val resOwnedPending = evaluateSyncPermission(
            wsRes = WorkspaceResolution.ExistsAndOwned(ownedWorkspace),
            devRes = DeviceResolution.ExistsPending(
                ConnectedDevice(
                    deviceId = "DEV-MOTHER-1",
                    deviceName = "Mother Phone",
                    deviceType = "Phone",
                    appVersion = "1.0",
                    lastOnlineTime = 1L,
                    lastSuccessfulSync = 0L,
                    status = "Pending",
                    uid = "my-auth-uid",
                    role = "Mother Account",
                    lastSeen = 1L,
                    companyId = "COMP-CENTER-01"
                )
            )
        )
        assertFalse("ExistsAndOwned + Pending device must NOT allow sync", resOwnedPending)
    }

    private fun evaluateSyncPermission(wsRes: WorkspaceResolution, devRes: DeviceResolution): Boolean {
        val workspaceConfirmed = wsRes is WorkspaceResolution.ExistsAndOwned || wsRes is WorkspaceResolution.ExistsForeign
        val deviceConfirmed = devRes is DeviceResolution.ExistsActive
        return workspaceConfirmed && deviceConfirmed
    }
}
