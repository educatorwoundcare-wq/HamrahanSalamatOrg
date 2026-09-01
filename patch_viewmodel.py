import re

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    text = f.read()

# Replace the SystemSetting inserts in joinCompanyWorkspace
old_settings = """                // Step 8: Persist locally with Pending status (NOT active until approved by Mother Account)
                val settings = listOf(
                    SystemSetting("company_sync_code", normalizedSyncCode),
                    SystemSetting("company_id", canonicalCompanyId),
                    SystemSetting("company_name", centerName),
                    SystemSetting("center_name", centerName),
                    SystemSetting("support_phone", phone.trim()),
                    SystemSetting("company_phone", phone.trim()),
                    SystemSetting("company_is_setup", "false"),
                    SystemSetting("active_device_id", devId),
                    SystemSetting("active_device_name", "دستگاه همراه (پرسنل)"),
                    SystemSetting("active_device_role", "Staff"),
                    SystemSetting("device_has_been_approved", "false"),
                    SystemSetting("active_device_status", "Pending")
                )
                for (s in settings) {
                    repository.insertSystemSetting(s)
                }
                repository.dao.insertConnectedDevice(selfDevice)
                repository.registerLocalChange("ConnectedDevice", selfDevice.deviceId)

                // Update tenant identity in workspace manager
                workspaceManager.saveIdentity(canonicalCompanyId, normalizedSyncCode, currentToken ?: "", finalAuthUid)
                
                // Reindex local workspace to target canonicalCompanyId
                repository.reindexWorkspaceData(canonicalCompanyId)"""

new_settings = """                // Step 8: Persist locally with Pending status (NOT active until approved by Mother Account)
                val settings = listOf(
                    SystemSetting("pending_sync_code", normalizedSyncCode),
                    SystemSetting("pending_company_id", canonicalCompanyId),
                    SystemSetting("pending_company_name", centerName),
                    SystemSetting("support_phone", phone.trim()),
                    SystemSetting("company_phone", phone.trim()),
                    SystemSetting("company_is_setup", "false"),
                    SystemSetting("active_device_id", devId),
                    SystemSetting("active_device_name", "دستگاه همراه (پرسنل)"),
                    SystemSetting("active_device_role", "Staff"),
                    SystemSetting("device_has_been_approved", "false"),
                    SystemSetting("active_device_status", "Pending")
                )
                for (s in settings) {
                    repository.insertSystemSetting(s)
                }
                repository.dao.insertConnectedDevice(selfDevice)
                repository.registerLocalChange("ConnectedDevice", selfDevice.deviceId)

                // DO NOT write to workspaceManager here. Wait until approved.
                // Reindex local workspace to target canonicalCompanyId for sync to function during pending state
                repository.reindexWorkspaceData(canonicalCompanyId)"""
text = text.replace(old_settings, new_settings)

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(text)
