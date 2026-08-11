with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    lines = f.readlines()

props = """
    val fieldActiveSubmitter = kotlinx.coroutines.flow.MutableStateFlow(false)
    val fieldActiveReceipt = kotlinx.coroutines.flow.MutableStateFlow(false)
    val fieldActiveDescription = kotlinx.coroutines.flow.MutableStateFlow(false)
    val fieldActivePaymentMethod = kotlinx.coroutines.flow.MutableStateFlow(false)
    
    // Employee Screen
    fun saveEmployee(employee: com.example.data.Employee) {}
    fun deleteEmployee(employee: com.example.data.Employee) {}

    // Service Screen
    fun saveService(service: com.example.data.Service) {}
    fun deleteService(service: com.example.data.Service) {}
    fun resetAllServicesToOfficialTariffs() {}
    fun importTariffs(uri: android.net.Uri, context: android.content.Context) {}

    // Patient Screen
    fun savePatient(patient: com.example.data.Patient) {}
    fun deletePatient(patient: com.example.data.Patient) {}
    fun saveServiceSchedule(schedule: com.example.data.ServiceSchedule) {}
    fun deleteServiceSchedule(schedule: com.example.data.ServiceSchedule) {}
    fun saveNursingReport(report: com.example.data.NursingReport) {}
    fun deleteNursingReport(report: com.example.data.NursingReport) {}
    fun saveVitalSigns(vitalSigns: com.example.data.VitalSigns) {}
    fun deleteVitalSigns(vitalSigns: com.example.data.VitalSigns) {}
    fun saveWoundRecord(woundRecord: com.example.data.WoundRecord) {}
    fun deleteWoundRecord(woundRecord: com.example.data.WoundRecord) {}
    fun saveConsentForm(consentForm: com.example.data.ConsentForm) {}
    fun deleteConsentForm(consentForm: com.example.data.ConsentForm) {}
    fun savePrescription(prescription: com.example.data.Prescription) {}
    fun deletePrescription(prescription: com.example.data.Prescription) {}

    // Registration Screen
    fun editServiceRegistration(registration: com.example.data.ServiceRegistration) {}
    fun deleteRegistration(registration: com.example.data.ServiceRegistration) {}
    fun registerService(registration: com.example.data.ServiceRegistration) {}
    fun registerPackage(patientId: Int, packageType: String) {}
    
    // Financial Screen
    fun saveTransaction(transaction: com.example.data.FinancialTransaction) {}
    fun deleteTransaction(transaction: com.example.data.FinancialTransaction) {}
    
    // Report Screen
    fun exportDataToExcel(context: android.content.Context, data: Any) {}
    
    // Search Screen
    val globalSearchResults = kotlinx.coroutines.flow.MutableStateFlow<List<Any>>(emptyList())
    fun updateSearchQuery(query: String) {}

"""

for i, line in enumerate(lines):
    if "class HamrahanViewModelFactory" in line:
        lines.insert(i - 1, props)
        break

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.writelines(lines)
