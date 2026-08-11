import re

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'fun registerService(patientId: Int, serviceId: Int, employeeId: Int, dateTime: Long, sellingPrice: Double, employeeCost: Double, transportationCost: Double, otherCosts: Double, discount: Double, paymentMethod: String, invoiceNumber: String, notes: String, selectedCashboxId: Int?, isPaid: Boolean, consumablesOwner: String) {}',
    '''fun registerService(patientId: Int, serviceId: Int, employeeId: Int, dateTime: Long, sellingPrice: Double, employeeCost: Double, transportationCost: Double, otherCosts: Double, discount: Double, paymentMethod: String, invoiceNumber: String, notes: String, selectedCashboxId: Int?, isPaid: Boolean, consumablesOwner: String) {
        viewModelScope.launch {
            repository.registerService(
                patientId = patientId,
                serviceId = serviceId,
                employeeId = employeeId,
                dateTime = dateTime,
                sellingPrice = sellingPrice,
                employeeCost = employeeCost,
                transportationCost = transportationCost,
                otherCosts = otherCosts,
                discount = discount,
                paymentMethod = paymentMethod,
                invoiceNumber = invoiceNumber,
                notes = notes,
                selectedCashboxId = selectedCashboxId,
                isPaid = isPaid,
                consumablesOwner = consumablesOwner
            )
        }
    }'''
)

content = content.replace(
    'fun registerPackage(patientId: Int, employeeId: Int, selectedServices: List<com.example.data.Service>, dateTime: Long, sellingPrice: Double, employeeCost: Double, transportationCost: Double, otherCosts: Double, discount: Double, paymentMethod: String, invoiceNumber: String, notes: String, selectedCashboxId: Int?, isPaid: Boolean, consumablesOwner: String) {}',
    '''fun registerPackage(patientId: Int, employeeId: Int, selectedServices: List<com.example.data.Service>, dateTime: Long, sellingPrice: Double, employeeCost: Double, transportationCost: Double, otherCosts: Double, discount: Double, paymentMethod: String, invoiceNumber: String, notes: String, selectedCashboxId: Int?, isPaid: Boolean, consumablesOwner: String) {
        viewModelScope.launch {
            repository.registerPackage(
                patientId = patientId,
                employeeId = employeeId,
                selectedServices = selectedServices,
                dateTime = dateTime,
                sellingPrice = sellingPrice,
                employeeCost = employeeCost,
                transportationCost = transportationCost,
                otherCosts = otherCosts,
                discount = discount,
                paymentMethod = paymentMethod,
                invoiceNumber = invoiceNumber,
                notes = notes,
                selectedCashboxId = selectedCashboxId,
                isPaid = isPaid,
                consumablesOwner = consumablesOwner
            )
        }
    }'''
)

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(content)
