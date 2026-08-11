with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    content = f.read()

import re

# registerService
content = re.sub(
    r'fun registerService\([^{]+{\s*viewModelScope\.launch\s*{\s*repository\.registerService\([^{]+}\s*}',
    '''fun registerService(patientId: Int, serviceId: Int, employeeId: Int, dateTime: Long, sellingPrice: Double, employeeCost: Double, transportationCost: Double, otherCosts: Double, discount: Double, paymentMethod: String, invoiceNumber: String, notes: String, selectedCashboxId: Int?, isPaid: Boolean, consumablesOwner: String) {
        viewModelScope.launch {
            val finalPrice = sellingPrice + otherCosts + transportationCost - discount
            val grossIncome = finalPrice
            val employeeCommission = if (consumablesOwner == "Nurse") employeeCost + otherCosts else employeeCost
            val companyProfit = finalPrice - employeeCommission
            
            val reg = com.example.data.ServiceRegistration(
                patientId = patientId,
                serviceId = serviceId,
                employeeId = employeeId,
                dateTime = dateTime,
                sellingPrice = sellingPrice,
                employeeCost = employeeCost,
                transportationCost = transportationCost,
                otherCosts = otherCosts,
                discount = discount,
                finalPrice = finalPrice,
                paymentMethod = paymentMethod,
                invoiceNumber = invoiceNumber,
                notes = notes,
                grossIncome = grossIncome,
                employeeCommission = employeeCommission,
                companyProfit = companyProfit,
                isPaid = isPaid,
                consumablesOwner = consumablesOwner,
                cashboxId = selectedCashboxId
            )
            
            val patient = repository.dao.getPatientById(patientId)
            val service = repository.dao.getServiceById(serviceId)
            val employee = repository.dao.getEmployeeById(employeeId)
            
            repository.registerService(
                reg = reg,
                patientName = patient?.fullName ?: "نامشخص",
                serviceName = service?.name ?: "نامشخص",
                employeeName = employee?.fullName ?: "نامشخص",
                selectedCashboxId = selectedCashboxId
            )
        }
    }''',
    content, flags=re.MULTILINE
)

# registerPackage
content = re.sub(
    r'fun registerPackage\([^{]+{\s*viewModelScope\.launch\s*{\s*repository\.registerPackage\([^{]+}\s*}',
    '''fun registerPackage(patientId: Int, employeeId: Int, selectedServices: List<com.example.data.Service>, dateTime: Long, sellingPrice: Double, employeeCost: Double, transportationCost: Double, otherCosts: Double, discount: Double, paymentMethod: String, invoiceNumber: String, notes: String, selectedCashboxId: Int?, isPaid: Boolean, consumablesOwner: String) {
        viewModelScope.launch {
            val finalPrice = sellingPrice + otherCosts + transportationCost - discount
            val grossIncome = finalPrice
            val employeeCommission = if (consumablesOwner == "Nurse") employeeCost + otherCosts else employeeCost
            val companyProfit = finalPrice - employeeCommission
            
            val reg = com.example.data.ServiceRegistration(
                patientId = patientId,
                serviceId = 0, // Package
                employeeId = employeeId,
                dateTime = dateTime,
                sellingPrice = sellingPrice,
                employeeCost = employeeCost,
                transportationCost = transportationCost,
                otherCosts = otherCosts,
                discount = discount,
                finalPrice = finalPrice,
                paymentMethod = paymentMethod,
                invoiceNumber = invoiceNumber,
                notes = notes,
                grossIncome = grossIncome,
                employeeCommission = employeeCommission,
                companyProfit = companyProfit,
                isPaid = isPaid,
                consumablesOwner = consumablesOwner,
                isPackage = true,
                cashboxId = selectedCashboxId
            )
            
            val patient = repository.dao.getPatientById(patientId)
            val employee = repository.dao.getEmployeeById(employeeId)
            val serviceNames = selectedServices.joinToString(", ") { it.name }
            
            repository.registerPackage(
                reg = reg,
                patientName = patient?.fullName ?: "نامشخص",
                serviceNames = serviceNames,
                employeeName = employee?.fullName ?: "نامشخص",
                selectedServices = selectedServices,
                selectedCashboxId = selectedCashboxId
            )
        }
    }''',
    content, flags=re.MULTILINE
)

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(content)
