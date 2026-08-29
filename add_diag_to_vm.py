with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    content = f.read()

if "val diagnosticEvents" not in content:
    idx = content.find("val isDeveloperMode")
    insert_str = """
    val diagnosticEvents: StateFlow<List<com.example.data.DiagnosticEvent>> = repository.dao.getDiagnosticEventsFlow(100)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
"""
    content = content[:idx] + insert_str + content[idx:]
    with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
        f.write(content)
