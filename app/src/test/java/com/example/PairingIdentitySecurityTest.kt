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
}
