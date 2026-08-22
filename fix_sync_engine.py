import re

with open('app/src/main/java/com/example/data/SyncEngine.kt', 'r') as f:
    content = f.read()

replacement_get_payload = """    private suspend fun getPayloadForOperation(op: SyncQueue): String? {
        if (op.operationType == "DELETE") {
            // op.recordId is already the canonical UUID
            return \"\"\"{"uuid": "${op.recordId}"}\"\"\"
        }
        
        val obj = when (op.tableName) {
            "Patient" -> dao.getPatientByUuid(op.recordId)
            "Employee" -> dao.getEmployeeByUuid(op.recordId)
            "Service" -> dao.getServiceByUuid(op.recordId)
            "ServiceRegistration" -> dao.getServiceRegistrationByUuid(op.recordId)
            "FinancialTransaction" -> dao.getFinancialTransactionByUuid(op.recordId)
            "Cashbox" -> dao.getCashboxByUuid(op.recordId)
            "Expense" -> dao.getExpenseByUuid(op.recordId)
            "JournalEntry" -> dao.getJournalEntryByUuid(op.recordId)
            "FinancialReport" -> dao.getFinancialReportsList().find { it.uuid == op.recordId }
            "SystemSetting" -> dao.getSystemSettingByKey(op.recordId) // key is used as identifier
            "Referral" -> dao.getReferralByUuid(op.recordId)
            "ReferralCommission" -> dao.getReferralCommissionByUuid(op.recordId)
            "CommissionSettlement" -> dao.getCommissionSettlementByUuid(op.recordId)
            "ExpenseCategory" -> dao.getExpenseCategoryByUuid(op.recordId)
            "FixedExpenseTemplate" -> dao.getFixedExpenseTemplateByUuid(op.recordId)
            "AuditLog" -> dao.getAllAuditLogsList().find { it.uuid == op.recordId }
            "UserPermission" -> dao.getAllUserPermissionsList().find { it.permissionName == op.recordId }
            "FinancialEditHistory" -> dao.getAllEditHistoriesList().find { it.uuid == op.recordId }
            "Alert" -> dao.getAlertByUuid(op.recordId)
            "Contract" -> dao.getContractByUuid(op.recordId)
            "StaffProfile" -> dao.getStaffProfileByUuid(op.recordId)
            "ServiceSchedule" -> dao.getServiceScheduleByUuid(op.recordId)
            "NursingReport" -> dao.getNursingReportByUuid(op.recordId)
            "VitalSigns" -> dao.getVitalSignsByUuid(op.recordId)
            "WoundRecord" -> dao.getWoundRecordByUuid(op.recordId)
            "ConsentForm" -> dao.getConsentFormByUuid(op.recordId)
            "Prescription" -> dao.getPrescriptionByUuid(op.recordId)
            "DashboardCache" -> dao.getDashboardCacheByUuid(op.recordId)
            else -> null
        }
        if (obj == null) return null
        return SyncSerializer.serialize(op.tableName, obj)
    }"""

content = re.sub(r'    private suspend fun getPayloadForOperation.*?return SyncSerializer\.serialize\(op\.tableName, obj\)\n    \}', replacement_get_payload, content, flags=re.DOTALL)

with open('app/src/main/java/com/example/data/SyncEngine.kt', 'w') as f:
    f.write(content)

