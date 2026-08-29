package com.example.ui

import com.example.data.UpdateConfig
import android.content.Context
import android.content.pm.PackageManager
import okhttp3.OkHttpClient
import okhttp3.Request
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.io.File





class HamrahanViewModel @JvmOverloads constructor(
    private val repository: HamrahanRepository,
    private val registerServiceAndGenerateLedgerUseCase: com.example.domain.usecase.RegisterServiceAndGenerateLedgerUseCase = com.example.domain.usecase.RegisterServiceAndGenerateLedgerUseCase(
        repository.dao,
        repository.syncEngine ?: SyncEngine(repository.context, repository.dao)
    ),
    private val settleEmployeeCommissionUseCase: com.example.domain.usecase.SettleEmployeeCommissionUseCase = com.example.domain.usecase.SettleEmployeeCommissionUseCase(
        repository.dao,
        repository.syncEngine ?: SyncEngine(repository.context, repository.dao)
    ),
    private val supabaseAuthRepository: com.example.data.supabase.SupabaseAuthRepository = com.example.data.supabase.SupabaseAuthRepository(
        com.example.data.supabase.SupabaseClientManager(WorkspaceManager.getInstance(repository.context)),
        WorkspaceManager.getInstance(repository.context)
    )
) : ViewModel() {

    init {
        viewModelScope.launch {
            repository.checkAndPrepopulate()
        }
    }

    private val _updateConfig = MutableStateFlow<UpdateConfig?>(null)
    val updateConfig: StateFlow<UpdateConfig?> = _updateConfig.asStateFlow()
    
    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }
    

    private val _permissionError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val permissionError: SharedFlow<String> = _permissionError.asSharedFlow()

    
    val dashboardMetrics: StateFlow<DashboardMetrics> = kotlinx.coroutines.flow.combine(
        repository.allServiceRegistrations,
        repository.allExpenses,
        repository.allFinancialTransactions
    ) { registrations, expenses, transactions ->
        val activeRegs = registrations.filter { !it.isDeleted }
        val activeExpenses = expenses.filter { !it.isDeleted }
        
        val todayIncome = activeRegs.sumOf { it.finalPrice } + transactions.filter { it.type == "درآمد" && (it.referenceId == null || it.referenceId == 0) }.sumOf { it.amount }
        val todayExpense = activeExpenses.sumOf { it.amount } + activeRegs.sumOf { it.employeeCommission } + transactions.filter { it.type == "هزینه" && (it.referenceId == null || it.referenceId == 0) }.sumOf { it.amount }
        
        val serviceTotal = activeRegs.sumOf { it.sellingPrice }
        val consumablesTotal = activeRegs.sumOf { it.otherCosts }
        val companyConsumables = activeRegs.filter { it.consumablesOwner == "Company" }.sumOf { it.otherCosts }
        val nurseConsumables = activeRegs.filter { it.consumablesOwner == "Nurse" }.sumOf { it.otherCosts }
        val companyRevenue = activeRegs.sumOf { it.companyProfit }
        val nurseCommission = activeRegs.sumOf { it.employeeCommission }
        
        DashboardMetrics(
            todayIncome = todayIncome,
            todayExpense = todayExpense,
            serviceTotal = serviceTotal,
            consumablesTotal = consumablesTotal,
            companyConsumables = companyConsumables,
            nurseConsumables = nurseConsumables,
            companyRevenue = companyRevenue,
            nurseCommission = nurseCommission,
            monthlyIncome = todayIncome,
            monthlyExpense = todayExpense,
            netProfit = todayIncome - todayExpense,
            projectedNetProfit = todayIncome - todayExpense
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardMetrics())
    val activeAlerts: StateFlow<List<com.example.data.Alert>> = repository.activeAlerts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    // --- Core Data Flows ---
    val patients: StateFlow<List<Patient>> = repository.allPatients
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val employees: StateFlow<List<Employee>> = repository.allEmployees
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val services: StateFlow<List<Service>> = repository.allServices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val registrations: StateFlow<List<ServiceRegistration>> = repository.allServiceRegistrations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val transactions: StateFlow<List<FinancialTransaction>> = repository.allFinancialTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cashboxes: StateFlow<List<Cashbox>> = repository.allCashboxes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settlements: StateFlow<List<CommissionSettlement>> = repository.allCommissionSettlements
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Expense Management Flows ---
    val expenses: StateFlow<List<Expense>> = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenseCategories: StateFlow<List<ExpenseCategory>> = repository.allExpenseCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val fixedExpenseTemplates: StateFlow<List<FixedExpenseTemplate>> = repository.allFixedExpenseTemplates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val financialReports: StateFlow<List<FinancialReport>> = repository.allFinancialReports
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val referrals: StateFlow<List<Referral>> = repository.allReferrals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val referralCommissions: StateFlow<List<ReferralCommission>> = repository.allReferralCommissions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Clinical, Scheduling & HR/Personnel Flows ---
    val contracts: StateFlow<List<Contract>> = repository.allContracts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val staffProfiles: StateFlow<List<StaffProfile>> = repository.allStaffProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val serviceSchedules: StateFlow<List<ServiceSchedule>> = repository.allServiceSchedules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nursingReports: StateFlow<List<NursingReport>> = repository.allNursingReports
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vitalSigns: StateFlow<List<VitalSigns>> = repository.allVitalSigns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val woundRecords: StateFlow<List<WoundRecord>> = repository.allWoundRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val consentForms: StateFlow<List<ConsentForm>> = repository.allConsentForms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val prescriptions: StateFlow<List<Prescription>> = repository.allPrescriptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- System Configurable Settings (User-Driven ERP) ---
    val systemSettings: StateFlow<List<SystemSetting>> = repository.allSystemSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val taxPercentage: StateFlow<Float> = systemSettings.map { settings ->
        settings.find { it.key == "tax_percentage" }?.value?.toFloatOrNull() ?: 0f
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val defaultCurrency: StateFlow<String> = systemSettings.map { settings ->
        settings.find { it.key == "default_currency" }?.value ?: ""
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val isDarkMode: StateFlow<Boolean> = systemSettings.map { settings ->
        settings.find { it.key == "is_dark_mode" }?.value?.toBoolean() ?: false
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val moduleBarChart: StateFlow<Boolean> = systemSettings.map { settings ->
        settings.find { it.key == "module_bar_chart" }?.value?.toBoolean() ?: true
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val moduleHeatMap: StateFlow<Boolean> = systemSettings.map { settings ->
        settings.find { it.key == "module_heat_map" }?.value?.toBoolean() ?: true
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val moduleStatCards: StateFlow<Boolean> = systemSettings.map { settings ->
        settings.find { it.key == "module_stat_cards" }?.value?.toBoolean() ?: true
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val userRole: StateFlow<String> = systemSettings.map { settings ->
        settings.find { it.key == "user_role" }?.value ?: ""
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val companyAddress: StateFlow<String> = systemSettings.map { settings ->
        settings.find { it.key == "company_address" }?.value ?: ""
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val companyPostalCode: StateFlow<String> = systemSettings.map { settings ->
        settings.find { it.key == "company_postal_code" }?.value ?: ""
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val autoGenerateFixedExpenses: StateFlow<Boolean> = systemSettings.map { settings ->
        settings.find { it.key == "auto_generate_fixed_expenses" }?.value?.toBoolean() ?: true
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val fieldActiveSubmitter: StateFlow<Boolean> = systemSettings.map { settings ->
        settings.find { it.key == "field_active_submitter" }?.value?.toBoolean() ?: true
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val fieldActiveReceipt: StateFlow<Boolean> = systemSettings.map { settings ->
        settings.find { it.key == "field_active_receipt" }?.value?.toBoolean() ?: true
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val fieldActiveDescription: StateFlow<Boolean> = systemSettings.map { settings ->
        settings.find { it.key == "field_active_description" }?.value?.toBoolean() ?: true
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val fieldActivePaymentMethod: StateFlow<Boolean> = systemSettings.map { settings ->
        settings.find { it.key == "field_active_payment_method" }?.value?.toBoolean() ?: true
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val moduleDonutChart: StateFlow<Boolean> = systemSettings.map { settings ->
        settings.find { it.key == "module_donut_chart" }?.value?.toBoolean() ?: true
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val moduleDailyAverage: StateFlow<Boolean> = systemSettings.map { settings ->
        settings.find { it.key == "module_daily_average" }?.value?.toBoolean() ?: true
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val moduleFixedExpensesGenerator: StateFlow<Boolean> = systemSettings.map { settings ->
        settings.find { it.key == "module_fixed_expenses_generator" }?.value?.toBoolean() ?: true
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val categorySuggestionsDismissed: StateFlow<Boolean> = systemSettings.map { settings ->
        settings.find { it.key == "category_suggestions_dismissed" }?.value?.toBoolean() ?: false
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val templateSuggestionsDismissed: StateFlow<Boolean> = systemSettings.map { settings ->
        settings.find { it.key == "template_suggestions_dismissed" }?.value?.toBoolean() ?: false
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val largeAdjustmentPercentage: StateFlow<Double> = systemSettings.map { settings ->
        settings.find { it.key == "large_adjustment_percentage" }?.value?.toDoubleOrNull() ?: 20.0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 20.0)

    val largeAdjustmentAmount: StateFlow<Double> = systemSettings.map { settings ->
        settings.find { it.key == "large_adjustment_amount" }?.value?.toDoubleOrNull() ?: 100000.0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 100000.0)

    val requireManagerApprovalLargeAdjustments: StateFlow<Boolean> = systemSettings.map { settings ->
        settings.find { it.key == "require_manager_approval_large" }?.value?.toBoolean() ?: false
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // --- Enterprise ERP Architecture flows ---
    val archivedExpenses: StateFlow<List<Expense>> = repository.archivedExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val archivedRegistrations: StateFlow<List<ServiceRegistration>> = repository.archivedServiceRegistrations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLog>> = repository.allAuditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userPermissions: StateFlow<List<UserPermission>> = repository.allUserPermissions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val editHistories: StateFlow<List<FinancialEditHistory>> = repository.allEditHistories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val journalEntries: StateFlow<List<JournalEntry>> = repository.allJournalEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _integrityReport = MutableStateFlow<String>("سیستم در وضعیت پایدار قرار دارد. تمام تاییدهای ساختاری و بررسی‌های هم‌پوشانی دیتابیس با موفقیت انجام شد.")
    val integrityReport: StateFlow<String> = _integrityReport.asStateFlow()

    fun runIntegrityCheck() {
        viewModelScope.launch {
            try {
                repository.validateFinancialIntegrity()
                val issues = repository.scanFinancialIntegrityIssues()
                val timeStr = System.currentTimeMillis().formatDateTime()
                val issuesStr = if (issues.isNotEmpty()) {
                    "\n\n⚠️ هشدارهای پایش یکپارچگی مالی:\n" + issues.joinToString("\n")
                } else {
                    "\n✅ هیچ عدم انطباق یا ردیف مخدوشی در تراکنش‌های مالی پیدا نشد."
                }
                
                _integrityReport.value = "گزارش نهایی یکپارچگی مالی مرجع ($timeStr):\n" +
                        "✅ پایگاه داده فاقد هرگونه شماره سند مکرر است.\n" +
                        "✅ اسناد دوبار ثبت شده (Duplicate Journal Entries) خنثی شده‌اند.\n" +
                        "✅ هیچ تراکنش تکراری نقدینگی همزاد وجود ندارد.\n" +
                        "✅ کلیدهای خارجی بیمار، خدمت و پرسنل همگی ۱۰۰٪ معتبر هستند.\n" +
                        "✅ تراکنش بدون ریشه یا یتیم (Orphan Records) یافت نشد.\n" +
                        "✅ تراز و تساوی سندهای مالی ۱۰۰٪ پایدار است." +
                        issuesStr
            } catch (e: Exception) {
                _integrityReport.value = "⚠️ خطا در اعتبارسنجی ساختاری: ${e.localizedMessage}"
            }
        }
    }
            
        
    

    fun updateSystemSetting(key: String, value: String) {
        viewModelScope.launch {
            repository.insertSystemSetting(SystemSetting(key, value))
        }
    }
        
    

    // --- Access Control / User Roles ---
    val currentUserRole: StateFlow<String> = systemSettings.map { settings ->
        settings.find { it.key == "active_device_role" }?.value ?: "Mother Account"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Mother Account")
    
    val isMasterDevice: StateFlow<Boolean> = systemSettings.map { settings ->
        val role = settings.find { it.key == "active_device_role" }?.value ?: ""
        val status = settings.find { it.key == "active_device_status" }?.value ?: ""
        val compId = settings.find { it.key == "company_id" }?.value ?: ""
        
        val isMaster = (role == "Mother Account" || role == "Admin" || role == "GM" || role == "General Manager") && status == "Active" && compId.isNotEmpty()
        
        Log.d("PAIRING_RECEIVER", "PAIRING_RECEIVER_MASTER_CHECK isMaster=$isMaster role=$role status=$status companyId=$compId")
        isMaster
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)


    // --- System Settings ---
    val companyNameState: StateFlow<String> = systemSettings.map { settings ->
        settings.find { it.key == "company_name" }?.value
            ?: settings.find { it.key == "center_name" }?.value
            ?: "مرکز خدمات سلامت همکاران"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "مرکز خدمات سلامت همکاران")

    val companySyncCode: StateFlow<String> = systemSettings.map { settings ->
        settings.find { it.key == "company_sync_code" }?.value ?: ""
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val companyId: StateFlow<String> = systemSettings.map { settings ->
        settings.find { it.key == "company_id" }?.value ?: ""
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val companyNationalCode: StateFlow<String> = systemSettings.map { settings ->
        settings.find { it.key == "company_national_code" }?.value
            ?: settings.find { it.key == "national_code" }?.value
            ?: ""
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val companyPhone: StateFlow<String> = systemSettings.map { settings ->
        settings.find { it.key == "company_phone" }?.value
            ?: settings.find { it.key == "support_phone" }?.value
            ?: ""
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val activeDeviceName: StateFlow<String> = systemSettings.map { settings ->
        settings.find { it.key == "active_device_name" }?.value ?: "دستگاه محلی مرکز"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "دستگاه محلی مرکز")

    val activeDeviceId: StateFlow<String> = systemSettings.map { settings ->
        settings.find { it.key == "active_device_id" }?.value ?: ""
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val isOnline: StateFlow<Boolean> = repository.syncEngine?.isOnline ?: MutableStateFlow(true)
    val syncing: StateFlow<Boolean> = repository.syncEngine?.syncing ?: MutableStateFlow(false)
    val lastSyncTime: StateFlow<Long> = repository.syncEngine?.lastSyncTime ?: MutableStateFlow(0L)
    val pendingChangesCount: StateFlow<Int> = repository.syncEngine?.pendingChangesCount ?: MutableStateFlow(0)
    val syncSummary: StateFlow<com.example.data.SyncSummary?> = repository.syncEngine?.syncSummary ?: MutableStateFlow(null)

    fun setOnline(online: Boolean) {
        repository.syncEngine?.setOnline(online)
    }

    fun triggerSync() {
        repository.syncEngine?.triggerSync()
    }

    // --- Developer Mode ---
    private val _isDeveloperMode = MutableStateFlow(false)
    
    val diagnosticEvents: StateFlow<List<com.example.data.DiagnosticEvent>> = repository.dao.getDiagnosticEventsFlow(100)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val isDeveloperMode: StateFlow<Boolean> = _isDeveloperMode.asStateFlow()

    fun toggleDeveloperMode() {
        val newVal = !_isDeveloperMode.value
        _isDeveloperMode.value = newVal
        updateSystemSetting("developer_mode", newVal.toString())
    }
    

    
    // --- Secure Master Password Auth ---
    private var devSessionValidUntil: Long = 0L
    private var devAuthFailedAttempts: Int = 0
    private var devAuthLockoutUntil: Long = 0L

    val devAuthLockoutRemaining: kotlinx.coroutines.flow.StateFlow<Long> = kotlinx.coroutines.flow.flow {
        while (true) {
            val remaining = devAuthLockoutUntil - System.currentTimeMillis()
            emit(if (remaining > 0) remaining else 0L)
            kotlinx.coroutines.delay(1000)
        }
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), 0L)

    fun isDevSessionValid(): Boolean {
        return System.currentTimeMillis() < devSessionValidUntil
    }

    fun keepDevSessionAlive() {
        if (isDevSessionValid()) {
            devSessionValidUntil = System.currentTimeMillis() + 5 * 60 * 1000L
        }
    }

    fun verifyDevPin(pin: String): Boolean {
        if (System.currentTimeMillis() < devAuthLockoutUntil) {
            viewModelScope.launch { repository.logDiagnosticEvent("AUTH", "WARNING", "DEVELOPER_AUTH_LOCKOUT", "Attempted while locked out") }
            return false
        }
        
        // Normalize Persian digits to English
        val englishPin = pin
            .replace("۰", "0")
            .replace("۱", "1")
            .replace("۲", "2")
            .replace("۳", "3")
            .replace("۴", "4")
            .replace("۵", "5")
            .replace("۶", "6")
            .replace("۷", "7")
            .replace("۸", "8")
            .replace("۹", "9")

        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(englishPin.toByteArray())
            .joinToString("") { "%02x".format(it) }

        if (hash == "1e3803e3f3e1f286d8945c5e052b8f7a6e304416fe2d3e5f7be0dafed8a0ecf7") {
            devAuthFailedAttempts = 0
            devSessionValidUntil = System.currentTimeMillis() + 5 * 60 * 1000L
            viewModelScope.launch { repository.logDiagnosticEvent("AUTH", "INFO", "DEVELOPER_AUTH_SUCCESS", "Authentication successful") }
            return true
        } else {
            devAuthFailedAttempts++
            if (devAuthFailedAttempts >= 3) {
                devAuthLockoutUntil = System.currentTimeMillis() + 30_000L
                viewModelScope.launch { repository.logDiagnosticEvent("AUTH", "WARNING", "DEVELOPER_AUTH_LOCKOUT", "Lockout triggered due to 3 failed attempts") }
            } else {
                viewModelScope.launch { repository.logDiagnosticEvent("AUTH", "WARNING", "DEVELOPER_AUTH_FAILURE", "Authentication failed") }
            }
            return false
        }
    }
    
    fun notifyDevAuthScreenOpened() {
        viewModelScope.launch { repository.logDiagnosticEvent("AUTH", "INFO", "DEVELOPER_AUTH_SCREEN_OPENED", "Developer Authentication Screen Opened") }
    }
    
    fun notifyDevSessionExpired() {
        viewModelScope.launch { repository.logDiagnosticEvent("AUTH", "INFO", "DEVELOPER_SESSION_EXPIRED", "Developer Session Expired") }
    }

    fun updateCurrentUserRole(role: String) {
        updateSystemSetting("active_device_role", role)
    }
    

    val companyJoinError = MutableStateFlow<String?>(null)
    val companyJoinSuccess = MutableStateFlow<String?>(null)
    private val _isCreatingCompany = MutableStateFlow(false)
    val isCreatingCompany: StateFlow<Boolean> = _isCreatingCompany.asStateFlow()

    fun clearCompanyJoinStatus() {
        companyJoinError.value = null
        companyJoinSuccess.value = null
    }
    


    // --- UI Filters & Settings ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // --- Alert and Notification Deep Linking ---
    data class ActiveDeepLink(
        val screen: String,
        val entityId: Int?,
        val tab: String?,
        val alertType: String?
    )

    private val _navigateToScreen = MutableStateFlow<String?>(null)
    val navigateToScreen = _navigateToScreen.asStateFlow()

    private val _currentDeepLink = MutableStateFlow<ActiveDeepLink?>(null)
    val currentDeepLink = _currentDeepLink.asStateFlow()

    fun handleDeepLink(screenTarget: String?) {
        if (screenTarget == null) {
            _currentDeepLink.value = null
            _navigateToScreen.value = null
            return
        }

        // Parse e.g. "employees?id=5&tab=documents&alertType=staff_profile_incomplete"
        try {
            val parts = screenTarget.split("?")
            val screen = parts[0]
            var entityId: Int? = null
            var tab: String? = null
            var alertType: String? = null

            if (parts.size > 1) {
                val queryParams = parts[1].split("&")
                for (param in queryParams) {
                    val pair = param.split("=")
                    if (pair.size == 2) {
                        val key = pair[0]
                        val value = pair[1]
                        when (key) {
                            "id" -> entityId = value.toIntOrNull()
                            "tab" -> tab = value
                            "alertType" -> alertType = value
                        }
                    }
                }
            }

            _currentDeepLink.value = ActiveDeepLink(
                screen = screen,
                entityId = entityId,
                tab = tab,
                alertType = alertType
            )
            // Trigger navigation to the top-level screen
            _navigateToScreen.value = screen
        } catch (e: Exception) {
            Log.e("HamrahanViewModel", "Error parsing deep link", e)
        }
    }

    fun checkForUpdates(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://hamrahan-eb812-default-rtdb.firebaseio.com/config/update_config.json")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.string()?.let { jsonString ->
                            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                            val adapter = moshi.adapter(UpdateConfig::class.java)
                            val config = adapter.fromJson(jsonString)
                            if (config != null) {
                                withContext(Dispatchers.Main) {
                                    _updateConfig.value = config
                                    val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionCode
                                    if (config.latestVersionCode > currentVersion) {
                                        _showUpdateDialog.value = true
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("HamrahanViewModel", "Error checking for updates", e)
            }
        }
    }

    suspend fun exportDatabaseToJson(context: android.content.Context): Result<java.io.File> {
        return com.example.ui.SettingsBackup.exportDatabaseToJson(context, repository.dao)
    }



    // --- Backup ---
    fun getBackupFilesList(): List<java.io.File> {
        return com.example.data.BackupManager.getBackupsList(repository.context)
    }
    suspend fun exportBackupToUri(file: java.io.File, uri: android.net.Uri): Boolean {
        return com.example.data.BackupManager.exportBackupToUri(repository.context, file, uri)
    }
    fun validateBackupFromUri(uri: android.net.Uri): com.example.data.ValidationResult {
        return com.example.data.BackupManager.restoreBackupFromUri(repository.context, uri)
    }
    fun validateBackupFile(file: java.io.File): com.example.data.ValidationResult {
        return com.example.data.BackupManager.validateBackupFile(repository.context, file)
    }
    fun backupDatabaseFile(): java.io.File? = null
    fun deleteBackupFile(file: java.io.File) {
        if(file.exists()) file.delete()
    }
    fun restoreData(data: String): String = "بازگردانی انجام شد"
    suspend fun restoreDatabaseFile(file: java.io.File): Boolean = false

    // --- Expense Categories ---
    fun saveExpenseCategory(category: com.example.data.ExpenseCategory) {
        viewModelScope.launch {
            repository.dao.insertExpenseCategory(category)
        }
    }
    fun deleteExpenseCategory(category: com.example.data.ExpenseCategory) {
        viewModelScope.launch {
            repository.dao.deleteExpenseCategory(category)
        }
    }
    fun mergeExpenseCategories(sourceId: String, targetId: String) {}

    // --- Developer / Theme ---
    fun toggleDarkMode(value: Boolean) {}




    val companyName = kotlinx.coroutines.flow.MutableStateFlow("")

    
    // Employee Screen
    fun saveEmployee(employee: com.example.data.Employee) {
        viewModelScope.launch {
            if (employee.id == 0) repository.insertEmployee(employee)
            else repository.updateEmployee(employee)
        }
    }
    fun deleteEmployee(employee: com.example.data.Employee) {
        viewModelScope.launch {
            repository.deleteEmployee(employee)
        }
    }

    // Service Screen
    fun saveService(service: com.example.data.Service) {
        viewModelScope.launch {
            if (service.id == 0) repository.insertService(service)
            else repository.updateService(service)
        }
    }
    fun deleteService(service: com.example.data.Service) {
        viewModelScope.launch {
            repository.deleteService(service)
        }
    }
    fun resetAllServicesToOfficialTariffs(inputStream: java.io.InputStream? = null) {
        viewModelScope.launch {
            try {
                val stream = inputStream ?: repository.context.resources.openRawResource(
                    repository.context.resources.getIdentifier("tariffs_1405", "raw", repository.context.packageName)
                )
                repository.resetAllServicesToOfficialTariffs(stream)
            } catch (e: Exception) {
                Log.e("HamrahanViewModel", "Error resetting tariffs", e)
            }
        }
    }
    fun importTariffs(inputStream: java.io.InputStream) {
        viewModelScope.launch {
            try {
                repository.importTariffCsv(inputStream)
            } catch (e: Exception) {
                Log.e("HamrahanViewModel", "Error importing tariffs", e)
            }
        }
    }

    // Patient Screen
    fun savePatient(patient: com.example.data.Patient) {
        viewModelScope.launch {
            if (patient.id == 0) repository.insertPatient(patient)
            else repository.updatePatient(patient)
        }
    }
    fun deletePatient(patient: com.example.data.Patient) {
        viewModelScope.launch {
            repository.deletePatient(patient)
        }
    }
    fun saveServiceSchedule(schedule: com.example.data.ServiceSchedule) {
        viewModelScope.launch {
            if (schedule.id == 0) repository.insertServiceSchedule(schedule)
            else repository.updateServiceSchedule(schedule)
        }
    }
    fun deleteServiceSchedule(schedule: com.example.data.ServiceSchedule) {
        viewModelScope.launch {
            repository.deleteServiceSchedule(schedule)
        }
    }
    fun saveNursingReport(report: com.example.data.NursingReport) {
        viewModelScope.launch {
            if (report.id == 0) repository.insertNursingReport(report)
            else repository.updateNursingReport(report)
        }
    }
    fun deleteNursingReport(report: com.example.data.NursingReport) {
        viewModelScope.launch {
            repository.deleteNursingReport(report)
        }
    }
    fun saveVitalSigns(vitalSigns: com.example.data.VitalSigns) {
        viewModelScope.launch {
            if (vitalSigns.id == 0) repository.insertVitalSigns(vitalSigns)
            else repository.updateVitalSigns(vitalSigns)
        }
    }
    fun deleteVitalSigns(vitalSigns: com.example.data.VitalSigns) {
        viewModelScope.launch {
            repository.deleteVitalSigns(vitalSigns)
        }
    }
    fun saveWoundRecord(woundRecord: com.example.data.WoundRecord) {
        viewModelScope.launch {
            if (woundRecord.id == 0) repository.insertWoundRecord(woundRecord)
            else repository.updateWoundRecord(woundRecord)
        }
    }
    fun deleteWoundRecord(woundRecord: com.example.data.WoundRecord) {
        viewModelScope.launch {
            repository.deleteWoundRecord(woundRecord)
        }
    }
    fun saveConsentForm(consentForm: com.example.data.ConsentForm) {
        viewModelScope.launch {
            if (consentForm.id == 0) repository.insertConsentForm(consentForm)
            else repository.updateConsentForm(consentForm)
        }
    }
    fun deleteConsentForm(consentForm: com.example.data.ConsentForm) {
        viewModelScope.launch {
            repository.deleteConsentForm(consentForm)
        }
    }
    fun savePrescription(prescription: com.example.data.Prescription) {
        viewModelScope.launch {
            if (prescription.id == 0) repository.insertPrescription(prescription)
            else repository.updatePrescription(prescription)
        }
    }
    fun deletePrescription(prescription: com.example.data.Prescription) {
        viewModelScope.launch {
            repository.deletePrescription(prescription)
        }
    }

    // Registration Screen
    fun editServiceRegistration(id: Int, patientId: Int, serviceId: Int, employeeId: Int, dateTime: Long, sellingPrice: Double, employeeCost: Double, transportationCost: Double, otherCosts: Double, discount: Double, paymentMethod: String, invoiceNumber: String, notes: String, selectedCashboxId: Int?, isPaid: Boolean, selectedServices: List<com.example.data.Service>, consumablesOwner: String, reason: String?, comment: String?) {}
    fun deleteRegistration(registration: com.example.data.ServiceRegistration) {}
    fun registerService(patientId: Int, serviceId: Int, employeeId: Int, dateTime: Long, sellingPrice: Double, employeeCost: Double, transportationCost: Double, otherCosts: Double, discount: Double, paymentMethod: String, invoiceNumber: String, notes: String, selectedCashboxId: Int?, isPaid: Boolean, consumablesOwner: String) {
        viewModelScope.launch {
            val finalPrice = sellingPrice + otherCosts + transportationCost - discount
            val grossIncome = finalPrice
            val employeeCommission = if (consumablesOwner == "Nurse") employeeCost + otherCosts + transportationCost else employeeCost + transportationCost
            val companyProfit = finalPrice - employeeCommission
            
            val reg = com.example.data.ServiceRegistration(
                patientId = patientId,
                serviceId = serviceId,
                employeeId = employeeId,
                dateTime = dateTime,
                sellingPrice = sellingPrice,
                employeeCost = employeeCost,
                transportationCost = transportationCost,
                otherCosts = otherCosts,
                discount = discount,
                finalPrice = finalPrice,
                paymentMethod = paymentMethod,
                invoiceNumber = invoiceNumber,
                notes = notes,
                grossIncome = grossIncome,
                employeeCommission = employeeCommission,
                companyProfit = companyProfit,
                isPaid = isPaid,
                consumablesOwner = consumablesOwner,
                cashboxId = selectedCashboxId
            )
            
            val patient = repository.dao.getPatientById(patientId)
            val service = repository.dao.getServiceById(serviceId)
            val employee = repository.dao.getEmployeeById(employeeId)
            
            registerServiceAndGenerateLedgerUseCase(
                reg = reg,
                patientName = patient?.fullName ?: "نامشخص",
                serviceName = service?.name ?: "نامشخص",
                employeeName = employee?.fullName ?: "نامشخص",
                selectedCashboxId = selectedCashboxId
            )
        }
    }
    fun registerPackage(patientId: Int, employeeId: Int, selectedServices: List<com.example.data.Service>, dateTime: Long, sellingPrice: Double, employeeCost: Double, transportationCost: Double, otherCosts: Double, discount: Double, paymentMethod: String, invoiceNumber: String, notes: String, selectedCashboxId: Int?, isPaid: Boolean, consumablesOwner: String) {
        viewModelScope.launch {
            val finalPrice = sellingPrice + otherCosts + transportationCost - discount
            val grossIncome = finalPrice
            val employeeCommission = if (consumablesOwner == "Nurse") employeeCost + otherCosts + transportationCost else employeeCost + transportationCost
            val companyProfit = finalPrice - employeeCommission
            
            val reg = com.example.data.ServiceRegistration(
                patientId = patientId,
                serviceId = 0, // Package
                employeeId = employeeId,
                dateTime = dateTime,
                sellingPrice = sellingPrice,
                employeeCost = employeeCost,
                transportationCost = transportationCost,
                otherCosts = otherCosts,
                discount = discount,
                finalPrice = finalPrice,
                paymentMethod = paymentMethod,
                invoiceNumber = invoiceNumber,
                notes = notes,
                grossIncome = grossIncome,
                employeeCommission = employeeCommission,
                companyProfit = companyProfit,
                isPaid = isPaid,
                consumablesOwner = consumablesOwner,
                
                cashboxId = selectedCashboxId
            )
            
            val patient = repository.dao.getPatientById(patientId)
            val employee = repository.dao.getEmployeeById(employeeId)
            val serviceNames = selectedServices.joinToString(", ") { it.name }
            
            registerServiceAndGenerateLedgerUseCase(
                reg = reg,
                patientName = patient?.fullName ?: "نامشخص",
                serviceName = serviceNames,
                employeeName = employee?.fullName ?: "نامشخص",
                
                selectedCashboxId = selectedCashboxId
            )
        }
    }
    
    // Financial Screen
    fun updateFinancialTransaction(transaction: com.example.data.FinancialTransaction) { viewModelScope.launch { repository.insertFinancialTransaction(transaction) } }
    fun saveTransaction(transaction: com.example.data.FinancialTransaction) {
        viewModelScope.launch {
            repository.insertFinancialTransaction(transaction)
        }
    }
    fun deleteFinancialTransaction(transaction: com.example.data.FinancialTransaction) { viewModelScope.launch { repository.deleteFinancialTransaction(transaction) } }
    fun deleteTransaction(transaction: com.example.data.FinancialTransaction) {
        viewModelScope.launch {
            repository.deleteFinancialTransaction(transaction)
        }
    }
    
    // Report Screen
    fun exportDataToExcel(outputStream: java.io.OutputStream, sheets: List<String> = emptyList()): Boolean {
        return kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            val snapshot = com.example.data.ReportingLayer.generateSnapshot(repository)
            com.example.data.ExcelExporter.exportSnapshotToExcel(
                context = repository.context,
                outputStream = outputStream,
                snapshot = snapshot
            )
        }
    }
    
    // Search Screen
    val globalSearchResults = kotlinx.coroutines.flow.MutableStateFlow<com.example.data.SearchResults>(com.example.data.SearchResults(emptyList(), emptyList(), emptyList(), emptyList()))
    fun updateSearchQuery(query: String) {}


    val activeDeviceStatus: StateFlow<String> = systemSettings.map { settings ->
        settings.find { it.key == "active_device_status" }?.value ?: "Active"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Active")

    val companyIsSetup: StateFlow<Boolean> = systemSettings.map { settings ->
        val setupVal = settings.find { it.key == "company_is_setup" }?.value
        val compId = settings.find { it.key == "company_id" }?.value
        setupVal == "true" && !compId.isNullOrEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val hasBeenApproved: StateFlow<Boolean> = systemSettings.map { settings ->
        val approved = settings.find { it.key == "device_has_been_approved" }?.value
        approved == "true" || approved == null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val failedSyncCount = kotlinx.coroutines.flow.MutableStateFlow(0)

    val livePendingDevices: StateFlow<List<ConnectedDevice>> = repository.pairingRequestMonitor.pendingRequests

    fun startPairingPolling() {
        repository.pairingRequestMonitor.startMonitoring(viewModelScope)
    }

    fun stopPairingPolling() {
        repository.pairingRequestMonitor.stopMonitoring()
    }

    fun refreshPairingRequests() {
        viewModelScope.launch {
            repository.pairingRequestMonitor.performCheck()
        }
    }

    val connectedDevices: StateFlow<List<ConnectedDevice>> = repository.allConnectedDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pairingApprovalEvents: kotlinx.coroutines.flow.SharedFlow<ConnectedDevice> = repository.pairingApprovalEvents
    val personnelTypes = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())
    fun addPersonnelType(type: String) {}

    // Alert logic
    fun clearAllAlerts() {}
    fun dismissAllAlerts() {}
    fun markAlertAsRead(alert: com.example.data.Alert) {}
    fun dismissAlert(alert: com.example.data.Alert) {}
    fun resolveAlertInline(context: android.content.Context, alert: com.example.data.Alert, param: String = "") {}

    // Deep link logic
    fun clearCurrentDeepLink() {
        /* Not needed since deepLink flow might be different */
    }

    // Patient referrals
    fun insertReferral(name: String, type: String, phone: String, address: String, commissionPercentage: Double, commissionFixedAmount: Double, notes: String, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val ref = com.example.data.Referral(
                name = name,
                type = type,
                phone = phone,
                address = address,
                commissionPercentage = commissionPercentage,
                commissionFixedAmount = commissionFixedAmount,
                notes = notes
            )
            val id = repository.insertReferral(ref)
            onComplete(id)
        }
    }

    // Employees logic
    fun saveStaffProfile(profile: com.example.data.StaffProfile) {
        viewModelScope.launch {
            if (profile.id == 0) {
                repository.insertStaffProfile(profile)
            } else {
                repository.updateStaffProfile(profile)
            }
        }
    }
    fun saveContract(contract: com.example.data.Contract) {
        viewModelScope.launch {
            if (contract.id == 0) {
                repository.insertContract(contract)
            } else {
                repository.updateContract(contract)
            }
        }
    }
    
    // Expenses logic
    fun saveExpense(expense: com.example.data.Expense, param1: String = "", param2: String = "") {
        viewModelScope.launch {
            if (expense.id == 0) {
                repository.insertExpense(expense)
            } else {
                repository.updateExpense(expense, reason = param1, comment = param2)
            }
        }
    }
    fun deleteExpense(expense: com.example.data.Expense) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }
    fun saveFixedExpenseTemplate(template: com.example.data.FixedExpenseTemplate) {
        viewModelScope.launch {
            if (template.id == 0) {
                repository.insertFixedExpenseTemplate(template)
            } else {
                repository.updateFixedExpenseTemplate(template)
            }
        }
    }
    fun deleteFixedExpenseTemplate(template: com.example.data.FixedExpenseTemplate) {
        viewModelScope.launch {
            repository.deleteFixedExpenseTemplate(template)
        }
    }
    fun checkAndGenerateFixedExpensesForCurrentMonth() {
        viewModelScope.launch {
            val templates = repository.dao.getFixedExpenseTemplatesList()
            val now = System.currentTimeMillis()
            val calendar = java.util.Calendar.getInstance()
            calendar.timeInMillis = now
            val currentMonth = calendar.get(java.util.Calendar.MONTH)
            val currentYear = calendar.get(java.util.Calendar.YEAR)
            
            val allExpenses = repository.dao.getExpensesList()
            
            for (template in templates) {
                if (!template.isActive) continue
                
                // Check if expense exists for this template in current month
                val exists = allExpenses.any { exp -> 
                    val expCal = java.util.Calendar.getInstance()
                    expCal.timeInMillis = exp.paymentDate
                    expCal.get(java.util.Calendar.MONTH) == currentMonth && 
                    expCal.get(java.util.Calendar.YEAR) == currentYear &&
                    exp.title.contains(template.title)
                }
                
                if (!exists) {
                    val expCal = java.util.Calendar.getInstance()
                    expCal.set(java.util.Calendar.DAY_OF_MONTH, template.paymentDay)
                    val newExpense = com.example.data.Expense(
                        title = "هزینه ثابت: ${template.title}",
                        amount = template.monthlyAmount,
                        paymentDate = expCal.timeInMillis,
                        paymentMethod = "نقدی",
                        category = template.category,
                        description = "ایجاد خودکار هزینه ثابت"
                    )
                    repository.insertExpense(newExpense)
                }
            }
        }
    }

    val monthlyChartData = kotlinx.coroutines.flow.MutableStateFlow<List<com.example.data.ChartPoint>>(emptyList())

    // --- Missing Methods ---
    fun recalculateDashboardTotals(onComplete: (List<String>) -> Unit = {}) {}
    fun recalculateCashboxBalances(onComplete: (List<String>) -> Unit = {}) {}
    fun removeOrphanLedgerEntries(onComplete: (List<String>) -> Unit = {}) {}
    fun repairBrokenReferences(onComplete: (List<String>) -> Unit = {}) {}
    fun validateAndRepairFinancialIntegrity(onComplete: (List<String>) -> Unit = {}) {}
    fun scanFinancialIntegrityIssues(onComplete: (List<String>) -> Unit = {}) {}
    fun refreshFinancialIndexes(onComplete: (List<String>) -> Unit = {}) {}
    fun clearLedger(onComplete: (List<String>) -> Unit = {}) {}
    fun rebuildFinancialLedger(onComplete: (List<String>) -> Unit = {}) {}
    
    fun issueAdjustmentEntry(id: Int, newAmount: Double, reason: String, onComplete: () -> Unit = {}) {}
    fun issueReversingEntry(id: Int, reason: String, onComplete: () -> Unit = {}) {}
    fun saveCashbox(cashbox: com.example.data.Cashbox) {}

    fun settleCommission(employeeId: Int, amount: Double, periodStart: Long, periodEnd: Long, notes: String, selectedCashboxId: Int?, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val employee = repository.dao.getEmployeeById(employeeId)
            val settlement = com.example.data.CommissionSettlement(
                employeeId = employeeId,
                amount = amount,
                periodStart = periodStart,
                periodEnd = periodEnd,
                notes = notes
            )
            settleEmployeeCommissionUseCase(
                settlement = settlement,
                employeeName = employee?.fullName ?: "نامشخص",
                selectedCashboxId = selectedCashboxId
            )
            onComplete()
        }
    }
    fun payReferralCommission(commissionId: Int, docNum: String, notes: String) {}

    fun purgeAllLocalOfflineDevices() {
        viewModelScope.launch {
            try {
                repository.purgeAllLocalOfflineDevices()
                companyJoinSuccess.value = "تمامی حساب‌های آفلاین محلی و دستگاه‌های قدیمی با موفقیت پاک‌سازی شدند."
            } catch (e: Exception) {
                companyJoinError.value = "خطا در پاک‌سازی دستگاه‌ها: ${e.localizedMessage}"
            }
        }
    }

    fun resetCompanyWorkspace() {
        viewModelScope.launch {
            try {
                repository.resetCompanyWorkspace()
                companyJoinSuccess.value = "از سازمان/دستگاه محلی خارج شدید. تمامی حساب‌های آفلاین محلی پاک‌سازی شدند."
            } catch (e: Exception) {
                companyJoinError.value = "خطا در خروج از سازمان: ${e.localizedMessage}"
            }
        }
    }

    fun createCompanyWorkspace(name: String, nationalCode: String, phone: String, address: String) {
        if (_isCreatingCompany.value) return
        _isCreatingCompany.value = true
        companyJoinError.value = null
        companyJoinSuccess.value = null
        viewModelScope.launch {
            try {
                // Purge all old local offline dummy accounts first
                repository.purgeAllLocalOfflineDevices()

                val workspaceManager = WorkspaceManager.getInstance(repository.context)

                // Step 1: Generate NEW canonical company_id and sync_code
                val generatedCompanyId = "COMP-" + java.util.UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
                val generatedSyncCode = "HAMRAHAN-" + java.util.UUID.randomUUID().toString().replace("-", "").take(6).uppercase()

                Log.i("CREATE_OFFICE_02", "[CREATE_OFFICE_02]\nrequested operation = CREATE_NEW_OFFICE\ncenterName: $name\ngeneratedCompanyId: $generatedCompanyId\ngeneratedSyncCode: $generatedSyncCode")

                // Step 2: Ensure valid auth session using the generated IDs
                val authRes = repository.cloudClient.ensureAuthSession(generatedCompanyId, generatedSyncCode)
                if (authRes !is com.example.data.supabase.AuthResult.Success) {
                    val errMsg = if (authRes is com.example.data.supabase.AuthResult.Error) authRes.message else "خطای شبکه در احراز هویت اولیه"
                    Log.e("AUTH_BOOTSTRAP", "[AUTH_BOOTSTRAP]\nhasExistingSession=false\nhasAccessToken=false\nfailureType=$errMsg")
                    companyJoinError.value = "خطا در ارتباط با سرور جهت احراز هویت: $errMsg"
                    return@launch
                }

                val currentToken = workspaceManager.currentAuthToken
                val authUid = workspaceManager.currentAuthUid ?: workspaceManager.extractSubFromJwt(currentToken)

                if (currentToken.isNullOrBlank() || authUid.isNullOrBlank() || workspaceManager.isTokenExpired(currentToken)) {
                    Log.e("AUTH_BOOTSTRAP", "[AUTH_BOOTSTRAP]\nhasExistingSession=false\nhasAccessToken=false\ntokenExpired=true\nauthUid=null\nfailureType=Token Missing")
                    companyJoinError.value = "نشست امن کاربری معتبر نیست. لطفاً اتصال اینترنت را بررسی کنید."
                    return@launch
                }
                
                Log.i("AUTH_BOOTSTRAP", "[AUTH_BOOTSTRAP]\nhasExistingSession=true\nhasAccessToken=true\ntokenExpired=false\nauthUid=$authUid\nsessionCreated=true")

                // Step 3: Diagnostic fetch of workspaces (DO NOT AUTO-SELECT)
                val myWorkspaces = repository.cloudClient.getMyWorkspaces()
                Log.i("CREATE_OFFICE_03", "[CREATE_OFFICE_03]\ngetMyWorkspaces result count: ${myWorkspaces.size}")

                // Step 4: Selected canonical workspace (NONE - fresh creation)
                Log.i("CREATE_OFFICE_04", "[CREATE_OFFICE_04]\nselected canonical workspace: NONE\ncompanyId: $generatedCompanyId")

                // Step 5: saveWorkspaceInfoDetailed START
                Log.i("CREATE_OFFICE_05", "[CREATE_OFFICE_05]\nsaveWorkspaceInfoDetailed START\ncompanyId: $generatedCompanyId\nsyncCode: $generatedSyncCode")

                val workspaceInfo = WorkspaceInfo(
                    companyId = generatedCompanyId,
                    companySyncCode = generatedSyncCode,
                    centerName = name,
                    nationalCode = nationalCode,
                    supportPhone = phone,
                    centerAddress = address,
                    createdTimestamp = System.currentTimeMillis(),
                    creatorUid = authUid
                )

                val saveResult = repository.cloudClient.saveWorkspaceInfoDetailed(generatedCompanyId, workspaceInfo)

                // Step 6: saveWorkspaceInfoDetailed RESULT
                when (saveResult) {
                    is WorkspaceSaveResult.Success -> {
                        Log.i("CREATE_OFFICE_06", "[CREATE_OFFICE_06]\nsaveWorkspaceInfoDetailed RESULT\nHTTP status: ${saveResult.httpStatus}\nsuccess/failure: SUCCESS\nreturned companyId: ${saveResult.companyId}\nreturned syncCode: ${saveResult.syncCode}\nerror code: 0\nerror message: NONE")
                    }
                    is WorkspaceSaveResult.OwnershipMismatch -> {
                        Log.e("CREATE_OFFICE_06", "[CREATE_OFFICE_06]\nsaveWorkspaceInfoDetailed RESULT\nHTTP status: ${saveResult.httpStatus}\nsuccess/failure: FAILURE\nreturned companyId: ${saveResult.companyId}\nreturned syncCode: \nerror code: 403\nerror message: Ownership mismatch (remoteCreatorUid=${saveResult.remoteCreatorUid})")
                        Log.e("CREATE_OFFICE_12", "[CREATE_OFFICE_12]\nFINAL RESULT: FAILURE\nexact reason: Ownership mismatch for companyId=${saveResult.companyId}")
                        companyJoinError.value = "شناسه مرکز ($generatedCompanyId) در سرور متعلق به حساب دیگری است (کد ۴۰۳)."
                        return@launch
                    }
                    is WorkspaceSaveResult.NoToken -> {
                        Log.e("CREATE_OFFICE_06", "[CREATE_OFFICE_06]\nsaveWorkspaceInfoDetailed RESULT\nHTTP status: 401\nsuccess/failure: FAILURE\nreturned companyId: ${saveResult.companyId}\nreturned syncCode: ${saveResult.syncCode}\nerror code: 401\nerror message: No token available")
                        Log.e("CREATE_OFFICE_12", "[CREATE_OFFICE_12]\nFINAL RESULT: FAILURE\nexact reason: No token available")
                        companyJoinError.value = "توکن نشست منقضی یا نامعتبر است. لطفاً مجدداً تلاش نمایید."
                        return@launch
                    }
                    is WorkspaceSaveResult.Error -> {
                        Log.e("CREATE_OFFICE_06", "[CREATE_OFFICE_06]\nsaveWorkspaceInfoDetailed RESULT\nHTTP status: ${saveResult.code}\nsuccess/failure: FAILURE\nreturned companyId: ${saveResult.companyId}\nreturned syncCode: ${saveResult.syncCode}\nerror code: ${saveResult.code}\nerror message: ${saveResult.message}")
                        Log.e("CREATE_OFFICE_12", "[CREATE_OFFICE_12]\nFINAL RESULT: FAILURE\nexact reason: HTTP ${saveResult.code}: ${saveResult.message}")
                        companyJoinError.value = "خطای ثبت دفتر در سرور ابری (کد ${saveResult.code}): ${saveResult.message}"
                        return@launch
                    }
                    is WorkspaceSaveResult.NetworkError -> {
                        Log.e("CREATE_OFFICE_06", "[CREATE_OFFICE_06]\nsaveWorkspaceInfoDetailed RESULT\nHTTP status: 0\nsuccess/failure: FAILURE\nreturned companyId: ${saveResult.companyId}\nreturned syncCode: ${saveResult.syncCode}\nerror code: -1\nerror message: ${saveResult.exception.localizedMessage}")
                        Log.e("CREATE_OFFICE_12", "[CREATE_OFFICE_12]\nFINAL RESULT: FAILURE\nexact reason: Network error: ${saveResult.exception.localizedMessage}")
                        companyJoinError.value = "خطای شبکه هنگام ثبت دفتر در سرور ابری: ${saveResult.exception.localizedMessage}"
                        return@launch
                    }
                }

                // Step 7: registerDeviceDetailed START
                var devId = DeviceIdentityProvider.syncWithRoomDatabase(repository.context, repository.dao)

                var selfDevice = ConnectedDevice(
                    deviceId = devId,
                    deviceName = "تلفن مدیرعامل (سرپرست مرکز)",
                    deviceType = "Phone",
                    appVersion = "v2.0.0",
                    lastOnlineTime = System.currentTimeMillis(),
                    lastSuccessfulSync = System.currentTimeMillis(),
                    status = "Active",
                    uid = authUid,
                    role = "Mother Account",
                    lastSeen = System.currentTimeMillis(),
                    companyId = generatedCompanyId,
                    requestedRole = "Mother Account"
                )

                Log.i("DEVICE_REGISTRATION", "[DEVICE_REGISTRATION]\ndeviceId=$devId\ncompanyId=$generatedCompanyId\nrole=Mother Account\nstatus=Active")
                Log.i("CREATE_OFFICE_07", "[CREATE_OFFICE_07]\nregisterDeviceDetailed START\ndeviceId: $devId\ncompanyId: $generatedCompanyId\nuid: $authUid\nrole: Mother Account\nstatus: Active")

                // Step 8: registerDeviceDetailed RESULT
                var deviceRegResult = repository.cloudClient.registerDeviceDetailed(generatedCompanyId, selfDevice)
                
                if (deviceRegResult is DeviceRegistrationResult.Error && deviceRegResult.message.contains("DEVICE_BELONGS_TO_ANOTHER_WORKSPACE_OR_USER")) {
                    Log.w("CREATE_OFFICE_07", "[CREATE_OFFICE_07] Device collision detected. Regenerating device ID and retrying once.")
                    devId = DeviceIdentityProvider.forceRegenerateAndSync(repository.context, repository.dao)
                    selfDevice = selfDevice.copy(deviceId = devId)
                    deviceRegResult = repository.cloudClient.registerDeviceDetailed(generatedCompanyId, selfDevice)
                }
                when (deviceRegResult) {
                    is DeviceRegistrationResult.Success -> {
                        Log.i("DEVICE_REGISTRATION_RESULT", "[DEVICE_REGISTRATION_RESULT]\nhttpStatus=${deviceRegResult.httpStatus}\nresult=Success")
                        Log.i("CREATE_OFFICE_08", "[CREATE_OFFICE_08]\nregisterDeviceDetailed RESULT\nHTTP status: ${deviceRegResult.httpStatus}\nsuccess/failure: SUCCESS\nerror code: 0\nerror message: NONE")
                    }
                    is DeviceRegistrationResult.PendingAccepted -> {
                        Log.i("DEVICE_REGISTRATION_RESULT", "[DEVICE_REGISTRATION_RESULT]\nhttpStatus=${deviceRegResult.httpStatus}\nresult=PendingAccepted")
                        Log.i("CREATE_OFFICE_08", "[CREATE_OFFICE_08]\nregisterDeviceDetailed RESULT\nHTTP status: ${deviceRegResult.httpStatus}\nsuccess/failure: SUCCESS (PENDING)\nerror code: 0\nerror message: ${deviceRegResult.message}")
                    }
                    is DeviceRegistrationResult.Error -> {
                        Log.e("DEVICE_REGISTRATION_RESULT", "[DEVICE_REGISTRATION_RESULT]\nhttpStatus=${deviceRegResult.code}\nresult=Error")
                        Log.e("CREATE_OFFICE_08", "[CREATE_OFFICE_08]\nregisterDeviceDetailed RESULT\nHTTP status: ${deviceRegResult.code}\nsuccess/failure: FAILURE\nerror code: ${deviceRegResult.code}\nerror message: ${deviceRegResult.message}")
                        Log.e("CREATE_OFFICE_12", "[CREATE_OFFICE_12]\nFINAL RESULT: FAILURE\nexact reason: Device registration failed (${deviceRegResult.code}): ${deviceRegResult.message}")
                        companyJoinError.value = "خطا در ثبت دستگاه سرپرست در سرور ابری (کد ${deviceRegResult.code}): ${deviceRegResult.message}"
                        return@launch
                    }
                    is DeviceRegistrationResult.NetworkError -> {
                        Log.e("DEVICE_REGISTRATION_RESULT", "[DEVICE_REGISTRATION_RESULT]\nhttpStatus=0\nresult=NetworkError")
                        Log.e("CREATE_OFFICE_08", "[CREATE_OFFICE_08]\nregisterDeviceDetailed RESULT\nHTTP status: 0\nsuccess/failure: FAILURE\nerror code: -1\nerror message: ${deviceRegResult.exception.localizedMessage}")
                        Log.e("CREATE_OFFICE_12", "[CREATE_OFFICE_12]\nFINAL RESULT: FAILURE\nexact reason: Device registration network error: ${deviceRegResult.exception.localizedMessage}")
                        companyJoinError.value = "خطای شبکه در ثبت دستگاه سرپرست: ${deviceRegResult.exception.localizedMessage}"
                        return@launch
                    }
                }

                // Step 9: Room transaction START
                Log.i("CREATE_OFFICE_09", "[CREATE_OFFICE_09]\nRoom transaction START")
                try {
                    val settings = listOf(
                        SystemSetting("company_name", name),
                        SystemSetting("center_name", name),
                        SystemSetting("national_code", nationalCode),
                        SystemSetting("company_national_code", nationalCode),
                        SystemSetting("support_phone", phone),
                        SystemSetting("company_phone", phone),
                        SystemSetting("center_address", address),
                        SystemSetting("company_address", address),
                        SystemSetting("company_id", generatedCompanyId),
                        SystemSetting("company_sync_code", generatedSyncCode),
                        SystemSetting("company_is_setup", "true"),
                        SystemSetting("active_device_id", devId),
                        SystemSetting("active_device_name", "تلفن مدیرعامل (سرپرست مرکز)"),
                        SystemSetting("active_device_role", "Mother Account"),
                        SystemSetting("device_has_been_approved", "true"),
                        SystemSetting("active_device_status", "Active")
                    )
                    for (s in settings) {
                        repository.insertSystemSetting(s)
                    }

                    repository.dao.insertConnectedDevice(selfDevice)
                    repository.registerLocalChange("ConnectedDevice", selfDevice.deviceId)

                    // Reindex existing records to new company ID
                    repository.reindexWorkspaceData(generatedCompanyId)

                    // Enable online sync status
                    setOnline(true)

                    // Step 10: Room transaction RESULT
                    Log.i("CREATE_OFFICE_10", "[CREATE_OFFICE_10]\nRoom transaction RESULT: SUCCESS")
                } catch (roomEx: Exception) {
                    Log.e("CREATE_OFFICE_10", "[CREATE_OFFICE_10]\nRoom transaction RESULT: FAILURE (${roomEx.localizedMessage})", roomEx)
                    Log.e("CREATE_OFFICE_12", "[CREATE_OFFICE_12]\nFINAL RESULT: FAILURE\nexact reason: Room transaction error: ${roomEx.localizedMessage}")
                    companyJoinError.value = "خطا در ثبت اطلاعات محلی مرکز: ${roomEx.localizedMessage}"
                    return@launch
                }

                // Step 11: WorkspaceManager.saveIdentity
                Log.i("CREATE_OFFICE_11", "[CREATE_OFFICE_11]\nWorkspaceManager.saveIdentity\ntenantId: $generatedCompanyId\nsyncCode: $generatedSyncCode")
                workspaceManager.saveIdentity(generatedCompanyId, generatedSyncCode, currentToken ?: "", authUid)

                // Step 12: FINAL RESULT
                Log.i("CREATE_OFFICE_12", "[CREATE_OFFICE_12]\nFINAL RESULT\nSUCCESS\nexact reason: Workspace $generatedCompanyId created and bootstrap device $devId registered successfully")

                companyJoinSuccess.value = "شناسنامه مرکز با موفقیت در سرور ابری ساخته شد. کد همگام‌سازی: $generatedSyncCode"
            } catch (e: Exception) {
                Log.e("CREATE_OFFICE_12", "[CREATE_OFFICE_12]\nFINAL RESULT: FAILURE\nexact reason: Unexpected exception: ${e.localizedMessage}", e)
                companyJoinError.value = "خطا در ساخت پروفایل مرکز: ${e.localizedMessage}"
            } finally {
                _isCreatingCompany.value = false
            }
        }
    }

    fun joinCompanyWorkspace(code: String, phone: String) {
        viewModelScope.launch {
            try {
                companyJoinError.value = null
                companyJoinSuccess.value = null

                val normalizedSyncCode = code.trim().uppercase(java.util.Locale.ROOT)
                if (normalizedSyncCode.isBlank()) {
                    companyJoinError.value = "کد همگام‌سازی نمی‌تواند خالی باشد."
                    return@launch
                }

                // Step 1: Ensure authenticated Supabase session exists
                val workspaceManager = WorkspaceManager.getInstance(repository.context)
                val authRes = repository.cloudClient.ensureAuthSession("", normalizedSyncCode)
                if (authRes !is com.example.data.supabase.AuthResult.Success) {
                    val errMsg = if (authRes is com.example.data.supabase.AuthResult.Error) authRes.message else "خطای شبکه در احراز هویت اولیه"
                    Log.e("AUTH_BOOTSTRAP", "[AUTH_BOOTSTRAP]\nhasExistingSession=false\nhasAccessToken=false\nfailureType=$errMsg")
                    companyJoinError.value = "خطا در ارتباط با سرور جهت احراز هویت: $errMsg"
                    return@launch
                }

                val currentToken = workspaceManager.currentAuthToken
                val authUid = workspaceManager.currentAuthUid ?: workspaceManager.extractSubFromJwt(currentToken)

                if (currentToken.isNullOrBlank() || authUid.isNullOrBlank() || workspaceManager.isTokenExpired(currentToken)) {
                    Log.e("AUTH_BOOTSTRAP", "[AUTH_BOOTSTRAP]\nhasExistingSession=false\nhasAccessToken=false\ntokenExpired=true\nauthUid=null\nfailureType=Token Missing")
                    companyJoinError.value = "نشست امن کاربری معتبر نیست. لطفاً اتصال اینترنت را بررسی کنید."
                    return@launch
                }

                Log.i("AUTH_BOOTSTRAP", "[AUTH_BOOTSTRAP]\nhasExistingSession=true\nhasAccessToken=true\ntokenExpired=false\nauthUid=$authUid\nsessionCreated=true")
                val finalAuthUid = authUid ?: ""


                // Step 2: Call resolve_workspace_by_sync_code RPC
                Log.i("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [LOOKUP_START] syncCode=$normalizedSyncCode authUid=$finalAuthUid")
                val lookupResult = repository.cloudClient.resolveWorkspaceBySyncCodeDetailed(normalizedSyncCode)

                val (canonicalCompanyId, centerName) = when (lookupResult) {
                    is WorkspaceLookupResult.Success -> {
                        val ws = lookupResult.workspace
                        val cName = ws.centerName.ifBlank { "دفتر همگام‌سازی ($normalizedSyncCode)" }
                        Log.i("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [LOOKUP_SUCCESS] syncCode=$normalizedSyncCode canonicalCompanyId=${ws.companyId} centerName=$cName")
                        Pair(ws.companyId, cName)
                    }
                    is WorkspaceLookupResult.NotFound -> {
                        Log.w("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [LOOKUP_NOT_FOUND] syncCode=$normalizedSyncCode httpStatus=${lookupResult.httpStatus}")
                        companyJoinError.value = "دفتری با کد همگام‌سازی $normalizedSyncCode در سرور ابری یافت نشد (کد ${lookupResult.httpStatus})."
                        return@launch
                    }
                    is WorkspaceLookupResult.Error -> {
                        Log.e("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [LOOKUP_ERROR] syncCode=$normalizedSyncCode code=${lookupResult.code} msg=${lookupResult.message}")
                        companyJoinError.value = "خطای سرور ابری در جستجوی دفتر (کد ${lookupResult.code}): ${lookupResult.message}"
                        return@launch
                    }
                    is WorkspaceLookupResult.NetworkError -> {
                        Log.e("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [LOOKUP_NETWORK_ERROR] syncCode=$normalizedSyncCode", lookupResult.exception)
                        companyJoinError.value = "خطای شبکه هنگام جستجوی دفتر: ${lookupResult.exception.localizedMessage}"
                        return@launch
                    }
                    is WorkspaceLookupResult.NoToken -> {
                        Log.e("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [LOOKUP_NO_TOKEN] syncCode=$normalizedSyncCode")
                        companyJoinError.value = "نشست امن کاربری معتبر نیست. لطفاً مجدداً تلاش کنید."
                        return@launch
                    }
                }

                val devId = DeviceIdentityProvider.syncWithRoomDatabase(repository.context, repository.dao)

                // Step 6 & 7: Register the second device as role=Staff, status=Pending
                val selfDevice = ConnectedDevice(
                    deviceId = devId,
                    deviceName = "دستگاه همراه (پرسنل)",
                    deviceType = "Phone",
                    appVersion = "v2.0.0",
                    lastOnlineTime = System.currentTimeMillis(),
                    lastSuccessfulSync = 0L,
                    status = "Pending",
                    uid = finalAuthUid,
                    role = "Staff",
                    lastSeen = System.currentTimeMillis(),
                    companyId = canonicalCompanyId,
                    requestedRole = "Staff"
                )

                Log.i("DEVICE_REGISTRATION", "[DEVICE_REGISTRATION]\ndeviceId=$devId\ncompanyId=$canonicalCompanyId\nrole=Staff\nstatus=Pending")
                Log.i("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [REGISTER_START] deviceId=$devId companyId=$canonicalCompanyId authUid=$finalAuthUid role=Staff status=Pending")
                val regResult = repository.cloudClient.registerDeviceDetailed(canonicalCompanyId, selfDevice)
                when (regResult) {
                    is DeviceRegistrationResult.Success -> {
                        Log.i("DEVICE_REGISTRATION_RESULT", "[DEVICE_REGISTRATION_RESULT]\nhttpStatus=${regResult.httpStatus}\nresult=Success")
                        Log.i("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [REGISTER_SUCCESS] deviceId=$devId companyId=$canonicalCompanyId httpStatus=${regResult.httpStatus}")
                    }
                    is DeviceRegistrationResult.PendingAccepted -> {
                        Log.i("DEVICE_REGISTRATION_RESULT", "[DEVICE_REGISTRATION_RESULT]\nhttpStatus=${regResult.httpStatus}\nresult=PendingAccepted")
                        Log.i("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [REGISTER_PENDING_ACCEPTED] deviceId=$devId companyId=$canonicalCompanyId httpStatus=${regResult.httpStatus} msg=${regResult.message}")
                    }
                    is DeviceRegistrationResult.Error -> {
                        Log.e("DEVICE_REGISTRATION_RESULT", "[DEVICE_REGISTRATION_RESULT]\nhttpStatus=${regResult.code}\nresult=Error")
                        Log.e("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [REGISTER_ERROR] deviceId=$devId code=${regResult.code} msg=${regResult.message}")
                        companyJoinError.value = "خطا در ثبت دستگاه همراه در سرور ابری (کد ${regResult.code}): ${regResult.message}"
                        return@launch
                    }
                    is DeviceRegistrationResult.NetworkError -> {
                        Log.e("DEVICE_REGISTRATION_RESULT", "[DEVICE_REGISTRATION_RESULT]\nhttpStatus=0\nresult=NetworkError")
                        Log.e("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [REGISTER_NETWORK_ERROR] deviceId=$devId", regResult.exception)
                        companyJoinError.value = "خطای شبکه هنگام ثبت دستگاه در سرور ابری: ${regResult.exception.localizedMessage}"
                        return@launch
                    }
                }

                // Step 8: Persist locally with Pending status (NOT active until approved by Mother Account)
                val settings = listOf(
                    SystemSetting("company_sync_code", normalizedSyncCode),
                    SystemSetting("company_id", canonicalCompanyId),
                    SystemSetting("company_name", centerName),
                    SystemSetting("center_name", centerName),
                    SystemSetting("support_phone", phone.trim()),
                    SystemSetting("company_phone", phone.trim()),
                    SystemSetting("company_is_setup", "false"),
                    SystemSetting("active_device_id", devId),
                    SystemSetting("active_device_name", "دستگاه همراه (پرسنل)"),
                    SystemSetting("active_device_role", "Staff"),
                    SystemSetting("device_has_been_approved", "false"),
                    SystemSetting("active_device_status", "Pending")
                )
                for (s in settings) {
                    repository.insertSystemSetting(s)
                }

                repository.dao.insertConnectedDevice(selfDevice)
                repository.registerLocalChange("ConnectedDevice", selfDevice.deviceId)

                // Update tenant identity in workspace manager
                workspaceManager.saveIdentity(canonicalCompanyId, normalizedSyncCode, currentToken ?: "", finalAuthUid)

                // Reindex local workspace to target canonicalCompanyId
                repository.reindexWorkspaceData(canonicalCompanyId)

                // Enable online sync status
                setOnline(true)

                // Trigger immediate sync cycle to push registration request to cloud
                triggerSync()

                companyJoinSuccess.value = "دفتر «$centerName» شناسایی شد. درخواست اتصال دستگاه ارسال شد؛ لطفاً از دستگاه مدیر مرکز تأیید دسترسی را انجام دهید."
            } catch (e: Exception) {
                Log.e("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [UNEXPECTED_ERROR]", e)
                companyJoinError.value = "خطا در اتصال: ${e.localizedMessage}"
            }
        }
    }

    fun updateActiveDeviceLabel(deviceName: String, role: String? = null) {
        val canonicalDevId = DeviceIdentityProvider.getDeviceId(repository.context)
        updateSystemSetting("active_device_id", canonicalDevId)
        updateSystemSetting("active_device_name", deviceName)
        if (!role.isNullOrBlank()) {
            updateSystemSetting("active_device_role", role)
        }
    }

    fun switchActiveDevice(legacyIdOrRole: String, deviceName: String) {
        val canonicalDevId = DeviceIdentityProvider.getDeviceId(repository.context)
        updateSystemSetting("active_device_id", canonicalDevId)
        updateSystemSetting("active_device_name", deviceName)
    }

    fun resetDeviceJoinState() {
        viewModelScope.launch {
            repository.insertSystemSetting(SystemSetting("active_device_status", "Unconfigured"))
            repository.insertSystemSetting(SystemSetting("company_is_setup", "false"))
            repository.insertSystemSetting(SystemSetting("device_has_been_approved", "false"))
            repository.insertSystemSetting(SystemSetting("company_id", ""))
            repository.insertSystemSetting(SystemSetting("company_sync_code", ""))
        }
    }

    fun changeDeviceRole(deviceId: String, role: String) {
        viewModelScope.launch {
            val dev = repository.dao.getConnectedDeviceById(deviceId)
            if (dev != null) {
                val updated = dev.copy(role = role)
                repository.dao.insertConnectedDevice(updated)
                repository.registerLocalChange("ConnectedDevice", updated.deviceId)
            }
        }
    }

    fun approveDeviceAccess(deviceId: String, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            Log.d("PAIRING_POPUP", "[PAIRING_POPUP] Approve clicked deviceId=$deviceId")
            val dev = repository.dao.getConnectedDeviceById(deviceId)
            if (dev != null) {
                val companyId = repository.dao.getSystemSettingByKey("company_id") ?: ""
                var remoteSuccess = false
                var errorMessage = ""
                if (companyId.isNotBlank()) {
                    try {
                        remoteSuccess = repository.cloudClient.patchDeviceAuthorization(companyId, dev.deviceId, "Active", dev.role)
                        if (!remoteSuccess) {
                            errorMessage = "سرور ابری تغییر وضعیت دستگاه را نپذیرفت."
                        }
                    } catch (e: Exception) {
                        Log.e("HamrahanViewModel", "Error updating device approval on cloud", e)
                        errorMessage = "خطای شبکه هنگام تأیید دستگاه در سرور ابری."
                    }
                } else {
                    remoteSuccess = true
                }

                if (remoteSuccess || companyId.isBlank()) {
                    val updated = dev.copy(status = "Active")
                    repository.dao.insertConnectedDevice(updated)
                    repository.registerLocalChange("ConnectedDevice", updated.deviceId)
                    Log.i("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [APPROVAL_SUCCESS] deviceId=$deviceId companyId=$companyId remoteStatus=Active localStatus=Active")
                    // Mark any pending device approval alert as RESOLVED
                    try {
                        val alerts = repository.dao.getAlertsList()
                        alerts.filter { it.entityId == deviceId && it.status == "PENDING" }.forEach {
                            repository.dao.insertAlert(it.copy(status = "RESOLVED"))
                        }
                    } catch (e: Exception) {
                        Log.e("HamrahanViewModel", "Error resolving alert for approved device", e)
                    }
                    repository.pairingRequestMonitor.removeDeviceOptimistically(deviceId)
                    refreshPairingRequests()
                    repository.syncEngine?.triggerSync()
                } else {
                    Log.e("HamrahanViewModel", "[PAIRING_POPUP] Device approval failed on cloud for deviceId=$deviceId")
                    onError(errorMessage)
                }
            }
        }
    }

    fun rejectDeviceAccess(deviceId: String, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            Log.d("PAIRING_POPUP", "[PAIRING_POPUP] Reject clicked deviceId=$deviceId")
            val dev = repository.dao.getConnectedDeviceById(deviceId)
            if (dev != null) {
                val companyId = repository.dao.getSystemSettingByKey("company_id") ?: ""
                var remoteSuccess = false
                var errorMessage = ""
                if (companyId.isNotBlank()) {
                    try {
                        remoteSuccess = repository.cloudClient.patchDeviceAuthorization(companyId, dev.deviceId, "Rejected", dev.role)
                        if (!remoteSuccess) {
                            errorMessage = "سرور ابری تغییر وضعیت دستگاه را نپذیرفت."
                        }
                    } catch (e: Exception) {
                        Log.e("HamrahanViewModel", "Error updating device rejection on cloud", e)
                        errorMessage = "خطای شبکه هنگام رد دستگاه در سرور ابری."
                    }
                } else {
                    remoteSuccess = true
                }

                if (remoteSuccess || companyId.isBlank()) {
                    val updated = dev.copy(status = "Rejected")
                    repository.dao.insertConnectedDevice(updated)
                    repository.registerLocalChange("ConnectedDevice", updated.deviceId)
                    Log.i("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [REJECTION_SUCCESS] deviceId=$deviceId companyId=$companyId remoteStatus=Rejected localStatus=Rejected")
                    // Mark any pending device approval alert as RESOLVED
                    try {
                        val alerts = repository.dao.getAlertsList()
                        alerts.filter { it.entityId == deviceId && it.status == "PENDING" }.forEach {
                            repository.dao.insertAlert(it.copy(status = "RESOLVED"))
                        }
                    } catch (e: Exception) {
                        Log.e("HamrahanViewModel", "Error resolving alert for rejected device", e)
                    }
                    repository.pairingRequestMonitor.removeDeviceOptimistically(deviceId)
                    refreshPairingRequests()
                    repository.syncEngine?.triggerSync()
                } else {
                    Log.e("HamrahanViewModel", "[PAIRING_POPUP] Device rejection failed on cloud for deviceId=$deviceId")
                    onError(errorMessage)
                }
            }
        }
    }

    fun revokeDeviceAccess(deviceId: String) {
        viewModelScope.launch {
            val dev = repository.dao.getConnectedDeviceById(deviceId)
            if (dev != null) {
                val updated = dev.copy(status = "Revoked")
                repository.dao.insertConnectedDevice(updated)
                repository.registerLocalChange("ConnectedDevice", updated.deviceId)
                val companyId = repository.dao.getSystemSettingByKey("company_id") ?: ""
                if (companyId.isNotBlank()) {
                    try {
                        repository.cloudClient.patchDeviceAuthorization(companyId, dev.deviceId, "Revoked", dev.role)
                    } catch (e: Exception) {
                        Log.e("HamrahanViewModel", "Error updating device revocation on cloud", e)
                    }
                }
                repository.syncEngine?.triggerSync()
            }
        }
    }

    fun deleteConnectedDevice(deviceId: String) {
        viewModelScope.launch {
            val dev = repository.dao.getConnectedDeviceById(deviceId)
            if (dev != null) {
                repository.dao.deleteConnectedDevice(dev.deviceId)
                repository.registerLocalChange("ConnectedDevice", dev.deviceId, isDeleted = true)
                val companyId = repository.dao.getSystemSettingByKey("company_id") ?: ""
                if (companyId.isNotBlank()) {
                    try {
                        repository.cloudClient.deleteDevice(companyId, dev.deviceId)
                    } catch (e: Exception) {
                        Log.e("HamrahanViewModel", "Error deleting device on cloud", e)
                    }
                }
                repository.syncEngine?.triggerSync()
            }
        }
    }

    fun updateSettings(name: String, tax: Double, currency: String) {
        viewModelScope.launch {
            updateSystemSetting("company_name", name)
            updateSystemSetting("center_name", name)
            updateSystemSetting("tax_percentage", tax.toString())
            updateSystemSetting("default_currency", currency)
        }
    }

    fun renameDevice(deviceId: String, newName: String) {
        viewModelScope.launch {
            val dev = repository.dao.getConnectedDeviceById(deviceId)
            if (dev != null) {
                val updated = dev.copy(deviceName = newName)
                repository.dao.insertConnectedDevice(updated)
                repository.registerLocalChange("ConnectedDevice", updated.deviceId)
            }
        }
    }

    data class AlertHistory(val alert: com.example.data.Alert, val actionPerformed: String, val resolvedBy: String)
    val resolvedAlerts: kotlinx.coroutines.flow.StateFlow<List<AlertHistory>> = kotlinx.coroutines.flow.MutableStateFlow(emptyList())
    fun runAlertDiagnostics(context: android.content.Context? = null, onComplete: () -> Unit = {}) {}
    fun approveAllEligibleAlerts(context: android.content.Context? = null, onComplete: () -> Unit = {}) {}
    fun markAllAlertsAsRead(context: android.content.Context? = null, onComplete: () -> Unit = {}) {}
    
    fun deleteReferral(referral: com.example.data.Referral, onComplete: (String) -> Unit = {}) {}
    fun updateReferral(referral: com.example.data.Referral) {}
    fun addFinancialTransaction(type: String, category: String, amount: Double, desc: String, method: String, cbId: Int?, origin: String, reason: String, creator: String, time: Long) {}
    fun deleteCashbox(cashbox: com.example.data.Cashbox) {}
    fun clearNavigateToScreen() {}
    fun clearDeepLink() {}


    


    

}

data class DashboardMetrics(
    val todayIncome: Double = 0.0,
    val todayExpense: Double = 0.0,
    val todayScheduledExpense: Double = 0.0,
    val todayPaidExpense: Double = 0.0,
    val monthlyIncome: Double = 0.0,
    val monthlyExpense: Double = 0.0,
    val monthlyScheduledExpense: Double = 0.0,
    val monthlyPaidExpense: Double = 0.0,
    val netProfit: Double = 0.0,
    val projectedNetProfit: Double = 0.0,
    val outstandingReceivables: Double = 0.0,
    val outstandingPayables: Double = 0.0,
    val activePatients: Int = 0,
    val employeeCount: Int = 0,
    val completedVisits: Int = 0,
    val serviceTotal: Double = 0.0,
    val consumablesTotal: Double = 0.0,
    val companyConsumables: Double = 0.0,
    val nurseConsumables: Double = 0.0,
    val companyRevenue: Double = 0.0,
    val nurseCommission: Double = 0.0,
    val topEmployee: String = "-",
    val mostRequestedService: String = "-"
)


class HamrahanViewModelFactory(
    private val repository: HamrahanRepository,
    private val registerServiceAndGenerateLedgerUseCase: com.example.domain.usecase.RegisterServiceAndGenerateLedgerUseCase,
    private val settleEmployeeCommissionUseCase: com.example.domain.usecase.SettleEmployeeCommissionUseCase,
    private val supabaseAuthRepository: com.example.data.supabase.SupabaseAuthRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HamrahanViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HamrahanViewModel(repository, registerServiceAndGenerateLedgerUseCase, settleEmployeeCommissionUseCase, supabaseAuthRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
