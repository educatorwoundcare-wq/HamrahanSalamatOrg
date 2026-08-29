import re

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    text = f.read()

text = text.replace(
    'viewModelScope.launch { repository.logDiagnosticEvent("AUTH", "INFO", "DEVELOPER_AUTH_SUCCESS", "Authentication successful")',
    'viewModelScope.launch { repository.logDiagnosticEvent("AUTH", "INFO", "DEVELOPER_AUTH_SUCCESS", "Authentication successful") }'
)

text = text.replace(
    'viewModelScope.launch { repository.logDiagnosticEvent("AUTH", "WARNING", "DEVELOPER_AUTH_LOCKOUT", "Lockout triggered due to 3 failed attempts")',
    'viewModelScope.launch { repository.logDiagnosticEvent("AUTH", "WARNING", "DEVELOPER_AUTH_LOCKOUT", "Lockout triggered due to 3 failed attempts") }'
)

text = text.replace(
    'viewModelScope.launch { repository.logDiagnosticEvent("AUTH", "WARNING", "DEVELOPER_AUTH_FAILURE", "Authentication failed")',
    'viewModelScope.launch { repository.logDiagnosticEvent("AUTH", "WARNING", "DEVELOPER_AUTH_FAILURE", "Authentication failed") }'
)

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(text)
