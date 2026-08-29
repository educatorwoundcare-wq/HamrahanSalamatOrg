import re

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    text = f.read()

# Fix the trailing braces issue which `grep` revealed! Look at those logs:
# 1316:                    Log.e("AUTH_BOOTSTRAP", "[AUTH_BOOTSTRAP]\nhasExistingSession=false\nhasAccessToken=false\nfailureType=$errMsg") }
# 1330:                Log.i("AUTH_BOOTSTRAP", "[AUTH_BOOTSTRAP]\nhasExistingSession=true\nhasAccessToken=true\ntokenExpired=false\nauthUid=$authUid\nsessionCreated=true") }
# 1342:                        Log.i("PAIRING_RUNTIME", "[PAIRING_RUNTIME] [LOOKUP_SUCCESS] syncCode=$normalizedSyncCode canonicalCompanyId=${ws.companyId} centerName=$cName") }

text = text.replace('") }', '")')
text = text.replace('val updated = dev.copy(status = "Active") }', 'val updated = dev.copy(status = "Active")')
text = text.replace('val updated = dev.copy(status = "Rejected") }', 'val updated = dev.copy(status = "Rejected")')
text = text.replace('val updated = dev.copy(status = "Revoked") }', 'val updated = dev.copy(status = "Revoked")')
text = text.replace('SystemSetting("active_device_status", "Pending") }', 'SystemSetting("active_device_status", "Pending")')
text = text.replace('@Suppress("UNCHECKED_CAST") }', '@Suppress("UNCHECKED_CAST")')
text = text.replace('throw IllegalArgumentException("Unknown ViewModel class") }', 'throw IllegalArgumentException("Unknown ViewModel class")')

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(text)
