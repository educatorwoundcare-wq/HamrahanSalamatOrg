with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    content = f.read()

import re

# Remove the previously added missing methods block
content = re.sub(r'// --- Missing Methods for AccountingScreen ---[\s\S]*?fun markAllAlertsAsRead\(\) \{\}', '', content)

missing_methods = """
    // --- Missing Methods ---
    fun recalculateDashboardTotals(onComplete: () -> Unit = {}) {}
    fun recalculateCashboxBalances(onComplete: () -> Unit = {}) {}
    fun removeOrphanLedgerEntries(onComplete: () -> Unit = {}) {}
    fun repairBrokenReferences(onComplete: () -> Unit = {}) {}
    fun validateAndRepairFinancialIntegrity(onComplete: () -> Unit = {}) {}
    fun scanFinancialIntegrityIssues(onComplete: () -> Unit = {}) {}
    fun refreshFinancialIndexes(onComplete: () -> Unit = {}) {}
    fun clearLedger(onComplete: () -> Unit = {}) {}
    fun rebuildFinancialLedger(onComplete: () -> Unit = {}) {}
    
    fun issueAdjustmentEntry(id: Int, newAmount: Double, reason: String, onComplete: () -> Unit = {}) {}
    fun issueReversingEntry(id: Int, reason: String, onComplete: () -> Unit = {}) {}
    fun saveCashbox(cashbox: com.example.data.Cashbox) {}

    fun settleCommission(employeeId: Int, amount: Double, periodStart: Long, periodEnd: Long, notes: String, selectedCashboxId: Int?, onComplete: () -> Unit = {}) {}
    fun payReferralCommission(commissionId: Int, amount: Double, date: Long, paymentMethod: String, onComplete: () -> Unit = {}) {}

    fun createCompanyWorkspace(name: String, nationalCode: String, phone: String, address: String) {}
    fun joinCompanyWorkspace(code: String, phone: String) {}
    fun switchActiveDevice(deviceId: String) {}
    fun changeDeviceRole(deviceId: String, role: String) {}
    fun approveDeviceAccess(deviceId: String) {}
    fun revokeDeviceAccess(deviceId: String) {}
    fun deleteConnectedDevice(deviceId: String) {}
    fun updateSettings(name: String, tax: String, currency: String) {}
    fun renameDevice(deviceId: String, newName: String) {}

    fun runAlertDiagnostics(onComplete: () -> Unit = {}) {}
    fun approveAllEligibleAlerts(onComplete: () -> Unit = {}) {}
    
    fun deleteReferral(referral: com.example.data.Referral, onComplete: (String) -> Unit = {}) {}
    fun updateReferral(referral: com.example.data.Referral) {}
"""

content = content.replace('val monthlyChartData = kotlinx.coroutines.flow.MutableStateFlow<List<com.example.data.ChartPoint>>(emptyList())', 'val monthlyChartData = kotlinx.coroutines.flow.MutableStateFlow<List<com.example.data.ChartPoint>>(emptyList())\n' + missing_methods)

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(content)
