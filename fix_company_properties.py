import re

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    content = f.read()

missing = """
    val companyNameState: kotlinx.coroutines.flow.StateFlow<String> = kotlinx.coroutines.flow.map(systemSettings) { settings ->
        settings.find { it.key == "company_name" }?.value ?: ""
    }.kotlinx.coroutines.flow.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), "")

    val companySyncCode: kotlinx.coroutines.flow.StateFlow<String> = kotlinx.coroutines.flow.map(systemSettings) { settings ->
        settings.find { it.key == "company_sync_code" }?.value ?: ""
    }.kotlinx.coroutines.flow.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), "")

    val companyId: kotlinx.coroutines.flow.StateFlow<String> = kotlinx.coroutines.flow.map(systemSettings) { settings ->
        settings.find { it.key == "company_id" }?.value ?: ""
    }.kotlinx.coroutines.flow.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), "")

    val companyNationalCode: kotlinx.coroutines.flow.StateFlow<String> = kotlinx.coroutines.flow.map(systemSettings) { settings ->
        settings.find { it.key == "company_national_code" }?.value ?: ""
    }.kotlinx.coroutines.flow.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), "")

    val companyPhone: kotlinx.coroutines.flow.StateFlow<String> = kotlinx.coroutines.flow.map(systemSettings) { settings ->
        settings.find { it.key == "company_phone" }?.value ?: ""
    }.kotlinx.coroutines.flow.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), "")

    val activeDeviceName: kotlinx.coroutines.flow.StateFlow<String> = kotlinx.coroutines.flow.map(systemSettings) { settings ->
        settings.find { it.key == "active_device_name" }?.value ?: ""
    }.kotlinx.coroutines.flow.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), "")

    val activeDeviceId: kotlinx.coroutines.flow.StateFlow<String> = kotlinx.coroutines.flow.map(systemSettings) { settings ->
        settings.find { it.key == "active_device_id" }?.value ?: ""
    }.kotlinx.coroutines.flow.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), "")

    val isOnline = kotlinx.coroutines.flow.MutableStateFlow(false)
"""
# Replace kotlin.coroutines.flow.map(..) with systemSettings.map (requires import kotlinx.coroutines.flow.map)
# We will just use `systemSettings.map` and assume the import is there (it is, I checked earlier, actually let me add it to the file just in case)
missing_clean = """
    // --- System Settings ---
    val companyNameState: kotlinx.coroutines.flow.StateFlow<String> = kotlinx.coroutines.flow.map(systemSettings) { settings ->
        settings.find { it.key == "company_name" }?.value ?: ""
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), "")

    val companySyncCode: kotlinx.coroutines.flow.StateFlow<String> = kotlinx.coroutines.flow.map(systemSettings) { settings ->
        settings.find { it.key == "company_sync_code" }?.value ?: ""
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), "")

    val companyId: kotlinx.coroutines.flow.StateFlow<String> = kotlinx.coroutines.flow.map(systemSettings) { settings ->
        settings.find { it.key == "company_id" }?.value ?: ""
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), "")

    val companyNationalCode: kotlinx.coroutines.flow.StateFlow<String> = kotlinx.coroutines.flow.map(systemSettings) { settings ->
        settings.find { it.key == "company_national_code" }?.value ?: ""
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), "")

    val companyPhone: kotlinx.coroutines.flow.StateFlow<String> = kotlinx.coroutines.flow.map(systemSettings) { settings ->
        settings.find { it.key == "company_phone" }?.value ?: ""
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), "")

    val activeDeviceName: kotlinx.coroutines.flow.StateFlow<String> = kotlinx.coroutines.flow.map(systemSettings) { settings ->
        settings.find { it.key == "active_device_name" }?.value ?: ""
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), "")

    val activeDeviceId: kotlinx.coroutines.flow.StateFlow<String> = kotlinx.coroutines.flow.map(systemSettings) { settings ->
        settings.find { it.key == "active_device_id" }?.value ?: ""
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), "")

    val isOnline = kotlinx.coroutines.flow.MutableStateFlow(false)
"""

content = content.replace('    // --- Developer Mode ---', missing_clean + '\n    // --- Developer Mode ---')

# Fix toggleDarkMode which expects an argument but was defined with 0 arguments
content = content.replace('fun toggleDarkMode() {}', 'fun toggleDarkMode(value: Boolean) {}')

# Fix mergeExpenseCategories which was called with Strings but expects Ints, wait, I defined it with Ints, let's change to String
content = content.replace('fun mergeExpenseCategories(sourceId: Int, targetId: Int) {}', 'fun mergeExpenseCategories(sourceId: String, targetId: String) {}')

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(content)

