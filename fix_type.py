with open('app/src/main/java/com/example/ui/dev/DeveloperControlCenterScreen.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'val diagnosticEvents by viewModel.diagnosticEvents.collectAsState(initial = emptyList())',
    'val diagnosticEvents: List<com.example.data.DiagnosticEvent> by viewModel.diagnosticEvents.collectAsState(initial = emptyList())'
)

with open('app/src/main/java/com/example/ui/dev/DeveloperControlCenterScreen.kt', 'w') as f:
    f.write(content)
