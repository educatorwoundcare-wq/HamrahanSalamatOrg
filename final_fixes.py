import re

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    vm_content = f.read()

# Fix update checking logic
vm_content = vm_content.replace('config.minVersion > currentVersion || config.latestVersion > currentVersion', 'config.latestVersionCode > currentVersion')

# Fix RegistrationScreen missing arguments by updating ViewModel instead of screen
# Wait, RegistrationScreen is calling `viewModel.editServiceRegistration` with `id`, `patientId`, etc.
# But HamrahanViewModel has `fun editServiceRegistration(registration: com.example.data.ServiceRegistration, patientId: Int... )`
# Which is a mix of both. We should update ViewModel to accept the exact arguments RegistrationScreen passes.

vm_content = re.sub(
    r'fun editServiceRegistration\(registration: com\.example\.data\.ServiceRegistration, patientId: Int, .*?\) \{',
    r'fun editServiceRegistration(id: Int, patientId: Int, serviceId: Int, employeeId: Int, dateTime: Long, sellingPrice: Double, employeeCost: Double, transportationCost: Double, otherCosts: Double, discount: Double, paymentMethod: String, invoiceNumber: String, notes: String, selectedCashboxId: Int?, isPaid: Boolean, selectedServices: List<com.example.data.Service>, consumablesOwner: String, reason: String?, comment: String?) {',
    vm_content, flags=re.DOTALL
)

vm_content = re.sub(
    r'fun registerPackage\(patientId: Int, packageType: String, .*?\) \{',
    r'fun registerPackage(patientId: Int, employeeId: Int, selectedServices: List<com.example.data.Service>, dateTime: Long, sellingPrice: Double, employeeCost: Double, transportationCost: Double, otherCosts: Double, discount: Double, paymentMethod: String, invoiceNumber: String, notes: String, selectedCashboxId: Int?, isPaid: Boolean, consumablesOwner: String) {',
    vm_content, flags=re.DOTALL
)

# And registerService:
vm_content = re.sub(
    r'fun registerService\(patientId: Int, serviceId: Int, .*?\) \{',
    r'fun registerService(patientId: Int, serviceId: Int, employeeId: Int, dateTime: Long, sellingPrice: Double, employeeCost: Double, transportationCost: Double, otherCosts: Double, discount: Double, paymentMethod: String, invoiceNumber: String, notes: String, selectedCashboxId: Int?, isPaid: Boolean, consumablesOwner: String) {',
    vm_content, flags=re.DOTALL
)

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(vm_content)
