import re
with open('app/src/main/java/com/example/ui/dev/DeveloperControlCenterScreen.kt', 'r') as f:
    content = f.read()

idx = content.find("val isDeveloperMode by viewModel.isDeveloperMode.collectAsState()")
insert_str = """
    LaunchedEffect(Unit) {
        while(true) {
            if (viewModel.isDevSessionValid()) {
                viewModel.keepDevSessionAlive()
            } else {
                viewModel.notifyDevSessionExpired()
                navController.navigate(com.example.ui.navigation.DeveloperAuth) {
                    popUpTo(com.example.ui.navigation.DeveloperControlCenter) { inclusive = true }
                }
            }
            kotlinx.coroutines.delay(1000)
        }
    }
"""
content = content[:idx] + insert_str + content[idx:]

with open('app/src/main/java/com/example/ui/dev/DeveloperControlCenterScreen.kt', 'w') as f:
    f.write(content)
