import re

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    text = f.read()

text = text.replace(
    'fun notifyDevAuthScreenOpened() {\n        viewModelScope.launch { repository.logDiagnosticEvent("AUTH", "INFO", "DEVELOPER_AUTH_SCREEN_OPENED", "Developer Authentication Screen Opened")\n    }',
    'fun notifyDevAuthScreenOpened() {\n        viewModelScope.launch { repository.logDiagnosticEvent("AUTH", "INFO", "DEVELOPER_AUTH_SCREEN_OPENED", "Developer Authentication Screen Opened") }\n    }'
)

text = text.replace(
    'fun notifyDevSessionExpired() {\n        viewModelScope.launch { repository.logDiagnosticEvent("AUTH", "INFO", "DEVELOPER_SESSION_EXPIRED", "Developer Session Expired")\n    }',
    'fun notifyDevSessionExpired() {\n        viewModelScope.launch { repository.logDiagnosticEvent("AUTH", "INFO", "DEVELOPER_SESSION_EXPIRED", "Developer Session Expired") }\n    }'
)

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(text)
