with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    content = f.read()

import re

# Fix addFinancialTransaction signature
content = content.replace(
    'fun addFinancialTransaction(transaction: com.example.data.FinancialTransaction) {}',
    'fun addFinancialTransaction(type: String, category: String, amount: Double, desc: String, method: String, cbId: Int?, origin: String, reason: String, creator: String, time: Long) {}'
)

# Replace DashboardMetrics and AlertHistory in the file
content = content.replace(
    'val resolvedAlerts: kotlinx.coroutines.flow.StateFlow<List<com.example.data.Alert>> = kotlinx.coroutines.flow.MutableStateFlow(emptyList())',
    'data class AlertHistory(val alert: com.example.data.Alert, val actionPerformed: String, val resolvedBy: String)\n    val resolvedAlerts: kotlinx.coroutines.flow.StateFlow<List<AlertHistory>> = kotlinx.coroutines.flow.MutableStateFlow(emptyList())'
)

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(content)
