with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    content = f.read()

# dismissAllAlerts
if 'fun dismissAllAlerts' not in content:
    content = content.replace(
        'fun clearAllAlerts() {}',
        'fun clearAllAlerts() {}\n    fun dismissAllAlerts() {}'
    )

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(content)
