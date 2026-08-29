import re
with open('app/src/main/java/com/example/ui/dev/DeveloperControlCenterScreen.kt', 'r') as f:
    content = f.read()

# I messed up and placed syncSummary and isOnline at line 48 which is outside the `@Composable`!
# Let's clean it up.
content = content.replace('val syncSummary by viewModel.syncSummary.collectAsState(initial = null)\n    val isOnline by viewModel.isOnline.collectAsState(initial = false)\n    ', '')

# Then we put it in the right place. Where is the right place? Inside the composable.
idx = content.find('val userRole by viewModel.currentUserRole.collectAsState()')
if idx != -1:
    content = content[:idx] + 'val syncSummary by viewModel.syncSummary.collectAsState()\n    val isOnline by viewModel.isOnline.collectAsState()\n    ' + content[idx:]
    
# But we also have `val syncSummary by viewModel.syncSummary.collectAsState()` already in there?
# grep said: 88:    val syncSummary by viewModel.syncSummary.collectAsState()
# Let's just remove the one at line 48!

content = content.replace('val syncSummary by viewModel.syncSummary.collectAsState()\n    val isOnline by viewModel.isOnline.collectAsState()\n    val userRole', 'val userRole')

with open('app/src/main/java/com/example/ui/dev/DeveloperControlCenterScreen.kt', 'w') as f:
    f.write(content)

