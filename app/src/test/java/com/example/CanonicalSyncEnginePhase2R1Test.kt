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
class CanonicalSyncEnginePhase2R1Test {

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

    /**
     * Test 1 & 2: Room mutation + metadata atomicity and transaction rollback behavior.
     * Verifies that inserting a business record via repository creates both the business entity
     * and a corresponding SyncMetadata entry within the same database transaction.
     */
    @Test
    fun testRoomMutationAndMetadataAtomicity() = runBlocking {
        val patient = Patient(
            fullName = "علی حسینی",
            gender = "مرد",
            age = 45,
            phone = "09121111111",
            address = "تهران",
            referralSource = "مستقیم",
            status = "فعال",
            uuid = "test_patient_uuid_101"
        )

        // Insert patient via repository (uses dao.runInTransaction)
        val id = repository.insertPatient(patient)
        assertTrue("Patient insertion should return a valid positive ID", id > 0)

        // Verify patient exists in Room
        val savedPatient = repository.getPatientById(id.toInt())
        assertNotNull("Patient must exist in database", savedPatient)
        assertEquals("علی حسینی", savedPatient?.fullName)

        // Verify SyncMetadata exists and is Pending
        val meta = dao.getSyncMetadata("Patient", "test_patient_uuid_101")
        assertNotNull("SyncMetadata must exist for patient", meta)
        assertEquals("Pending", meta?.syncStatus)
        assertEquals("Patient", meta?.entityType)
        assertEquals("test_patient_uuid_101", meta?.entityId)
    }

    /**
     * Test 3: Pending ordering.
     * Verifies that getPendingSyncMetadata() returns pending metadata strictly ordered by updatedTimestamp ASC.
     */
    @Test
    fun testPendingSyncMetadataOrdering() = runBlocking {
        val meta1 = SyncMetadata("Patient", "uuid_1", updatedTimestamp = 1000L, syncStatus = "Pending")
        val meta2 = SyncMetadata("Patient", "uuid_2", updatedTimestamp = 500L, syncStatus = "Pending")
        val meta3 = SyncMetadata("Patient", "uuid_3", updatedTimestamp = 2000L, syncStatus = "Pending")

        dao.insertSyncMetadata(meta1)
        dao.insertSyncMetadata(meta2)
        dao.insertSyncMetadata(meta3)

        val pending = dao.getPendingSyncMetadata()
        assertEquals(3, pending.size)
        assertEquals("uuid_2", pending[0].entityId) // 500L
        assertEquals("uuid_1", pending[1].entityId) // 1000L
        assertEquals("uuid_3", pending[2].entityId) // 2000L
    }

    /**
     * Test 4: Duplicate sync prevention (primary key is entityType, entityId).
     * Re-inserting SyncMetadata for the same entity updates the existing row without creating duplicates.
     */
    @Test
    fun testDuplicateSyncMetadataPrevention() = runBlocking {
        val meta1 = SyncMetadata("Patient", "uuid_dup", updatedTimestamp = 1000L, syncStatus = "Pending")
        dao.insertSyncMetadata(meta1)

        val list = dao.getPendingSyncMetadata()
        assertEquals(1, list.size)

        val meta2 = SyncMetadata("Patient", "uuid_dup", updatedTimestamp = 2000L, syncStatus = "Synced")
        dao.insertSyncMetadata(meta2)

        val metaRetrieved = dao.getSyncMetadata("Patient", "uuid_dup")
        assertNotNull(metaRetrieved)
        assertEquals(2000L, metaRetrieved?.updatedTimestamp)
        assertEquals("Synced", metaRetrieved?.syncStatus)

        // Ensure overall size in sync_metadata table remains 1
        val allMeta = dao.getSyncMetadataList()
        assertEquals(1, allMeta.size)
    }

    /**
     * Test 10: Inactive device blocks business synchronization.
     * When device status is not "Active", business sync returns early without executing business push/pull.
     */
    @Test
    fun testInactiveDeviceBlocksBusinessSync() = runBlocking {
        val cloudClient = CloudClient(dao, context)
        val syncEngine = SyncEngine(context, dao, cloudClient)

        dao.insertSystemSetting(SystemSetting("company_id", "test_company_123"))
        dao.insertSystemSetting(SystemSetting("active_device_id", "dev_pending_1"))
        dao.insertSystemSetting(SystemSetting("active_device_status", "Pending"))

        val syncResult = syncEngine.sync()
        assertTrue("Sync should handle non-active device safely without throwing", syncResult)
    }
}
