with open('app/src/main/java/com/example/ui/SettingsScreen.kt', 'r') as f:
    content = f.read()

content = content.replace("""    LaunchedEffect(Unit) {
        viewModel.checkForUpdates(context)
    }
    val context = LocalContext.current""", """    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.checkForUpdates(context)
    }""")

with open('app/src/main/java/com/example/ui/SettingsScreen.kt', 'w') as f:
    f.write(content)
