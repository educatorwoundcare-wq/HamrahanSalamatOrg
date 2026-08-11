package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "patients")
data class Patient(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fullName: String,
    val gender: String, // "مرد" / "زن"
    val age: Int,
    val phone: String,
    val address: String,
    val referralSource: String, // منبع ارجاع (مثلا: پزشک، تبلیغات، معرفی)
    val referralId: Int? = null, // شناسه معرف
    val status: String, // "فعال" / "غیرفعال"
    val registrationDate: Long = System.currentTimeMillis(),
    val notes: String = "",
    val tags: String = "", // برچسب‌ها (جدا شده با کاما)
    val uuid: String = java.util.UUID.randomUUID().toString()
)

@Entity(tableName = "employees")
data class Employee(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fullName: String,
    val nationalId: String,
    val phone: String,
    val profession: String, // "پرستار" / "کمک‌پرستار" / "پزشک" / "فیزیوتراپ" / etc.
    val position: String, // سمت
    val skill: String, // مهارت‌ها
    val employmentType: String, // "تمام وقت" / "پاره وقت" / "قراردادی"
    val commissionModel: String, // "درصدی" / "مبلغ ثابت" / "ترکیبی"
    val commissionValue: Double, // درصد یا مبلغ ثابت
    val bankInfo: String, // اطلاعات حساب و کارت بانکی
    val status: String, // "فعال" / "غیرفعال"
    val startDate: Long = System.currentTimeMillis(),
    val notes: String = "",
    val uuid: String = java.util.UUID.randomUUID().toString()
)

@Entity(tableName = "services")
data class Service(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val officialCode: String = "", // کد رسمی خدمت
    val officialName: String = "", // نام رسمی خدمت
    val name: String, // نام نمایشی/فروش خدمت
    val category: String, // گروه خدمت (مانند: تزریقات، پانسمان، مراقبت سالمند، ...)
    val officialTariff: Double = 0.0, // تعرفه مصوب ۱۴۰۵
    val sellingPrice: Double, // قیمت فروش مرکز (Center Selling Price)
    val defaultCost: Double = 0.0, // هزینه دستمزد همکار (Employee Cost)
    val transportationCost: Double = 0.0, // هزینه ایاب ذهاب پیش‌فرض
    val consumablesCost: Double = 0.0, // هزینه لوازم مصرفی پیش‌فرض
    val discount: Double = 0.0, // تخفیف پیش‌فرض
    val employeeCommission: Double = 0.0, // کارمزد همکار پیش‌فرض (تومان یا درصد)
    val durationMinutes: Int, // مدت زمان تقریبی به دقیقه (Estimated Duration)
    val description: String = "", // توضیحات
    val isActive: Boolean = true, // وضعیت فعال/غیرفعال
    val pricingUnit: String = "بازدید", // واحد قیمت‌گذاری (جلسه، ساعت، روز، بازدید و ...)
    val isVisibleInApp: Boolean = true, // قابلیت نمایش در اپلیکیشن
    val isSelectableByPatient: Boolean = true, // قابلیت انتخاب توسط بیمار
    val lastModifiedDate: Long = System.currentTimeMillis(), // تاریخ آخرین ویرایش
    val uuid: String = java.util.UUID.randomUUID().toString()
) {
    // Calculated Net Profit: Center Selling Price - Employee Cost - Transportation Cost - Consumables Cost - Discount
    val netProfit: Double
        get() = sellingPrice - defaultCost - transportationCost - discount
}

@Entity(tableName = "service_registrations")
data class ServiceRegistration(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val patientId: Int,
    val serviceId: Int,
    val employeeId: Int,
    val dateTime: Long = System.currentTimeMillis(),
    val sellingPrice: Double, // قیمت توافق شده با بیمار
    val employeeCost: Double, // دستمزد پرستار (کمیسیون پرستار)
    val transportationCost: Double = 0.0, // هزینه ایاب ذهاب
    val otherCosts: Double = 0.0, // سایر هزینه‌ها (لوازم مصرفی)
    val discount: Double = 0.0, // تخفیف داده شده
    val finalPrice: Double, // قیمت نهایی پرداختی بیمار (sellingPrice + otherCosts + transport - discount)
    val paymentMethod: String, // "نقدی" / "کارت به کارت" / "دستگاه کارتخوان" / "حواله بانکی"
    val invoiceNumber: String, // شماره فاکتور
    val notes: String = "",
    val grossIncome: Double, // درآمد ناخالص (finalPrice)
    val employeeCommission: Double, // کارمزد همکار (employeeCost + optionally otherCosts if nurse owned)
    val companyProfit: Double, // سود خالص شرکت (finalPrice - employeeCost - otherCosts)
    val isPaid: Boolean = true,
    val isDeleted: Boolean = false,
    val workflowStatus: String = "Submitted",
    val consumablesOwner: String = "Nurse", // "Nurse" or "Company" (extensible ownership model)
    val cashboxId: Int? = null, // ID of the cashbox where payment was recorded
    val scheduledDate: Long = dateTime,
    val serviceDate: Long = dateTime,
    val uuid: String = java.util.UUID.randomUUID().toString()
)

