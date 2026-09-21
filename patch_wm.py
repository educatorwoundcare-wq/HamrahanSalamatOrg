import sys

with open('app/src/main/java/com/example/data/WorkspaceManager.kt', 'r') as f:
    content = f.read()

# 1. Add LEGACY key and update COMPANY_ID_KEY
content = content.replace('private val COMPANY_ID_KEY = stringPreferencesKey("tenant_id")',
'''private val LEGACY_TENANT_ID_KEY = stringPreferencesKey("tenant_id")
    private val COMPANY_ID_KEY = stringPreferencesKey("company_id")''')

# 2. Update init block for migration
old_init = """    init {
        runBlocking {
            val prefs = context.workspaceDataStore.data.first()
            currentTenantId = WorkspaceSanitizer.getCanonicalCompanyId(prefs[COMPANY_ID_KEY])"""
new_init = """    init {
        runBlocking {
            val prefs = context.workspaceDataStore.data.first()
            var compId = prefs[COMPANY_ID_KEY]
            val legacyId = prefs[LEGACY_TENANT_ID_KEY]
            
            if (compId.isNullOrBlank() && !legacyId.isNullOrBlank()) {
                // Safe migration from legacy tenant_id to company_id
                compId = legacyId
                context.workspaceDataStore.edit { editPrefs ->
                    editPrefs[COMPANY_ID_KEY] = legacyId
                    // We deliberately leave the legacy key intact for backward compatibility just in case
                }
            }
            currentCompanyId = WorkspaceSanitizer.getCanonicalCompanyId(compId)"""
content = content.replace(old_init, new_init)

# 3. Rename currentTenantId to currentCompanyId
content = content.replace("currentTenantId", "currentCompanyId")
content = content.replace("tenantId", "companyId")
content = content.replace("effectiveTenantId", "effectiveCompanyId")

with open('app/src/main/java/com/example/data/WorkspaceManager.kt', 'w') as f:
    f.write(content)

print("WorkspaceManager patched")
