package com.example.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

object ReportingLayer {

    // Common marker interface for all Excel rows to enforce compile-time clean architecture type safety
    interface ExcelExportRow

    data class PersonnelReportDto(
        val id: Int,
        val fullName: String,
        val nationalId: String,
        val phone: String,
        val profession: String,
        val position: String,
        val employmentType: String,
        val status: String, // "فعال" / "غیرفعال"
        val commissionModel: String,
        val commissionValue: Double,
        val totalSettledCommissions: Double,
        val totalPendingCommissions: Double,
        val totalPaymentsReceived: Double,
        val bankInfo: String
    ) : ExcelExportRow

    data class PatientReportDto(
        val id: Int,
        val fullName: String,
        val gender: String,
        val age: Int,
        val phone: String,
        val address: String,
        val referralSource: String,
        val status: String, // "فعال" / "غیرفعال"
        val totalInvoiced: Double,
        val totalPaid: Double,
        val remainingBalance: Double,
        val servicesCount: Int,
        val registrationDate: Long,
        val notes: String
    ) : ExcelExportRow

    data class PatientNursingHistoryDto(
        val patientId: Int,
        val patientName: String,
        val date: Long,
        val type: String, // "گزارش پرستاری", "علائم حیاتی", "ثبت زخم"
        val description: String
    ) : ExcelExportRow

    data class ServiceReportDto(
        val id: Int,
        val name: String,
        val category: String,
        val sellingPrice: Double,
        val defaultCost: Double,
        val durationMinutes: Int,
        val timesCompleted: Int,
        val timesScheduled: Int,
        val timesCancelled: Int,
        val status: String // "فعال" / "غیرفعال"
    ) : ExcelExportRow

    data class FinancialSummaryReportDto(
        val totalIncome: Double,
        val totalExpenses: Double,
        val netProfit: Double,
        val totalReceivables: Double,
        val totalPayables: Double
    ) : ExcelExportRow

    data class CashboxReportDto(
        val id: Int,
        val name: String,
        val type: String,
        val accountNumber: String,
        val balance: Double
    ) : ExcelExportRow

    data class ExpenseReportDto(
        val id: Int,
        val title: String,
        val category: String,
        val amount: Double,
        val registrationDate: Long,
        val paymentDate: Long,
        val paymentMethod: String,
        val submitterName: String,
        val description: String,
        val status: String
    ) : ExcelExportRow

    data class FinancialTransactionReportDto(
        val id: Int,
        val type: String, // "درآمد" / "هزینه"
        val category: String,
        val amount: Double,
        val date: Long,
        val description: String,
        val paymentMethod: String,
        val referenceId: Int?
    ) : ExcelExportRow

    data class InvoiceReportDto(
        val invoiceNumber: String,
        val patientName: String,
        val serviceName: String,
        val date: Long,
        val finalPrice: Double,
        val isPaid: Boolean,
        val paymentMethod: String,
        val notes: String
    ) : ExcelExportRow

    data class ContractReportDto(
        val id: Int,
        val employeeId: Int,
        val employeeName: String,
        val title: String,
        val startDate: Long,
        val endDate: Long,
        val status: String,
        val comment: String
    ) : ExcelExportRow

    data class DashboardSummaryReportDto(
        val monthYear: String, // e.g. "۱۴۰۵-۰۴"
        val revenue: Double,
        val expenses: Double,
        val profit: Double,
        val patientRegistrations: Int,
        val employeeVisits: Int
    ) : ExcelExportRow

    data class ReferralReportDto(
        val id: Int,
        val fullName: String,
        val phone: String,
        val specialty: String,
        val notes: String
    ) : ExcelExportRow

    data class ReferralCommissionReportDto(
        val id: Int,
        val referralId: Int,
        val referralName: String,
        val patientId: Int,
        val patientName: String,
        val serviceRegistrationId: Int,
        val serviceName: String,
        val serviceAmount: Double,
        val commissionPercentage: Double,
        val commissionAmount: Double,
        val date: Long,
        val status: String,
        val paymentDate: Long?,
        val documentNumber: String,
        val notes: String
    ) : ExcelExportRow

    data class SystemSettingReportDto(
        val key: String,
        val value: String
    ) : ExcelExportRow