@Entity(tableName = "financial_transactions")
data class FinancialTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "درآمد" / "هزینه"
    val category: String, // دسته بندی (مثلا: "ثبت خدمت"، "حقوق همکار"، "اجاره دفتر"، "خرید تجهیزات")
    val amount: Double,
    val date: Long = System.currentTimeMillis(),
    val description: String,
    val paymentMethod: String, // روش پرداخت
    val referenceId: Int? = null, // آیدی خدمت ثبت شده یا سند مرتبط
    val isCleared: Boolean = true,
    val origin: String = "Manual Entry", // "Service", "Expense", "Salary", "Manual Entry", "Adjustment"
    val manualReason: String? = null,
    val creatorName: String? = null,
    val uuid: String = java.util.UUID.randomUUID().toString()
)

@Entity(tableName = "cashboxes")
data class Cashbox(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String, // نام صندوق (مثلا: صندوق اصلی، تنخواه‌گردان، حساب صادرات)
    val type: String, // "صندوق" / "حساب بانکی"
    val accountNumber: String = "",
    val balance: Double = 0.0,
    val uuid: String = java.util.UUID.randomUUID().toString()
)

@Entity(tableName = "commission_settlements")
data class CommissionSettlement(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val employeeId: Int,
    val amount: Double,
    val settlementDate: Long = System.currentTimeMillis(),
    val periodStart: Long,
    val periodEnd: Long,
    val notes: String = "",
    val uuid: String = java.util.UUID.randomUUID().toString()
)

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val category: String, // e.g. "هزینه ثابت", "هزینه متغیر", "هزینه عملیاتی روزانه", etc.
    val amount: Double,
    val registrationDate: Long = System.currentTimeMillis(),
    val paymentDate: Long = System.currentTimeMillis(),
    val paymentMethod: String, // "نقدی" / "کارت" / "انتقال بانکی" / "چک" / "سایر"
    val description: String = "",
    val submitterName: String = "", // شخص ثبت کننده
    val receiptAttachmentPath: String = "", // پیوست رسید پرداخت
    val isDeleted: Boolean = false,
    val workflowStatus: String = "Submitted",
    val uuid: String = java.util.UUID.randomUUID().toString()
)

@Entity(tableName = "expense_categories")
data class ExpenseCategory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val isSystemDefault: Boolean = false,
    val uuid: String = java.util.UUID.randomUUID().toString()
)

@Entity(tableName = "fixed_expense_templates")
data class FixedExpenseTemplate(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val monthlyAmount: Double,
    val paymentDay: Int, // روز پرداخت (۱ تا ۳۱)
    val isActive: Boolean = true,
    val category: String = "هزینه ثابت",
    val recurringInterval: String = "Monthly", // "Hourly", "Daily", "Weekly", "Bi-weekly", "Monthly", "Quarterly", "Bi-annually", "Annually"
    val uuid: String = java.util.UUID.randomUUID().toString()
)

@Entity(tableName = "financial_reports")
data class FinancialReport(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val startDate: Long,
    val endDate: Long,
    val totalExpenses: Double,
    val totalFixedExpenses: Double,
    val totalVariableExpenses: Double,
    val averageDailyExpense: Double,
    val topExpenseCategory: String,
    val transactionCount: Int,
    val generatedDate: Long = System.currentTimeMillis()
)

@Entity(tableName = "system_settings")
data class SystemSetting(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val relatedScreen: String = "",
    val user: String = "مدیر سیستم",
    val device: String = "دستگاه همراه",
    val action: String, // "Create", "Edit", "Delete", "Restore", "Backup", etc.
    val affectedModule: String, // "Expenses", "ServiceRegistrations", etc.
    val details: String
)

@Entity(tableName = "user_permissions")
data class UserPermission(
    @PrimaryKey val permissionName: String, // e.g., "Create Expense"
    val isGranted: Boolean = true
)

@Entity(tableName = "financial_edit_histories")
data class FinancialEditHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val entityType: String, // "Expense", "ServiceRegistration"
    val entityId: Int,
    val previousValue: String,
    val newValue: String,
    val differenceAmount: Double = 0.0,
    val editedBy: String = "مدیر سیستم",
    val userRole: String = "مدیر",
    val timestamp: Long = System.currentTimeMillis(),
    val relatedScreen: String = "",
    val reason: String,
    val comment: String = ""
)

@Entity(tableName = "journal_entries")
data class JournalEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val documentNumber: String,
    val debitAccount: String,
    val creditAccount: String,
    val amount: Double,
    val date: Long = System.currentTimeMillis(),
    val reference: String,
    val referenceId: Int? = null
)

@Entity(tableName = "sync_metadata", primaryKeys = ["entityType", "entityId"])
data class SyncMetadata(
    val entityType: String,
    val entityId: String,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val updatedTimestamp: Long = System.currentTimeMillis(),
    val deletedStatus: Boolean = false,
    val lastModifiedDeviceId: String = "",
    val syncStatus: String = "Pending" // "Pending", "Synced", "Failed", "Conflict"
)

