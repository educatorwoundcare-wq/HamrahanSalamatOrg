with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    content = f.read()

# Fix indent
content = content.replace('val isDeveloperMode: StateFlow<Boolean> = _isDeveloperMode.asStateFlow()', '    val isDeveloperMode: StateFlow<Boolean> = _isDeveloperMode.asStateFlow()')

# Check missing brace on line 177
content = content.replace('}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "") }', '}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")')

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(content)
