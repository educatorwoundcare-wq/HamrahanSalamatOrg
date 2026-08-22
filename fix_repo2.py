import re

with open('app/src/main/java/com/example/data/HamrahanRepository.kt', 'r') as f:
    content = f.read()

# Replace all remaining isDeleted = true with "DELETE"
content = content.replace('isDeleted = true', '"DELETE"')

# Fix the signature of registerLocalChange
old_sig = 'suspend fun registerLocalChange(entityType: String, entityId: String, "DELETE": Boolean = false) {'
new_sig = 'suspend fun registerLocalChange(entityType: String, entityId: String, operationType: String = "UPDATE") {'
content = content.replace(old_sig, new_sig)
content = content.replace('suspend fun registerLocalChange(entityType: String, entityId: String, isDeleted: Boolean = false) {', new_sig)

# Replace the body of registerLocalChange
old_body = """        val activeDeviceId = dao.getSystemSettingByKey("active_device_id") ?: "UNKNOWN-DEVICE"
        val meta = com.example.data.SyncMetadata(
            entityType = entityType,
            entityId = entityId,
            updatedTimestamp = System.currentTimeMillis(),
            deletedStatus = isDeleted,
            lastModifiedDeviceId = activeDeviceId,
            syncStatus = "Pending"
        )
        dao.insertSyncMetadata(meta)
        syncEngine.triggerSync()
    }"""
    
new_body = """        val activeCompanyId = dao.getSystemSettingByKey("company_id") ?: "COMP-LOCAL"
        val syncQueue = com.example.data.SyncQueue(
            tableName = entityType,
            recordId = entityId,
            operationType = operationType,
            companyId = activeCompanyId,
            timestamp = System.currentTimeMillis(),
            status = "PENDING",
            retryCount = 0,
            operationUuid = java.util.UUID.randomUUID().toString()
        )
        dao.insertSyncQueue(syncQueue)
        syncEngine.triggerSync()
    }"""

if 'dao.insertSyncMetadata(meta)' in content:
    # Need to regex it if exact match fails
    content = re.sub(r'val activeDeviceId.*syncEngine\.triggerSync\(\)\s*\}', new_body, content, flags=re.DOTALL)

with open('app/src/main/java/com/example/data/HamrahanRepository.kt', 'w') as f:
    f.write(content)

