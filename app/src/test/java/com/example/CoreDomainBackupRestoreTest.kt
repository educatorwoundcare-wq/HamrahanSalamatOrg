package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoreDomainBackupRestoreTest {

    private lateinit var db: HamrahanDatabase
    private lateinit var dao: HamrahanDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, HamrahanDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.hamrahanDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testBackupExportContainsNewEntities() = runBlocking {
        // Insert new entities
        val categoryUuid = UUID.randomUUID().toString()
        dao.insertExpenseCategory(ExpenseCategory(name = "تجهیزات پزشکی", isSystemDefault = false, uuid = categoryUuid))

        val templateUuid = UUID.randomUUID().toString()
        dao.insertFixedExpenseTemplate(FixedExpenseTemplate(title = "اجاره مطب", monthlyAmount = 15000000.0, paymentDay = 1, category = "تجهیزات پزشکی", uuid = templateUuid))

        val referralUuid = UUID.randomUUID().toString()
        val refId = dao.insertReferral(Referral(name = "دکتر رضا حسینی", type = "پزشک", commissionPercentage = 10.0, uuid = referralUuid)).toInt()

        val employeeUuid = UUID.randomUUID().toString()
        val empId = dao.insertEmployee(Employee(fullName = "سارا محمدی", nationalId = "1234567890", phone = "09123456789", profession = "پرستار", position = "پرستار بخش", skill = "ICU", employmentType = "تمام وقت", commissionModel = "درصدی", commissionValue = 50.0, bankInfo = "IR123", status = "فعال", uuid = employeeUuid)).toInt()

        val settlementUuid = UUID.randomUUID().toString()
        dao.insertCommissionSettlement(CommissionSettlement(employeeId = empId, amount = 500000.0, periodStart = 1000L, periodEnd = 2000L, uuid = settlementUuid))

        val patientUuid = UUID.randomUUID().toString()
        val patId = dao.insertPatient(Patient(fullName = "علی علوی", gender = "مرد", age = 40, phone = "09120000000", address = "تهران", referralSource = "دکتر حسینی", referralId = refId, status = "فعال", uuid = patientUuid)).toInt()

        val serviceUuid = UUID.randomUUID().toString()
        val svcId = dao.insertService(Service(name = "پانسمان", category = "پرستاری", sellingPrice = 200000.0, durationMinutes = 30, uuid = serviceUuid)).toInt()

        val regUuid = UUID.randomUUID().toString()
        val regId = dao.insertServiceRegistration(ServiceRegistration(patientId = patId, serviceId = svcId, employeeId = empId, sellingPrice = 200000.0, employeeCost = 100000.0, finalPrice = 200000.0, paymentMethod = "نقدی", invoiceNumber = "INV-100", grossIncome = 200000.0, employeeCommission = 100000.0, companyProfit = 100000.0, uuid = regUuid)).toInt()

        val commUuid = UUID.randomUUID().toString()
        dao.insertReferralCommission(ReferralCommission(referralId = refId, patientId = patId, serviceRegistrationId = regId, serviceName = "پانسمان", serviceAmount = 200000.0, commissionPercentage = 10.0, commissionAmount = 20000.0, status = "در انتظار پرداخت", uuid = commUuid))

        // Export JSON
        val exportedJson = CoreDomainBackupManager.exportBackupJson(dao)
        assertNotNull(exportedJson)

        val root = JSONObject(exportedJson)
        assertTrue(root.has("expenseCategories"))
        assertTrue(root.has("fixedExpenseTemplates"))
        assertTrue(root.has("referrals"))
        assertTrue(root.has("commissionSettlements"))
        assertTrue(root.has("referralCommissions"))

        val catArr = root.getJSONArray("expenseCategories")
        assertEquals(1, catArr.length())
        assertEquals("تجهیزات پزشکی", catArr.getJSONObject(0).getString("name"))

        val templateArr = root.getJSONArray("fixedExpenseTemplates")
        assertEquals(1, templateArr.length())
        assertEquals("اجاره مطب", templateArr.getJSONObject(0).getString("title"))

        val refArr = root.getJSONArray("referrals")
        assertEquals(1, refArr.length())
        assertEquals("دکتر رضا حسینی", refArr.getJSONObject(0).getString("name"))

        val settlementArr = root.getJSONArray("commissionSettlements")
        assertEquals(1, settlementArr.length())
        assertEquals(employeeUuid, settlementArr.getJSONObject(0).getString("employeeUuid"))

        val commArr = root.getJSONArray("referralCommissions")
        assertEquals(1, commArr.length())
        assertEquals(referralUuid, commArr.getJSONObject(0).getString("referralUuid"))
        assertEquals(patientUuid, commArr.getJSONObject(0).getString("patientUuid"))
        assertEquals(regUuid, commArr.getJSONObject(0).getString("serviceRegistrationUuid"))
    }

    @Test
    fun testRestorePreservesRelationships() = runBlocking {
        // Setup original database with entities & relationships
        val referralUuid = UUID.randomUUID().toString()
        val refId = dao.insertReferral(Referral(name = "دکتر علوی", type = "پزشک", uuid = referralUuid)).toInt()

        val employeeUuid = UUID.randomUUID().toString()
        val empId = dao.insertEmployee(Employee(fullName = "رضا رضایی", nationalId = "9876543210", phone = "09121111111", profession = "پزشک", position = "پزشک عمومی", skill = "طب عمومی", employmentType = "تمام وقت", commissionModel = "درصدی", commissionValue = 60.0, bankInfo = "IR456", status = "فعال", uuid = employeeUuid)).toInt()

        val patientUuid = UUID.randomUUID().toString()
        val patId = dao.insertPatient(Patient(fullName = "مریم نوری", gender = "زن", age = 30, phone = "09122222222", address = "شیراز", referralSource = "دکتر علوی", referralId = refId, status = "فعال", uuid = patientUuid)).toInt()

        val serviceUuid = UUID.randomUUID().toString()
        val svcId = dao.insertService(Service(name = "ویزیت", category = "پزشکی", sellingPrice = 300000.0, durationMinutes = 20, uuid = serviceUuid)).toInt()

        val regUuid = UUID.randomUUID().toString()
        val regId = dao.insertServiceRegistration(ServiceRegistration(patientId = patId, serviceId = svcId, employeeId = empId, sellingPrice = 300000.0, employeeCost = 180000.0, finalPrice = 300000.0, paymentMethod = "کارت", invoiceNumber = "INV-200", grossIncome = 300000.0, employeeCommission = 180000.0, companyProfit = 120000.0, uuid = regUuid)).toInt()

        val settlementUuid = UUID.randomUUID().toString()
        dao.insertCommissionSettlement(CommissionSettlement(employeeId = empId, amount = 180000.0, periodStart = 1000L, periodEnd = 2000L, uuid = settlementUuid))

        val commUuid = UUID.randomUUID().toString()
        dao.insertReferralCommission(ReferralCommission(referralId = refId, patientId = patId, serviceRegistrationId = regId, serviceName = "ویزیت", serviceAmount = 300000.0, commissionPercentage = 10.0, commissionAmount = 30000.0, status = "پرداخت شده", uuid = commUuid))

        val exportedJson = CoreDomainBackupManager.exportBackupJson(dao)

        // Create a new clean database
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val cleanDb = Room.inMemoryDatabaseBuilder(context, HamrahanDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val cleanDao = cleanDb.hamrahanDao()

        val report = CoreDomainBackupManager.restoreBackupJson(cleanDao, exportedJson)

        assertTrue(report.imported > 0)
        assertEquals(0, report.deferred)
        assertEquals(0, report.failed)

        // Verify relationships in clean database
        val restoredRef = cleanDao.getReferralByUuid(referralUuid)
        assertNotNull(restoredRef)

        val restoredEmp = cleanDao.getEmployeeByUuid(employeeUuid)
        assertNotNull(restoredEmp)

        val restoredPat = cleanDao.getPatientByUuid(patientUuid)
        assertNotNull(restoredPat)
        assertEquals(restoredRef!!.id, restoredPat!!.referralId)

        val restoredComm = cleanDao.getReferralCommissionByUuid(commUuid)
        assertNotNull(restoredComm)
        assertEquals(restoredRef.id, restoredComm!!.referralId)
        assertEquals(restoredPat.id, restoredComm.patientId)

        val restoredSettlement = cleanDao.getCommissionSettlementByUuid(settlementUuid)
        assertNotNull(restoredSettlement)
        assertEquals(restoredEmp!!.id, restoredSettlement!!.employeeId)

        cleanDb.close()
    }

    @Test
    fun testMissingParentCreatesDeferredItem() = runBlocking {
        val orphanCommUuid = UUID.randomUUID().toString()
        val missingRefUuid = UUID.randomUUID().toString()
        val missingPatUuid = UUID.randomUUID().toString()
        val missingRegUuid = UUID.randomUUID().toString()

        val jsonWithOrphan = """
            {
              "version": 1,
              "timestamp": 1720000000000,
              "referralCommissions": [
                {
                  "id": 999,
                  "referralId": 999,
                  "referralUuid": "$missingRefUuid",
                  "patientId": 999,
                  "patientUuid": "$missingPatUuid",
                  "serviceRegistrationId": 999,
                  "serviceRegistrationUuid": "$missingRegUuid",
                  "serviceName": "خدمت گمشده",
                  "serviceAmount": 100000.0,
                  "commissionPercentage": 5.0,
                  "commissionAmount": 5000.0,
                  "date": 1720000000000,
                  "status": "در انتظار پرداخت",
                  "documentNumber": "",
                  "notes": "یتیم",
                  "uuid": "$orphanCommUuid"
                }
              ]
            }
        """.trimIndent()

        val report = CoreDomainBackupManager.restoreBackupJson(dao, jsonWithOrphan)

        assertEquals(0, report.imported)
        assertEquals(1, report.deferred)

        val commInDb = dao.getReferralCommissionByUuid(orphanCommUuid)
        assertNull("ReferralCommission with missing parents should NOT be inserted into database", commInDb)
    }
}