@Entity(tableName = "cloud_sync_records")
data class CloudSyncRecord(
    @PrimaryKey val id: String, // format: "entityType_entityId"
    val entityType: String,
    val entityId: String,
    val dataJson: String,
    val updatedTimestamp: Long,
    val lastModifiedDeviceId: String,
    val isDeleted: Boolean
)

@Entity(tableName = "connected_devices")
data class ConnectedDevice(
    @PrimaryKey val deviceId: String,
    val deviceName: String,
    val deviceType: String,
    val appVersion: String,
    val lastOnlineTime: Long,
    val lastSuccessfulSync: Long,
    val status: String, // "Active", "Revoked", "Pending"
    val uid: String = "",
    val role: String = "",
    val lastSeen: Long = 0L,
    val companyId: String = "",
    val requestedRole: String = ""
)

@Entity(tableName = "alerts")
data class Alert(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val type: String, // e.g. "today_visits", "overdue_reports", "unpaid_invoices", "low_inventory", "pending_approvals", "missed_followups", "expiring_documents", "failed_sync", "failed_backup", "abnormal_financial"
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val isDismissed: Boolean = false,
    val relatedScreen: String = "", // e.g. "Register", "Report", "Accounting", "Settings"
    val uuid: String = java.util.UUID.randomUUID().toString(),
    // Required fields for the updated alert lifecycle
    val alertType: String = "",
    val entityId: String = "",
    val status: String = "PENDING", // "PENDING", "RESOLVED", "COMPLETED"
    val createdAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null
)

@Entity(tableName = "contracts")
data class Contract(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val employeeId: Int,
    val title: String,
    val content: String,
    val startDate: Long,
    val endDate: Long,
    val status: String = "Pending", // "Pending", "Approved", "Rejected", "NeedsCorrection"
    val comment: String = "",
    val uuid: String = java.util.UUID.randomUUID().toString()
)

@Entity(tableName = "staff_profiles")
data class StaffProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val employeeId: Int,
    val hasNationalIdCard: Boolean = false,
    val hasDegree: Boolean = false,
    val hasLicense: Boolean = false,
    val hasContract: Boolean = false,
    val status: String = "Pending", // "Pending", "Approved", "Rejected", "NeedsCorrection"
    val comment: String = "",
    val uuid: String = java.util.UUID.randomUUID().toString()
)

@Entity(tableName = "service_schedules")
data class ServiceSchedule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val registrationId: Int,
    val employeeId: Int,
    val scheduledDate: Long,
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val report: String = "",
    val status: String = "Scheduled", // "Scheduled", "Started", "Completed", "Closed"
    val uuid: String = java.util.UUID.randomUUID().toString()
)

@Entity(tableName = "nursing_reports")
data class NursingReport(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val registrationId: Int,
    val description: String,
    val reporterName: String,
    val date: Long = System.currentTimeMillis(),
    val uuid: String = java.util.UUID.randomUUID().toString()
)

@Entity(tableName = "vital_signs")
data class VitalSigns(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val patientId: Int,
    val bloodPressureSystolic: Int,
    val bloodPressureDiastolic: Int,
    val heartRate: Int,
    val temperatureCelsius: Double,
    val oxygenSaturation: Int,
    val date: Long = System.currentTimeMillis(),
    val uuid: String = java.util.UUID.randomUUID().toString()
)

@Entity(tableName = "wound_records")
data class WoundRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val patientId: Int,
    val woundType: String, // "بستر", "دیابتی", etc.
    val stage: String, // "Grade 1", "Grade 2", etc.
    val description: String,
    val date: Long = System.currentTimeMillis(),
    val uuid: String = java.util.UUID.randomUUID().toString()
)

@Entity(tableName = "consent_forms")
data class ConsentForm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val patientId: Int,
    val title: String,
    val content: String,
    val isSigned: Boolean = false,
    val date: Long = System.currentTimeMillis(),
    val uuid: String = java.util.UUID.randomUUID().toString()
)

@Entity(tableName = "prescriptions")
data class Prescription(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val patientId: Int,
    val doctorName: String,
    val medicineList: String,
    val date: Long = System.currentTimeMillis(),
    val uuid: String = java.util.UUID.randomUUID().toString()
)

@Entity(tableName = "dashboard_caches")
data class DashboardCache(
    @PrimaryKey val key: String,
    val dataJson: String,
    val updatedTimestamp: Long = System.currentTimeMillis(),
    val uuid: String = java.util.UUID.randomUUID().toString()
)




data class SearchResults(
    val patients: List<Patient>,
    val employees: List<Employee>,
    val services: List<Service>,
    val transactions: List<FinancialTransaction>
)

data class ChartPoint(
    val day: Int,
    val income: Double,
    val expense: Double,
    val profit: Double
)

@Entity(tableName = "sync_queue")
data class SyncQueue(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tableName: String,
    val recordId: String, // String to support UUIDs if needed, though most are Int
    val operationType: String, // "INSERT", "UPDATE", "DELETE"
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "PENDING", // "PENDING", "PROCESSING", "FAILED", "CONFLICT", "COMPLETED"
    val retryCount: Int = 0
)
