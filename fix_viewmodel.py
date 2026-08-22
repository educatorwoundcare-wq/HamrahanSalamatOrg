import re

with open("app/src/main/java/com/example/ui/HamrahanViewModel.kt", "r") as f:
    content = f.read()

replacement = """    fun createCompanyWorkspace(name: String, nationalCode: String, phone: String, address: String) {
        viewModelScope.launch {
            try {
                // Purge all old local offline dummy accounts first
                repository.purgeAllLocalOfflineDevices()

                val existingCompanyId = repository.getSystemSettingByKey("company_id")
                val companyId = if (!existingCompanyId.isNullOrBlank() && existingCompanyId != "COMP-LOCAL") {
                    existingCompanyId
                } else {
                    "COMP-" + java.util.UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
                }

                val existingSyncCode = repository.getSystemSettingByKey("company_sync_code")
                val syncCode = if (!existingSyncCode.isNullOrBlank() && existingSyncCode != "HAMRAHAN-LOCAL-WORK") {
                    existingSyncCode
                } else {
                    "HAMRAHAN-" + java.util.UUID.randomUUID().toString().replace("-", "").take(6).uppercase()
                }

                // Authenticate with Supabase Anonymously using Connection IDs
                val authResult = supabaseAuthRepository.signInAnonymously(companyId, syncCode)
                val authUserId = if (authResult is com.example.data.supabase.AuthResult.Success) {
                    authResult.uid
                } else {
                    companyJoinError.value = "خطای سرور ابری در ثبت مرکز: احراز هویت ناموفق بود."
                    return@launch
                }

                val existingDevId = repository.getSystemSettingByKey("active_device_id")
                val devId = if (!existingDevId.isNullOrBlank()) existingDevId else "DEV-" + java.util.UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
                
                // Create Workspace Remotely
                val workspaceInfo = com.example.data.WorkspaceInfo(
                    companyId = companyId,
                    companySyncCode = syncCode,
                    centerName = name,
                    nationalCode = nationalCode,
                    supportPhone = phone,
                    centerAddress = address,
                    createdTimestamp = System.currentTimeMillis()
                )
                val workspaceCreated = repository.cloudClient.saveWorkspaceInfo(companyId, workspaceInfo)
                if (!workspaceCreated) {
                    companyJoinError.value = "خطا در ایجاد مرکز در سرور ابری. لطفاً دوباره تلاش کنید."
                    return@launch
                }
                
                val selfDevice = ConnectedDevice(
                    deviceId = devId,
                    deviceName = "تلفن مدیرعامل (سرپرست مرکز)",
                    deviceType = "Phone",
                    appVersion = "v2.0.0",
                    lastOnlineTime = System.currentTimeMillis(),
                    lastSuccessfulSync = System.currentTimeMillis(),
                    status = "Active",
                    uid = authUserId,
                    role = "Mother Account",
                    lastSeen = System.currentTimeMillis(),
                    companyId = companyId,
                    requestedRole = "Mother Account"
                )
                
                // Register Device Remotely
                val deviceRegistered = repository.cloudClient.registerDevice(companyId, selfDevice)
                if (!deviceRegistered) {
                    companyJoinError.value = "خطا در ثبت دستگاه در سرور ابری. لطفاً دوباره تلاش کنید."
                    return@launch
                }

                val settings = listOf(
                    SystemSetting("company_name", name),
                    SystemSetting("centerName", name),
                    SystemSetting("national_code", nationalCode),
                    SystemSetting("company_national_code", nationalCode),
                    SystemSetting("support_phone", phone),
                    SystemSetting("company_phone", phone),
                    SystemSetting("center_address", address),
                    SystemSetting("company_address", address),
                    SystemSetting("company_id", companyId),
                    SystemSetting("company_sync_code", syncCode),
                    SystemSetting("company_is_setup", "true"),
                    SystemSetting("active_device_id", devId),
                    SystemSetting("active_device_name", "تلفن مدیرعامل (سرپرست مرکز)"),
                    SystemSetting("active_device_role", "Mother Account"),
                    SystemSetting("device_has_been_approved", "true"),
                    SystemSetting("active_device_status", "Active")
                )
                
                for (s in settings) {
                    repository.insertSystemSetting(s)
                }
                
                // Enable online sync status
                setOnline(true)

                repository.dao.insertConnectedDevice(selfDevice)
                repository.registerLocalChange("ConnectedDevice", selfDevice.deviceId)

                // Reindex existing records to new company ID
                repository.reindexWorkspaceData(companyId)
                companyJoinSuccess.value = "شناسنامه مرکز با موفقیت ساخته شد. کد همگام‌سازی: $syncCode"
            } catch (e: Exception) {
                companyJoinError.value = "خطا در ساخت پروفایل مرکز: ${e.localizedMessage}"
            }
        }
    }"""

# Use regex to find `fun createCompanyWorkspace` to `fun joinCompanyWorkspace` and replace it
pattern = re.compile(r'    fun createCompanyWorkspace\(name: String, nationalCode: String, phone: String, address: String\) \{.*?(?=    fun joinCompanyWorkspace\()', re.DOTALL)
new_content = pattern.sub(replacement + "\n\n", content)

with open("app/src/main/java/com/example/ui/HamrahanViewModel.kt", "w") as f:
    f.write(new_content)
