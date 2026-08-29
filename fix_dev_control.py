import re
with open('app/src/main/java/com/example/ui/dev/DeveloperControlCenterScreen.kt', 'r') as f:
    content = f.read()

# Fix Unresolved reference 'diagnosticEvents' by making sure it's collected properly with the correct type.
# And fix 'not' for operator '!' (which is actually `isDeveloperMode` instead of `!isDeveloperMode` if it was a boolean, but it's a State).
content = content.replace(
    'val diagnosticEvents by viewModel.diagnosticEvents.collectAsState()',
    'val diagnosticEvents by viewModel.diagnosticEvents.collectAsState(initial = emptyList())'
)

# And missing 'syncSummary'. In DeveloperControlCenterScreen.kt we have:
idx = content.find('val isDeveloperMode by viewModel.isDeveloperMode.collectAsState()')
if idx != -1:
    content = content[:idx] + 'val syncSummary by viewModel.syncSummary.collectAsState(initial = null)\n    val isOnline by viewModel.isOnline.collectAsState(initial = false)\n    ' + content[idx:]

with open('app/src/main/java/com/example/ui/dev/DeveloperControlCenterScreen.kt', 'w') as f:
    f.write(content)
