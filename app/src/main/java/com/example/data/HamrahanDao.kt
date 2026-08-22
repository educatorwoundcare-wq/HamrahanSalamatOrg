package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HamrahanDao {

    // --- Patient Queries ---
    @Query("SELECT * FROM patients ORDER BY fullName ASC")
    fun getAllPatients(): Flow<List<Patient>>

    @Query("SELECT * FROM patients WHERE id = :id")
    suspend fun getPatientById(id: Int): Patient?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatient(patient: Patient): Long

    @Update
    suspend fun updatePatient(patient: Patient)

    @Delete
    suspend fun deletePatient(patient: Patient)


    // --- Employee Queries ---
    @Query("SELECT * FROM employees ORDER BY fullName ASC")
    fun getAllEmployees(): Flow<List<Employee>>

    @Query("SELECT * FROM employees WHERE id = :id")
    suspend fun getEmployeeById(id: Int): Employee?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmployee(employee: Employee): Long

    @Update
    suspend fun updateEmployee(employee: Employee)

    @Delete
    suspend fun deleteEmployee(employee: Employee)


    // --- Service Queries ---
    @Query("SELECT * FROM services ORDER BY category ASC, name ASC")
    fun getAllServices(): Flow<List<Service>>

    @Query("DELETE FROM services")
    suspend fun deleteAllServices()

    @Query("SELECT * FROM services WHERE id = :id")
    suspend fun getServiceById(id: Int): Service?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertService(service: Service): Long

    @Update
    suspend fun updateService(service: Service)

    @Delete
    suspend fun deleteService(service: Service)


    // --- ServiceRegistration Queries ---
    @Query("SELECT * FROM service_registrations WHERE isDeleted = 0 ORDER BY dateTime DESC")
    fun getAllServiceRegistrations(): Flow<List<ServiceRegistration>>

    @Query("SELECT * FROM service_registrations WHERE isDeleted = 1 ORDER BY dateTime DESC")
    fun getArchivedServiceRegistrations(): Flow<List<ServiceRegistration>>

    @Query("SELECT * FROM service_registrations WHERE id = :id")
    suspend fun getServiceRegistrationById(id: Int): ServiceRegistration?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServiceRegistration(reg: ServiceRegistration): Long

    @Update
    suspend fun updateServiceRegistration(reg: ServiceRegistration)

    @Delete
    suspend fun deleteServiceRegistration(reg: ServiceRegistration)


    // --- FinancialTransaction Queries ---
    @Query("SELECT * FROM financial_transactions ORDER BY date DESC")
    fun getAllFinancialTransactions(): Flow<List<FinancialTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFinancialTransaction(tx: FinancialTransaction): Long

    @Delete
    suspend fun deleteFinancialTransaction(tx: FinancialTransaction)

    @Query("DELETE FROM financial_transactions WHERE referenceId = :referenceId AND category = :category")
    suspend fun deleteFinancialTransactionByReference(referenceId: Int, category: String)

    @Query("DELETE FROM financial_transactions WHERE referenceId = :referenceId AND type = :type")
    suspend fun deleteFinancialTransactionsByReferenceAndType(referenceId: Int, type: String)

    @Query("DELETE FROM financial_transactions WHERE referenceId = :referenceId AND type = 'هزینه' AND category != 'حقوق همکار'")
    suspend fun deleteExpenseFinancialTransactions(referenceId: Int)

    @Query("SELECT * FROM financial_transactions WHERE referenceId = :referenceId")
    suspend fun getFinancialTransactionsByReference(referenceId: Int): List<FinancialTransaction>


    // --- Cashbox / BankAccount Queries ---
    @Query("SELECT * FROM cashboxes ORDER BY name ASC")
    fun getAllCashboxes(): Flow<List<Cashbox>>

    @Query("SELECT * FROM cashboxes WHERE id = :id")
    suspend fun getCashboxById(id: Int): Cashbox?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashbox(cashbox: Cashbox): Long

    @Update
    suspend fun updateCashbox(cashbox: Cashbox)

    @Delete
    suspend fun deleteCashbox(cashbox: Cashbox)


    // --- Commission Settlement Queries ---
    @Query("SELECT * FROM commission_settlements ORDER BY settlementDate DESC")
    fun getAllCommissionSettlements(): Flow<List<CommissionSettlement>>

    @Query("SELECT * FROM commission_settlements WHERE employeeId = :employeeId ORDER BY settlementDate DESC")
    fun getCommissionSettlementsForEmployee(employeeId: Int): Flow<List<CommissionSettlement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommissionSettlement(settlement: CommissionSettlement): Long

    @Delete
    suspend fun deleteCommissionSettlement(settlement: CommissionSettlement)

    // --- Expense Queries ---
    @Query("SELECT * FROM expenses WHERE isDeleted = 0 ORDER BY registrationDate DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE isDeleted = 1 ORDER BY registrationDate DESC")
    fun getArchivedExpenses(): Flow<List<Expense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense): Long

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpenseById(id: Int): Expense?

    @Update
    suspend fun updateExpense(expense: Expense)

    @Delete
    suspend fun deleteExpense(expense: Expense)

    // --- ExpenseCategory Queries ---
    @Query("SELECT * FROM expense_categories ORDER BY name ASC")
    fun getAllExpenseCategories(): Flow<List<ExpenseCategory>>

    @Query("SELECT * FROM expense_categories WHERE uuid = :uuid LIMIT 1")
    suspend fun getExpenseCategoryByUuid(uuid: String): ExpenseCategory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenseCategory(category: ExpenseCategory): Long

    @Update
    suspend fun updateExpenseCategory(category: ExpenseCategory)

    @Delete
    suspend fun deleteExpenseCategory(category: ExpenseCategory)

    // --- FixedExpenseTemplate Queries ---
    @Query("SELECT * FROM fixed_expense_templates ORDER BY title ASC")
    fun getAllFixedExpenseTemplates(): Flow<List<FixedExpenseTemplate>>

    @Query("SELECT * FROM fixed_expense_templates WHERE uuid = :uuid LIMIT 1")
    suspend fun getFixedExpenseTemplateByUuid(uuid: String): FixedExpenseTemplate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFixedExpenseTemplate(template: FixedExpenseTemplate): Long

    @Update
    suspend fun updateFixedExpenseTemplate(template: FixedExpenseTemplate)

    @Delete
    suspend fun deleteFixedExpenseTemplate(template: FixedExpenseTemplate)

    // --- FinancialReport Queries ---
    @Query("SELECT * FROM financial_reports ORDER BY generatedDate DESC")
    fun getAllFinancialReports(): Flow<List<FinancialReport>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFinancialReport(report: FinancialReport): Long

    @Query("DELETE FROM financial_reports WHERE id = :id")
    suspend fun deleteFinancialReportById(id: Int)

    @Delete
    suspend fun deleteFinancialReport(report: FinancialReport)

    // --- SystemSetting Queries ---
    @Query("SELECT * FROM system_settings")
    fun getAllSystemSettings(): Flow<List<SystemSetting>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSystemSetting(setting: SystemSetting): Long

    @Query("SELECT value FROM system_settings WHERE `key` = :key")
    suspend fun getSystemSettingByKey(key: String): String?

    @Query("DELETE FROM system_settings WHERE `key` = :key")
    suspend fun deleteSystemSettingByKey(key: String)

    // --- Audit Log Queries ---
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    fun getAllAuditLogs(): Flow<List<AuditLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLog): Long

    @Query("DELETE FROM audit_logs WHERE id = :id")
    suspend fun deleteAuditLogById(id: Int)

    // --- User Permission Queries ---
    @Query("SELECT * FROM user_permissions")
    fun getAllUserPermissions(): Flow<List<UserPermission>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserPermission(permission: UserPermission): Long

    @Query("DELETE FROM user_permissions WHERE permissionName = :name")
    suspend fun deleteUserPermissionByName(name: String)

    @Query("SELECT isGranted FROM user_permissions WHERE permissionName = :name")
    suspend fun isPermissionGranted(name: String): Boolean?

    // --- Financial Edit History Queries ---
    @Query("SELECT * FROM financial_edit_histories ORDER BY timestamp DESC")
    fun getAllEditHistories(): Flow<List<FinancialEditHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEditHistory(history: FinancialEditHistory): Long

    @Query("DELETE FROM financial_edit_histories WHERE id = :id")
    suspend fun deleteEditHistoryById(id: Int)

    // --- Journal Entry Queries ---
    @Query("SELECT * FROM journal_entries WHERE id = :id")
    suspend fun getJournalEntryById(id: Int): JournalEntry?

    @Query("SELECT * FROM journal_entries ORDER BY date DESC")
    fun getAllJournalEntries(): Flow<List<JournalEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournalEntry(entry: JournalEntry): Long

    @Query("DELETE FROM journal_entries WHERE referenceId = :referenceId")
    suspend fun deleteJournalEntriesByReferenceId(referenceId: Int)

    @Query("DELETE FROM journal_entries WHERE referenceId = :referenceId AND documentNumber LIKE :docPattern")
    suspend fun deleteJournalEntriesByReferenceAndDocPattern(referenceId: Int, docPattern: String)

    @Delete
    suspend fun deleteJournalEntry(entry: JournalEntry)

    @Query("SELECT * FROM cashboxes")
    suspend fun getCashboxesList(): List<Cashbox>

    @Query("DELETE FROM journal_entries")
    suspend fun deleteAllJournalEntries()

    @Query("DELETE FROM financial_transactions WHERE referenceId IS NOT NULL AND referenceId != 0")
    suspend fun deleteGeneratedFinancialTransactions()

    // --- ACID Transaction Support ---
    @Transaction
    suspend fun runInTransaction(action: suspend () -> Unit) {
        action()
    }

    @Query("SELECT * FROM journal_entries")
    suspend fun getJournalEntriesList(): List<JournalEntry>

    @Query("SELECT * FROM financial_transactions")
    suspend fun getFinancialTransactionsList(): List<FinancialTransaction>

    @Query("SELECT * FROM service_registrations")
    suspend fun getServiceRegistrationsList(): List<ServiceRegistration>

    @Query("SELECT * FROM expenses")
    suspend fun getExpensesList(): List<Expense>

    @Query("SELECT * FROM commission_settlements WHERE id = :id")
    suspend fun getCommissionSettlementById(id: Int): CommissionSettlement?

    @Query("SELECT * FROM commission_settlements WHERE uuid = :uuid LIMIT 1")
    suspend fun getCommissionSettlementByUuid(uuid: String): CommissionSettlement?

    @Query("SELECT * FROM commission_settlements")
    suspend fun getCommissionSettlementsList(): List<CommissionSettlement>

    @Query("SELECT * FROM patients")
    suspend fun getPatientsList(): List<Patient>

    @Query("SELECT * FROM services")
    suspend fun getServicesList(): List<Service>

    @Query("SELECT * FROM employees")
    suspend fun getEmployeesList(): List<Employee>

    @Query("SELECT * FROM patients WHERE uuid = :uuid")
    suspend fun getPatientByUuid(uuid: String): Patient?

    @Query("SELECT * FROM employees WHERE uuid = :uuid")
    suspend fun getEmployeeByUuid(uuid: String): Employee?

    @Query("SELECT * FROM services WHERE uuid = :uuid")
    suspend fun getServiceByUuid(uuid: String): Service?

    @Query("SELECT * FROM service_registrations WHERE uuid = :uuid")
    suspend fun getServiceRegistrationByUuid(uuid: String): ServiceRegistration?

    @Query("SELECT * FROM financial_transactions WHERE uuid = :uuid")
    suspend fun getFinancialTransactionByUuid(uuid: String): FinancialTransaction?

    @Query("SELECT * FROM cashboxes WHERE uuid = :uuid")
    suspend fun getCashboxByUuid(uuid: String): Cashbox?

    @Query("SELECT * FROM expenses WHERE uuid = :uuid")
    suspend fun getExpenseByUuid(uuid: String): Expense?

    // --- Sync Metadata Queries ---
    @Query("SELECT * FROM sync_metadata")
    fun getAllSyncMetadata(): Flow<List<SyncMetadata>>

    @Query("SELECT * FROM sync_metadata WHERE syncStatus = 'Pending' OR syncStatus = 'Failed' ORDER BY updatedTimestamp ASC")
    suspend fun getPendingSyncMetadata(): List<SyncMetadata>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncMetadata(meta: SyncMetadata)

    @Query("DELETE FROM sync_metadata WHERE entityType = :type AND entityId = :id")
    suspend fun deleteSyncMetadata(type: String, id: String)

    @Query("DELETE FROM sync_metadata")
    suspend fun deleteAllSyncMetadata()

    // --- Cloud Sync Record Queries ---
    @Query("SELECT * FROM cloud_sync_records")
    fun getAllCloudSyncRecordsFlow(): Flow<List<CloudSyncRecord>>

    @Query("SELECT * FROM cloud_sync_records")
    suspend fun getAllCloudSyncRecords(): List<CloudSyncRecord>

    @Query("SELECT * FROM cloud_sync_records WHERE updatedTimestamp > :sinceTime")
    suspend fun getCloudSyncRecordsSince(sinceTime: Long): List<CloudSyncRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCloudSyncRecord(record: CloudSyncRecord)

    @Query("DELETE FROM cloud_sync_records WHERE entityType = :type AND entityId = :id")
    suspend fun deleteCloudSyncRecord(type: String, id: String)

    @Query("DELETE FROM cloud_sync_records")
    suspend fun deleteAllCloudSyncRecords()

    // --- Connected Device Queries ---
    @Query("SELECT * FROM connected_devices ORDER BY lastOnlineTime DESC")
    fun getAllConnectedDevices(): Flow<List<ConnectedDevice>>

    @Query("SELECT * FROM connected_devices")
    suspend fun getAllConnectedDevicesList(): List<ConnectedDevice>

    @Query("SELECT * FROM connected_devices WHERE deviceId = :deviceId")
    suspend fun getConnectedDeviceById(deviceId: String): ConnectedDevice?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnectedDevice(device: ConnectedDevice)

    @Query("DELETE FROM connected_devices WHERE deviceId = :deviceId")
    suspend fun deleteConnectedDevice(deviceId: String)

    @Query("DELETE FROM connected_devices")
    suspend fun deleteAllConnectedDevices()

    // --- Referral Queries ---
    @Query("SELECT * FROM referrals ORDER BY name ASC")
    fun getAllReferrals(): Flow<List<Referral>>

    @Query("SELECT * FROM referrals WHERE id = :id")
    suspend fun getReferralById(id: Int): Referral?

    @Query("SELECT * FROM referrals WHERE uuid = :uuid")
    suspend fun getReferralByUuid(uuid: String): Referral?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReferral(referral: Referral): Long

    @Update
    suspend fun updateReferral(referral: Referral)

    @Delete
    suspend fun deleteReferral(referral: Referral)


    // --- Referral Commission Queries ---
    @Query("SELECT * FROM referral_commissions ORDER BY date DESC")
    fun getAllReferralCommissions(): Flow<List<ReferralCommission>>

    @Query("SELECT * FROM referral_commissions WHERE referralId = :referralId ORDER BY date DESC")
    fun getCommissionsByReferral(referralId: Int): Flow<List<ReferralCommission>>

    @Query("SELECT * FROM referral_commissions WHERE serviceRegistrationId = :regId")
    suspend fun getCommissionByServiceRegistration(regId: Int): ReferralCommission?

    @Query("SELECT * FROM referral_commissions WHERE id = :id")
    suspend fun getReferralCommissionById(id: Int): ReferralCommission?

    @Query("SELECT * FROM referral_commissions WHERE uuid = :uuid")
    suspend fun getReferralCommissionByUuid(uuid: String): ReferralCommission?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReferralCommission(commission: ReferralCommission): Long

    @Update
    suspend fun updateReferralCommission(commission: ReferralCommission)

    @Delete
    suspend fun deleteReferralCommission(commission: ReferralCommission)

    @Query("DELETE FROM referral_commissions WHERE serviceRegistrationId = :regId")
    suspend fun deleteReferralCommissionByServiceRegistration(regId: Int)

    // --- Alert Queries ---
    @Query("SELECT * FROM alerts WHERE isDismissed = 0 ORDER BY timestamp DESC")
    fun getActiveAlerts(): Flow<List<Alert>>

    @Query("SELECT * FROM alerts ORDER BY timestamp DESC")
    fun getAllAlerts(): Flow<List<Alert>>

    @Query("SELECT * FROM alerts WHERE id = :id")
    suspend fun getAlertById(id: Int): Alert?

    @Query("SELECT * FROM alerts WHERE uuid = :uuid")
    suspend fun getAlertByUuid(uuid: String): Alert?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: Alert): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlerts(alerts: List<Alert>)

    @Update
    suspend fun updateAlert(alert: Alert)

    @Delete
    suspend fun deleteAlert(alert: Alert)

    @Query("DELETE FROM alerts")
    suspend fun clearAllAlerts()

    @Query("DELETE FROM alerts WHERE type = :type")
    suspend fun deleteAlertsByType(type: String)


    // --- Contract Queries ---
    @Query("SELECT * FROM contracts ORDER BY id DESC")
    fun getAllContracts(): Flow<List<Contract>>

    @Query("SELECT * FROM contracts WHERE uuid = :uuid")
    suspend fun getContractByUuid(uuid: String): Contract?

    @Query("SELECT * FROM contracts WHERE id = :id")
    suspend fun getContractById(id: Int): Contract?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContract(contract: Contract): Long

    @Update
    suspend fun updateContract(contract: Contract)

    @Delete
    suspend fun deleteContract(contract: Contract)


    // --- Staff Profile Queries ---
    @Query("SELECT * FROM staff_profiles ORDER BY id DESC")
    fun getAllStaffProfiles(): Flow<List<StaffProfile>>

    @Query("SELECT * FROM staff_profiles WHERE uuid = :uuid")
    suspend fun getStaffProfileByUuid(uuid: String): StaffProfile?

    @Query("SELECT * FROM staff_profiles WHERE id = :id")
    suspend fun getStaffProfileById(id: Int): StaffProfile?

    @Query("SELECT * FROM staff_profiles WHERE employeeId = :employeeId")
    suspend fun getStaffProfileByEmployeeId(employeeId: Int): StaffProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStaffProfile(profile: StaffProfile): Long

    @Update
    suspend fun updateStaffProfile(profile: StaffProfile)

    @Delete
    suspend fun deleteStaffProfile(profile: StaffProfile)


    // --- Service Schedule Queries ---
    @Query("SELECT * FROM service_schedules ORDER BY scheduledDate DESC")
    fun getAllServiceSchedules(): Flow<List<ServiceSchedule>>

    @Query("SELECT * FROM service_schedules")
    suspend fun getServiceSchedulesList(): List<ServiceSchedule>

    @Query("SELECT * FROM service_schedules WHERE uuid = :uuid")
    suspend fun getServiceScheduleByUuid(uuid: String): ServiceSchedule?

    @Query("SELECT * FROM service_schedules WHERE id = :id")
    suspend fun getServiceScheduleById(id: Int): ServiceSchedule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServiceSchedule(schedule: ServiceSchedule): Long

    @Update
    suspend fun updateServiceSchedule(schedule: ServiceSchedule)

    @Delete
    suspend fun deleteServiceSchedule(schedule: ServiceSchedule)


    // --- Nursing Report Queries ---
    @Query("SELECT * FROM nursing_reports ORDER BY date DESC")
    fun getAllNursingReports(): Flow<List<NursingReport>>

    @Query("SELECT * FROM nursing_reports WHERE uuid = :uuid")
    suspend fun getNursingReportByUuid(uuid: String): NursingReport?

    @Query("SELECT * FROM nursing_reports WHERE id = :id")
    suspend fun getNursingReportById(id: Int): NursingReport?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNursingReport(report: NursingReport): Long

    @Update
    suspend fun updateNursingReport(report: NursingReport)

    @Delete
    suspend fun deleteNursingReport(report: NursingReport)


    // --- Vital Signs Queries ---
    @Query("SELECT * FROM vital_signs ORDER BY date DESC")
    fun getAllVitalSigns(): Flow<List<VitalSigns>>

    @Query("SELECT * FROM vital_signs WHERE uuid = :uuid")
    suspend fun getVitalSignsByUuid(uuid: String): VitalSigns?

    @Query("SELECT * FROM vital_signs WHERE id = :id")
    suspend fun getVitalSignsById(id: Int): VitalSigns?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVitalSigns(signs: VitalSigns): Long

    @Update
    suspend fun updateVitalSigns(signs: VitalSigns)

    @Delete
    suspend fun deleteVitalSigns(signs: VitalSigns)


    // --- Wound Record Queries ---
    @Query("SELECT * FROM wound_records ORDER BY date DESC")
    fun getAllWoundRecords(): Flow<List<WoundRecord>>

    @Query("SELECT * FROM wound_records WHERE uuid = :uuid")
    suspend fun getWoundRecordByUuid(uuid: String): WoundRecord?

    @Query("SELECT * FROM wound_records WHERE id = :id")
    suspend fun getWoundRecordById(id: Int): WoundRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWoundRecord(record: WoundRecord): Long

    @Update
    suspend fun updateWoundRecord(record: WoundRecord)

    @Delete
    suspend fun deleteWoundRecord(record: WoundRecord)


    // --- Consent Form Queries ---
    @Query("SELECT * FROM consent_forms ORDER BY date DESC")
    fun getAllConsentForms(): Flow<List<ConsentForm>>

    @Query("SELECT * FROM consent_forms WHERE uuid = :uuid")
    suspend fun getConsentFormByUuid(uuid: String): ConsentForm?

    @Query("SELECT * FROM consent_forms WHERE id = :id")
    suspend fun getConsentFormById(id: Int): ConsentForm?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsentForm(form: ConsentForm): Long

    @Update
    suspend fun updateConsentForm(form: ConsentForm)

    @Delete
    suspend fun deleteConsentForm(form: ConsentForm)


    // --- Prescription Queries ---
    @Query("SELECT * FROM prescriptions ORDER BY date DESC")
    fun getAllPrescriptions(): Flow<List<Prescription>>

    @Query("SELECT * FROM prescriptions WHERE uuid = :uuid")
    suspend fun getPrescriptionByUuid(uuid: String): Prescription?

    @Query("SELECT * FROM prescriptions WHERE id = :id")
    suspend fun getPrescriptionById(id: Int): Prescription?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrescription(prescription: Prescription): Long

    @Update
    suspend fun updatePrescription(prescription: Prescription)

    @Delete
    suspend fun deletePrescription(prescription: Prescription)


    // --- Dashboard Cache Queries ---
    @Query("SELECT * FROM dashboard_caches WHERE `key` = :key")
    suspend fun getDashboardCacheByKey(key: String): DashboardCache?

    @Query("SELECT * FROM dashboard_caches WHERE uuid = :uuid")
    suspend fun getDashboardCacheByUuid(uuid: String): DashboardCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDashboardCache(cache: DashboardCache)

    @Query("DELETE FROM dashboard_caches WHERE `key` = :key")
    suspend fun deleteDashboardCacheByKey(key: String)

    @Query("DELETE FROM dashboard_caches WHERE uuid = :uuid")
    suspend fun deleteDashboardCacheByUuid(uuid: String)

    @Query("SELECT * FROM referrals")
    suspend fun getReferralsList(): List<Referral>

    @Query("SELECT * FROM referral_commissions")
    suspend fun getReferralCommissionsList(): List<ReferralCommission>

    @Query("SELECT * FROM system_settings")
    suspend fun getSystemSettingsList(): List<SystemSetting>

    @Query("SELECT * FROM audit_logs")
    suspend fun getAuditLogsList(): List<AuditLog>

    @Query("SELECT * FROM financial_edit_histories")
    suspend fun getEditHistoriesList(): List<FinancialEditHistory>

    @Query("SELECT * FROM sync_metadata")
    suspend fun getSyncMetadataList(): List<SyncMetadata>

    @Query("SELECT * FROM sync_metadata WHERE entityType = :type AND entityId = :id LIMIT 1")
    suspend fun getSyncMetadata(type: String, id: String): SyncMetadata?

    @Query("SELECT * FROM contracts")
    suspend fun getContractsList(): List<Contract>

    @Query("SELECT * FROM nursing_reports")
    suspend fun getNursingReportsList(): List<NursingReport>

    @Query("SELECT * FROM vital_signs")
    suspend fun getVitalSignsList(): List<VitalSigns>

    @Query("SELECT * FROM wound_records")
    suspend fun getWoundRecordsList(): List<WoundRecord>

    @Query("SELECT * FROM staff_profiles")
    suspend fun getStaffProfilesList(): List<StaffProfile>

    @Query("SELECT * FROM consent_forms")
    suspend fun getConsentFormsList(): List<ConsentForm>

    @Query("SELECT * FROM prescriptions")
    suspend fun getPrescriptionsList(): List<Prescription>

    @Query("SELECT * FROM expense_categories")
    suspend fun getExpenseCategoriesList(): List<ExpenseCategory>

    @Query("SELECT * FROM fixed_expense_templates")
    suspend fun getFixedExpenseTemplatesList(): List<FixedExpenseTemplate>

    @Query("SELECT * FROM financial_reports")
    suspend fun getFinancialReportsList(): List<FinancialReport>

    @Query("SELECT * FROM user_permissions")
    suspend fun getUserPermissionsList(): List<UserPermission>

    @Query("SELECT * FROM alerts")
    suspend fun getAlertsList(): List<Alert>

    @Query("SELECT * FROM dashboard_caches")
    suspend fun getDashboardCachesList(): List<DashboardCache>

    // --- SyncQueue Queries ---
    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' OR status = 'FAILED' ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getPendingSyncTasks(limit: Int): List<SyncQueue>

    @Query("SELECT * FROM sync_queue ORDER BY timestamp DESC")
    fun getAllSyncQueues(): Flow<List<SyncQueue>>

    @Update
    suspend fun updateSyncQueue(syncQueue: SyncQueue)

    @Update
    suspend fun updateSyncQueues(syncQueues: List<SyncQueue>)
    
    @Query("UPDATE sync_queue SET status = :status WHERE id IN (:ids)")
    suspend fun updateSyncStatuses(ids: List<Int>, status: String)

    @Query("DELETE FROM sync_queue WHERE status = 'COMPLETED'")
    suspend fun deleteCompletedSyncTasks()
}
