import re

with open('app/src/main/java/com/example/data/SyncEngine.kt', 'r') as f:
    content = f.read()

replacement_pull = """
    private suspend fun pullRemoteChanges() {
        val activeCompanyId = dao.getSystemSettingByKey("company_id") ?: return
        var lastSyncStr = dao.getSystemSettingByKey("last_server_version") ?: "0"
        var lastSyncVersion = lastSyncStr.toLongOrNull() ?: 0L

        var hasMore = true
        var pullLimit = 5 // Prevent infinite loops just in case
        
        while (hasMore && pullLimit > 0) {
            pullLimit--
            val result = cloudClient.syncPull(activeCompanyId, lastSyncVersion)
            if (result.isSuccess) {
                val pullResponse = result.getOrNull() ?: break
                val rows = pullResponse.changes
                
                dao.runInTransaction {
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
                    dao.insertSystemSetting(SystemSetting("last_server_version", pullResponse.nextCursor.toString()))
                }
                
                lastSyncVersion = pullResponse.nextCursor
                hasMore = pullResponse.hasMore
            } else {
                Log.e("SyncEngine", "Pull failed: ${result.exceptionOrNull()?.message}")
                break
            }
        }
    }
"""

content = re.sub(r'    private suspend fun pullRemoteChanges\(\) \{.*?\n    \}', replacement_pull, content, flags=re.DOTALL)

with open('app/src/main/java/com/example/data/SyncEngine.kt', 'w') as f:
    f.write(content)
