import re

with open('app/src/main/java/com/example/data/SyncEngine.kt', 'r') as f:
    content = f.read()

# Fix duplicates created by my sed command
content = re.sub(r'class SyncEngine\([\s\S]*?\) \{[\s\S]*?private var isSyncing', 'class SyncEngine(\n    private val dao: HamrahanDao,\n    private val cloudClient: CloudClient,\n    private val context: Context,\n    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())\n) {\n    private val uuidResolver = CanonicalUuidResolver(dao)\n    private var isSyncing', content)

# Fix applyRemoteRow
old_apply = r"""    private suspend fun applyRemoteRow\(row: SyncPullRow\) \{
        if \(row.deletedAt > 0\) \{
            markAsDeletedLocally\(row.entityType, row.uuid\)
            return
        \}

        val jsonPayload = uuidResolver\.resolveIncomingPayload\(row\.entityType, row\.payload\)
        val entityObj = SyncSerializer\.deserialize\(row\.entityType, jsonPayload\)
        
        if \(row\.deletedAt > 0\) \{"""

new_apply = """    private suspend fun applyRemoteRow(row: SyncPullRow) {
        if (row.deletedAt > 0) {
            markAsDeletedLocally(row.entityType, row.uuid)
            return
        }

        val jsonPayload = uuidResolver.resolveIncomingPayload(row.entityType, row.payload)
        val entityObj = SyncSerializer.deserialize(row.entityType, jsonPayload)
        
        if (row.deletedAt > 0) {"""
content = re.sub(old_apply, new_apply, content) # Already modified by sed

old_apply2 = r"""    private suspend fun applyRemoteRow\(row: SyncPullRow\) \{
        if \(row.deletedAt > 0\) \{
            markAsDeletedLocally\(row.entityType, row.uuid\)
            return
        \}

        val jsonPayload = row.payload
        val entityObj = SyncSerializer\.deserialize\(row\.entityType, jsonPayload\)
        
        if \(row\.deletedAt > 0\) \{"""
        
content = re.sub(old_apply2, new_apply, content)

# Fix transaction
old_tx = r"""                dao\.runInTransaction \{
                    rows\.forEach \{ row ->
                        try \{
                            if \(row\.companyId == activeCompanyId\) \{
                                applyRemoteRow\(row\)
                            \}
                        \} catch \(e: Exception\) \{
                            Log\.e\("SyncEngine", "Error applying row: \$\{e\.message\}"\)
                        \}
                    \}
                    // ONLY update the global cursor if all mutations succeed locally\.
                    dao\.insertSystemSetting\(SystemSetting\(cursorKey, pullResponse\.nextCursor\.toString\(\)\)\)
                \}"""

new_tx = """                dao.runInTransaction {
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
content = re.sub(old_tx, new_tx, content)

# getPayloadForOperation
old_ret = r"""        if \(obj == null\) return null
        return SyncSerializer\.serialize\(op\.tableName, obj\)"""
new_ret = """        if (obj == null) return null
        return uuidResolver.resolveOutgoingPayload(op.tableName, obj)"""
content = re.sub(old_ret, new_ret, content)

old_ret_broken = r"""        if \(obj == null\) return null
        return uuidResolver\.resolveOutgoingPayload\(op\.tableName, obj\)"""
content = re.sub(old_ret_broken, new_ret, content)

with open('app/src/main/java/com/example/data/SyncEngine.kt', 'w') as f:
    f.write(content)