    data class AuditLogReportDto(
        val id: Int,
        val timestamp: Long,
        val user: String,
        val device: String,
        val action: String,
        val affectedModule: String,
        val details: String
    ) : ExcelExportRow

    data class FinancialEditHistoryReportDto(
        val id: Int,
        val entityType: String,
        val entityId: Int,
        val previousValue: String,
        val newValue: String,
        val differenceAmount: Double,
        val editedBy: String,
        val timestamp: Long,
        val reason: String,
        val comment: String
    ) : ExcelExportRow

    data class SyncMetadataReportDto(
        val entityType: String,
        val entityId: String,
        val updatedTimestamp: Long,
        val deletedStatus: Boolean,
        val lastModifiedDeviceId: String,
        val syncStatus: String
    ) : ExcelExportRow

    data class CommissionSettlementReportDto(
        val id: Int,
        val employeeId: Int,
        val employeeName: String,
        val amount: Double,
        val settlementDate: Long,
        val periodStart: Long,
        val periodEnd: Long,
        val notes: String
    ) : ExcelExportRow

    data class NursingReportReportDto(
        val id: Int,
        val registrationId: Int,
        val patientName: String,
        val reporterName: String,
        val date: Long,
        val description: String
    ) : ExcelExportRow

    data class VitalSignsReportDto(
        val id: Int,
        val patientId: Int,
        val patientName: String,
        val bloodPressureSystolic: Int,
        val bloodPressureDiastolic: Int,
        val heartRate: Int,
        val temperatureCelsius: Double,
        val oxygenSaturation: Int,
        val date: Long
    ) : ExcelExportRow

    data class WoundRecordReportDto(
        val id: Int,
        val patientId: Int,
        val patientName: String,
        val woundType: String,
        val stage: String,
        val description: String,
        val date: Long
    ) : ExcelExportRow

    data class ServiceRegistrationReportDto(
        val id: Int,
        val patientId: Int,
        val patientName: String,
        val employeeId: Int,
        val employeeName: String,
        val serviceId: Int,
        val serviceName: String,
        val dateTime: Long,
        val sellingPrice: Double,
        val employeeCost: Double,
        val transportationCost: Double,
        val otherCosts: Double,
        val discount: Double,
        val finalPrice: Double,
        val paymentMethod: String,
        val invoiceNumber: String,
        val notes: String,
        val isPaid: Boolean,
        val workflowStatus: String
    ) : ExcelExportRow

    data class StaffProfileReportDto(
        val id: Int,
        val employeeId: Int,
        val employeeName: String,
        val profession: String,
        val phone: String,
        val hasNationalIdCard: Boolean,
        val hasDegree: Boolean,
        val hasLicense: Boolean,
        val hasContract: Boolean,
        val status: String,
        val comment: String
    ) : ExcelExportRow

    data class ConsentFormReportDto(
        val id: Int,
        val patientId: Int,
        val patientName: String,
        val title: String,
        val content: String,
        val isSigned: Boolean,
        val date: Long
    ) : ExcelExportRow

    data class PrescriptionReportDto(
        val id: Int,
        val patientId: Int,
        val patientName: String,
        val doctorName: String,
        val medicineList: String,
        val date: Long
    ) : ExcelExportRow

    data class JournalEntryReportDto(
        val id: Int,
        val documentNumber: String,
        val debitAccount: String,
        val creditAccount: String,
        val amount: Double,
        val date: Long,
        val reference: String,
        val referenceId: Int?
    ) : ExcelExportRow

