with open('app/src/main/java/com/example/ui/dev/DeveloperControlCenterScreen.kt', 'r') as f:
    content = f.read()

content = content.replace('val syncSummary by viewModel.syncSummary.collectAsState()', 'val syncSummary: com.example.data.SyncSummary? by viewModel.syncSummary.collectAsState(initial = null)')
content = content.replace('val isOnline by viewModel.isOnline.collectAsState()', 'val isOnline: Boolean by viewModel.isOnline.collectAsState(initial = true)')

with open('app/src/main/java/com/example/ui/dev/DeveloperControlCenterScreen.kt', 'w') as f:
    f.write(content)
