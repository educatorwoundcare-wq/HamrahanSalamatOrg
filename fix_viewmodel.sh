#!/bin/bash
# Replaces everything between `// Register Device Remotely` and the end of the `createCompanyWorkspace` function

cat << 'INNER_EOF' > replacement.txt
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
    }
INNER_EOF

# Find the start line number
start_line=$(grep -n "// Register Device Remotely" app/src/main/java/com/example/ui/HamrahanViewModel.kt | head -1 | cut -d: -f1)

# Find the end line number, which is the first `    fun joinCompanyWorkspace`
end_line=$(grep -n "fun joinCompanyWorkspace" app/src/main/java/com/example/ui/HamrahanViewModel.kt | head -1 | cut -d: -f1)
end_line=$((end_line - 1))

# Replace the lines
sed -i "${start_line},${end_line}c\\
$(cat replacement.txt | sed 's/$/\\/g' | head -n -1)
$(tail -n 1 replacement.txt)" app/src/main/java/com/example/ui/HamrahanViewModel.kt