    // Complete snapshot package containing independent, fully mapped report DTO lists.
    data class BusinessReportSnapshot(
        val personnel: List<PersonnelReportDto>,
        val patients: List<PatientReportDto>,
        val nursingHistories: List<PatientNursingHistoryDto>,
        val services: List<ServiceReportDto>,
        val financialSummary: FinancialSummaryReportDto,
        val cashboxes: List<CashboxReportDto>,
        val expenses: List<ExpenseReportDto>,
        val financialTransactions: List<FinancialTransactionReportDto>,
        val invoices: List<InvoiceReportDto>,
        val contracts: List<ContractReportDto>,
        val dashboardSummaries: List<DashboardSummaryReportDto>,
        val referrals: List<ReferralReportDto>, 
        val referralCommissions: List<ReferralCommissionReportDto>,
        val systemSettings: List<SystemSettingReportDto>,
        val auditLogs: List<AuditLogReportDto>,
        val editHistories: List<FinancialEditHistoryReportDto>,
        val syncMetadata: List<SyncMetadataReportDto>,
        val commissionSettlements: List<CommissionSettlementReportDto>,
        val nursingReports: List<NursingReportReportDto>,
        val vitalSigns: List<VitalSignsReportDto>,
        val woundRecords: List<WoundRecordReportDto>,
        val serviceRegistrations: List<ServiceRegistrationReportDto>,
        val staffProfiles: List<StaffProfileReportDto> = emptyList(),
        val consentForms: List<ConsentFormReportDto> = emptyList(),
        val prescriptions: List<PrescriptionReportDto> = emptyList(),
        val journalEntries: List<JournalEntryReportDto> = emptyList()
    )

