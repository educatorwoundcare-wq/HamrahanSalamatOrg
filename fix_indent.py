import re

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    text = f.read()

text = text.replace(
    'fun updateCurrentUserRole(role: String) {',
    '    fun updateCurrentUserRole(role: String) {'
)

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(text)
