with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'r') as f:
    content = f.read()

content = content.replace('kotlinx.coroutines.flow.map(systemSettings) { settings ->', 'systemSettings.map { settings ->')
content = content.replace('kotlinx.coroutines.flow.SharingStarted.WhileSubscribed', 'SharingStarted.WhileSubscribed')
content = content.replace('kotlinx.coroutines.flow.StateFlow', 'StateFlow')
content = content.replace('kotlinx.coroutines.flow.MutableStateFlow', 'MutableStateFlow')

with open('app/src/main/java/com/example/ui/HamrahanViewModel.kt', 'w') as f:
    f.write(content)
