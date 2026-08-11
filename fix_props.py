with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    lines = f.readlines()

props = """
    val taxPercentage: StateFlow<Float> = systemSettings.map { settings ->
        settings.find { it.key == "tax_percentage" }?.value?.toFloatOrNull() ?: 0f
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val defaultCurrency: StateFlow<String> = systemSettings.map { settings ->
        settings.find { it.key == "default_currency" }?.value ?: ""
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val isDarkMode: StateFlow<Boolean> = systemSettings.map { settings ->
        settings.find { it.key == "is_dark_mode" }?.value?.toBoolean() ?: false
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val moduleBarChart: StateFlow<Boolean> = systemSettings.map { settings ->
        settings.find { it.key == "module_bar_chart" }?.value?.toBoolean() ?: true
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val moduleHeatMap: StateFlow<Boolean> = systemSettings.map { settings ->
        settings.find { it.key == "module_heat_map" }?.value?.toBoolean() ?: true
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val moduleStatCards: StateFlow<Boolean> = systemSettings.map { settings ->
        settings.find { it.key == "module_stat_cards" }?.value?.toBoolean() ?: true
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val userRole: StateFlow<String> = systemSettings.map { settings ->
        settings.find { it.key == "user_role" }?.value ?: ""
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val companyAddress: StateFlow<String> = systemSettings.map { settings ->
        settings.find { it.key == "company_address" }?.value ?: ""
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val companyPostalCode: StateFlow<String> = systemSettings.map { settings ->
        settings.find { it.key == "company_postal_code" }?.value ?: ""
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
"""

for i, line in enumerate(lines):
    if "val systemSettings" in line:
        lines.insert(i + 2, props)
        break

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.writelines(lines)
