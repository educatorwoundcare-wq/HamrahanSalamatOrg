import os

files_to_fix = [
    'app/src/main/java/com/example/domain/usecase/RegisterServiceAndGenerateLedgerUseCase.kt',
    'app/src/main/java/com/example/domain/usecase/SettleEmployeeCommissionUseCase.kt'
]

for file_path in files_to_fix:
    with open(file_path, 'r') as f:
        content = f.read()

    # Change "isDeleted = true" to "DELETE"
    content = content.replace('isDeleted = true', '"DELETE"')

    # Fix the signature
    old_sig = 'private suspend fun registerLocalChange(entityType: String, entityId: String, "DELETE": Boolean = false) {'
    new_sig = 'private suspend fun registerLocalChange(entityType: String, entityId: String, operationType: String = "UPDATE") {'
    content = content.replace(old_sig, new_sig)
    content = content.replace('private suspend fun registerLocalChange(entityType: String, entityId: String, isDeleted: Boolean = false) {', new_sig)

    # Change body
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
    import re
    content = re.sub(r'val activeDeviceId.*?syncEngine\.triggerSync\(\)\s*\}', new_body, content, flags=re.DOTALL)

    # Note: UseCases don't explicitly pass operationType for INSERT inside the body for everything (they just omitted it, so it defaults to UPDATE).
    # Since these UseCases primarily create new records (INSERT), we should change the registerLocalChange calls for inserts to explicitly pass "INSERT".
    
    with open(file_path, 'w') as f:
        f.write(content)

