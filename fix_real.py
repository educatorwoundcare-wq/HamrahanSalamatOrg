import re

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    text = f.read()

# Fix the brace on line 424
target_1 = 'viewModelScope.launch { repository.logDiagnosticEvent("AUTH", "WARNING", "DEVELOPER_AUTH_LOCKOUT", "Attempted while locked out")\n            return false'
replacement_1 = 'viewModelScope.launch { repository.logDiagnosticEvent("AUTH", "WARNING", "DEVELOPER_AUTH_LOCKOUT", "Attempted while locked out") }\n            return false'
text = text.replace(target_1, replacement_1)

# Remove the dummy methods block
# It was between 'fun clearDeepLink() {}' and the end.
# Specifically:
dummy_methods = """
    fun importTariffs(uri: android.net.Uri, context: android.content.Context) {}
    fun resetAllServicesToOfficialTariffs() {}
    fun saveService(service: com.example.data.Service) {}
    fun deleteService(service: com.example.data.Service) {}
    fun checkForUpdates(context: android.content.Context) {}
    fun getBackupFilesList(): List<File> = emptyList()
    fun exportBackupToUri(uri: android.net.Uri, file: File, context: android.content.Context) {}
    fun validateBackupFromUri(uri: android.net.Uri, context: android.content.Context, callback: (Boolean, String) -> Unit) {}
    fun validateBackupFile(file: File, callback: (Boolean, String) -> Unit) {}
    fun backupDatabaseFile(name: String, callback: (Boolean, String) -> Unit) {}
    fun deleteBackupFile(file: File) {}
    fun resetCompanyWorkspace(context: android.content.Context, callback: (Boolean, String) -> Unit) {}
    fun restoreData(file: File, context: android.content.Context, callback: (Boolean, String) -> Unit) {}
    fun restoreDatabaseFile(file: File, context: android.content.Context, callback: (Boolean, String) -> Unit) {}
    fun exportDatabaseToJson(context: android.content.Context, callback: (android.net.Uri?) -> Unit) {}
    
    val companyName: StateFlow<String> = MutableStateFlow("")
"""

if dummy_methods in text:
    text = text.replace('\n' + dummy_methods, '')

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(text)
