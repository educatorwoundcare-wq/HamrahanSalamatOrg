package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object SyncSerializer {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    fun serialize(entityType: String, obj: Any): String {
        return when (entityType) {
            "Patient" -> moshi.adapter(Patient::class.java).toJson(obj as Patient)
            "Employee" -> moshi.adapter(Employee::class.java).toJson(obj as Employee)
            "Service" -> moshi.adapter(Service::class.java).toJson(obj as Service)
            "ServiceRegistration" -> moshi.adapter(ServiceRegistration::class.java).toJson(obj as ServiceRegistration)
            "FinancialTransaction" -> moshi.adapter(FinancialTransaction::class.java).toJson(obj as FinancialTransaction)
            "Cashbox" -> moshi.adapter(Cashbox::class.java).toJson(obj as Cashbox)
            "CommissionSettlement" -> moshi.adapter(CommissionSettlement::class.java).toJson(obj as CommissionSettlement)
            "Expense" -> moshi.adapter(Expense::class.java).toJson(obj as Expense)
            "ExpenseCategory" -> moshi.adapter(ExpenseCategory::class.java).toJson(obj as ExpenseCategory)
            "FixedExpenseTemplate" -> moshi.adapter(FixedExpenseTemplate::class.java).toJson(obj as FixedExpenseTemplate)
            "FinancialReport" -> moshi.adapter(FinancialReport::class.java).toJson(obj as FinancialReport)
            "SystemSetting" -> moshi.adapter(SystemSetting::class.java).toJson(obj as SystemSetting)
            "AuditLog" -> moshi.adapter(AuditLog::class.java).toJson(obj as AuditLog)
            "UserPermission" -> moshi.adapter(UserPermission::class.java).toJson(obj as UserPermission)
            "FinancialEditHistory" -> moshi.adapter(FinancialEditHistory::class.java).toJson(obj as FinancialEditHistory)
            "JournalEntry" -> moshi.adapter(JournalEntry::class.java).toJson(obj as JournalEntry)
            "Referral" -> moshi.adapter(Referral::class.java).toJson(obj as Referral)
            "ReferralCommission" -> moshi.adapter(ReferralCommission::class.java).toJson(obj as ReferralCommission)
            "Alert" -> moshi.adapter(Alert::class.java).toJson(obj as Alert)
            "Contract" -> moshi.adapter(Contract::class.java).toJson(obj as Contract)
            "StaffProfile" -> moshi.adapter(StaffProfile::class.java).toJson(obj as StaffProfile)
            "ServiceSchedule" -> moshi.adapter(ServiceSchedule::class.java).toJson(obj as ServiceSchedule)
            "NursingReport" -> moshi.adapter(NursingReport::class.java).toJson(obj as NursingReport)
            "VitalSigns" -> moshi.adapter(VitalSigns::class.java).toJson(obj as VitalSigns)
            "WoundRecord" -> moshi.adapter(WoundRecord::class.java).toJson(obj as WoundRecord)
            "ConsentForm" -> moshi.adapter(ConsentForm::class.java).toJson(obj as ConsentForm)
            "Prescription" -> moshi.adapter(Prescription::class.java).toJson(obj as Prescription)
            "DashboardCache" -> moshi.adapter(DashboardCache::class.java).toJson(obj as DashboardCache)
            "ConnectedDevice" -> moshi.adapter(ConnectedDevice::class.java).toJson(obj as ConnectedDevice)
            else -> throw IllegalArgumentException("Unknown entity type: $entityType")
        }
    }

    fun deserialize(entityType: String, json: String): Any? {
        return try {
            when (entityType) {
                "Patient" -> moshi.adapter(Patient::class.java).fromJson(json)
                "Employee" -> moshi.adapter(Employee::class.java).fromJson(json)
                "Service" -> moshi.adapter(Service::class.java).fromJson(json)
                "ServiceRegistration" -> moshi.adapter(ServiceRegistration::class.java).fromJson(json)
                "FinancialTransaction" -> moshi.adapter(FinancialTransaction::class.java).fromJson(json)
                "Cashbox" -> moshi.adapter(Cashbox::class.java).fromJson(json)
                "CommissionSettlement" -> moshi.adapter(CommissionSettlement::class.java).fromJson(json)
                "Expense" -> moshi.adapter(Expense::class.java).fromJson(json)
                "ExpenseCategory" -> moshi.adapter(ExpenseCategory::class.java).fromJson(json)
                "FixedExpenseTemplate" -> moshi.adapter(FixedExpenseTemplate::class.java).fromJson(json)
                "FinancialReport" -> moshi.adapter(FinancialReport::class.java).fromJson(json)
                "SystemSetting" -> moshi.adapter(SystemSetting::class.java).fromJson(json)
                "AuditLog" -> moshi.adapter(AuditLog::class.java).fromJson(json)
                "UserPermission" -> moshi.adapter(UserPermission::class.java).fromJson(json)
                "FinancialEditHistory" -> moshi.adapter(FinancialEditHistory::class.java).fromJson(json)
                "JournalEntry" -> moshi.adapter(JournalEntry::class.java).fromJson(json)
                "Referral" -> moshi.adapter(Referral::class.java).fromJson(json)
                "ReferralCommission" -> moshi.adapter(ReferralCommission::class.java).fromJson(json)
                "Alert" -> moshi.adapter(Alert::class.java).fromJson(json)
                "Contract" -> moshi.adapter(Contract::class.java).fromJson(json)
                "StaffProfile" -> moshi.adapter(StaffProfile::class.java).fromJson(json)
                "ServiceSchedule" -> moshi.adapter(ServiceSchedule::class.java).fromJson(json)
                "NursingReport" -> moshi.adapter(NursingReport::class.java).fromJson(json)
                "VitalSigns" -> moshi.adapter(VitalSigns::class.java).fromJson(json)
                "WoundRecord" -> moshi.adapter(WoundRecord::class.java).fromJson(json)
                "ConsentForm" -> moshi.adapter(ConsentForm::class.java).fromJson(json)
                "Prescription" -> moshi.adapter(Prescription::class.java).fromJson(json)
                "DashboardCache" -> moshi.adapter(DashboardCache::class.java).fromJson(json)
                "ConnectedDevice" -> moshi.adapter(ConnectedDevice::class.java).fromJson(json)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
