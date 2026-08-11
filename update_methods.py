import re

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    vm_content = f.read()

# Fix resolveAlertInline
vm_content = vm_content.replace('fun resolveAlertInline(alertId: String) {}', 'fun resolveAlertInline(alertId: String, context: android.content.Context, param: String = "") {}')

# Fix deepLink
vm_content = vm_content.replace('_deepLink.value = null', '/* Not needed since deepLink flow might be different */')

# Fix PatientScreen insertReferral
vm_content = vm_content.replace('fun insertReferral(referral: com.example.data.Referral) {}', 'fun insertReferral(name: String, type: String, phone: String, address: String, commissionPercentage: Double, commissionFixedAmount: Double, notes: String, onComplete: (Boolean) -> Unit) {}')

# Fix ExpensesScreen saveExpense
vm_content = vm_content.replace('fun saveExpense(expense: com.example.data.Expense) {}', 'fun saveExpense(expense: com.example.data.Expense, param1: String = "", param2: String = "") {}')

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(vm_content)
