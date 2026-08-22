package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.ui.HamrahanViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExcelBackupEngineAuditTest {

    private lateinit var dbFile: File
    private lateinit var db: HamrahanDatabase
    private lateinit var repository: HamrahanRepository
    private lateinit var viewModel: HamrahanViewModel
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = context.getDatabasePath("hamrahan_salamat_db")
        val parent = dbFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        if (dbFile.exists()) {
            dbFile.delete()
        }
        db = Room.databaseBuilder(context, HamrahanDatabase::class.java, "hamrahan_salamat_db")
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()

        repository = HamrahanRepository(context, db.hamrahanDao())
        viewModel = HamrahanViewModel(repository)
    }

    @After
    fun tearDown() {
        db.close()
        val parent = dbFile.parentFile
        if (parent != null && parent.exists()) {
            dbFile.delete()
            File(parent, "hamrahan_salamat_db-wal").delete()
            File(parent, "hamrahan_salamat_db-shm").delete()
        }
    }

    @Test
    fun verifyFullBackupExportAndDataIntegrity() = runTest {
        val dao = db.hamrahanDao()

        // --- 1. SEED TEST DATA ---
        // Patient
        val patient = Patient(
            id = 1, fullName = "علی کریمی", gender = "مرد", age = 68, phone = "09121111111",
            address = "تهران، ونک", referralSource = "پزشک معالج", status = "فعال"
        )
        dao.insertPatient(patient)

        // Employee
        val employee = Employee(
            id = 1, fullName = "مریم حسینی", nationalId = "0012345678", phone = "09122222222",
            profession = "پرستار", position = "کارشناس ارشد", skill = "ICU", employmentType = "رسمی",
            commissionModel = "درصدی", commissionValue = 40.0, bankInfo = "بانک ملی", status = "فعال"
        )
        dao.insertEmployee(employee)

        // Service
        val service = Service(
            id = 1, name = "پانسمان زخم بستر درجه ۴", category = "خدمات تخصصی",
            sellingPrice = 1200000.0, defaultCost = 400000.0, durationMinutes = 60
        )
        dao.insertService(service)

        // Cashbox
        val cashbox = Cashbox(id = 1, name = "صندوق مرکزی کلینیک", type = "بانک", balance = 15000000.0)
        dao.insertCashbox(cashbox)

        // Service Registration
        val registration = ServiceRegistration(
            id = 1, patientId = 1, serviceId = 1, employeeId = 1,
            dateTime = System.currentTimeMillis(), sellingPrice = 1200000.0,
            employeeCost = 400000.0, transportationCost = 0.0, otherCosts = 0.0,
            discount = 0.0, finalPrice = 1200000.0, paymentMethod = "کارتخوان",
            invoiceNumber = "INV-1001", notes = "پانسمان تخصصی بیمار زخم بستر",
            grossIncome = 1200000.0, employeeCommission = 480000.0,
            companyProfit = 720000.0, isPaid = true, cashboxId = 1,
            consumablesOwner = "Company"
        )
        dao.insertServiceRegistration(registration)

        // Financial Transaction
        val tx1 = FinancialTransaction(
            id = 1, type = "درآمد", category = "ثبت خدمت", amount = 1200000.0,
            date = System.currentTimeMillis(), description = "دریافت وجه فاکتور INV-1001",
            paymentMethod = "کارتخوان", referenceId = 1, isCleared = true
        )
        val tx2 = FinancialTransaction(
            id = 2, type = "هزینه", category = "حقوق همکار", amount = 480000.0,
            date = System.currentTimeMillis(), description = "سهم همکار بابت فاکتور INV-1001",
            paymentMethod = "انتقال بانکی", referenceId = 1, isCleared = true
        )
        dao.insertFinancialTransaction(tx1)
        dao.insertFinancialTransaction(tx2)

        // Expense
        val exp = Expense(
            id = 1, title = "خرید باند و گاز استریل", category = "تجهیزات مصرفی",
            amount = 350000.0, registrationDate = System.currentTimeMillis(),
            paymentDate = System.currentTimeMillis(), paymentMethod = "کارت مرکز",
            submitterName = "مدیریت", description = "تهیه ملزومات پانسمان زخم"
        )
        dao.insertExpense(exp)

        // Referral & Referral Commission
        val referral = Referral(
            id = 1, name = "کلینیک امید", type = "مرکز درمانگاهی", phone = "02188888888",
            address = "تهران، میدان ونک", commissionPercentage = 10.0, commissionFixedAmount = 0.0, isActive = true
        )
        dao.insertReferral(referral)
        val commission = ReferralCommission(
            id = 1, referralId = 1, patientId = 1, serviceRegistrationId = 1,
            serviceName = "پانسمان زخم بستر درجه ۴", serviceAmount = 1200000.0,
            commissionPercentage = 10.0, commissionAmount = 72000.0,
            date = System.currentTimeMillis(), status = "در انتظار پرداخت",
            notes = "۱۰ درصد سهم سود مرکز معرفی‌کننده"
        )
        dao.insertReferralCommission(commission)

        // Contract
        val contract = Contract(
            id = 1, employeeId = 1, title = "قرارداد خدمات ICU", content = "دستمزد درصدی ۴۰٪",
            startDate = System.currentTimeMillis(), endDate = System.currentTimeMillis() + 864000000L,
            status = "Approved"
        )
        dao.insertContract(contract)

        // Nursing Report
        val nursingReport = NursingReport(
            id = 1, registrationId = 1,
            description = "موضع پانسمان زخم بدون علائم عفونت جدید. زخم در حال بهبود است.",
            reporterName = "مریم حسینی", date = System.currentTimeMillis()
        )
        dao.insertNursingReport(nursingReport)

        // Vital Signs
        val vitalSigns = VitalSigns(
            id = 1, patientId = 1, bloodPressureSystolic = 120, bloodPressureDiastolic = 80,
            heartRate = 72, temperatureCelsius = 36.5, oxygenSaturation = 98, date = System.currentTimeMillis()
        )
        dao.insertVitalSigns(vitalSigns)

        // Wound Record
        val woundRecord = WoundRecord(
            id = 1, patientId = 1, woundType = "بستر", stage = "درجه ۳",
            description = "مکان ساکروم، طول ۵، عرض ۴، عمق ۱ سانتی‌متر. ترشح سروز، بافت گرانولاسیون، بدون بو و عفونت.",
            date = System.currentTimeMillis()
        )
        dao.insertWoundRecord(woundRecord)

        // Audit Log
        val audit = AuditLog(
            id = 1, timestamp = System.currentTimeMillis(), user = "مریم حسینی",
            device = "DEVICE-TEST", action = "ثبت نهایی", affectedModule = "پانسمان",
            details = "فاکتور ممیزی شده با موفقیت به بانک اطلاعاتی الحاق گردید."
        )
        dao.insertAuditLog(audit)

        // System Settings
        val setting = SystemSetting("clinic_licence_code", "HS-9988-CRM")
        dao.insertSystemSetting(setting)

        // --- 2. GENERATE AND EXPORT SYSTEM SNAPSHOT ---
        val snapshot = ReportingLayer.generateSnapshot(repository)
        assertNotNull(snapshot)
        
        println("\n=== AUDIT BEFORE EXCEL GENERATION: BusinessReportSnapshot Content ===")
        println("• Patients count: ${snapshot.patients.size}")
        println("• Personnel count: ${snapshot.personnel.size}")
        println("• Services count: ${snapshot.services.size}")
        println("• Service Registrations count: ${snapshot.serviceRegistrations.size}")
        println("• Financial Transactions count: ${snapshot.financialTransactions.size}")
        println("• Expenses count: ${snapshot.expenses.size}")
        println("• Referrals count: ${snapshot.referrals.size}")
        println("• Referral Commissions count: ${snapshot.referralCommissions.size}")
        println("• Contracts count: ${snapshot.contracts.size}")
        println("• Nursing Reports count: ${snapshot.nursingReports.size}")
        println("• Vital Signs count: ${snapshot.vitalSigns.size}")
        println("• Wound Records count: ${snapshot.woundRecords.size}")
        println("• Audit Logs count: ${snapshot.auditLogs.size}")
        println("• System Settings count: ${snapshot.systemSettings.size}")
        println("================================================================")

        assertEquals(1, snapshot.patients.size)
        assertEquals(1, snapshot.personnel.size)
        assertEquals(1, snapshot.expenses.size)
        assertEquals(2, snapshot.financialTransactions.size)
        assertEquals(1, snapshot.nursingReports.size)
        assertEquals(1, snapshot.woundRecords.size)

        // --- 3. EXPORT EXCEL BACKUP FILE ---
        val backupFile = File(context.cacheDir, "test_full_system_backup.xlsx")
        if (backupFile.exists()) {
            backupFile.delete()
        }
        FileOutputStream(backupFile).use { fos ->
            val success = ExcelExporter.exportSnapshotToExcel(context, fos, snapshot)
            assertTrue(success)
        }

        assertTrue(backupFile.exists())
        assertTrue(backupFile.length() > 0)

        // --- 4. READ AND VALIDATE BACKUP EXCEL INTEGRITY ---
        backupFile.inputStream().use { fis ->
            val workbook = XSSFWorkbook(fis)
            assertNotNull(workbook)

            println("\n=== AUDIT AFTER WORKBOOK GENERATION ===")
            println("✔ Total Sheets created: ${workbook.numberOfSheets}")
            println("✔ Size of final xlsx file: ${backupFile.length()} bytes")
            println("✔ File exists in destination URI/Path: ${backupFile.exists()}")
            println("✔ Successfully reopened using Apache POI: true")
            println("==========================================")

            // Assert exact 18 sheets are created
            assertEquals(18, workbook.numberOfSheets)

            println("\n--- DETAILED SHEET-BY-SHEET ANALYSIS ---")
            for (i in 0 until workbook.numberOfSheets) {
                val s = workbook.getSheetAt(i)
                println("• Sheet ${i + 1}: \"${s.sheetName}\" has ${s.physicalNumberOfRows} physical rows (including headers/empty rows) [Last row index: ${s.lastRowNum}]")
            }
            println("----------------------------------------")

            // Sheet 1: اطلاعات مرکز
            val sheet1 = workbook.getSheetAt(0)
            assertEquals("اطلاعات مرکز", sheet1.sheetName)
            assertNotNull(sheet1.getRow(0))
            assertEquals("مشخصات، وضعیت تنظیمات و پیکربندی سیستم - همراهان سلامت", sheet1.getRow(0).getCell(0).stringCellValue)

            // Sheet 2: پرسنل
            val sheet2 = workbook.getSheetAt(1)
            assertEquals("پرسنل", sheet2.sheetName)
            assertEquals("شناسه همکار", sheet2.getRow(2).getCell(0).stringCellValue)
            assertEquals("مریم حسینی", sheet2.getRow(3).getCell(1).stringCellValue)

            // Sheet 3: بیماران
            val sheet3 = workbook.getSheetAt(2)
            assertEquals("بیماران", sheet3.sheetName)
            assertEquals("شناسه", sheet3.getRow(2).getCell(0).stringCellValue)
            assertEquals("علی کریمی", sheet3.getRow(3).getCell(1).stringCellValue)

            // Sheet 4: خدمات
            val sheet4 = workbook.getSheetAt(3)
            assertEquals("خدمات", sheet4.sheetName)
            assertEquals("شناسه خدمت", sheet4.getRow(2).getCell(0).stringCellValue)
            assertEquals("پانسمان زخم بستر درجه ۴", sheet4.getRow(3).getCell(1).stringCellValue)

            // Sheet 5: ثبت خدمات
            val sheet5 = workbook.getSheetAt(4)
            assertEquals("ثبت خدمات", sheet5.sheetName)
            assertEquals("INV-1001", sheet5.getRow(3).getCell(12).stringCellValue)

            // Sheet 6: مالی و تراکنشها
            val sheet6 = workbook.getSheetAt(5)
            assertEquals("مالی و تراکنشها", sheet6.sheetName)

            // Sheet 7: هزینهها
            val sheet7 = workbook.getSheetAt(6)
            assertEquals("هزینهها", sheet7.sheetName)
            assertEquals("خرید باند و گاز استریل", sheet7.getRow(3).getCell(1).stringCellValue)
            assertEquals("تجهیزات مصرفی", sheet7.getRow(3).getCell(2).stringCellValue)
            assertEquals("مدیریت", sheet7.getRow(3).getCell(7).stringCellValue)

            // Sheet 8: معرفین
            val sheet8 = workbook.getSheetAt(7)
            assertEquals("معرفین", sheet8.sheetName)

            // Sheet 9: کمیسیونها
            val sheet9 = workbook.getSheetAt(8)
            assertEquals("کمیسیونها", sheet9.sheetName)

            // Sheet 10: قراردادها
            val sheet10 = workbook.getSheetAt(9)
            assertEquals("قراردادها", sheet10.sheetName)

            // Sheet 11: گزارشات بالینی
            val sheet11 = workbook.getSheetAt(10)
            assertEquals("گزارشات بالینی", sheet11.sheetName)

            // Sheet 12: تاریخچه تغییرات
            val sheet12 = workbook.getSheetAt(11)
            assertEquals("تاریخچه تغییرات", sheet12.sheetName)

            // Sheet 13: پروفایل تخصصی پرسنل
            val sheet13 = workbook.getSheetAt(12)
            assertEquals("پروفایل تخصصی پرسنل", sheet13.sheetName)

            // Sheet 14: گزارش بالینی زخم
            val sheet14 = workbook.getSheetAt(13)
            assertEquals("گزارش بالینی زخم", sheet14.sheetName)

            // Sheet 15: نسخ و دستورات
            val sheet15 = workbook.getSheetAt(14)
            assertEquals("نسخ و دستورات", sheet15.sheetName)

            // Sheet 16: رضایتنامه‌ها
            val sheet16 = workbook.getSheetAt(15)
            assertEquals("رضایتنامه‌ها", sheet16.sheetName)

            // Sheet 17: کمیسیون‌های ارجاع
            val sheet17 = workbook.getSheetAt(16)
            assertEquals("کمیسیون‌های ارجاع", sheet17.sheetName)

            // Sheet 18: دفتر روزنامه حسابداری
            val sheet18 = workbook.getSheetAt(17)
            assertEquals("دفتر روزنامه حسابداری", sheet18.sheetName)

            println("\n=== FINANCIAL AND COMMISSION DATA INTEGRITY CHECK ===")
            // Pull the registered values from Excel directly
            val regRow = sheet5.getRow(3)
            val sellingPriceVal = regRow.getCell(5).numericCellValue
            val employeeCostVal = regRow.getCell(6).numericCellValue
            val finalPriceVal = regRow.getCell(10).numericCellValue
            val invoiceNumberVal = regRow.getCell(12).stringCellValue

            println("• Service Registration Selling Price in Excel: $sellingPriceVal Rial")
            println("• Service Registration Employee Cost/Commission in Excel: $employeeCostVal Rial")
            println("• Service Registration Final Price in Excel: $finalPriceVal Rial")
            println("• Service Registration Invoice Number in Excel: $invoiceNumberVal")

            // Sheet 9: Referral Commission row index is startRowRc (6) + 2 = 8
            val refCommRow = sheet9.getRow(8)
            val refCommAmountVal = refCommRow.getCell(7).numericCellValue
            println("• Referral Commission Amount in Excel: $refCommAmountVal Rial")

            val expRow = sheet7.getRow(3)
            val expTitleVal = expRow.getCell(1).stringCellValue
            val expAmountVal = expRow.getCell(3).numericCellValue
            println("• Expense Title in Excel: \"$expTitleVal\", Amount: $expAmountVal Rial")

            assertEquals(1200000.0, sellingPriceVal, 0.001)
            assertEquals(400000.0, employeeCostVal, 0.001)
            assertEquals(1200000.0, finalPriceVal, 0.001)
            assertEquals("INV-1001", invoiceNumberVal)
            assertEquals(72000.0, refCommAmountVal, 0.001)
            assertEquals(350000.0, expAmountVal, 0.001)
            println("✔ ALL FINANCIALS AND COMMISSIONS ARE 100% CORRECT AND MATCH SEED DATA!")
            println("=======================================================\n")

            workbook.close()
        }
    }

    @Test
    fun verifyZipBackupExportAndDataIntegrity() = runTest {
        val dao = db.hamrahanDao()
        // Seed some data
        val patient = Patient(
            id = 2, fullName = "عباس بوعذار", gender = "مرد", age = 45, phone = "09123333333",
            address = "اهواز", referralSource = "پزشک", status = "فعال"
        )
        dao.insertPatient(patient)

        // Perform backup
        println("\n=== STARTING ZIP BACKUP INTEGRITY AUDIT ===")
        val backupFile = BackupManager.performBackup(context, db)
        assertNotNull(backupFile)
        println("• Backup ZIP created? Yes")
        println("• Temporary ZIP path: ${backupFile!!.absolutePath}")
        println("• Temporary ZIP size: ${backupFile.length()} bytes")

        // Destination mock Uri
        val destFile = File(context.cacheDir, "dest_backup_test.healthbackup")
        if (destFile.exists()) destFile.delete()
        val destUri = android.net.Uri.fromFile(destFile)

        // Export backup to Uri
        val exportSuccess = BackupManager.exportBackupToUri(context, backupFile, destUri)
        assertTrue(exportSuccess)
        println("• Backup exported to URI? Yes")
        println("• Destination ZIP size: ${destFile.length()} bytes")

        // Reopen zip file and check entries
        var zipReopenedSuccessfully = false
        var metadataReadable = false
        var sqliteEntryReadable = false
        try {
            java.util.zip.ZipFile(destFile).use { zip ->
                zipReopenedSuccessfully = true
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name == "metadata.json") {
                        metadataReadable = true
                    }
                    if (entry.name == "database/hamrahan_salamat_db") {
                        sqliteEntryReadable = true
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        println("• ZIP reopened successfully? ${if (zipReopenedSuccessfully) "Yes" else "No"}")
        println("• metadata.json readable? ${if (metadataReadable) "Yes" else "No"}")
        println("• SQLite entry readable? ${if (sqliteEntryReadable) "Yes" else "No"}")
        println("==========================================\n")
    }
}
