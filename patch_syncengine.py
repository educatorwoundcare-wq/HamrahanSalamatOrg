import re

with open('app/src/main/java/com/example/data/SyncEngine.kt', 'r') as f:
    text = f.read()

# 1. Update companyId logic
old_company_id = 'val companyId = wm.currentTenantId ?: dao.getSystemSettingByKey("company_id")'
new_company_id = 'val companyId = wm.currentTenantId?.takeIf { it.isNotBlank() } ?: dao.getSystemSettingByKey("company_id")?.takeIf { it.isNotBlank() } ?: dao.getSystemSettingByKey("pending_company_id")'
text = text.replace(old_company_id, new_company_id)

# 2. Update syncCode logic
old_sync_code = 'val syncCode = wm.currentSyncCode ?: dao.getSystemSettingByKey("company_sync_code")'
new_sync_code = 'val syncCode = wm.currentSyncCode?.takeIf { it.isNotBlank() } ?: dao.getSystemSettingByKey("company_sync_code")?.takeIf { it.isNotBlank() } ?: dao.getSystemSettingByKey("pending_sync_code")'
text = text.replace(old_sync_code, new_sync_code)

# 3. Promote pending to active when SyncAllowed
promotion_code = """            val activeDeviceStatus = confirmedDevice.status

            // Promote pending target to active workspace if applicable
            val pendingCompId = dao.getSystemSettingByKey("pending_company_id")
            if (!pendingCompId.isNullOrBlank() && pendingCompId == confirmedWorkspace.companyId && activeDeviceStatus == "Active") {
                dao.insertSystemSetting(SystemSetting("company_id", confirmedWorkspace.companyId))
                dao.insertSystemSetting(SystemSetting("company_sync_code", confirmedWorkspace.syncCode))
                val pName = dao.getSystemSettingByKey("pending_company_name") ?: confirmedWorkspace.centerName
                dao.insertSystemSetting(SystemSetting("company_name", pName))
                dao.insertSystemSetting(SystemSetting("center_name", pName))
                
                dao.insertSystemSetting(SystemSetting("pending_company_id", ""))
                dao.insertSystemSetting(SystemSetting("pending_sync_code", ""))
                dao.insertSystemSetting(SystemSetting("pending_company_name", ""))
                
                wm.saveIdentity(confirmedWorkspace.companyId, confirmedWorkspace.syncCode, wm.currentAuthToken ?: "", confirmedAuthUid)
                
                // Reindex local workspace
                (context.applicationContext as? com.example.HamrahanApplication)?.repository?.reindexWorkspaceData(confirmedWorkspace.companyId)
            }
"""
text = text.replace('val activeDeviceStatus = confirmedDevice.status', promotion_code)

with open('app/src/main/java/com/example/data/SyncEngine.kt', 'w') as f:
    f.write(text)
