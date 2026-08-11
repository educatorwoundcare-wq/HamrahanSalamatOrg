with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    content = f.read()

import re
# First, remove the previously added missing methods
content = re.sub(r'// --- Missing Methods ---[\s\S]*?fun updateReferral\(referral: com\.example\.data\.Referral\) \{\}', '', content)

missing_methods = """
    // --- Missing Methods ---
    fun recalculateDashboardTotals(onComplete: (String) -> Unit = {}) {}
    fun recalculateCashboxBalances(onComplete: (String) -> Unit = {}) {}
    fun removeOrphanLedgerEntries(onComplete: (String) -> Unit = {}) {}
    fun repairBrokenReferences(onComplete: (String) -> Unit = {}) {}
    fun validateAndRepairFinancialIntegrity(onComplete: (String) -> Unit = {}) {}
    fun scanFinancialIntegrityIssues(onComplete: (String) -> Unit = {}) {}
    fun refreshFinancialIndexes(onComplete: (String) -> Unit = {}) {}
    fun clearLedger(onComplete: (String) -> Unit = {}) {}
    fun rebuildFinancialLedger(onComplete: (String) -> Unit = {}) {}
    
    fun issueAdjustmentEntry(id: Int, newAmount: Double, reason: String, onComplete: () -> Unit = {}) {}
    fun issueReversingEntry(id: Int, reason: String, onComplete: () -> Unit = {}) {}
    fun saveCashbox(cashbox: com.example.data.Cashbox) {}

    fun settleCommission(employeeId: Int, amount: Double, periodStart: Long, periodEnd: Long, notes: String, selectedCashboxId: Int?, onComplete: () -> Unit = {}) {}
    fun payReferralCommission(commissionId: Int, docNum: String, notes: String) {}

    fun createCompanyWorkspace(name: String, nationalCode: String, phone: String, address: String) {}
    fun joinCompanyWorkspace(code: String, phone: String) {}
    fun switchActiveDevice(deviceId: String, deviceName: String) {}
    fun changeDeviceRole(deviceId: String, role: String) {}
    fun approveDeviceAccess(deviceId: String) {}
    fun revokeDeviceAccess(deviceId: String) {}
    fun deleteConnectedDevice(deviceId: String) {}
    fun updateSettings(name: String, tax: Double, currency: String) {}
    fun renameDevice(deviceId: String, newName: String) {}

    val resolvedAlerts: kotlinx.coroutines.flow.StateFlow<List<com.example.data.Alert>> = kotlinx.coroutines.flow.MutableStateFlow(emptyList())
    fun runAlertDiagnostics(context: android.content.Context? = null, onComplete: () -> Unit = {}) {}
    fun approveAllEligibleAlerts(context: android.content.Context? = null, onComplete: () -> Unit = {}) {}
    fun markAllAlertsAsRead(context: android.content.Context? = null, onComplete: () -> Unit = {}) {}
    
    fun deleteReferral(referral: com.example.data.Referral, onComplete: (String) -> Unit = {}) {}
    fun updateReferral(referral: com.example.data.Referral) {}
    fun addFinancialTransaction(transaction: com.example.data.FinancialTransaction) {}
    fun deleteCashbox(cashbox: com.example.data.Cashbox) {}
    fun clearNavigateToScreen() {}
    fun clearDeepLink() {}
"""

content = content.replace('val monthlyChartData = kotlinx.coroutines.flow.MutableStateFlow<List<com.example.data.ChartPoint>>(emptyList())', 'val monthlyChartData = kotlinx.coroutines.flow.MutableStateFlow<List<com.example.data.ChartPoint>>(emptyList())\n' + missing_methods)

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(content)
