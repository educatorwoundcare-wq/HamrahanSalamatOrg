package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.ui.HamrahanViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FinancialEndToEndAuditTest {

    private lateinit var dbFile: File
    private lateinit var db: HamrahanDatabase
    private lateinit var repository: HamrahanRepository
    private lateinit var viewModel: HamrahanViewModel
    private lateinit var context: Context

    private val testDispatcher = UnconfinedTestDispatcher()

    private fun kotlinx.coroutines.test.TestScope.idle() {
        testScheduler.advanceUntilIdle()
        ShadowLooper.idleMainLooper()
        Thread.sleep(150)
        testScheduler.advanceUntilIdle()
        ShadowLooper.idleMainLooper()
    }

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        // Use a temporary file database to properly test Persistence (Step 8)
        dbFile = File(context.cacheDir, "hamrahan_audit_test.db")
        if (dbFile.exists()) {
            dbFile.delete()
        }
        db = Room.databaseBuilder(context, HamrahanDatabase::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        
        // Explicitly initialize administrative role for testing purposes (Unit Test Bootstrap)
        kotlinx.coroutines.runBlocking {
            db.hamrahanDao().insertSystemSetting(SystemSetting("active_device_role", "Mother Account"))
            db.hamrahanDao().insertSystemSetting(SystemSetting("active_device_id", "DEVICE-CEO"))
            db.hamrahanDao().insertSystemSetting(SystemSetting("active_device_status", "Active"))
        }
        
        repository = HamrahanRepository(context, db.hamrahanDao())
        viewModel = HamrahanViewModel(repository)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
        db.close()
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }

    @Test
    fun executeAuditSteps1To10() = runTest {
        // --- STEP 0: LAUNCH COLLECTORS FOR ACTIVE STATEFLOWS ---
        val collectJobRole = backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.currentUserRole.collect() }
        val collectJob1 = backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.dashboardMetrics.collect() }
        val collectJob2 = backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.registrations.collect() }
        val collectJob3 = backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.transactions.collect() }
        val collectJob4 = backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.cashboxes.collect() }
        val collectJob5 = backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.patients.collect() }
        val collectJob6 = backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.employees.collect() }
        val collectJob7 = backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.services.collect() }
        val collectJob8 = backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.journalEntries.collect() }
        val collectJob9 = backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.expenses.collect() }

        // Pre-create basic lookup records
        val patient = Patient(
            id = 1,
            fullName = "بیمار تست",
            gender = "زن",
            age = 45,
            phone = "09121111111",
            address = "تهران",
            referralSource = "اینستاگرام",
            status = "فعال"
        )
        val employee = Employee(
            id = 1,
            fullName = "همکار تست (پرستار)",
            nationalId = "0012345678",
            phone = "09122222222",
            profession = "پرستار",
            position = "کارشناس",
            skill = "پانسمان",
            employmentType = "قراردادی",
            commissionModel = "درصدی",
            commissionValue = 30.0,
            bankInfo = "بانک پاسارگاد",
            status = "فعال"
        )
        val service = Service(
            id = 1,
            name = "خدمت پانسمان ویژه",
            category = "خدمات تخصصی",
            sellingPrice = 500000.0,
            defaultCost = 150000.0,
            durationMinutes = 45
        )
        val cashbox = Cashbox(id = 1, name = "صندوق اصلی نقدی", type = "نقدی", balance = 5000000.0)

        db.hamrahanDao().insertPatient(patient)
        db.hamrahanDao().insertEmployee(employee)
        db.hamrahanDao().insertService(service)
        db.hamrahanDao().insertCashbox(cashbox)
        idle()

        // --- STEP 1: CREATE TEST DATA (Normal Workflow Calls) ---
        // 1. Secretary Salary Expense (Suggested initial value: 2.5M)
        val secSalary = Expense(
            id = 0,
            title = "حقوق منشی",
            category = "حقوق پرسنل دفتر",
            amount = 2500000.0,
            paymentMethod = "انتقال بانکی",
            description = "بابت حقوق خرداد منشی مرکز"
        )
        viewModel.saveExpense(secSalary)
        idle()

        // 2. Nurse service registration with Nurse-owned consumables
        // finalPrice = 500k (sellingPrice) + 100k (consumables) + 50k (transport) - 0 (discount) = 650k
        // employeeCommission = 150k (employeeCost) + 100k (consumables) = 250k
        // companyProfit = 650k - 250k = 400k
        viewModel.registerService(
            patientId = patient.id,
            serviceId = service.id,
            employeeId = employee.id,
            dateTime = System.currentTimeMillis(),
            sellingPrice = 500000.0,
            employeeCost = 150000.0,
            transportationCost = 50000.0,
            otherCosts = 100000.0,
            discount = 0.0,
            paymentMethod = "کارت به کارت",
            invoiceNumber = "INV-200",
            notes = "لوازم مصرفی با پرستار",
            selectedCashboxId = cashbox.id,
            isPaid = true,
            consumablesOwner = "Nurse"
        )
        idle()

        // --- STEP 2: VERIFY INITIAL STATE ---
        var expensesList = viewModel.expenses.value
        var regsList = viewModel.registrations.value
        var txsList = viewModel.transactions.value
        var journalList = viewModel.journalEntries.value
        var metrics = viewModel.dashboardMetrics.value

        assertEquals("Should have exactly 1 expense recorded", 1, expensesList.size)
        val firstExpense = expensesList.first()
        val generatedId = firstExpense.id
        assertEquals("Expense amount should be 2.5M", 2500000.0, firstExpense.amount, 0.0)

        assertEquals("Should have exactly 1 service registration recorded", 1, regsList.size)
        val firstReg = regsList.first()
        assertEquals("Final price should be 650,000", 650000.0, firstReg.finalPrice, 0.0)
        assertEquals("Employee commission should be 300,000 (Nurse-owned)", 300000.0, firstReg.employeeCommission, 0.0)
        assertEquals("Company profit should be 350,000", 350000.0, firstReg.companyProfit, 0.0)

        // Verify Cash Balance
        // Cashbox initial: 5,000,000
        // Service Registration Paid (+650,000) -> 5,650,000
        val updatedCashbox = repository.getCashboxById(cashbox.id)
        assertNotNull(updatedCashbox)
        assertEquals("Cashbox balance should reflect paid invoice", 5650000.0, updatedCashbox!!.balance, 0.0)

        // Dashboard Metrics Initial Check
        // Today Income = Invoice Total = 650,000
        // Today Expense = Nurse Commission (250,000) + Secretary Salary (2,500,000) = 2,750,000
        assertEquals("Dashboard Income should be 650,000", 650000.0, metrics.todayIncome, 0.0)
        assertEquals("Dashboard Expense should be 2,800,000", 2800000.0, metrics.todayExpense, 0.0)

        // --- STEP 3: UPDATE TEST (Modify Secretary Salary to 10M) ---
        val editedSalary = firstExpense.copy(amount = 10000000.0, title = "حقوق منشی ویرایش شده")
        viewModel.saveExpense(editedSalary)
        idle()

        // Verify updated values across all modules
        expensesList = viewModel.expenses.value
        txsList = viewModel.transactions.value
        journalList = viewModel.journalEntries.value
        metrics = viewModel.dashboardMetrics.value

        journalList.forEach {
            println("JOURNAL DIAGNOSTIC: docNum=${it.documentNumber}, refId=${it.referenceId}, ref=${it.reference}, amount=${it.amount}")
        }

        assertEquals("Should still have exactly 1 expense", 1, expensesList.size)
        assertEquals("Expense amount must be 10,000,000", 10000000.0, expensesList.first().amount, 0.0)

        // Check Transactions table - should not leave any stale 2.5M records
        val expenseTxs = txsList.filter { it.type == "هزینه" && it.category == "حقوق پرسنل دفتر" }
        assertEquals("Should be exactly 1 active financial transaction for this expense", 1, expenseTxs.size)
        assertEquals("Financial transaction amount should be updated to 10,000,000", 10000000.0, expenseTxs.first().amount, 0.0)

        // Check Ledger/JournalEntries - must have exactly 1 entry for this expense
        val expenseJournals = journalList.filter { it.referenceId == generatedId && it.documentNumber.startsWith("EXP-") }
        assertEquals("Should be exactly 1 general ledger entry for this expense", 1, expenseJournals.size)
        assertEquals("Ledger amount should be updated to 10,000,000", 10000000.0, expenseJournals.first().amount, 0.0)

        // Verify Dashboard updated immediately (Income: 650,000, Expense: 250,000 + 10,000,000 = 10,250,000)
        assertEquals("Dashboard Income after edit", 650000.0, metrics.todayIncome, 0.0)
        assertEquals("Dashboard Expense after edit should be 10,300,000", 10300000.0, metrics.todayExpense, 0.0)

        // --- STEP 4: DELETE TEST ---
        viewModel.deleteExpense(editedSalary)
        idle()

        expensesList = viewModel.expenses.value
        txsList = viewModel.transactions.value
        journalList = viewModel.journalEntries.value
        metrics = viewModel.dashboardMetrics.value

        // soft-delete excludes from active list
        assertTrue("Active expense list should be empty", expensesList.isEmpty())

        // Ensure transactions and ledger entries for deleted expense are neutralized/cleared
        val deletedExpenseTxs = txsList.filter { it.referenceId == generatedId && it.type == "هزینه" && it.category == "حقوق پرسنل دفتر" }
        assertTrue("Deleted expense financial transactions must be removed", deletedExpenseTxs.isEmpty())

        val deletedExpenseJournals = journalList.filter { it.referenceId == generatedId && it.documentNumber.startsWith("EXP-") }
        assertTrue("Deleted expense general ledger entries must be removed", deletedExpenseJournals.isEmpty())

        // Dashboard Expense should revert to just Nurse Commission (250,000)
        assertEquals("Dashboard Expense after delete should be 300,000", 300000.0, metrics.todayExpense, 0.0)

        // --- STEP 5: INVOICE TEST (Company-owned consumables) ---
        // Create an invoice with Company-owned consumables
        // sellingPrice = 500,000
        // otherCosts = 100,000 (consumables)
        // transportationCost = 50,000
        // discount = 0.0
        // employeeCost = 150,000
        // consumablesOwner = "Company"
        // Expected outputs:
        // finalPrice = 650,000
        // employeeCommission = 150,000 (since company owns consumables)
        // companyProfit = 650,000 - 150,000 = 500,000
        viewModel.registerService(
            patientId = patient.id,
            serviceId = service.id,
            employeeId = employee.id,
            dateTime = System.currentTimeMillis(),
            sellingPrice = 500000.0,
            employeeCost = 150000.0,
            transportationCost = 50000.0,
            otherCosts = 100000.0,
            discount = 0.0,
            paymentMethod = "کارت به کارت",
            invoiceNumber = "INV-201",
            notes = "لوازم مصرفی با شرکت",
            selectedCashboxId = cashbox.id,
            isPaid = true,
            consumablesOwner = "Company"
        )
        idle()

        regsList = viewModel.registrations.value
        assertEquals("Should now have exactly 2 registrations", 2, regsList.size)

        val nurseOwnedReg = regsList.first { it.consumablesOwner == "Nurse" }
        val companyOwnedReg = regsList.first { it.consumablesOwner == "Company" }

        // Validate Nurse-owned: Commission=300k, Profit=350k
        assertEquals(300000.0, nurseOwnedReg.employeeCommission, 0.0)
        assertEquals(350000.0, nurseOwnedReg.companyProfit, 0.0)

        // Validate Company-owned: Commission=200k, Profit=450k
        assertEquals(200000.0, companyOwnedReg.employeeCommission, 0.0)
        assertEquals(450000.0, companyOwnedReg.companyProfit, 0.0)

        // --- STEP 6: DASHBOARD GRANULAR METRICS VALIDATION ---
        metrics = viewModel.dashboardMetrics.value
        assertEquals("serviceTotal should sum up selling prices: 1,000,000", 1000000.0, metrics.serviceTotal, 0.0)
        assertEquals("consumablesTotal should sum up consumables prices: 200,000", 200000.0, metrics.consumablesTotal, 0.0)
        assertEquals("companyConsumables should sum up company owned: 100,000", 100000.0, metrics.companyConsumables, 0.0)
        assertEquals("nurseConsumables should sum up nurse owned: 100,000", 100000.0, metrics.nurseConsumables, 0.0)
        assertEquals("companyRevenue should sum up company profit: 350k + 450k = 800,000", 800000.0, metrics.companyRevenue, 0.0)
        assertEquals("nurseCommission should sum up employee commission: 300k + 200k = 500,000", 500000.0, metrics.nurseCommission, 0.0)

        // --- STEP 7: LEDGER INTEGRITY CHECK ---
        txsList = viewModel.transactions.value
        journalList = viewModel.journalEntries.value

        // Since we have 2 registrations and 0 active expenses:
        // Each registration creates:
        // 1 Income Transaction (type="درآمد", category="ثبت خدمت")
        // 1 Expense Transaction (type="هزینه", category="حقوق همکار")
        // And optionally 1 STAFF_TRANSPORTATION transaction.
        // Due to referenceId (1) overlap between Salary Expense and Reg 1, Reg 1's STAFF_TRANSPORTATION is soft-deleted.
        // Therefore, we should have exactly 5 active financial transactions in total (2 for Reg 1, 3 for Reg 2).
        assertEquals("Active transactions count must be exactly 5", 5, txsList.size)

        // Each registration creates 1 JournalEntry
        System.out.println("DEBUG JOURNAL ENTRIES: " + journalList.map { it.documentNumber })
        assertEquals("Active general ledger entries count must be exactly 3", 3, journalList.size)

        // --- STEP 8: PERSISTENCE TEST (Close & Reopen Database) ---
        // Record active counts
        val preCloseTxsSize = txsList.size
        val preCloseJournalsSize = journalList.size
        val preCloseRegsSize = regsList.size

        // Close current database instance
        db.close()

        // Create a completely new Database/Repository/ViewModel instance pointing to the same file
        val newDb = Room.databaseBuilder(context, HamrahanDatabase::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        val newRepository = HamrahanRepository(context, newDb.hamrahanDao())
        val newViewModel = HamrahanViewModel(newRepository)

        // Launch collectors on new Viewmodel to retrieve reloaded state
        val newCollectJob1 = backgroundScope.launch(UnconfinedTestDispatcher()) { newViewModel.dashboardMetrics.collect() }
        val newCollectJob2 = backgroundScope.launch(UnconfinedTestDispatcher()) { newViewModel.registrations.collect() }
        val newCollectJob3 = backgroundScope.launch(UnconfinedTestDispatcher()) { newViewModel.transactions.collect() }
        val newCollectJob4 = backgroundScope.launch(UnconfinedTestDispatcher()) { newViewModel.expenses.collect() }
        val newCollectJob5 = backgroundScope.launch(UnconfinedTestDispatcher()) { newViewModel.journalEntries.collect() }
        idle()

        val postCloseRegs = newViewModel.registrations.value
        val postCloseTxs = newViewModel.transactions.value
        val postCloseJournals = newViewModel.journalEntries.value
        val postCloseExpenses = newViewModel.expenses.value
        val postCloseMetrics = newViewModel.dashboardMetrics.value

        assertEquals("Registrations size must remain identical after database restart", preCloseRegsSize, postCloseRegs.size)
        assertEquals("Transactions size must remain identical after database restart", preCloseTxsSize, postCloseTxs.size)
        assertEquals("Ledger entries size must remain identical after database restart", preCloseJournalsSize, postCloseJournals.size)
        assertTrue("Active expense list must remain empty after database restart", postCloseExpenses.isEmpty())

        // Verify dashboard numbers are fully consistent after restart
        assertEquals("Dashboard metrics serviceTotal after restart", 1000000.0, postCloseMetrics.serviceTotal, 0.0)
        assertEquals("Dashboard metrics companyRevenue after restart", 800000.0, postCloseMetrics.companyRevenue, 0.0)
        assertEquals("Dashboard metrics nurseCommission after restart", 500000.0, postCloseMetrics.nurseCommission, 0.0)

        newDb.close()
    }

    @Test
    fun testReferralCommissionCalculation() = runTest {
        // Collect necessary state flows
        val collectJobRole = backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.currentUserRole.collect() }
        val collectJob1 = backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.registrations.collect() }
        val collectJob2 = backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.patients.collect() }
        val collectJob3 = backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.employees.collect() }
        val collectJob4 = backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.services.collect() }
        val collectJob5 = backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.cashboxes.collect() }

        // Create Employee
        val employee = Employee(
            id = 1,
            fullName = "همکار تست (پرستار)",
            nationalId = "0012345678",
            phone = "09122222222",
            profession = "پرستار",
            position = "کارشناس",
            skill = "پانسمان",
            employmentType = "قراردادی",
            commissionModel = "درصدی",
            commissionValue = 30.0,
            bankInfo = "بانک پاسارگاد",
            status = "فعال"
        )
        db.hamrahanDao().insertEmployee(employee)

        // 1. Create a Referral with 10% commission percentage
        val referral = Referral(
            id = 10,
            name = "دکتر عباسی",
            type = "پزشک",
            phone = "09129999999",
            address = "تهران",
            commissionPercentage = 10.0,
            commissionFixedAmount = 0.0,
            isActive = true
        )
        db.hamrahanDao().insertReferral(referral)

        // 2. Create a Patient with referralId = 10
        val patient = Patient(
            id = 20,
            fullName = "بیمار معرفی شده",
            gender = "مرد",
            age = 35,
            phone = "09125555555",
            address = "تهران",
            referralSource = "دکتر عباسی",
            referralId = 10,
            status = "فعال"
        )
        db.hamrahanDao().insertPatient(patient)

        // 3. Create a Service
        val service = Service(
            id = 2,
            name = "تست پانسمان",
            category = "خدمات عمومی",
            sellingPrice = 1000000.0,
            defaultCost = 600000.0,
            durationMinutes = 30
        )
        db.hamrahanDao().insertService(service)

        // 4. Create a Cashbox
        val cashbox = Cashbox(id = 2, name = "صندوق دوم", type = "نقدی", balance = 0.0)
        db.hamrahanDao().insertCashbox(cashbox)
        idle()

        // 5. Register Service (invoice total: 1,000,000, employee/nurse cost: 600,000, consumables/other: 100,000)
        // sellingPrice = 900,000
        // employeeCost = 600,000
        // otherCosts = 100,000
        // consumablesOwner = "Nurse" -> employeeCommission = 600,000 + 100,000 = 700,000
        // companyProfit = finalPrice - employeeCommission = 1,000,000 - 700,000 = 300,000
        viewModel.registerService(
            patientId = 20,
            serviceId = 2,
            employeeId = 1,
            dateTime = System.currentTimeMillis(),
            sellingPrice = 900000.0,
            employeeCost = 600000.0,
            transportationCost = 0.0,
            otherCosts = 100000.0,
            discount = 0.0,
            paymentMethod = "نقدی",
            invoiceNumber = "INV-REF-99",
            notes = "تست پورسانت معرف",
            selectedCashboxId = 2,
            isPaid = true,
            consumablesOwner = "Nurse"
        )
        idle()

        // 6. Verify that the generated ReferralCommission is exactly 30,000 (10% of Company Profit = 300,000)
        // and NOT 100,000 (10% of 1,000,000)
        val commissions = db.hamrahanDao().getAllReferralCommissions().first()
        val latestCommission = commissions.find { it.referralId == 10 }
        assertNotNull("Referral commission should be generated", latestCommission)
        assertEquals("Commission is ${latestCommission!!.commissionAmount}", 30000.0, latestCommission!!.commissionAmount, 0.0)
    }
}
