import re
with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'logDiagnosticEvent(',
    'viewModelScope.launch { repository.logDiagnosticEvent('
)
content = content.replace(
    '")\n',
    '") }\n'
)

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(content)
