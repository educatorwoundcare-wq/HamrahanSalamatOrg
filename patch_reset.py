import re

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    text = f.read()

old_reset = """    fun resetDeviceJoinState() {
        viewModelScope.launch {
            repository.insertSystemSetting(SystemSetting("active_device_status", "Unconfigured"))
            repository.insertSystemSetting(SystemSetting("company_is_setup", "false"))
            repository.insertSystemSetting(SystemSetting("device_has_been_approved", "false"))
            repository.insertSystemSetting(SystemSetting("company_id", ""))
            repository.insertSystemSetting(SystemSetting("company_sync_code", ""))
        }
    }"""

new_reset = """    fun resetDeviceJoinState() {
        viewModelScope.launch {
            repository.insertSystemSetting(SystemSetting("active_device_status", "Unconfigured"))
            repository.insertSystemSetting(SystemSetting("company_is_setup", "false"))
            repository.insertSystemSetting(SystemSetting("device_has_been_approved", "false"))
            repository.insertSystemSetting(SystemSetting("company_id", ""))
            repository.insertSystemSetting(SystemSetting("company_sync_code", ""))
            repository.insertSystemSetting(SystemSetting("pending_company_id", ""))
            repository.insertSystemSetting(SystemSetting("pending_sync_code", ""))
            repository.insertSystemSetting(SystemSetting("pending_company_name", ""))
            
            val workspaceManager = com.example.data.WorkspaceManager.getInstance(repository.context)
            workspaceManager.clearWorkspaceTenantOnly()
        }
    }"""
text = text.replace(old_reset, new_reset)

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(text)
