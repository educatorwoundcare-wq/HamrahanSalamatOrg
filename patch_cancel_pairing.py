import re

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    text = f.read()

cancel_method = """
    fun cancelPairingRequest() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val devId = repository.dao.getSystemSettingByKey("active_device_id") ?: return@launch
                val compId = repository.dao.getSystemSettingByKey("pending_company_id") ?: repository.dao.getSystemSettingByKey("company_id") ?: return@launch
                
                // Cancel on cloud
                repository.cloudClient.patchDeviceAuthorization(compId, devId, "Cancelled", "Staff")
                
                // Clear local pending settings
                repository.dao.insertSystemSetting(SystemSetting("active_device_status", "Unpaired"))
                repository.dao.insertSystemSetting(SystemSetting("company_id", ""))
                repository.dao.insertSystemSetting(SystemSetting("company_sync_code", ""))
                repository.dao.insertSystemSetting(SystemSetting("pending_company_id", ""))
                repository.dao.insertSystemSetting(SystemSetting("pending_sync_code", ""))
                repository.dao.insertSystemSetting(SystemSetting("pending_company_name", ""))
                
                workspaceManager.clearWorkspaceTenantOnly()
                
                triggerSync()
            } catch (e: Exception) {
                Log.e("HamrahanViewModel", "Error cancelling pairing request", e)
            }
        }
    }
"""

text = text.replace('    fun joinCompanyWorkspace(code: String, phone: String) {', cancel_method + '\n    fun joinCompanyWorkspace(code: String, phone: String) {')

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(text)
