with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    vm_content = f.read()

import re

# resolveAlertInline signature: fun resolveAlertInline(alertId: String, context: android.content.Context, param: String = "") {}
# But in DashboardScreen it's called with (context, alert, "approve") 
# So let's change signature to fun resolveAlertInline(context: android.content.Context, alert: com.example.data.Alert, param: String = "") {}
vm_content = re.sub(
    r'fun resolveAlertInline\(alertId: String, context: android\.content\.Context, param: String = ""\) \{\}',
    r'fun resolveAlertInline(context: android.content.Context, alert: com.example.data.Alert, param: String = "") {}',
    vm_content
)

# updateFinancialTransaction and deleteFinancialTransaction
if 'fun updateFinancialTransaction' not in vm_content:
    vm_content = vm_content.replace(
        'fun saveTransaction(transaction: com.example.data.FinancialTransaction) {',
        'fun updateFinancialTransaction(transaction: com.example.data.FinancialTransaction) { viewModelScope.launch { repository.insertFinancialTransaction(transaction) } }\n    fun saveTransaction(transaction: com.example.data.FinancialTransaction) {'
    )
    
if 'fun deleteFinancialTransaction' not in vm_content:
    vm_content = vm_content.replace(
        'fun deleteTransaction(transaction: com.example.data.FinancialTransaction) {',
        'fun deleteFinancialTransaction(transaction: com.example.data.FinancialTransaction) { viewModelScope.launch { repository.deleteFinancialTransaction(transaction) } }\n    fun deleteTransaction(transaction: com.example.data.FinancialTransaction) {'
    )

# Alert related methods
if 'fun clearAllAlerts' not in vm_content:
    vm_content = vm_content.replace(
        'fun resolveAlertInline',
        'fun clearAllAlerts() {}\n    fun markAlertAsRead(alert: com.example.data.Alert) {}\n    fun dismissAlert(alert: com.example.data.Alert) {}\n    fun resolveAlertInline'
    )

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(vm_content)
