import re

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace("fun registerService(registration: com.example.data.ServiceRegistration) {}", "fun registerService(patientId: Int, serviceId: Int, employeeId: Int, dateTime: Long, sellingPrice: Double, employeeCost: Double, transportationCost: Double, otherCosts: Double, discount: Double, paymentMethod: String, invoiceNumber: String, notes: String, selectedCashboxId: Int, isPaid: Boolean, consumablesOwner: String) {}")
content = content.replace("fun exportDataToExcel(context: android.content.Context, data: Any) {}", "fun exportDataToExcel(outputStream: java.io.OutputStream): Boolean { return true }")

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(content)
