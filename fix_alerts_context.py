with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'fun runAlertDiagnostics(onComplete: () -> Unit = {}) {}',
    'fun runAlertDiagnostics(context: android.content.Context? = null, onComplete: () -> Unit = {}) {}'
)
content = content.replace(
    'fun approveAllEligibleAlerts(onComplete: () -> Unit = {}) {}',
    'fun approveAllEligibleAlerts(context: android.content.Context? = null, onComplete: () -> Unit = {}) {}'
)
content = content.replace(
    'fun markAllAlertsAsRead() {}',
    'fun markAllAlertsAsRead(context: android.content.Context? = null) {}'
)

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(content)
