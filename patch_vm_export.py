import re

with open("app/src/main/java/com/example/ui/HamrahanViewModel.kt", "r") as f:
    content = f.read()

target = """    // Report Screen
    fun exportDataToExcel(outputStream: java.io.OutputStream, sheets: List<String> = emptyList()): Boolean { return true }"""

replacement = """    // Report Screen
    fun exportDataToExcel(outputStream: java.io.OutputStream, sheets: List<String> = emptyList()): Boolean {
        return com.example.data.ExcelExporter.exportToExcel(
            context = repository.context,
            outputStream = outputStream,
            patients = patients.value,
            employees = employees.value,
            services = services.value,
            registrations = registrations.value,
            transactions = transactions.value,
            expenses = expenses.value,
            referrals = referrals.value,
            commissions = referralCommissions.value
        )
    }"""

if target in content:
    content = content.replace(target, replacement)
    with open("app/src/main/java/com/example/ui/HamrahanViewModel.kt", "w") as f:
        f.write(content)
    print("Patched HamrahanViewModel successfully!")
else:
    print("Could not find the target string!")