    // Generate snapshot completely from repository on a background thread.
    suspend fun generateSnapshot(repository: HamrahanRepository): BusinessReportSnapshot {
        // Fetch snapshots of all database tables directly via suspending lists to ensure real-time integrity and eliminate staleness.
        val pList = repository.dao.getPatientsList()
        val eList = repository.dao.getEmployeesList()
        val sList = repository.dao.getServicesList()
        val regList = repository.dao.getServiceRegistrationsList()
        val txList = repository.dao.getFinancialTransactionsList()
        val cbList = repository.dao.getCashboxesList()
        val setList = repository.dao.getCommissionSettlementsList()
        val expList = repository.dao.getExpensesList()
        val refList = repository.dao.getReferralsList()
        val rcList = repository.dao.getReferralCommissionsList()
        val conList = repository.dao.getContractsList()
        val nrList = repository.dao.getNursingReportsList()
        val vsList = repository.dao.getVitalSignsList()
        val wrList = repository.dao.getWoundRecordsList()
        val spList = repository.dao.getStaffProfilesList()
        val cfList = repository.dao.getConsentFormsList()
        val prList = repository.dao.getPrescriptionsList()
        val jeList = repository.dao.getJournalEntriesList()

        // Create fast lookup maps to optimize performance (O(N) instead of O(N^2))
        val employeesMap = eList.associateBy { it.id }
        val patientsMap = pList.associateBy { it.id }
        val servicesMap = sList.associateBy { it.id }

        // --- 1. Personnel Mapping ---
        val personnelDtos = eList.map { emp ->
            val empRegs = regList.filter { it.employeeId == emp.id }
            val totalCommissions = empRegs.sumOf { it.employeeCost }
            val settledCommissions = setList.filter { it.employeeId == emp.id }.sumOf { it.amount }
            val pendingCommissions = (totalCommissions - settledCommissions).coerceAtLeast(0.0)
            
            val payments = txList.filter { tx -> 
                tx.type == "هزینه" && 
                (tx.category == "حقوق همکار" || tx.category == "تسویه کارمزد" || tx.description.contains(emp.fullName))
            }.sumOf { it.amount }

            PersonnelReportDto(
                id = emp.id,
                fullName = emp.fullName,
                nationalId = emp.nationalId,
                phone = emp.phone,
                profession = emp.profession,
                position = emp.position,
                employmentType = emp.employmentType,
                status = emp.status,
                commissionModel = emp.commissionModel,
                commissionValue = emp.commissionValue,
                totalSettledCommissions = settledCommissions,
                totalPendingCommissions = pendingCommissions,
                totalPaymentsReceived = payments,
                bankInfo = emp.bankInfo
            )
        }

        // --- 2. Patient Mapping ---
        val patientDtos = pList.map { p ->
            val pRegs = regList.filter { it.patientId == p.id }
            val totalInvoiced = pRegs.sumOf { it.finalPrice }
            val totalPaid = pRegs.filter { it.isPaid }.sumOf { it.finalPrice }
            val remainingBalance = totalInvoiced - totalPaid

            PatientReportDto(
                id = p.id,
                fullName = p.fullName,
                gender = p.gender,
                age = p.age,
                phone = p.phone,
                address = p.address,
                referralSource = p.referralSource,
                status = p.status,
                totalInvoiced = totalInvoiced,
                totalPaid = totalPaid,
                remainingBalance = remainingBalance,
                servicesCount = pRegs.size,
                registrationDate = p.registrationDate,
                notes = p.notes
            )
        }

        // --- 2.1 Nursing History Mapping ---
        val nursingHistoryDtos = ArrayList<PatientNursingHistoryDto>()
        nrList.forEach { nr ->
            val pName = regList.find { it.id == nr.registrationId }?.let { patientsMap[it.patientId]?.fullName } ?: "نامشخص"
            val pId = regList.find { it.id == nr.registrationId }?.patientId ?: 0
            nursingHistoryDtos.add(
                PatientNursingHistoryDto(
                    patientId = pId,
                    patientName = pName,
                    date = nr.date,
                    type = "گزارش پرستاری",
                    description = nr.description
                )
            )
        }
        vsList.forEach { vs ->
            val pName = patientsMap[vs.patientId]?.fullName ?: "نامشخص"
            nursingHistoryDtos.add(
                PatientNursingHistoryDto(
                    patientId = vs.patientId,
                    patientName = pName,
                    date = vs.date,
                    type = "علائم حیاتی",
                    description = "فشار: ${vs.bloodPressureSystolic}/${vs.bloodPressureDiastolic} | ضربان: ${vs.heartRate} | دمای بدن: ${vs.temperatureCelsius} | اکسیژن: ${vs.oxygenSaturation}%"
                )
            )
        }
        wrList.forEach { wr ->
            val pName = patientsMap[wr.patientId]?.fullName ?: "نامشخص"
            nursingHistoryDtos.add(
                PatientNursingHistoryDto(
                    patientId = wr.patientId,
                    patientName = pName,
                    date = wr.date,
                    type = "ثبت زخم",
                    description = "نوع زخم: ${wr.woundType} | گرید/شدت: ${wr.stage} | توضیحات: ${wr.description}"
                )
            )
        }
        nursingHistoryDtos.sortByDescending { it.date }

        // --- 3. Service Mapping ---
        val serviceDtos = sList.map { s ->
            val sRegs = regList.filter { it.serviceId == s.id }
            val completed = sRegs.filter { it.workflowStatus == "Completed" || it.isPaid }.size
            val scheduled = sRegs.filter { it.workflowStatus == "Scheduled" || it.workflowStatus == "Submitted" }.size
            val cancelled = sRegs.filter { it.isDeleted || it.workflowStatus == "Cancelled" }.size

            ServiceReportDto(
                id = s.id,
                name = s.name,
                category = s.category,
                sellingPrice = s.sellingPrice,
                defaultCost = s.defaultCost,
                durationMinutes = s.durationMinutes,
                timesCompleted = completed,
                timesScheduled = scheduled,
                timesCancelled = cancelled,
                status = if (s.isActive) "فعال" else "غیرفعال"
            )
        }

        // --- 4. Financial Summary Mapping ---
        val totalRevenue = txList.filter { it.type == "درآمد" }.sumOf { it.amount }
        val totalExpenses = txList.filter { it.type == "هزینه" }.sumOf { it.amount }
        val netProfit = totalRevenue - totalExpenses
        val totalReceivables = regList.filter { !it.isPaid }.sumOf { it.finalPrice }
        
        val totalCommissionsAll = regList.sumOf { it.employeeCost }
        val totalSettledAll = setList.sumOf { it.amount }
        val totalPayables = (totalCommissionsAll - totalSettledAll).coerceAtLeast(0.0)

        val financialSummaryDto = FinancialSummaryReportDto(
            totalIncome = totalRevenue,
            totalExpenses = totalExpenses,
            netProfit = netProfit,
            totalReceivables = totalReceivables,
            totalPayables = totalPayables
        )

        // --- 4.1 Cashbox Report Mapping ---
        val cashboxDtos = cbList.map { cb ->
            CashboxReportDto(
                id = cb.id,
                name = cb.name,
                type = cb.type,
                accountNumber = cb.accountNumber,
                balance = cb.balance
            )
        }

        // --- 4.2 Expense Report Mapping ---
        val expenseDtos = expList.map { exp ->
            ExpenseReportDto(
                id = exp.id,
                title = exp.title,
                category = exp.category,
                amount = exp.amount,
                registrationDate = exp.registrationDate,
                paymentDate = exp.paymentDate,
                paymentMethod = exp.paymentMethod,
                submitterName = exp.submitterName,
                description = exp.description,
                status = exp.workflowStatus
            )
        }

        // --- 4.3 Transaction Mapping ---
        val txDtos = txList.map { tx ->
            FinancialTransactionReportDto(
                id = tx.id,
                type = tx.type,
                category = tx.category,
                amount = tx.amount,
                date = tx.date,
                description = tx.description,
                paymentMethod = tx.paymentMethod,
                referenceId = tx.referenceId
            )
        }

        // --- 5. Invoice Mapping ---
        val invoiceDtos = regList.map { reg ->
            val pName = patientsMap[reg.patientId]?.fullName ?: "بیمار شناسه ${reg.patientId}"
            val sName = servicesMap[reg.serviceId]?.name ?: "خدمت شناسه ${reg.serviceId}"
            InvoiceReportDto(
                invoiceNumber = reg.invoiceNumber.ifBlank { "INV-${reg.id}" },
                patientName = pName,
                serviceName = sName,
                date = reg.dateTime,
                finalPrice = reg.finalPrice,
                isPaid = reg.isPaid,
                paymentMethod = reg.paymentMethod,
                notes = reg.notes
            )
        }

        // --- 6. Contract Mapping ---
        val contractDtos = conList.map { con ->
            val empName = employeesMap[con.employeeId]?.fullName ?: "همکار شناسه ${con.employeeId}"
            ContractReportDto(
                id = con.id,
                employeeId = con.employeeId,
                employeeName = empName,
                title = con.title,
                startDate = con.startDate,
                endDate = con.endDate,
                status = con.status,
                comment = con.comment
            )
        }

        // --- 7. Dashboard Summaries Mapping ---
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
        val txGrouped = txList.groupBy { tx ->
            sdf.format(Date(tx.date))
        }
        val pRegGrouped = pList.groupBy { p ->
            sdf.format(Date(p.registrationDate))
        }
        val sRegGrouped = regList.groupBy { r ->
            sdf.format(Date(r.dateTime))
        }

        val allMonths = (txGrouped.keys + pRegGrouped.keys + sRegGrouped.keys).filter { it.isNotBlank() }.sortedDescending()
        val dashboardDtos = allMonths.map { month ->
            val monthTxs = txGrouped[month] ?: emptyList()
            val monthIncome = monthTxs.filter { it.type == "درآمد" }.sumOf { it.amount }
            val monthExp = monthTxs.filter { it.type == "هزینه" }.sumOf { it.amount }
            val regCount = pRegGrouped[month]?.size ?: 0
            val visitCount = sRegGrouped[month]?.size ?: 0

            DashboardSummaryReportDto(
                monthYear = month,
                revenue = monthIncome,
                expenses = monthExp,
                profit = monthIncome - monthExp,
                patientRegistrations = regCount,
                employeeVisits = visitCount
            )
        }

        // --- 8. Referral Mapping ---
        val referralDtos = refList.map { ref ->
            ReferralReportDto(
                id = ref.id,
                fullName = ref.name,
                phone = ref.phone,
                specialty = ref.type,
                notes = ref.notes
            )
        }

        // --- 9. Referral Commission Mapping ---
        val referralCommissionDtos = rcList.map { rc ->
            val refName = refList.find { it.id == rc.referralId }?.name ?: "شناسه ${rc.referralId}"
            val patName = pList.find { it.id == rc.patientId }?.fullName ?: "شناسه ${rc.patientId}"
            ReferralCommissionReportDto(
                id = rc.id,
                referralId = rc.referralId,
                referralName = refName,
                patientId = rc.patientId,
                patientName = patName,
                serviceRegistrationId = rc.serviceRegistrationId,
                serviceName = rc.serviceName,
                serviceAmount = rc.serviceAmount,
                commissionPercentage = rc.commissionPercentage,
                commissionAmount = rc.commissionAmount,
                date = rc.date,
                status = rc.status,
                paymentDate = rc.paymentDate,
                documentNumber = rc.documentNumber,
                notes = rc.notes
            )
        }

        // --- 10. System Settings Mapping ---
        val sysSettings = repository.dao.getSystemSettingsList()
        val systemSettingDtos = sysSettings.map { setting ->
            SystemSettingReportDto(
                key = setting.key,
                value = setting.value
            )
        }

        // --- 11. Audit Logs Mapping ---
        val auditLgs = repository.dao.getAuditLogsList()
        val auditLogDtos = auditLgs.map { al ->
            AuditLogReportDto(
                id = al.id,
                timestamp = al.timestamp,
                user = al.user,
                device = al.device,
                action = al.action,
                affectedModule = al.affectedModule,
                details = al.details
            )
        }

        // --- 12. Financial Edit History Mapping ---
        val edHistories = repository.dao.getEditHistoriesList()
        val editHistoryDtos = edHistories.map { fe ->
            FinancialEditHistoryReportDto(
                id = fe.id,
                entityType = fe.entityType,
                entityId = fe.entityId,
                previousValue = fe.previousValue,
                newValue = fe.newValue,
                differenceAmount = fe.differenceAmount,
                editedBy = fe.editedBy,
                timestamp = fe.timestamp,
                reason = fe.reason,
                comment = fe.comment
            )
        }

        // --- 13. Sync Metadata Mapping ---
        val synMetadata = repository.dao.getSyncMetadataList()
        val syncMetadataDtos = synMetadata.map { sm ->
            SyncMetadataReportDto(
                entityType = sm.entityType,
                entityId = sm.entityId,
                updatedTimestamp = sm.updatedTimestamp,
                deletedStatus = sm.deletedStatus,
                lastModifiedDeviceId = sm.lastModifiedDeviceId,
                syncStatus = sm.syncStatus
            )
        }

        // --- 14. Commission Settlement Mapping ---
        val commSettlements = repository.dao.getCommissionSettlementsList()
        val commissionSettlementDtos = commSettlements.map { cs ->
            val empName = employeesMap[cs.employeeId]?.fullName ?: "همکار شناسه ${cs.employeeId}"
            CommissionSettlementReportDto(
                id = cs.id,
                employeeId = cs.employeeId,
                employeeName = empName,
                amount = cs.amount,
                settlementDate = cs.settlementDate,
                periodStart = cs.periodStart,
                periodEnd = cs.periodEnd,
                notes = cs.notes
            )
        }

        // --- 15. Nursing Reports Mapping ---
        val nursReports = repository.dao.getNursingReportsList()
        val nursingReportDtos = nursReports.map { nr ->
            val pName = patientsMap[regList.find { it.id == nr.registrationId }?.patientId ?: 0]?.fullName ?: "بیمار ID: ${nr.registrationId}"
            NursingReportReportDto(
                id = nr.id,
                registrationId = nr.registrationId,
                patientName = pName,
                reporterName = nr.reporterName,
                date = nr.date,
                description = nr.description
            )
        }

        // --- 16. Vital Signs Mapping ---
        val vitSigns = repository.dao.getVitalSignsList()
        val vitalSignsDtos = vitSigns.map { vs ->
            val pName = patientsMap[vs.patientId]?.fullName ?: "بیمار شناسه ${vs.patientId}"
            VitalSignsReportDto(
                id = vs.id,
                patientId = vs.patientId,
                patientName = pName,
                bloodPressureSystolic = vs.bloodPressureSystolic,
                bloodPressureDiastolic = vs.bloodPressureDiastolic,
                heartRate = vs.heartRate,
                temperatureCelsius = vs.temperatureCelsius,
                oxygenSaturation = vs.oxygenSaturation,
                date = vs.date
            )
        }

        // --- 17. Wound Records Mapping ---
        val wndRecords = repository.dao.getWoundRecordsList()
        val woundRecordDtos = wndRecords.map { wr ->
            val pName = patientsMap[wr.patientId]?.fullName ?: "بیمار شناسه ${wr.patientId}"
            WoundRecordReportDto(
                id = wr.id,
                patientId = wr.patientId,
                patientName = pName,
                woundType = wr.woundType,
                stage = wr.stage,
                description = wr.description,
                date = wr.date
            )
        }

        // --- 18. Service Registrations Mapping ---
        val serviceRegistrationDtos = regList.map { reg ->
            val pName = patientsMap[reg.patientId]?.fullName ?: "بیمار شناسه ${reg.patientId}"
            val eName = employeesMap[reg.employeeId]?.fullName ?: "کادر درمان شناسه ${reg.employeeId}"
            val sName = servicesMap[reg.serviceId]?.name ?: "خدمت شناسه ${reg.serviceId}"
            ServiceRegistrationReportDto(
                id = reg.id,
                patientId = reg.patientId,
                patientName = pName,
                employeeId = reg.employeeId,
                employeeName = eName,
                serviceId = reg.serviceId,
                serviceName = sName,
                dateTime = reg.dateTime,
                sellingPrice = reg.sellingPrice,
                employeeCost = reg.employeeCost,
                transportationCost = reg.transportationCost,
                otherCosts = reg.otherCosts,
                discount = reg.discount,
                finalPrice = reg.finalPrice,
                paymentMethod = reg.paymentMethod,
                invoiceNumber = reg.invoiceNumber,
                notes = reg.notes,
                isPaid = reg.isPaid,
                workflowStatus = reg.workflowStatus
            )
        }

        // --- 19. Staff Profiles Mapping ---
        val staffProfileDtos = spList.map { sp ->
            val emp = employeesMap[sp.employeeId]
            StaffProfileReportDto(
                id = sp.id,
                employeeId = sp.employeeId,
                employeeName = emp?.fullName ?: "همکار شناسه ${sp.employeeId}",
                profession = emp?.profession ?: "-",
                phone = emp?.phone ?: "-",
                hasNationalIdCard = sp.hasNationalIdCard,
                hasDegree = sp.hasDegree,
                hasLicense = sp.hasLicense,
                hasContract = sp.hasContract,
                status = sp.status,
                comment = sp.comment
            )
        }

        // --- 20. Consent Forms Mapping ---
        val consentFormDtos = cfList.map { cf ->
            val pName = patientsMap[cf.patientId]?.fullName ?: "بیمار شناسه ${cf.patientId}"
            ConsentFormReportDto(
                id = cf.id,
                patientId = cf.patientId,
                patientName = pName,
                title = cf.title,
                content = cf.content,
                isSigned = cf.isSigned,
                date = cf.date
            )
        }

        // --- 21. Prescriptions Mapping ---
        val prescriptionDtos = prList.map { pr ->
            val pName = patientsMap[pr.patientId]?.fullName ?: "بیمار شناسه ${pr.patientId}"
            PrescriptionReportDto(
                id = pr.id,
                patientId = pr.patientId,
                patientName = pName,
                doctorName = pr.doctorName,
                medicineList = pr.medicineList,
                date = pr.date
            )
        }

        // --- 22. Journal Entries Mapping ---
        val journalEntryDtos = jeList.map { je ->
            JournalEntryReportDto(
                id = je.id,
                documentNumber = je.documentNumber,
                debitAccount = je.debitAccount,
                creditAccount = je.creditAccount,
                amount = je.amount,
                date = je.date,
                reference = je.reference,
                referenceId = je.referenceId
            )
        }

        return BusinessReportSnapshot(
            personnel = personnelDtos,
            patients = patientDtos,
            nursingHistories = nursingHistoryDtos,
            services = serviceDtos,
            financialSummary = financialSummaryDto,
            cashboxes = cashboxDtos,
            expenses = expenseDtos,
            financialTransactions = txDtos,
            invoices = invoiceDtos,
            contracts = contractDtos,
            dashboardSummaries = dashboardDtos,
            referrals = referralDtos,
            referralCommissions = referralCommissionDtos,
            systemSettings = systemSettingDtos,
            auditLogs = auditLogDtos,
            editHistories = editHistoryDtos,
            syncMetadata = syncMetadataDtos,
            commissionSettlements = commissionSettlementDtos,
            nursingReports = nursingReportDtos,
            vitalSigns = vitalSignsDtos,
            woundRecords = woundRecordDtos,
            serviceRegistrations = serviceRegistrationDtos,
            staffProfiles = staffProfileDtos,
            consentForms = consentFormDtos,
            prescriptions = prescriptionDtos,
            journalEntries = journalEntryDtos
        )
    }
}
