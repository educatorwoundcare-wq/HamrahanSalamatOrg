with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    lines = f.readlines()

props = """
    val activeDeviceStatus = kotlinx.coroutines.flow.MutableStateFlow("Active")
    val companyIsSetup = kotlinx.coroutines.flow.MutableStateFlow(true)
    val hasBeenApproved = kotlinx.coroutines.flow.MutableStateFlow(true)
    val failedSyncCount = kotlinx.coroutines.flow.MutableStateFlow(0)
    val connectedDevices = kotlinx.coroutines.flow.MutableStateFlow<List<Any>>(emptyList())
    val companyJoinError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val companyJoinSuccess = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val personnelTypes = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())
    fun addPersonnelType(type: String) {}
"""

for i, line in enumerate(lines):
    if "class HamrahanViewModelFactory" in line:
        lines.insert(i - 1, props)
        break

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.writelines(lines)
