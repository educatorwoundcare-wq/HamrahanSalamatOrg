import re

with open('app/src/main/java/com/example/data/HamrahanDao.kt', 'r') as f:
    content = f.read()

# Add getJournalEntryByUuid
if "fun getJournalEntryByUuid" not in content:
    content = content.replace('suspend fun getJournalEntryById(id: Int): JournalEntry?', 
                              'suspend fun getJournalEntryById(id: Int): JournalEntry?\n    @Query("SELECT * FROM journal_entries WHERE uuid = :uuid LIMIT 1")\n    suspend fun getJournalEntryByUuid(uuid: String): JournalEntry?')

# Add getAllAuditLogsList
if "fun getAllAuditLogsList" not in content:
    content = content.replace('fun getAllAuditLogs(): Flow<List<AuditLog>>',
                              'fun getAllAuditLogs(): Flow<List<AuditLog>>\n    @Query("SELECT * FROM audit_logs")\n    suspend fun getAllAuditLogsList(): List<AuditLog>')

# Add getAllUserPermissionsList
if "fun getAllUserPermissionsList" not in content:
    content = content.replace('fun getAllUserPermissions(): Flow<List<UserPermission>>',
                              'fun getAllUserPermissions(): Flow<List<UserPermission>>\n    @Query("SELECT * FROM user_permissions")\n    suspend fun getAllUserPermissionsList(): List<UserPermission>')

# Add getAllEditHistoriesList
if "fun getAllEditHistoriesList" not in content:
    content = content.replace('fun getAllEditHistories(): Flow<List<FinancialEditHistory>>',
                              'fun getAllEditHistories(): Flow<List<FinancialEditHistory>>\n    @Query("SELECT * FROM edit_history")\n    suspend fun getAllEditHistoriesList(): List<FinancialEditHistory>')

with open('app/src/main/java/com/example/data/HamrahanDao.kt', 'w') as f:
    f.write(content)
