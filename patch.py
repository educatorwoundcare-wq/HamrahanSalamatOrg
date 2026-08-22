import sys

with open('app/src/main/java/com/example/data/SyncEngine.kt', 'r') as f:
    content = f.read()

content = content.replace(
    "class SyncEngine(\n    private val dao: HamrahanDao,\n    private val cloudClient: CloudClient,\n    private val context: Context,\n    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {",
    "class SyncEngine(\n    private val dao: HamrahanDao,\n    private val cloudClient: CloudClient,\n    private val context: Context,\n    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {\n    private val uuidResolver = CanonicalUuidResolver(dao)"
)

old_transaction = """                dao.runInTransaction {
                    rows.forEach { row ->
                        try {
                            if (row.companyId == activeCompanyId) {
                                applyRemoteRow(row)
                            }
                        } catch (e: Exception) {
                            Log.e("SyncEngine", "Error applying row: ${e.message}")
                        }
                    }
                    // ONLY update the global cursor if all mutations succeed locally.
                    dao.insertSystemSetting(SystemSetting(cursorKey, pullResponse.nextCursor.toString()))
                }"""
                
new_transaction = """                dao.runInTransaction {
                    var allSuccess = true
                    rows.forEach { row ->
                        try {
                            if (row.companyId == activeCompanyId) {
                                applyRemoteRow(row)
                            }
                        } catch (e: PendingDependencyException) {
                            Log.w("SyncEngine", "Missing dependency: ${e.message}")
                            allSuccess = false
                        } catch (e: Exception) {
                            Log.e("SyncEngine", "Error applying row: ${e.message}")
                            allSuccess = false
                        }
                    }
                    // ONLY update the global cursor if all mutations succeed locally.
                    if (allSuccess) {
                        dao.insertSystemSetting(SystemSetting(cursorKey, pullResponse.nextCursor.toString()))
                    } else {
                        throw Exception("Sync batch failed due to pending dependencies or errors")
                    }
                }"""
                
content = content.replace(old_transaction, new_transaction)

with open('app/src/main/java/com/example/data/SyncEngine.kt', 'w') as f:
    f.write(content)

