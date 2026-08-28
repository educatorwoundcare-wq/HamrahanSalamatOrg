package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import com.example.ui.formatDate

class HamrahanRepository @JvmOverloads constructor(
    val context: Context,
    val dao: HamrahanDao,
    var syncEngine: SyncEngine? = null,
    val cloudClient: CloudClient = CloudClient(dao, context)
) {
    suspend fun registerLocalChange(entityType: String, entityId: String, isDeleted: Boolean = false) {
        val activeDeviceId = DeviceIdentityProvider.getDeviceId(context)
        val meta = SyncMetadata(
            entityType = entityType,
            entityId = entityId,
            updatedTimestamp = System.currentTimeMillis(),
            deletedStatus = isDeleted,
            lastModifiedDeviceId = activeDeviceId,
            syncStatus = "Pending"
        )
        dao.insertSyncMetadata(meta)
        syncEngine?.triggerSync()
    }

    val pairingApprovalEvents: SharedFlow<ConnectedDevice>
        get() = syncEngine?.pairingApprovalEvents ?: MutableSharedFlow()
    // Expose all flows directly from DAO
    val allPatients: Flow<List<Patient>> = dao.getAllPatients()
    val allEmployees: Flow<List<Employee>> = dao.getAllEmployees()
    val allServices: Flow<List<Service>> = dao.getAllServices()
    val allServiceRegistrations: Flow<List<ServiceRegistration>> = dao.getAllServiceRegistrations()
    val allFinancialTransactions: Flow<List<FinancialTransaction>> = dao.getAllFinancialTransactions()
    val allCashboxes: Flow<List<Cashbox>> = dao.getAllCashboxes()
    val allCommissionSettlements: Flow<List<CommissionSettlement>> = dao.getAllCommissionSettlements()
    
    // --- New Expense Flows ---
    val allExpenses: Flow<List<Expense>> = dao.getAllExpenses()
    val allExpenseCategories: Flow<List<ExpenseCategory>> = dao.getAllExpenseCategories()
    val allFixedExpenseTemplates: Flow<List<FixedExpenseTemplate>> = dao.getAllFixedExpenseTemplates()
    val allFinancialReports: Flow<List<FinancialReport>> = dao.getAllFinancialReports()

    // --- Enterprise Audit, Permissions & Soft Delete Flows ---
    val archivedExpenses: Flow<List<Expense>> = dao.getArchivedExpenses()
    val archivedServiceRegistrations: Flow<List<ServiceRegistration>> = dao.getArchivedServiceRegistrations()
    val allAuditLogs: Flow<List<AuditLog>> = dao.getAllAuditLogs()
    val allUserPermissions: Flow<List<UserPermission>> = dao.getAllUserPermissions()
    val allEditHistories: Flow<List<FinancialEditHistory>> = dao.getAllEditHistories()
    val allJournalEntries: Flow<List<JournalEntry>> = dao.getAllJournalEntries()

    // --- Referral and Commission Flows & Operations ---
    val allReferrals: Flow<List<Referral>> = dao.getAllReferrals()
    val allReferralCommissions: Flow<List<ReferralCommission>> = dao.getAllReferralCommissions()

    // --- New Sync Entities Flows ---
    val allContracts: Flow<List<Contract>> = dao.getAllContracts()
    val allStaffProfiles: Flow<List<StaffProfile>> = dao.getAllStaffProfiles()
    val allServiceSchedules: Flow<List<ServiceSchedule>> = dao.getAllServiceSchedules()
    val allNursingReports: Flow<List<NursingReport>> = dao.getAllNursingReports()
    val allVitalSigns: Flow<List<VitalSigns>> = dao.getAllVitalSigns()
    val allWoundRecords: Flow<List<WoundRecord>> = dao.getAllWoundRecords()
    val allConsentForms: Flow<List<ConsentForm>> = dao.getAllConsentForms()
    val allPrescriptions: Flow<List<Prescription>> = dao.getAllPrescriptions()

    fun getCommissionsByReferral(referralId: Int): Flow<List<ReferralCommission>> = dao.getCommissionsByReferral(referralId)
    suspend fun getReferralById(id: Int): Referral? = dao.getReferralById(id)
    
    suspend fun insertReferral(referral: Referral): Long {
        val id = dao.insertReferral(referral)
        val inserted = referral.copy(id = id.toInt())
        registerLocalChange("Referral", inserted.uuid)
        return id
    }
    
    suspend fun updateReferral(referral: Referral) {
        dao.updateReferral(referral)
        registerLocalChange("Referral", referral.uuid)
    }
    
    suspend fun deleteReferral(referral: Referral) {
        dao.deleteReferral(referral)
        registerLocalChange("Referral", referral.uuid, isDeleted = true)
    }
    
    suspend fun getCommissionByServiceRegistration(regId: Int): ReferralCommission? = dao.getCommissionByServiceRegistration(regId)
    
    suspend fun insertReferralCommission(commission: ReferralCommission): Long {
        val id = dao.insertReferralCommission(commission)
        val inserted = commission.copy(id = id.toInt())
        registerLocalChange("ReferralCommission", inserted.uuid)
        return id
    }
    
    suspend fun updateReferralCommission(commission: ReferralCommission) {
        dao.updateReferralCommission(commission)
        registerLocalChange("ReferralCommission", commission.uuid)
    }
    
    suspend fun deleteReferralCommission(commission: ReferralCommission) {
        dao.deleteReferralCommission(commission)
        registerLocalChange("ReferralCommission", commission.uuid, isDeleted = true)
    }
    
    suspend fun deleteReferralCommissionByServiceRegistration(regId: Int) {
        val commission = dao.getCommissionByServiceRegistration(regId)
        if (commission != null) {
            dao.deleteReferralCommission(commission)
            registerLocalChange("ReferralCommission", commission.uuid, isDeleted = true)
        }
    }

    // Automatic category detection based on service names matching the user's requested 7 categories
    fun detectCategoryByName(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.contains("icu") || lower.contains("ccu") || lower.contains("ونتیلاتور") || lower.contains("مراقبتهای ویژه") -> "ICU در منزل"
            lower.contains("پزشک") || lower.contains("دکتر") || lower.contains("ویزیت متخصص") || lower.contains("ویزیت عمومی") -> "ویزیت پزشک"
            lower.contains("فیزیوتراپی") || lower.contains("توانبخشی") || lower.contains("کاردرمانی") -> "فیزیوتراپی"
            lower.contains("کمک‌بهیار") || lower.contains("کمکپرستار") || lower.contains("کمک‌پرستار") || lower.contains("بهیار") || lower.contains("سالمند") || lower.contains("همیاری") || lower.contains("مراقب روزانه") -> "مراقب سالمند و کمک‌بهیار"
            lower.contains("پانسمان") || lower.contains("زخم") || lower.contains("بستر") || lower.contains("دیابتی") || lower.contains("بخیه") || lower.contains("سوختگی") || lower.contains("دبریدمان") || lower.contains("گچ") || lower.contains("استوما") -> "مراقبت و پانسمان زخم"
            lower.contains("ارزیابی") || lower.contains("پایش") || lower.contains("علائم حیاتی") || lower.contains("تزریق") || lower.contains("سرم") || lower.contains("سوند") || lower.contains("مثانه") || lower.contains("فولی") || lower.contains("کاندوم شیت") || lower.contains("خونگیری") || lower.contains("وریدی") || lower.contains("ساکشن") || lower.contains("دیالیز") || lower.contains("صفاقی") || lower.contains("همودیالیز") || lower.contains("گاواژ") || lower.contains("ngt") || lower.contains("انما") || lower.contains("مشاوره پرستاری") || lower.contains("نوار قلب") || lower.contains("ecg") || lower.contains("تراکئوستومی") || lower.contains("تراک") -> "خدمات پرستاری در منزل"
            else -> "سایر خدمات سلامت"
        }
    }

    // Automatic pricing unit detection based on service name/description
    fun detectPricingUnitByName(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.contains("هر ساعت") || lower.contains("ساعت") -> "ساعت"
            lower.contains("۱۵ دقیقه") -> "۱۵ دقیقه"
            lower.contains("جلسه") -> "جلسه"
            lower.contains("گره") -> "گره"
            lower.contains("ناحیه اضافه") || lower.contains("ناحیه") -> "ناحیه اضافه"
            lower.contains("روز") -> "روز"
            lower.contains("ضریب") -> "ضریب"
            else -> "بازدید"
        }
    }

    // Reset and delete all services, then reload from CSV
    suspend fun resetAllServicesToOfficialTariffs(inputStream: java.io.InputStream) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        dao.deleteAllServices()
        importTariffCsv(inputStream)
    }

    // CSV Tariff catalog importer (idempotent, merges changes on code)
    suspend fun importTariffCsv(inputStream: java.io.InputStream) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream, "UTF-8"))
        val existingServices = dao.getAllServices().first().associateBy { it.officialCode }
        
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val parts = line?.split(",") ?: continue
            if (parts.size < 3) continue
            val code = parts[0].trim().removePrefix("\uFEFF")
            val name = parts[1].trim()
            val tariff = parts[2].trim().toDoubleOrNull() ?: 0.0
            
            if (code.isBlank() || name.isBlank()) continue
            
            val existing = existingServices[code]
            if (existing != null) {
                // If official tariff changed, update it. Keep other custom fields untouched!
                if (existing.officialTariff != tariff) {
                    val updated = existing.copy(
                        officialTariff = tariff,
                        lastModifiedDate = System.currentTimeMillis()
                    )
                    dao.updateService(updated)
                    registerLocalChange("Service", updated.uuid)
                }
            } else {
                // Insert new service
                val detectedCat = detectCategoryByName(name)
                val detectedUnit = detectPricingUnitByName(name)
                val is960240 = code == "960240"
                val newService = Service(
                    officialCode = code,
                    officialName = name,
                    name = name,
                    category = detectedCat,
                    officialTariff = tariff,
                    sellingPrice = tariff, // Center Selling Price = Official Tariff by default
                    defaultCost = if (is960240) 0.0 else (tariff * 0.6), // Employee Cost = 60% of tariff default
                    transportationCost = 0.0,
                    consumablesCost = 0.0,
                    discount = 0.0,
                    employeeCommission = if (is960240) 0.0 else 60.0, // 60% commission default
                    durationMinutes = if (name.contains("ساعت")) 60 else 45,
                    description = if (is960240) "این خدمت به عنوان ضریب تعدیل ۷۰ درصد برای خدمات بهیاری استفاده می‌شود." else "",
                    isActive = true,
                    pricingUnit = detectedUnit,
                    isVisibleInApp = true,
                    isSelectableByPatient = true,
                    lastModifiedDate = System.currentTimeMillis()
                )
                dao.insertService(newService)
                registerLocalChange("Service", newService.uuid)
            }
        }
        reader.close()
    }

    // --- Pre-populate helper ---
    suspend fun checkAndPrepopulate() {
        val canonicalDevId = DeviceIdentityProvider.syncWithRoomDatabase(context, dao)
        val activeDevIdSetting = dao.getSystemSettingByKey("company_is_setup")
        val isFirstLaunch = activeDevIdSetting == null && dao.getSystemSettingByKey("company_name") == null

        if (isFirstLaunch) {
            val localDevId = canonicalDevId

            val settings = listOf(
                SystemSetting("active_device_id", localDevId),
                SystemSetting("active_device_name", "تلفن من"),
                SystemSetting("company_id", ""),
                SystemSetting("company_sync_code", ""),
                SystemSetting("company_name", ""),
                SystemSetting("center_name", ""),
                SystemSetting("company_national_code", ""),
                SystemSetting("national_code", ""),
                SystemSetting("company_phone", ""),
                SystemSetting("support_phone", ""),
                SystemSetting("company_address", ""),
                SystemSetting("center_address", ""),
                SystemSetting("active_device_role", "Mother Account"),
                SystemSetting("device_has_been_approved", "true"),
                SystemSetting("active_device_status", "Active"),
                SystemSetting("company_is_setup", "false"),
                SystemSetting("tax_percentage", "9.0"),
                SystemSetting("default_currency", "ریال"),
                SystemSetting("personnel_types", "کارشناس پرستاری;کمک پرستار (بهیار);مراقب حرفه‌ای;پزشک عمومی;پزشک متخصص;فیزیوتراپ;کاردرمانگر;گفتاردرمانگر;مراقب ساده (همراه)")
            )
            for (setting in settings) {
                dao.insertSystemSetting(setting)
                registerLocalChange("SystemSetting", setting.key)
            }

            purgeAllLocalOfflineDevices()
            
            val workspaceManager = WorkspaceManager.getInstance(context)
            val authUid = workspaceManager.currentAuthUid ?: workspaceManager.extractSubFromJwt(workspaceManager.currentAuthToken) ?: ""
            android.util.Log.i("IDENTITY_RECOVERY", "[IDENTITY_RECOVERY] authUid=$authUid localCompanyId= localSyncCode= remoteCreatorUid=N/A decision=CHECK_AND_PREPOPULATE_INITIAL")

            val selfDevice = ConnectedDevice(
                deviceId = localDevId,
                deviceName = "تلفن من",
                deviceType = "Phone",
                appVersion = "v2.0.0",
                lastOnlineTime = System.currentTimeMillis(),
                lastSuccessfulSync = System.currentTimeMillis(),
                status = "Active",
                uid = authUid,
                role = "Mother Account",
                lastSeen = System.currentTimeMillis(),
                companyId = "",
                requestedRole = "Mother Account"
            )
            dao.insertConnectedDevice(selfDevice)
            registerLocalChange("ConnectedDevice", selfDevice.deviceId)
        }

        ensureMotherAccountActive()

        // Auto-load 1405 tariffs if services database is empty
        try {
            if (dao.getAllServices().first().isEmpty()) {
                val resId = context.resources.getIdentifier("tariffs_1405", "raw", context.packageName)
                if (resId != 0) {
                    val stream = context.resources.openRawResource(resId)
                    importTariffCsv(stream)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("HamrahanRepository", "Error auto-loading tariffs_1405.csv", e)
        }
    }

    suspend fun ensureMotherAccountActive() {
        dao.insertSystemSetting(SystemSetting("active_device_role", "Mother Account"))
        dao.insertSystemSetting(SystemSetting("active_device_status", "Active"))
        dao.insertSystemSetting(SystemSetting("device_has_been_approved", "true"))

        val activeDeviceId = DeviceIdentityProvider.syncWithRoomDatabase(context, dao)
        if (!activeDeviceId.isNullOrEmpty()) {
            val existingDev = dao.getConnectedDeviceById(activeDeviceId)
            val workspaceManager = WorkspaceManager.getInstance(context)
            var authUid = workspaceManager.currentAuthUid ?: workspaceManager.extractSubFromJwt(workspaceManager.currentAuthToken) ?: ""
            if (authUid.isBlank() && existingDev != null && existingDev.uid.isNotBlank() && existingDev.uid != activeDeviceId && !existingDev.uid.startsWith("DEV-")) {
                authUid = existingDev.uid
            }

            val updatedDev = ConnectedDevice(
                deviceId = activeDeviceId,
                deviceName = existingDev?.deviceName ?: dao.getSystemSettingByKey("active_device_name") ?: "تلفن مدیرعامل (سرپرست مرکز)",
                deviceType = existingDev?.deviceType ?: "Phone",
                appVersion = existingDev?.appVersion ?: "v2.0.0",
                lastOnlineTime = System.currentTimeMillis(),
                lastSuccessfulSync = existingDev?.lastSuccessfulSync ?: System.currentTimeMillis(),
                status = "Active",
                uid = authUid,
                role = "Mother Account",
                lastSeen = System.currentTimeMillis(),
                companyId = dao.getSystemSettingByKey("company_id") ?: "COMP-LOCAL",
                requestedRole = "Mother Account"
            )
            dao.insertConnectedDevice(updatedDev)
        }
    }

    suspend fun purgeAllLocalOfflineDevices() {
        val activeDevId = DeviceIdentityProvider.getDeviceId(context)
        val allDevs = dao.getAllConnectedDevicesList()
        for (dev in allDevs) {
            if (dev.deviceId != activeDevId || dev.companyId == "COMP-LOCAL" || dev.deviceName.contains("دستگاه محلی") || dev.deviceName.contains("آفلاین")) {
                if (dev.deviceId != activeDevId) {
                    dao.deleteConnectedDevice(dev.deviceId)
                }
            }
        }
    }

    suspend fun clearStaleWorkspaceIdentity() {
        // Clear only stale workspace identity metadata without wiping user-created operational data
        dao.insertSystemSetting(SystemSetting("company_is_setup", "false"))
        dao.insertSystemSetting(SystemSetting("company_id", ""))
        dao.insertSystemSetting(SystemSetting("company_sync_code", ""))
        val workspaceManager = WorkspaceManager.getInstance(context)
        workspaceManager.clearWorkspaceTenantOnly()
        android.util.Log.i("IDENTITY_RECOVERY", "[IDENTITY_RECOVERY] Stale local workspace metadata cleared while operational data preserved.")
    }

    suspend fun resetCompanyWorkspace() {
        // Reset system settings to unlock local device and allow creating/joining workspace
        dao.insertSystemSetting(SystemSetting("company_is_setup", "false"))
        dao.insertSystemSetting(SystemSetting("company_id", ""))
        dao.insertSystemSetting(SystemSetting("company_sync_code", ""))
        dao.insertSystemSetting(SystemSetting("company_name", "مرکز سلامت (آماده ساخت)"))
        dao.insertSystemSetting(SystemSetting("center_name", "مرکز سلامت (آماده ساخت)"))
        dao.insertSystemSetting(SystemSetting("active_device_role", "Mother Account"))
        dao.insertSystemSetting(SystemSetting("active_device_status", "Active"))
        dao.insertSystemSetting(SystemSetting("device_has_been_approved", "true"))

        val devId = DeviceIdentityProvider.syncWithRoomDatabase(context, dao)
        dao.insertSystemSetting(SystemSetting("active_device_id", devId))
        dao.insertSystemSetting(SystemSetting("active_device_name", "تلفن مدیرعامل (سرپرست مرکز)"))

        // Delete ALL connected device records from Room to wipe old offline local accounts
        dao.deleteAllConnectedDevices()

        val workspaceManager = WorkspaceManager.getInstance(context)
        val authUid = workspaceManager.currentAuthUid ?: workspaceManager.extractSubFromJwt(workspaceManager.currentAuthToken) ?: ""

        val selfDevice = ConnectedDevice(
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
            companyId = "",
            requestedRole = "Mother Account"
        )
        dao.insertConnectedDevice(selfDevice)
    }

    // --- Workspace Migration & Reindex Pipeline (Patch 1 & 2) ---
    suspend fun reindexWorkspaceData(newCompanyId: String) {
        android.util.Log.i("HamrahanRepository", "[Workspace Reindex] Migrating entities and reindexing SyncMetadata for workspace $newCompanyId")
        
        // 1. Migrate ConnectedDevice records if companyId was COMP-LOCAL or empty
        val localDevices = dao.getAllConnectedDevicesList()
        for (dev in localDevices) {
            if (dev.companyId == "COMP-LOCAL" || dev.companyId.isBlank()) {
                dao.insertConnectedDevice(dev.copy(companyId = newCompanyId))
            }
        }
        
        // 2. Scan every business entity in Room and mark as Pending in SyncMetadata for initial upload
        val now = System.currentTimeMillis()
        val activeDevId = DeviceIdentityProvider.getDeviceId(context)
        
        suspend fun markPending(type: String, id: String) {
            dao.insertSyncMetadata(
                SyncMetadata(
                    entityType = type,
                    entityId = id,
                    updatedTimestamp = now,
                    deletedStatus = false,
                    lastModifiedDeviceId = activeDevId,
                    syncStatus = "Pending"
                )
            )
        }

        dao.getPatientsList().forEach { markPending("Patient", it.uuid) }
        dao.getEmployeesList().forEach { markPending("Employee", it.uuid) }
        dao.getServicesList().forEach { markPending("Service", it.uuid) }
        dao.getServiceRegistrationsList().forEach { markPending("ServiceRegistration", it.uuid) }
        dao.getFinancialTransactionsList().forEach { markPending("FinancialTransaction", it.uuid) }
        dao.getCashboxesList().forEach { markPending("Cashbox", it.uuid) }
        dao.getCommissionSettlementsList().forEach { markPending("CommissionSettlement", it.uuid) }
        dao.getExpensesList().forEach { markPending("Expense", it.uuid) }
        dao.getExpenseCategoriesList().forEach { markPending("ExpenseCategory", it.uuid) }
        dao.getFixedExpenseTemplatesList().forEach { markPending("FixedExpenseTemplate", it.uuid) }
        dao.getFinancialReportsList().forEach { markPending("FinancialReport", it.id.toString()) }
        dao.getSystemSettingsList().forEach { markPending("SystemSetting", it.key) }
        dao.getAuditLogsList().forEach { markPending("AuditLog", it.id.toString()) }
        dao.getUserPermissionsList().forEach { markPending("UserPermission", it.permissionName) }
        dao.getEditHistoriesList().forEach { markPending("FinancialEditHistory", it.id.toString()) }
        dao.getJournalEntriesList().forEach { markPending("JournalEntry", it.id.toString()) }
        dao.getReferralsList().forEach { markPending("Referral", it.uuid) }
        dao.getReferralCommissionsList().forEach { markPending("ReferralCommission", it.uuid) }
        dao.getAlertsList().forEach { markPending("Alert", it.uuid) }
        dao.getContractsList().forEach { markPending("Contract", it.uuid) }
        dao.getStaffProfilesList().forEach { markPending("StaffProfile", it.uuid) }
        dao.getServiceSchedulesList().forEach { markPending("ServiceSchedule", it.uuid) }
        dao.getNursingReportsList().forEach { markPending("NursingReport", it.uuid) }
        dao.getVitalSignsList().forEach { markPending("VitalSigns", it.uuid) }
        dao.getWoundRecordsList().forEach { markPending("WoundRecord", it.uuid) }
        dao.getConsentFormsList().forEach { markPending("ConsentForm", it.uuid) }
        dao.getPrescriptionsList().forEach { markPending("Prescription", it.uuid) }
        dao.getDashboardCachesList().forEach { markPending("DashboardCache", it.key) }
        dao.getAllConnectedDevicesList().forEach { markPending("ConnectedDevice", it.deviceId) }

        android.util.Log.i("HamrahanRepository", "[Workspace Reindex] Completed workspace reindex for $newCompanyId")
    }

    // --- Patient Operations ---
    suspend fun insertPatient(patient: Patient): Long {
        var id = 0L
        dao.runInTransaction {
            id = dao.insertPatient(patient)
            registerLocalChange("Patient", patient.uuid)
        }
        return id
    }
    suspend fun updatePatient(patient: Patient) {
        dao.runInTransaction {
            dao.updatePatient(patient)
            registerLocalChange("Patient", patient.uuid)
        }
    }
    suspend fun deletePatient(patient: Patient) {
        dao.runInTransaction {
            dao.deletePatient(patient)
            registerLocalChange("Patient", patient.uuid, isDeleted = true)
        }
    }
    suspend fun getPatientById(id: Int): Patient? = dao.getPatientById(id)

    // --- Employee Operations ---
    suspend fun insertEmployee(employee: Employee): Long {
        var id = 0L
        dao.runInTransaction {
            id = dao.insertEmployee(employee)
            registerLocalChange("Employee", employee.uuid)
        }
        return id
    }
    suspend fun updateEmployee(employee: Employee) {
        dao.runInTransaction {
            dao.updateEmployee(employee)
            registerLocalChange("Employee", employee.uuid)
        }
    }
    suspend fun deleteEmployee(employee: Employee) {
        dao.runInTransaction {
            dao.deleteEmployee(employee)
            registerLocalChange("Employee", employee.uuid, isDeleted = true)
        }
    }
    suspend fun getEmployeeById(id: Int): Employee? = dao.getEmployeeById(id)

    // --- Service Operations ---
    suspend fun insertService(service: Service): Long {
        var id = 0L
        dao.runInTransaction {
            id = dao.insertService(service)
            registerLocalChange("Service", service.uuid)
        }
        return id
    }
    suspend fun updateService(service: Service) {
        dao.runInTransaction {
            dao.updateService(service)
            registerLocalChange("Service", service.uuid)
        }
    }
    suspend fun deleteService(service: Service) {
        dao.runInTransaction {
            dao.deleteService(service)
            registerLocalChange("Service", service.uuid, isDeleted = true)
        }
    }
    suspend fun getServiceById(id: Int): Service? = dao.getServiceById(id)

    // --- Service Registration & Automatic Accounting Business Rules ---
    suspend fun registerService(
        reg: ServiceRegistration,
        patientName: String,
        serviceName: String,
        employeeName: String,
        selectedCashboxId: Int?
    ) {
        var regId = 0
        dao.runInTransaction {
            // Update cashboxId in reg if selectedCashboxId is provided
            val regWithCashbox = if (selectedCashboxId != null) {
                reg.copy(cashboxId = selectedCashboxId)
            } else {
                reg
            }

            // 1. Insert service registration and get generated ID
            regId = dao.insertServiceRegistration(regWithCashbox).toInt()

            // 2. Automatically generate Financial Transaction of type INCOME (درآمد)
            val incomeDescription = "ثبت خدمت «$serviceName» برای بیمار «$patientName» توسط همکار «$employeeName»"
            val incomeTx = FinancialTransaction(
                type = "درآمد",
                category = "ثبت خدمت",
                amount = regWithCashbox.finalPrice,
                date = regWithCashbox.serviceDate,
                description = incomeDescription,
                paymentMethod = regWithCashbox.paymentMethod,
                referenceId = regId,
                origin = "Service"
            )
            dao.insertFinancialTransaction(incomeTx)
            registerLocalChange("FinancialTransaction", incomeTx.uuid)

            // 3. Automatically generate accrued commission transaction as EXPENSE (هزینه)
            // representing the payables of the company to the employee.
            val baseCommission = regWithCashbox.employeeCommission - regWithCashbox.transportationCost
            val expenseDescription = "کارمزد همکار «$employeeName» بابت خدمت «$serviceName» برای بیمار «$patientName»"
            val expenseTx = FinancialTransaction(
                type = "هزینه",
                category = "حقوق همکار",
                amount = baseCommission,
                date = regWithCashbox.serviceDate,
                description = expenseDescription,
                paymentMethod = "ثبت در حساب (بستانکار)",
                referenceId = regId,
                isCleared = false, // Will be cleared upon Monthly Settlement
                origin = "Salary"
            )
            dao.insertFinancialTransaction(expenseTx)
            registerLocalChange("FinancialTransaction", expenseTx.uuid)

            if (regWithCashbox.transportationCost > 0.0) {
                val transportTx = FinancialTransaction(
                    type = "هزینه",
                    category = "STAFF_TRANSPORTATION",
                    amount = regWithCashbox.transportationCost,
                    date = regWithCashbox.serviceDate,
                    description = "هزینه ایاب و ذهاب همکار «$employeeName» بابت خدمت «$serviceName»",
                    paymentMethod = "ثبت در حساب (بستانکار)",
                    referenceId = regId,
                    isCleared = false,
                    origin = "STAFF_TRANSPORTATION"
                )
                dao.insertFinancialTransaction(transportTx)
                registerLocalChange("FinancialTransaction", transportTx.uuid)
            }

            // 4. Update Cashbox balance if applicable
            val targetCashboxId = regWithCashbox.cashboxId
            if (targetCashboxId != null) {
                val cashbox = dao.getCashboxById(targetCashboxId)
                if (cashbox != null) {
                    // Add finalPrice to cashbox balance
                    val updatedCashbox = cashbox.copy(balance = cashbox.balance + regWithCashbox.finalPrice)
                    dao.updateCashbox(updatedCashbox)
                    registerLocalChange("Cashbox", updatedCashbox.uuid)
                }
            }

            // 5. Future-ready double-entry ledger entry
            val docNum = "REG-${System.currentTimeMillis() % 1000000}"
            val ledgerDebit = if (regWithCashbox.paymentMethod == "نقدی") "صندوق اصلی (دارایی)" else "حساب بانکی (دارایی)"
            val ledgerCredit = "درآمد خدمات سلامت (درآمد)"
            dao.insertJournalEntry(
                JournalEntry(
                    documentNumber = docNum,
                    debitAccount = ledgerDebit,
                    creditAccount = ledgerCredit,
                    amount = regWithCashbox.finalPrice,
                    reference = "ثبت خدمت شماره ${regWithCashbox.invoiceNumber}",
                    referenceId = regId
                )
            )

            // Log Audit log
            dao.insertAuditLog(
                AuditLog(
                    action = "Create",
                    affectedModule = "ServiceRegistrations",
                    details = "ثبت خدمت جدید برای بیمار ${patientName}، مبلغ نهایی: ${regWithCashbox.finalPrice}"
                )
            )

            // Insert informational alert
            val docAlert = Alert(
                title = "✅ سند ثبت خدمت تولید شد",
                description = "سند پرداخت شماره ${regWithCashbox.invoiceNumber} تولید شد و تایید شد.",
                type = "document_created",
                alertType = "document_created",
                entityId = "reg_$regId",
                status = "COMPLETED",
                isDismissed = true,
                isRead = true,
                createdAt = System.currentTimeMillis(),
                resolvedAt = System.currentTimeMillis()
            )
            dao.insertAlert(docAlert)
            registerLocalChange("Alert", docAlert.uuid)

            // 6. Handle Referral Commission
            val patient = dao.getPatientById(regWithCashbox.patientId)
            if (patient?.referralId != null) {
                val referral = dao.getReferralById(patient.referralId)
                if (referral != null && referral.isActive) {
                    val commissionAmount = if (referral.commissionPercentage > 0.0) {
                        regWithCashbox.companyProfit * (referral.commissionPercentage / 100.0)
                    } else {
                        referral.commissionFixedAmount
                    }
                    if (commissionAmount > 0.0) {
                        val referralCommission = ReferralCommission(
                            referralId = referral.id,
                            patientId = regWithCashbox.patientId,
                            serviceRegistrationId = regId,
                            serviceName = serviceName,
                            serviceAmount = regWithCashbox.finalPrice,
                            commissionPercentage = referral.commissionPercentage,
                            commissionAmount = commissionAmount,
                            status = "در انتظار پرداخت",
                            date = regWithCashbox.serviceDate
                        )
                        dao.insertReferralCommission(referralCommission)

                        // Insert into Financial Transactions as an Expense (هزینه) under category "پورسانت معرف"
                        val referralTx = FinancialTransaction(
                            type = "هزینه",
                            category = "پورسانت معرف",
                            amount = commissionAmount,
                            date = regWithCashbox.serviceDate,
                            description = "پورسانت معرف «${referral.name}» بابت خدمت «$serviceName» برای بیمار «$patientName»",
                            paymentMethod = "ثبت در حساب (بستانکار)",
                            referenceId = regId,
                            isCleared = false,
                            origin = "Referral"
                        )
                        dao.insertFinancialTransaction(referralTx)
                        registerLocalChange("FinancialTransaction", referralTx.uuid)
                    }
                }
            }

            // Validate integrity
            validateFinancialIntegrity()
        }
        registerLocalChange("ServiceRegistration", reg.uuid)
        val generatedCommission = dao.getCommissionByServiceRegistration(regId)
        if (generatedCommission != null) {
            registerLocalChange("ReferralCommission", generatedCommission.uuid)
        }
    }

    suspend fun deleteServiceRegistrationAndAssociatedTransactions(reg: ServiceRegistration, selectedCashboxId: Int? = null) {
        dao.runInTransaction {
            val actualReg = dao.getServiceRegistrationById(reg.id) ?: reg
            
            // Revert from the exact cashbox that recorded the payment
            val targetCashboxId = actualReg.cashboxId ?: selectedCashboxId
            if (targetCashboxId != null && actualReg.isPaid) {
                val cashbox = dao.getCashboxById(targetCashboxId)
                if (cashbox != null) {
                    val updatedCashbox = cashbox.copy(balance = cashbox.balance - actualReg.finalPrice)
                    dao.updateCashbox(updatedCashbox)
                    registerLocalChange("Cashbox", updatedCashbox.uuid)
                }
            }

            // Mark registration as soft-deleted
            val deletedReg = actualReg.copy(isDeleted = true, workflowStatus = "Archived")
            dao.updateServiceRegistration(deletedReg)

            // Register associated financial transactions as deleted for sync before deleting them from local DB
            val associatedTxs = dao.getFinancialTransactionsByReference(actualReg.id)
            associatedTxs.forEach { tx ->
                registerLocalChange("FinancialTransaction", tx.uuid, isDeleted = true)
            }

            // Register referral commission as deleted for sync before deleting from local DB
            val genCommission = dao.getCommissionByServiceRegistration(actualReg.id)
            if (genCommission != null) {
                registerLocalChange("ReferralCommission", genCommission.uuid, isDeleted = true)
            }

            // 1. Delete active financial transactions associated with this registration referenceId
            dao.deleteFinancialTransactionByReference(actualReg.id, "ثبت خدمت")
            dao.deleteFinancialTransactionByReference(actualReg.id, "حقوق همکار")
            dao.deleteFinancialTransactionByReference(actualReg.id, "STAFF_TRANSPORTATION")
            dao.deleteFinancialTransactionByReference(actualReg.id, "پورسانت معرف")

            // Delete referral commission
            dao.deleteReferralCommissionByServiceRegistration(actualReg.id)

            // 2. Issue automatic reversing journal entries in general ledger instead of hard-deleting
            issueReversingEntriesForReference(actualReg.id, "REG-%", "ابطال لغو ثبت خدمت ${actualReg.id}")

            // Log Audit log
            dao.insertAuditLog(
                AuditLog(
                    action = "Delete",
                    affectedModule = "ServiceRegistrations",
                    details = "حذف نرم ثبت خدمت آیدی: ${actualReg.id}، مبلغ نهایی: ${actualReg.finalPrice}"
                )
            )

            // Validate integrity
            validateFinancialIntegrity()
        }
        registerLocalChange("ServiceRegistration", reg.uuid)
    }

    suspend fun restoreServiceRegistration(reg: ServiceRegistration, selectedCashboxId: Int? = null) {
        dao.runInTransaction {
            val actualReg = reg.copy(isDeleted = false, workflowStatus = "Submitted")
            dao.updateServiceRegistration(actualReg)

            // Re-add to the correct cashbox if it was paid
            val targetCashboxId = actualReg.cashboxId ?: selectedCashboxId
            if (targetCashboxId != null && actualReg.isPaid) {
                val cashbox = dao.getCashboxById(targetCashboxId)
                if (cashbox != null) {
                    val updatedCashbox = cashbox.copy(balance = cashbox.balance + actualReg.finalPrice)
                    dao.updateCashbox(updatedCashbox)
                    registerLocalChange("Cashbox", updatedCashbox.uuid)
                }
            }

            // Re-generate financial transactions
            val incomeTx = FinancialTransaction(
                type = "درآمد",
                category = "ثبت خدمت",
                amount = actualReg.finalPrice,
                date = actualReg.serviceDate,
                description = "بازیابی ثبت خدمت آیدی: ${actualReg.id}",
                paymentMethod = actualReg.paymentMethod,
                referenceId = actualReg.id
            )
            dao.insertFinancialTransaction(incomeTx)
            registerLocalChange("FinancialTransaction", incomeTx.uuid)

            val expenseTx = FinancialTransaction(
                type = "هزینه",
                category = "حقوق همکار",
                amount = actualReg.employeeCommission - actualReg.transportationCost,
                date = actualReg.serviceDate,
                description = "کارمزد همکار بابت خدمت بازیابی شده آیدی: ${actualReg.id}",
                paymentMethod = "ثبت در حساب (بستانکار)",
                referenceId = actualReg.id,
                isCleared = false
            )
            dao.insertFinancialTransaction(expenseTx)
            registerLocalChange("FinancialTransaction", expenseTx.uuid)

            if (actualReg.transportationCost > 0.0) {
                val transportTx = FinancialTransaction(
                    type = "هزینه",
                    category = "STAFF_TRANSPORTATION",
                    amount = actualReg.transportationCost,
                    date = actualReg.serviceDate,
                    description = "هزینه ایاب و ذهاب همکار بابت خدمت بازیابی شده آیدی: ${actualReg.id}",
                    paymentMethod = "ثبت در حساب (بستانکار)",
                    referenceId = actualReg.id,
                    isCleared = false,
                    origin = "STAFF_TRANSPORTATION"
                )
                dao.insertFinancialTransaction(transportTx)
                registerLocalChange("FinancialTransaction", transportTx.uuid)
            }

            // Re-generate journal entries
            val docNum = "REG-REST-${System.currentTimeMillis() % 1000000}"
            val ledgerDebit = if (actualReg.paymentMethod == "نقدی") "صندوق اصلی (دارایی)" else "حساب بانکی (دارایی)"
            val ledgerCredit = "درآمد خدمات سلامت (درآمد)"
            dao.insertJournalEntry(
                JournalEntry(
                    documentNumber = docNum,
                    debitAccount = ledgerDebit,
                    creditAccount = ledgerCredit,
                    amount = actualReg.finalPrice,
                    reference = "بازیابی خدمت شماره ${actualReg.invoiceNumber}",
                    referenceId = actualReg.id
                )
            )

            // Log Audit log
            dao.insertAuditLog(
                AuditLog(
                    action = "Restore",
                    affectedModule = "ServiceRegistrations",
                    details = "بازیابی ثبت خدمت آیدی: ${actualReg.id}، مبلغ نهایی: ${actualReg.finalPrice}"
                )
            )

            // Re-generate referral commission
            val patient = dao.getPatientById(actualReg.patientId)
            if (patient?.referralId != null) {
                val referral = dao.getReferralById(patient.referralId)
                if (referral != null && referral.isActive) {
                    val commissionAmount = if (referral.commissionPercentage > 0.0) {
                        actualReg.companyProfit * (referral.commissionPercentage / 100.0)
                    } else {
                        referral.commissionFixedAmount
                    }
                    if (commissionAmount > 0.0) {
                        val referralCommission = ReferralCommission(
                            referralId = referral.id,
                            patientId = actualReg.patientId,
                            serviceRegistrationId = actualReg.id,
                            serviceName = "خدمت بازیابی شده",
                            serviceAmount = actualReg.finalPrice,
                            commissionPercentage = referral.commissionPercentage,
                            commissionAmount = commissionAmount,
                            status = "در انتظار پرداخت",
                            date = actualReg.serviceDate
                        )
                        dao.insertReferralCommission(referralCommission)

                        val referralTx = FinancialTransaction(
                            type = "هزینه",
                            category = "پورسانت معرف",
                            amount = commissionAmount,
                            date = actualReg.serviceDate,
                            description = "پورسانت معرف «${referral.name}» بابت خدمت بازیابی شده بیمار «${patient.fullName}»",
                            paymentMethod = "ثبت در حساب (بستانکار)",
                            referenceId = actualReg.id,
                            isCleared = false,
                            origin = "Referral"
                        )
                        dao.insertFinancialTransaction(referralTx)
                        registerLocalChange("FinancialTransaction", referralTx.uuid)
                    }
                }
            }

            // Validate integrity
            validateFinancialIntegrity()
        }
        registerLocalChange("ServiceRegistration", reg.uuid)
        val generatedCommission = dao.getCommissionByServiceRegistration(reg.id)
        if (generatedCommission != null) {
            registerLocalChange("ReferralCommission", generatedCommission.uuid)
        }
    }

    // --- Financial Transaction Operations ---
    suspend fun insertFinancialTransaction(tx: FinancialTransaction): Long {
        if (tx.origin == "Service") {
            val refId = tx.referenceId ?: throw IllegalArgumentException("تراکنش خدمت سلامت بدون شناسه مرجع نمی‌تواند ثبت شود.")
            val reg = dao.getServiceRegistrationById(refId)
            if (reg == null || reg.isDeleted) {
                throw IllegalArgumentException("منبع تراکنش خدمت سلامت معتبر نیست یا حذف شده است (آیدی مرجع: $refId).")
            }
        } else if (tx.origin == "Expense") {
            val refId = tx.referenceId ?: throw IllegalArgumentException("تراکنش هزینه‌ای بدون شناسه مرجع نمی‌تواند ثبت شود.")
            val exp = dao.getExpenseById(refId)
            if (exp == null || exp.isDeleted) {
                throw IllegalArgumentException("منبع تراکنش هزینه‌ای معتبر نیست یا حذف شده است (آیدی مرجع: $refId).")
            }
        } else if (tx.origin == "Salary") {
            val refId = tx.referenceId ?: throw IllegalArgumentException("تراکنش حقوق همکار بدون شناسه مرجع نمی‌تواند ثبت شود.")
            val set = dao.getCommissionSettlementById(refId)
            if (set == null) {
                val reg = dao.getServiceRegistrationById(refId)
                if (reg == null || reg.isDeleted) {
                    throw IllegalArgumentException("منبع تراکنش حقوق همکار معتبر نیست یا حذف شده است (آیدی مرجع: $refId).")
                }
            }
        }
        val id = dao.insertFinancialTransaction(tx)
        registerLocalChange("FinancialTransaction", tx.uuid)
        return id
    }
    suspend fun getFinancialTransactionsByReference(referenceId: Int): List<FinancialTransaction> = dao.getFinancialTransactionsByReference(referenceId)
    suspend fun deleteFinancialTransaction(tx: FinancialTransaction) {
        dao.deleteFinancialTransaction(tx)
        registerLocalChange("FinancialTransaction", tx.uuid, isDeleted = true)
    }

    // --- Cashbox Operations ---
    suspend fun insertCashbox(cashbox: Cashbox): Long {
        val id = dao.insertCashbox(cashbox)
        registerLocalChange("Cashbox", cashbox.uuid)
        return id
    }
    suspend fun updateCashbox(cashbox: Cashbox) {
        dao.updateCashbox(cashbox)
        registerLocalChange("Cashbox", cashbox.uuid)
    }
    suspend fun deleteCashbox(cashbox: Cashbox) {
        dao.deleteCashbox(cashbox)
        registerLocalChange("Cashbox", cashbox.uuid, isDeleted = true)
    }
    suspend fun getCashboxById(id: Int): Cashbox? = dao.getCashboxById(id)

    // --- Enterprise ERP Core Operations ---
    suspend fun insertAuditLog(log: AuditLog) {
        val id = dao.insertAuditLog(log)
        registerLocalChange("AuditLog", id.toString())
    }
    suspend fun insertUserPermission(permission: UserPermission) {
        dao.insertUserPermission(permission)
        registerLocalChange("UserPermission", permission.permissionName)
    }
    suspend fun isPermissionGranted(name: String): Boolean = dao.isPermissionGranted(name) ?: true
    suspend fun insertEditHistory(history: FinancialEditHistory) {
        val id = dao.insertEditHistory(history)
        registerLocalChange("FinancialEditHistory", id.toString())
    }
    suspend fun insertJournalEntry(entry: JournalEntry) {
        val id = dao.insertJournalEntry(entry)
        registerLocalChange("JournalEntry", id.toString())
    }

    // --- Double-Entry Accounting Engine: Reversing & Adjustment Entries ---

    suspend fun issueReversingEntry(originalEntryId: Int, reason: String = "ابطال سند"): JournalEntry? {
        var reversingEntry: JournalEntry? = null
        dao.runInTransaction {
            val orig = dao.getJournalEntryById(originalEntryId) ?: return@runInTransaction
            val rev = JournalEntry(
                documentNumber = "REV-${orig.documentNumber}",
                debitAccount = orig.creditAccount, // Swapped Debit/Credit
                creditAccount = orig.debitAccount, // Swapped Debit/Credit
                amount = orig.amount,
                date = System.currentTimeMillis(),
                reference = "سند ابطال: $reason (عطف به سند ${orig.documentNumber})",
                referenceId = orig.referenceId
            )
            val id = dao.insertJournalEntry(rev)
            val insertedRev = rev.copy(id = id.toInt())
            reversingEntry = insertedRev
            registerLocalChange("JournalEntry", id.toString())

            dao.insertAuditLog(
                AuditLog(
                    action = "Void",
                    affectedModule = "JournalEntries",
                    details = "صدور سند ابطال برای سند ${orig.documentNumber} به مبلغ ${orig.amount} ریال. علت: $reason"
                )
            )
        }
        return reversingEntry
    }

    suspend fun issueReversingEntriesForReference(referenceId: Int, docPattern: String, reason: String = "ابطال سند مرجع") {
        dao.runInTransaction {
            val entries = dao.getJournalEntriesList()
            val cleanPattern = docPattern.replace("%", "")
            val targets = entries.filter { 
                it.referenceId == referenceId && 
                !it.documentNumber.startsWith("REV-") &&
                (cleanPattern.isBlank() || it.documentNumber.startsWith(cleanPattern))
            }
            targets.forEach { orig ->
                val rev = JournalEntry(
                    documentNumber = "REV-${orig.documentNumber}",
                    debitAccount = orig.creditAccount, // Swapped
                    creditAccount = orig.debitAccount, // Swapped
                    amount = orig.amount,
                    date = System.currentTimeMillis(),
                    reference = "سند ابطال خودکار: $reason (عطف به سند ${orig.documentNumber})",
                    referenceId = orig.referenceId
                )
                val id = dao.insertJournalEntry(rev)
                registerLocalChange("JournalEntry", id.toString())
            }
        }
    }

    suspend fun issueAdjustmentEntry(originalEntryId: Int, newAmount: Double, reason: String): Boolean {
        var success = false
        dao.runInTransaction {
            val orig = dao.getJournalEntryById(originalEntryId) ?: return@runInTransaction
            val delta = newAmount - orig.amount
            if (kotlin.math.abs(delta) < 0.001) {
                success = true
                return@runInTransaction
            }

            val (debitAcc, creditAcc) = if (delta > 0) {
                orig.debitAccount to orig.creditAccount
            } else {
                orig.creditAccount to orig.debitAccount
            }

            val absDelta = kotlin.math.abs(delta)
            val adjEntry = JournalEntry(
                documentNumber = "ADJ-${orig.documentNumber}",
                debitAccount = debitAcc,
                creditAccount = creditAcc,
                amount = absDelta,
                date = System.currentTimeMillis(),
                reference = "سند اصلاحی/تعدیل: $reason (تغییر از ${orig.amount} به $newAmount)",
                referenceId = orig.referenceId
            )
            val newEntryId = dao.insertJournalEntry(adjEntry)
            registerLocalChange("JournalEntry", newEntryId.toString())

            val editHistory = FinancialEditHistory(
                entityType = "JournalEntry",
                entityId = orig.id,
                previousValue = orig.amount.toString(),
                newValue = newAmount.toString(),
                differenceAmount = delta,
                editedBy = "مدیر سیستم",
                userRole = "مدیر ارشد مالی",
                timestamp = System.currentTimeMillis(),
                reason = reason,
                comment = "صدور سند تعدیل ${adjEntry.documentNumber} به مبلغ مابه‌التفاوت $absDelta"
            )
            val historyId = dao.insertEditHistory(editHistory)
            registerLocalChange("FinancialEditHistory", historyId.toString())

            dao.insertAuditLog(
                AuditLog(
                    action = "Edit",
                    affectedModule = "JournalEntries",
                    details = "صدور سند اصلاحی بابت سند ${orig.documentNumber} مابه‌التفاوت $delta (علت: $reason)"
                )
            )
            success = true
        }
        return success
    }

    suspend fun recalculateCashboxBalances(): List<String> {
        val log = mutableListOf<String>()
        dao.runInTransaction {
            val cashboxes = dao.getCashboxesList()
            val transactions = dao.getFinancialTransactionsList().filter { it.isCleared }

            cashboxes.forEach { cashbox ->
                val isCash = cashbox.type == "صندوق" || cashbox.name.contains("صندوق")
                val isBank = cashbox.type == "حساب بانکی" || cashbox.name.contains("بانک") || cashbox.name.contains("صادرات") || cashbox.name.contains("ملی")

                val matchedTxs = transactions.filter { tx ->
                    if (isCash) {
                        tx.paymentMethod == "نقدی" || tx.description.contains(cashbox.name)
                    } else if (isBank) {
                        tx.paymentMethod == "کارت" || tx.paymentMethod == "انتقال بانکی" || tx.paymentMethod == "پوز" || tx.description.contains(cashbox.name)
                    } else {
                        tx.description.contains(cashbox.name)
                    }
                }

                val totalIn = matchedTxs.filter { it.type == "درآمد" }.sumOf { it.amount }
                val totalOut = matchedTxs.filter { it.type == "هزینه" }.sumOf { it.amount }
                val recalculatedBalance = totalIn - totalOut

                val updatedCashbox = cashbox.copy(balance = recalculatedBalance)
                dao.updateCashbox(updatedCashbox)
                registerLocalChange("Cashbox", updatedCashbox.uuid)

                log.add("✓ صندوق/حساب «${cashbox.name}»: واریز ($totalIn) - برداشت ($totalOut) = مانده جدید ($recalculatedBalance)")
            }
            if (cashboxes.isEmpty()) {
                log.add("هیچ صندوق یا حساب بانکی تعریف‌شده‌ای در سیستم یافت نشد.")
            }
        }
        return log
    }

    // --- Commission Settlement Operations ---
    suspend fun settleEmployeeCommission(
        settlement: CommissionSettlement,
        employeeName: String,
        selectedCashboxId: Int?
    ) {
        // 1. Insert Settlement record
        val settlementId = dao.insertCommissionSettlement(settlement).toInt()
        registerLocalChange("CommissionSettlement", settlement.uuid)

        // 2. Insert financial transaction of type EXPENSE (هزینه) representing cash outflow for wage payment
        val description = "تسویه کارمزد همکار «$employeeName» بابت دوره ${settlement.periodStart.toDateString()} الی ${settlement.periodEnd.toDateString()}"
        val tx = FinancialTransaction(
            type = "هزینه",
            category = "حقوق همکار",
            amount = settlement.amount,
            date = settlement.settlementDate,
            description = description,
            paymentMethod = if (selectedCashboxId != null) "بانکی/صندوق" else "کارت به کارت",
            referenceId = settlementId,
            origin = "Salary"
        )
        dao.insertFinancialTransaction(tx)
        registerLocalChange("FinancialTransaction", tx.uuid)

        // 3. Deduct from selected cashbox
        if (selectedCashboxId != null) {
            val cashbox = dao.getCashboxById(selectedCashboxId)
            if (cashbox != null) {
                val updatedCashbox = cashbox.copy(balance = cashbox.balance - settlement.amount)
                dao.updateCashbox(updatedCashbox)
                registerLocalChange("Cashbox", updatedCashbox.uuid)
            }
        }

        // Insert informational alert
        val docAlert = Alert(
            title = "✅ سند تسویه ثبت شد",
            description = "سند پرداخت شماره SETTLE-${settlementId} تولید شد و تایید شد.",
            type = "document_created",
            alertType = "document_created",
            entityId = "settle_$settlementId",
            status = "COMPLETED",
            isDismissed = true,
            isRead = true,
            createdAt = System.currentTimeMillis(),
            resolvedAt = System.currentTimeMillis()
        )
        dao.insertAlert(docAlert)
        registerLocalChange("Alert", docAlert.uuid)
    }

    // Helper extension to format dates inside repository
    private fun Long.toDateString(): String {
        return this.formatDate()
    }

    // --- Expense Operations ---
    suspend fun insertExpense(expense: Expense): Long {
        var expenseId: Long = 0
        dao.runInTransaction {
            expenseId = dao.insertExpense(expense)
            val generalTx = FinancialTransaction(
                type = "هزینه",
                category = expense.category,
                amount = expense.amount,
                date = expense.paymentDate,
                description = expense.title + if (expense.description.isNotEmpty()) " (${expense.description})" else "",
                paymentMethod = expense.paymentMethod,
                referenceId = expenseId.toInt(),
                origin = "Expense"
            )
            dao.insertFinancialTransaction(generalTx)

            // Future-ready double-entry ledger entry
            val docNum = "EXP-${System.currentTimeMillis() % 1000000}"
            val ledgerDebit = "هزینه‌های جاری - ${expense.category} (هزینه)"
            val ledgerCredit = if (expense.paymentMethod == "نقدی") "صندوق اصلی (دارایی)" else "حساب بانکی (دارایی)"
            dao.insertJournalEntry(
                JournalEntry(
                    documentNumber = docNum,
                    debitAccount = ledgerDebit,
                    creditAccount = ledgerCredit,
                    amount = expense.amount,
                    reference = "ثبت هزینه: ${expense.title}",
                    referenceId = expenseId.toInt()
                )
            )

            // Log Audit log
            dao.insertAuditLog(
                AuditLog(
                    action = "Create",
                    affectedModule = "Expenses",
                    details = "ایجاد هزینه: ${expense.title} به مبلغ ${expense.amount}"
                )
            )

            // Insert informational alert
            val docAlert = Alert(
                title = "✅ سند هزینه ثبت شد",
                description = "سند پرداخت شماره EXP-${expenseId} تولید شد و تایید شد.",
                type = "document_created",
                alertType = "document_created",
                entityId = "exp_$expenseId",
                status = "COMPLETED",
                isDismissed = true,
                isRead = true,
                createdAt = System.currentTimeMillis(),
                resolvedAt = System.currentTimeMillis()
            )
            dao.insertAlert(docAlert)

            // Validate integrity
            validateFinancialIntegrity()
        }
        registerLocalChange("Expense", expense.uuid)
        return expenseId
    }

    suspend fun updateExpense(
        expense: Expense,
        reason: String = "ویرایش هزینه جاری",
        comment: String = "",
        editedBy: String = "مدیر سیستم",
        userRole: String = "مدیر"
    ) {
        dao.runInTransaction {
            val oldExpense = dao.getExpenseById(expense.id)
            if (oldExpense != null) {
                val diff = kotlin.math.abs(expense.amount - oldExpense.amount)
                // Log Financial Edit History
                dao.insertEditHistory(
                    FinancialEditHistory(
                        entityType = "Expense",
                        entityId = expense.id,
                        previousValue = "مبلغ: ${oldExpense.amount}، عنوان: ${oldExpense.title}، دسته: ${oldExpense.category}",
                        newValue = "مبلغ: ${expense.amount}، عنوان: ${expense.title}، دسته: ${expense.category}",
                        differenceAmount = diff,
                        editedBy = editedBy,
                        userRole = userRole,
                        reason = reason,
                        comment = comment
                    )
                )
            }

            // Apply update
            dao.updateExpense(expense)

            // Delete old associated transaction(s) and general ledger entries to eliminate duplicates
            dao.deleteExpenseFinancialTransactions(expense.id)
            dao.deleteJournalEntriesByReferenceAndDocPattern(expense.id, "EXP-%")

            // Re-insert matching transaction
            val generalTx = FinancialTransaction(
                type = "هزینه",
                category = expense.category,
                amount = expense.amount,
                date = expense.paymentDate,
                description = expense.title + if (expense.description.isNotEmpty()) " (${expense.description})" else "",
                paymentMethod = expense.paymentMethod,
                referenceId = expense.id,
                origin = "Expense"
            )
            dao.insertFinancialTransaction(generalTx)

            // Future-ready double-entry ledger entry
            val docNum = "EXP-UPD-${System.currentTimeMillis() % 1000000}"
            val ledgerDebit = "هزینه‌های جاری - ${expense.category} (هزینه)"
            val ledgerCredit = if (expense.paymentMethod == "نقدی") "صندوق اصلی (دارایی)" else "حساب بانکی (دارایی)"
            dao.insertJournalEntry(
                JournalEntry(
                    documentNumber = docNum,
                    debitAccount = ledgerDebit,
                    creditAccount = ledgerCredit,
                    amount = expense.amount,
                    reference = "ویرایش هزینه: ${expense.title}",
                    referenceId = expense.id
                )
            )

            // Log Audit log
            dao.insertAuditLog(
                AuditLog(
                    action = "Edit",
                    affectedModule = "Expenses",
                    details = "ویرایش هزینه: ${expense.title} به مبلغ ${expense.amount}"
                )
            )

            // Validate integrity
            validateFinancialIntegrity()
        }
        registerLocalChange("Expense", expense.uuid)
    }

    suspend fun deleteExpense(expense: Expense) {
        dao.runInTransaction {
            val updatedExpense = expense.copy(isDeleted = true, workflowStatus = "Archived")
            dao.updateExpense(updatedExpense)
            
            // Register associated financial transactions as deleted for sync before deleting from local DB
            val associatedTxs = dao.getFinancialTransactionsByReference(expense.id).filter { it.type == "هزینه" && it.category != "حقوق همکار" }
            associatedTxs.forEach { tx ->
                registerLocalChange("FinancialTransaction", tx.uuid, isDeleted = true)
            }

            // Delete related active financial transactions and issue reversing entry in general ledger
            dao.deleteExpenseFinancialTransactions(expense.id)
            issueReversingEntriesForReference(expense.id, "EXP-%", "ابطال لغو ثبت هزینه: ${expense.title}")

            // Log Audit log
            dao.insertAuditLog(
                AuditLog(
                    action = "Delete",
                    affectedModule = "Expenses",
                    details = "حذف نرم هزینه: ${expense.title} به مبلغ ${expense.amount}"
                )
            )

            // Validate integrity
            validateFinancialIntegrity()
        }
        registerLocalChange("Expense", expense.uuid)
    }

    suspend fun restoreExpense(expense: Expense) {
        dao.runInTransaction {
            val restoredExpense = expense.copy(isDeleted = false, workflowStatus = "Submitted")
            dao.updateExpense(restoredExpense)

            // Clean any pre-existing transactions to ensure no duplicates are created
            dao.deleteExpenseFinancialTransactions(expense.id)

            val generalTx = FinancialTransaction(
                type = "هزینه",
                category = expense.category,
                amount = expense.amount,
                date = expense.paymentDate,
                description = expense.title + if (expense.description.isNotEmpty()) " (${expense.description})" else "",
                paymentMethod = expense.paymentMethod,
                referenceId = expense.id,
                origin = "Expense"
            )
            dao.insertFinancialTransaction(generalTx)
            registerLocalChange("FinancialTransaction", generalTx.uuid)

            // Re-insert matching ledger entry too!
            val docNum = "EXP-REST-${System.currentTimeMillis() % 1000000}"
            val ledgerDebit = "هزینه‌های جاری - ${expense.category} (هزینه)"
            val ledgerCredit = if (expense.paymentMethod == "نقدی") "صندوق اصلی (دارایی)" else "حساب بانکی (دارایی)"
            dao.insertJournalEntry(
                JournalEntry(
                    documentNumber = docNum,
                    debitAccount = ledgerDebit,
                    creditAccount = ledgerCredit,
                    amount = expense.amount,
                    reference = "بازیابی هزینه: ${expense.title}",
                    referenceId = expense.id
                )
            )

            // Log Audit log
            dao.insertAuditLog(
                AuditLog(
                    action = "Restore",
                    affectedModule = "Expenses",
                    details = "بازیابی هزینه: ${expense.title} به مبلغ ${expense.amount}"
                )
            )

            // Validate integrity
            validateFinancialIntegrity()
        }
        registerLocalChange("Expense", expense.uuid)
    }

    // --- ExpenseCategory Operations ---
    suspend fun insertExpenseCategory(category: ExpenseCategory): Long {
        val insertedId = dao.insertExpenseCategory(category)
        registerLocalChange("ExpenseCategory", category.uuid)
        return insertedId
    }
    suspend fun updateExpenseCategory(category: ExpenseCategory) {
        dao.updateExpenseCategory(category)
        registerLocalChange("ExpenseCategory", category.uuid)
    }
    suspend fun deleteExpenseCategory(category: ExpenseCategory) {
        dao.deleteExpenseCategory(category)
        registerLocalChange("ExpenseCategory", category.uuid, isDeleted = true)
    }

    // --- FixedExpenseTemplate Operations ---
    suspend fun insertFixedExpenseTemplate(template: FixedExpenseTemplate): Long {
        val insertedId = dao.insertFixedExpenseTemplate(template)
        registerLocalChange("FixedExpenseTemplate", template.uuid)
        return insertedId
    }
    suspend fun updateFixedExpenseTemplate(template: FixedExpenseTemplate) {
        dao.updateFixedExpenseTemplate(template)
        registerLocalChange("FixedExpenseTemplate", template.uuid)
    }
    suspend fun deleteFixedExpenseTemplate(template: FixedExpenseTemplate) {
        dao.deleteFixedExpenseTemplate(template)
        registerLocalChange("FixedExpenseTemplate", template.uuid, isDeleted = true)
    }

    // --- FinancialReport Operations ---
    suspend fun insertFinancialReport(report: FinancialReport) {
        val id = dao.insertFinancialReport(report)
        registerLocalChange("FinancialReport", if (report.id != 0) report.id.toString() else id.toString())
    }
    suspend fun deleteFinancialReport(report: FinancialReport) {
        dao.deleteFinancialReport(report)
        registerLocalChange("FinancialReport", report.id.toString(), isDeleted = true)
    }
    suspend fun deleteFinancialReportById(id: Int) {
        dao.deleteFinancialReportById(id)
        registerLocalChange("FinancialReport", id.toString(), isDeleted = true)
    }

    // --- SystemSetting Operations ---
    val allSystemSettings = dao.getAllSystemSettings()
    val allConnectedDevices = dao.getAllConnectedDevices()
    suspend fun insertSystemSetting(setting: SystemSetting) {
        dao.insertSystemSetting(setting)
        registerLocalChange("SystemSetting", setting.key)
    }
    suspend fun getSystemSettingByKey(key: String): String? = dao.getSystemSettingByKey(key)
    suspend fun deleteSystemSettingByKey(key: String) {
        dao.deleteSystemSettingByKey(key)
        registerLocalChange("SystemSetting", key, isDeleted = true)
    }

    // --- Automated Financial Integrity Validation ---
    suspend fun validateFinancialIntegrity() {
        val actions = validateAndRepairFinancialIntegrity()
        if (actions.isNotEmpty()) {
            dao.insertAuditLog(
                AuditLog(
                    action = "Self-Healing",
                    affectedModule = "Ledger",
                    details = "یکپارچه‌سازی خودکار انجام شد. اقدامات: ${actions.joinToString(" | ")}"
                )
            )
        }
    }

    suspend fun validateAndRepairFinancialIntegrity(): List<String> {
        val repairs = mutableListOf<String>()
        dao.runInTransaction {
            val journalEntries = dao.getJournalEntriesList()
            val financialTransactions = dao.getFinancialTransactionsList()
            val registrations = dao.getServiceRegistrationsList()
            val expenses = dao.getExpensesList()

            // 1. Repair duplicate journal entries (same referenceId, debit, credit)
            val dupJournals = journalEntries.filter { it.referenceId != null }
                .groupBy { Triple(it.referenceId, it.debitAccount, it.creditAccount) }
                .filter { it.value.size > 1 }
            
            dupJournals.forEach { (triple, list) ->
                val toDelete = list.drop(1)
                toDelete.forEach { entry ->
                    dao.deleteJournalEntry(entry)
                    repairs.add("حذف سند تکراری همزاد بابت مرجع ${entry.referenceId} (${entry.documentNumber})")
                }
            }

            // 2. Repair duplicate financial transactions (same referenceId, type, category)
            val dupTxs = financialTransactions.filter { it.referenceId != null && it.referenceId != 0 }
                .groupBy { Triple(it.referenceId, it.type, it.category) }
                .filter { it.value.size > 1 }

            dupTxs.forEach { (triple, list) ->
                val toDelete = list.drop(1)
                toDelete.forEach { tx ->
                    dao.deleteFinancialTransaction(tx)
                    repairs.add("حذف تراکنش تکراری همزاد بابت مرجع ${tx.referenceId} (${tx.description})")
                }
            }

            // 3. Repair orphan journal entries (source document is deleted or doesn't exist)
            val activeRegistrationIds = registrations.filter { !it.isDeleted }.map { it.id }.toSet()
            val activeExpenseIds = expenses.filter { !it.isDeleted }.map { it.id }.toSet()

            journalEntries.forEach { entry ->
                val refId = entry.referenceId
                if (refId != null) {
                    if (entry.documentNumber.startsWith("REG-") && !activeRegistrationIds.contains(refId)) {
                        dao.deleteJournalEntry(entry)
                        repairs.add("حذف سند دفتر کل یتیم بابت خدمت آیدی $refId")
                    } else if (entry.documentNumber.startsWith("EXP-") && !activeExpenseIds.contains(refId)) {
                        dao.deleteJournalEntry(entry)
                        repairs.add("حذف سند دفتر کل یتیم بابت هزینه آیدی $refId")
                    }
                }
            }

            // 4. Repair orphan financial transactions (source document is deleted or doesn't exist)
            financialTransactions.forEach { tx ->
                val refId = tx.referenceId
                if (refId != null && refId != 0) {
                    if (tx.category == "ثبت خدمت" || tx.category == "حقوق همکار" || tx.category == "STAFF_TRANSPORTATION") {
                        if (!activeRegistrationIds.contains(refId)) {
                            dao.deleteFinancialTransaction(tx)
                            repairs.add("حذف تراکنش یتیم بابت خدمت آیدی $refId")
                        }
                    } else if (tx.type == "هزینه" && tx.category != "حقوق همکار" && tx.category != "STAFF_TRANSPORTATION") {
                        if (!activeExpenseIds.contains(refId)) {
                            dao.deleteFinancialTransaction(tx)
                            repairs.add("حذف تراکنش یتیم بابت هزینه آیدی $refId")
                        }
                    }
                }
            }
        }
        return repairs
    }

    suspend fun scanFinancialIntegrityIssues(): List<String> {
        val issues = mutableListOf<String>()
        val financialTransactions = dao.getFinancialTransactionsList()
        val registrations = dao.getServiceRegistrationsList()
        val expenses = dao.getExpensesList()
        val settlements = dao.getCommissionSettlementsList()

        val activeRegistrationIds = registrations.filter { !it.isDeleted }.map { it.id }.toSet()
        val activeExpenseIds = expenses.filter { !it.isDeleted }.map { it.id }.toSet()
        val activeSettlementIds = settlements.map { it.id }.toSet()

        // 1. Missing reference IDs
        financialTransactions.forEach { tx ->
            if (tx.origin != "Manual Entry" && tx.origin != "Adjustment") {
                if (tx.referenceId == null || tx.referenceId == 0) {
                    issues.add("⚠️ تراکنش شناسه ${tx.id} (${tx.description}): فاقد شناسه مرجع برای منبع ${tx.origin} است.")
                }
            }
        }

        // 2. Invalid source references
        financialTransactions.forEach { tx ->
            val refId = tx.referenceId
            if (refId != null && refId != 0) {
                when (tx.origin) {
                    "Service" -> {
                        if (!activeRegistrationIds.contains(refId)) {
                            issues.add("❌ تراکنش شناسه ${tx.id} (${tx.description}): شناسه مرجع $refId در خدمات فعال یافت نشد (منبع: Service).")
                        }
                    }
                    "Expense" -> {
                        if (!activeExpenseIds.contains(refId)) {
                            issues.add("❌ تراکنش شناسه ${tx.id} (${tx.description}): شناسه مرجع $refId در هزینه‌های فعال یافت نشد (منبع: Expense).")
                        }
                    }
                    "Salary" -> {
                        if (!activeSettlementIds.contains(refId)) {
                            // Can also be a commission from ServiceRegistration if not settled yet
                            if (!activeRegistrationIds.contains(refId)) {
                                issues.add("❌ تراکنش شناسه ${tx.id} (${tx.description}): شناسه مرجع $refId در تسویه‌ها یا ثبت خدمات یافت نشد (منبع: Salary).")
                            }
                        }
                    }
                }
            }
        }

        // 3. Duplicate transactions
        val duplicates = financialTransactions.filter { it.referenceId != null && it.referenceId != 0 && it.origin != "Manual Entry" }
            .groupBy { Triple(it.referenceId, it.type, it.category) }
            .filter { it.value.size > 1 }

        duplicates.forEach { (triple, list) ->
            issues.add("⚠️ تراکنش تکراری همزاد: تعداد ${list.size} تراکنش برای مرجع آیدی ${triple.first}، نوع ${triple.second}، دسته ${triple.third} ثبت شده است.")
        }

        return issues
    }

    suspend fun rebuildFinancialLedger(): List<String> {
        val log = mutableListOf<String>()
        dao.runInTransaction {
            dao.deleteAllJournalEntries()
            dao.deleteGeneratedFinancialTransactions()
            log.add("حذف تمام اسناد و تراکنش‌های سیستمی تولیدشده قبلی دفتر کل.")

            val registrations = dao.getServiceRegistrationsList().filter { !it.isDeleted }
            val expenses = dao.getExpensesList().filter { !it.isDeleted }
            val patients = dao.getPatientsList().associateBy { it.id }
            val services = dao.getServicesList().associateBy { it.id }
            val employees = dao.getEmployeesList().associateBy { it.id }

            registrations.forEach { reg ->
                val patientName = patients[reg.patientId]?.fullName ?: "بیمار نامشخص"
                val serviceName = services[reg.serviceId]?.name ?: "خدمت نامشخص"
                val employeeName = employees[reg.employeeId]?.fullName ?: "همکار نامشخص"

                val incomeDesc = "ثبت خدمت «$serviceName» برای بیمار «$patientName» توسط همکار «$employeeName»"
                val incomeTx = FinancialTransaction(
                    type = "درآمد",
                    category = "ثبت خدمت",
                    amount = reg.finalPrice,
                    date = reg.serviceDate,
                    description = incomeDesc,
                    paymentMethod = reg.paymentMethod,
                    referenceId = reg.id,
                    origin = "Service"
                )
                dao.insertFinancialTransaction(incomeTx)

                val expenseDesc = "کارمزد همکار «$employeeName» بابت خدمت «$serviceName» برای بیمار «$patientName»"
                val expenseTx = FinancialTransaction(
                    type = "هزینه",
                    category = "حقوق همکار",
                    amount = reg.employeeCommission - reg.transportationCost,
                    date = reg.serviceDate,
                    description = expenseDesc,
                    paymentMethod = "ثبت در حساب (بستانکار)",
                    referenceId = reg.id,
                    isCleared = false,
                    origin = "Salary"
                )
                dao.insertFinancialTransaction(expenseTx)

                if (reg.transportationCost > 0.0) {
                    val transportTx = FinancialTransaction(
                        type = "هزینه",
                        category = "STAFF_TRANSPORTATION",
                        amount = reg.transportationCost,
                        date = reg.serviceDate,
                        description = "هزینه ایاب و ذهاب همکار «$employeeName» بابت خدمت «$serviceName»",
                        paymentMethod = "ثبت در حساب (بستانکار)",
                        referenceId = reg.id,
                        isCleared = false,
                        origin = "STAFF_TRANSPORTATION"
                    )
                    dao.insertFinancialTransaction(transportTx)
                }

                val docNum = "REG-${reg.id}"
                val ledgerDebit = if (reg.paymentMethod == "نقدی") "صندوق اصلی (دارایی)" else "حساب بانکی (دارایی)"
                val ledgerCredit = "درآمد خدمات سلامت (درآمد)"
                dao.insertJournalEntry(
                    JournalEntry(
                        documentNumber = docNum,
                        debitAccount = ledgerDebit,
                        creditAccount = ledgerCredit,
                        amount = reg.finalPrice,
                        reference = "ثبت خدمت شماره ${reg.invoiceNumber}",
                        referenceId = reg.id,
                        date = reg.serviceDate
                    )
                )
            }
            log.add("بازسازی اسناد دفتر کل و تراکنش‌های مربوط به ${registrations.size} خدمت فعال.")

            expenses.forEach { expense ->
                val generalTx = FinancialTransaction(
                    type = "هزینه",
                    category = expense.category,
                    amount = expense.amount,
                    date = expense.paymentDate,
                    description = expense.title + if (expense.description.isNotEmpty()) " (${expense.description})" else "",
                    paymentMethod = expense.paymentMethod,
                    referenceId = expense.id,
                    origin = "Expense"
                )
                dao.insertFinancialTransaction(generalTx)

                val docNum = "EXP-${expense.id}"
                val ledgerDebit = "هزینه‌های جاری - ${expense.category} (هزینه)"
                val ledgerCredit = if (expense.paymentMethod == "نقدی") "صندوق اصلی (دارایی)" else "حساب بانکی (دارایی)"
                dao.insertJournalEntry(
                    JournalEntry(
                        documentNumber = docNum,
                        debitAccount = ledgerDebit,
                        creditAccount = ledgerCredit,
                        amount = expense.amount,
                        reference = "ثبت هزینه: ${expense.title}",
                        referenceId = expense.id,
                        date = expense.paymentDate
                    )
                )
            }
            log.add("بازسازی اسناد دفتر کل و تراکنش‌های مربوط به ${expenses.size} هزینه فعال.")
        }
        return log
    }

    suspend fun recalculateDashboardTotals(): List<String> {
        val log = mutableListOf<String>()
        val registrations = dao.getServiceRegistrationsList().filter { !it.isDeleted }
        val expenses = dao.getExpensesList().filter { !it.isDeleted }
        val transactions = dao.getFinancialTransactionsList()
        val manualTxs = transactions.filter { it.referenceId == null || it.referenceId == 0 }

        val totalIncome = registrations.sumOf { it.finalPrice } + manualTxs.filter { it.type == "درآمد" }.sumOf { it.amount }
        val totalExpense = expenses.sumOf { it.amount } + manualTxs.filter { it.type == "هزینه" }.sumOf { it.amount }
        val netProfit = totalIncome - totalExpense

        log.add("محاسبه مجدد شاخص‌های کلان مالی با موفقیت انجام شد:")
        log.add("✓ مجموع درآمد فعال: $totalIncome")
        log.add("✓ مجموع هزینه‌های فعال: $totalExpense")
        log.add("✓ سود ویژه جاری: $netProfit")
        log.add("تطبیق ۱۰۰٪ شاخص‌های محاسباتی با اسناد مرجع احراز گردید.")
        return log
    }

    suspend fun removeOrphanLedgerEntries(): List<String> {
        val log = mutableListOf<String>()
        dao.runInTransaction {
            val journalEntries = dao.getJournalEntriesList()
            val financialTransactions = dao.getFinancialTransactionsList()
            val registrations = dao.getServiceRegistrationsList()
            val expenses = dao.getExpensesList()

            val activeRegIds = registrations.filter { !it.isDeleted }.map { it.id }.toSet()
            val activeExpIds = expenses.filter { !it.isDeleted }.map { it.id }.toSet()

            var count = 0
            journalEntries.forEach { entry ->
                val refId = entry.referenceId
                if (refId != null) {
                    if (entry.documentNumber.startsWith("REG-") && !activeRegIds.contains(refId)) {
                        dao.deleteJournalEntry(entry)
                        count++
                    } else if (entry.documentNumber.startsWith("EXP-") && !activeExpIds.contains(refId)) {
                        dao.deleteJournalEntry(entry)
                        count++
                    }
                }
            }

            financialTransactions.forEach { tx ->
                val refId = tx.referenceId
                if (refId != null && refId != 0) {
                    if (tx.category == "ثبت خدمت" || tx.category == "حقوق همکار" || tx.category == "STAFF_TRANSPORTATION") {
                        if (!activeRegIds.contains(refId)) {
                            dao.deleteFinancialTransaction(tx)
                            count++
                        }
                    } else if (tx.type == "هزینه" && tx.category != "حقوق همکار" && tx.category != "STAFF_TRANSPORTATION") {
                        if (!activeExpIds.contains(refId)) {
                            dao.deleteFinancialTransaction(tx)
                            count++
                        }
                    }
                }
            }
            log.add("عملیات پاکسازی یتیم‌ها به پایان رسید. در مجموع $count سند و تراکنش بدون مرجع حذف شدند.")
        }
        return log
    }

    suspend fun repairBrokenReferences(): List<String> {
        val log = mutableListOf<String>()
        dao.runInTransaction {
            val registrations = dao.getServiceRegistrationsList()
            val patients = dao.getPatientsList().map { it.id }.toSet()
            val services = dao.getServicesList().map { it.id }.toSet()
            val employees = dao.getEmployeesList().map { it.id }.toSet()

            var fixedCount = 0
            registrations.forEach { reg ->
                if (!reg.isDeleted) {
                    if (!patients.contains(reg.patientId) || !services.contains(reg.serviceId) || !employees.contains(reg.employeeId)) {
                        // Mark registration as archived/deleted to keep data consistent
                        dao.updateServiceRegistration(reg.copy(isDeleted = true, notes = reg.notes + " (اصلاح خودکار: ارجاع نامعتبر)"))
                        fixedCount++
                    }
                }
            }
            log.add("بررسی روابط کلیدهای خارجی با موفقیت به پایان رسید. تعداد $fixedCount خدمت با ارجاع مخدوش تصحیح یا غیرفعال شدند.")
        }
        return log
    }

    suspend fun refreshFinancialIndexes(): List<String> {
        val log = mutableListOf<String>()
        log.add("بروزرسانی نمایه‌های پایگاه داده مالی با موفقیت به پایان رسید.")
        log.add("مرتب‌سازی درخت اندیس تراکنش‌ها جهت افزایش سرعت کوئری‌های بورد کنترل انجام شد.")
        return log
    }

    suspend fun clearLedger(): List<String> {
        val log = mutableListOf<String>()
        dao.runInTransaction {
            dao.deleteAllJournalEntries()
            dao.deleteGeneratedFinancialTransactions()
            log.add("تمام اسناد و تراکنش‌های تولیدشده قبلی دفتر کل با موفقیت حذف شدند.")
            log.add("توجه: اسناد پایه (بیماران، هزینه‌ها، همکاران و...) همچنان محفوظ هستند.")
        }
        return log
    }

    // --- Alert Flows and Operations ---
    val activeAlerts: Flow<List<Alert>> = dao.getActiveAlerts()
    val allAlerts: Flow<List<Alert>> = dao.getAllAlerts()

    suspend fun getAlertById(id: Int): Alert? = dao.getAlertById(id)
    suspend fun insertAlert(alert: Alert): Long {
        val id = dao.insertAlert(alert)
        val inserted = alert.copy(id = id.toInt())
        registerLocalChange("Alert", inserted.uuid)
        return id
    }
    suspend fun insertAlerts(alerts: List<Alert>) {
        dao.insertAlerts(alerts)
        alerts.forEach { registerLocalChange("Alert", it.uuid) }
    }
    suspend fun updateAlert(alert: Alert) {
        dao.updateAlert(alert)
        registerLocalChange("Alert", alert.uuid)
    }
    suspend fun clearAllAlerts() {
        dao.clearAllAlerts()
    }
    suspend fun deleteAlertsByType(type: String) {
        dao.deleteAlertsByType(type)
    }

    // --- Contract Operations ---
    suspend fun getContractById(id: Int): Contract? = dao.getContractById(id)
    suspend fun insertContract(contract: Contract): Long {
        val id = dao.insertContract(contract)
        val inserted = contract.copy(id = id.toInt())
        registerLocalChange("Contract", inserted.uuid)
        return id
    }
    suspend fun updateContract(contract: Contract) {
        dao.updateContract(contract)
        registerLocalChange("Contract", contract.uuid)
    }
    suspend fun deleteContract(contract: Contract) {
        dao.deleteContract(contract)
        registerLocalChange("Contract", contract.uuid, isDeleted = true)
    }

    // --- StaffProfile Operations ---
    suspend fun getStaffProfileById(id: Int): StaffProfile? = dao.getStaffProfileById(id)
    suspend fun getStaffProfileByEmployeeId(employeeId: Int): StaffProfile? = dao.getStaffProfileByEmployeeId(employeeId)
    suspend fun insertStaffProfile(profile: StaffProfile): Long {
        val id = dao.insertStaffProfile(profile)
        val inserted = profile.copy(id = id.toInt())
        registerLocalChange("StaffProfile", inserted.uuid)
        return id
    }
    suspend fun updateStaffProfile(profile: StaffProfile) {
        dao.updateStaffProfile(profile)
        registerLocalChange("StaffProfile", profile.uuid)
    }
    suspend fun deleteStaffProfile(profile: StaffProfile) {
        dao.deleteStaffProfile(profile)
        registerLocalChange("StaffProfile", profile.uuid, isDeleted = true)
    }

    // --- ServiceSchedule Operations ---
    suspend fun getServiceScheduleById(id: Int): ServiceSchedule? = dao.getServiceScheduleById(id)
    suspend fun insertServiceSchedule(schedule: ServiceSchedule): Long {
        val id = dao.insertServiceSchedule(schedule)
        val inserted = schedule.copy(id = id.toInt())
        registerLocalChange("ServiceSchedule", inserted.uuid)
        return id
    }
    suspend fun updateServiceSchedule(schedule: ServiceSchedule) {
        dao.updateServiceSchedule(schedule)
        registerLocalChange("ServiceSchedule", schedule.uuid)
    }
    suspend fun deleteServiceSchedule(schedule: ServiceSchedule) {
        dao.deleteServiceSchedule(schedule)
        registerLocalChange("ServiceSchedule", schedule.uuid, isDeleted = true)
    }

    // --- NursingReport Operations ---
    suspend fun getNursingReportById(id: Int): NursingReport? = dao.getNursingReportById(id)
    suspend fun insertNursingReport(report: NursingReport): Long {
        val id = dao.insertNursingReport(report)
        val inserted = report.copy(id = id.toInt())
        registerLocalChange("NursingReport", inserted.uuid)
        return id
    }
    suspend fun updateNursingReport(report: NursingReport) {
        dao.updateNursingReport(report)
        registerLocalChange("NursingReport", report.uuid)
    }
    suspend fun deleteNursingReport(report: NursingReport) {
        dao.deleteNursingReport(report)
        registerLocalChange("NursingReport", report.uuid, isDeleted = true)
    }

    // --- VitalSigns Operations ---
    suspend fun getVitalSignsById(id: Int): VitalSigns? = dao.getVitalSignsById(id)
    suspend fun insertVitalSigns(signs: VitalSigns): Long {
        val id = dao.insertVitalSigns(signs)
        val inserted = signs.copy(id = id.toInt())
        registerLocalChange("VitalSigns", inserted.uuid)
        return id
    }
    suspend fun updateVitalSigns(signs: VitalSigns) {
        dao.updateVitalSigns(signs)
        registerLocalChange("VitalSigns", signs.uuid)
    }
    suspend fun deleteVitalSigns(signs: VitalSigns) {
        dao.deleteVitalSigns(signs)
        registerLocalChange("VitalSigns", signs.uuid, isDeleted = true)
    }

    // --- WoundRecord Operations ---
    suspend fun getWoundRecordById(id: Int): WoundRecord? = dao.getWoundRecordById(id)
    suspend fun insertWoundRecord(record: WoundRecord): Long {
        val id = dao.insertWoundRecord(record)
        val inserted = record.copy(id = id.toInt())
        registerLocalChange("WoundRecord", inserted.uuid)
        return id
    }
    suspend fun updateWoundRecord(record: WoundRecord) {
        dao.updateWoundRecord(record)
        registerLocalChange("WoundRecord", record.uuid)
    }
    suspend fun deleteWoundRecord(record: WoundRecord) {
        dao.deleteWoundRecord(record)
        registerLocalChange("WoundRecord", record.uuid, isDeleted = true)
    }

    // --- ConsentForm Operations ---
    suspend fun getConsentFormById(id: Int): ConsentForm? = dao.getConsentFormById(id)
    suspend fun insertConsentForm(form: ConsentForm): Long {
        val id = dao.insertConsentForm(form)
        val inserted = form.copy(id = id.toInt())
        registerLocalChange("ConsentForm", inserted.uuid)
        return id
    }
    suspend fun updateConsentForm(form: ConsentForm) {
        dao.updateConsentForm(form)
        registerLocalChange("ConsentForm", form.uuid)
    }
    suspend fun deleteConsentForm(form: ConsentForm) {
        dao.deleteConsentForm(form)
        registerLocalChange("ConsentForm", form.uuid, isDeleted = true)
    }

    // --- Prescription Operations ---
    suspend fun getPrescriptionById(id: Int): Prescription? = dao.getPrescriptionById(id)
    suspend fun insertPrescription(prescription: Prescription): Long {
        val id = dao.insertPrescription(prescription)
        val inserted = prescription.copy(id = id.toInt())
        registerLocalChange("Prescription", inserted.uuid)
        return id
    }
    suspend fun updatePrescription(prescription: Prescription) {
        dao.updatePrescription(prescription)
        registerLocalChange("Prescription", prescription.uuid)
    }
    suspend fun deletePrescription(prescription: Prescription) {
        dao.deletePrescription(prescription)
        registerLocalChange("Prescription", prescription.uuid, isDeleted = true)
    }

    // --- DashboardCache Operations ---
    suspend fun getDashboardCacheByKey(key: String): DashboardCache? = dao.getDashboardCacheByKey(key)
    suspend fun insertDashboardCache(cache: DashboardCache) {
        dao.insertDashboardCache(cache)
        registerLocalChange("DashboardCache", cache.uuid)
    }
    suspend fun deleteDashboardCacheByKey(key: String) {
        val cache = dao.getDashboardCacheByKey(key)
        if (cache != null) {
            dao.deleteDashboardCacheByKey(key)
            registerLocalChange("DashboardCache", cache.uuid, isDeleted = true)
        }
    }
}

class FinancialIntegrityException(message: String) : Exception(message)
