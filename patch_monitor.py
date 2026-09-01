import re

with open('app/src/main/java/com/example/data/PairingRequestMonitor.kt', 'r') as f:
    text = f.read()

# Replace the performCheck method
old_perform_check_start = '        Log.d("PAIRING_RECEIVER", "PAIRING_RECEIVER_AUTH_START")'

new_perform_check = """
        Log.d("PAIRING_RUNTIME", "[PAIRING_MASTER_QUERY_START] role=$role, status=$status, companyId=$companyId")
        val authResult = cloudClient.ensureAuthSession(companyId)
        if (authResult !is com.example.data.supabase.AuthResult.Success) {
            Log.d("PAIRING_RUNTIME", "[PAIRING_MASTER_QUERY_FAIL] reason=AUTH_FAILED")
            return
        }
        
        try {
            val remoteDevices = cloudClient.getConnectedDevices(companyId)
            Log.d("PAIRING_RUNTIME", "[PAIRING_MASTER_QUERY_RESULT] count=${remoteDevices.size}")
            
            val pending = remoteDevices.filter { 
                it.status.equals("Pending", ignoreCase = true) && it.deviceId != currentDeviceId 
            }
            Log.d("PAIRING_RUNTIME", "[PAIRING_MASTER_PENDING_COUNT] count=${pending.size}")
            
            _pendingRequests.value = pending
            Log.d("PAIRING_RUNTIME", "[PAIRING_MASTER_STATE_EMIT] count=${pending.size}")
            
            pending.forEach { dev ->
                val existing = dao.getConnectedDeviceById(dev.deviceId)
                if (existing == null || existing.status == "Pending") {
                    dao.insertConnectedDevice(dev)
                }
            }
        } catch (e: Exception) {
            Log.e("PAIRING_RUNTIME", "[PAIRING_MASTER_QUERY_FAIL] reason=EXCEPTION", e)
        }
"""

# we need to replace the whole block from AUTH_START down to the end of the method
text = re.sub(r'        Log\.d\("PAIRING_RECEIVER", "PAIRING_RECEIVER_AUTH_START"\).*?\}\s*\}', new_perform_check + '    }\n}', text, flags=re.DOTALL)

with open('app/src/main/java/com/example/data/PairingRequestMonitor.kt', 'w') as f:
    f.write(text)
