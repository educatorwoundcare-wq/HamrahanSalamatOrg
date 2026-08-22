import re

with open("app/src/main/java/com/example/ui/HamrahanViewModel.kt", "r") as f:
    content = f.read()

replacement = """    fun joinCompanyWorkspace(code: String, phone: String) {
        viewModelScope.launch {
            try {
                val cleanCode = code.trim().uppercase()
                if (cleanCode.isBlank()) {
                    companyJoinError.value = "کد همگام‌سازی نمی‌تواند خالی باشد."
                    return@launch
                }

                val devId = repository.getSystemSettingByKey("active_device_id") ?: ("DEV-" + java.util.UUID.randomUUID().toString().replace("-", "").take(8).uppercase())

                // Resolve target workspace from cloud by sync code
                val remoteWorkspace = repository.cloudClient.getWorkspaceBySyncCode(cleanCode)
                if (remoteWorkspace == null) {
                    companyJoinError.value = "کد همگام‌سازی نامعتبر است یا مرکز یافت نشد."
                    return@launch
                }
                
                val companyId = remoteWorkspace.companyId
                val centerName = remoteWorkspace.centerName
                
                // Authenticate with Supabase Anonymously using Connection IDs
                val authResult = supabaseAuthRepository.signInAnonymously(companyId, cleanCode)
                val authUserId = if (authResult is com.example.data.supabase.AuthResult.Success) {
                    authResult.uid
                } else {
                    companyJoinError.value = "خطای سرور ابری: احراز هویت ناموفق بود."
                    return@launch
                }

                val selfDevice = ConnectedDevice(
                    deviceId = devId,
                    deviceName = "دستگاه همراه (پرسنل)",
                    deviceType = "Phone",
                    appVersion = "v2.0.0",
                    lastOnlineTime = System.currentTimeMillis(),
                    lastSuccessfulSync = System.currentTimeMillis(),
                    status = "Pending",
                    uid = authUserId,
                    role = "Staff",
                    lastSeen = System.currentTimeMillis(),
                    companyId = companyId,
                    requestedRole = "Staff"
                )

                // Register Device Remotely as Pending
                val deviceRegistered = repository.cloudClient.registerDevice(companyId, selfDevice)
                if (!deviceRegistered) {
                    companyJoinError.value = "خطا در ثبت درخواست اتصال در سرور ابری."
                    return@launch
                }

                val settings = listOf(
                    SystemSetting("company_sync_code", cleanCode),
                    SystemSetting("company_id", companyId),
                    SystemSetting("company_name", centerName),
                    SystemSetting("center_name", centerName),
                    SystemSetting("support_phone", phone.trim()),
                    SystemSetting("company_phone", phone.trim()),
                    SystemSetting("company_is_setup", "true"),
                    SystemSetting("active_device_id", devId),
                    SystemSetting("active_device_role", "Staff"),
                    SystemSetting("device_has_been_approved", "false"),
                    SystemSetting("active_device_status", "Pending")
                )
                
                for (s in settings) {
                    repository.insertSystemSetting(s)
                }
                
                // Enable online sync status
                setOnline(true)

                repository.dao.insertConnectedDevice(selfDevice)
                // We do NOT register local change here because this is already synced to cloud, and we don't want SyncWorker to overwrite it yet.
                // Reindex existing records to new company ID
                repository.reindexWorkspaceData(companyId)
                companyJoinSuccess.value = "درخواست اتصال با موفقیت ثبت شد. منتظر تایید مدیر باشید."
            } catch (e: Exception) {
                companyJoinError.value = "خطا در اتصال به مرکز: ${e.localizedMessage}"
            }
        }
    }"""

pattern = re.compile(r'    fun joinCompanyWorkspace\(code: String, phone: String\) \{.*?(?=    fun clearJoinStatus\()', re.DOTALL)
new_content = pattern.sub(replacement + "\n\n", content)

with open("app/src/main/java/com/example/ui/HamrahanViewModel.kt", "w") as f:
    f.write(new_content)
