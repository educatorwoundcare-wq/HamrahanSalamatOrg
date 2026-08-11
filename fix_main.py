import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    content = f.read()

content = content.replace(
    '''        setContent {
            val isDarkMode by viewModel.isDarkMode.collectAsState()
            val companyName by viewModel.companyName.collectAsState()''',
    '''        val prefs = getSharedPreferences("crash_prefs", android.content.Context.MODE_PRIVATE)
        val lastCrash = prefs.getString("last_crash", null)
        if (lastCrash != null) {
            prefs.edit().remove("last_crash").apply()
        }

        setContent {
            var crashToShow by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(lastCrash) }
            if (crashToShow != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { crashToShow = null },
                    confirmButton = {
                        androidx.compose.material3.Button(onClick = { crashToShow = null }) {
                            androidx.compose.material3.Text("Close")
                        }
                    },
                    title = { androidx.compose.material3.Text("Crash Detected") },
                    text = {
                        androidx.compose.foundation.lazy.LazyColumn(modifier = androidx.compose.ui.Modifier.heightIn(max = 400.dp)) {
                            item {
                                androidx.compose.material3.Text(
                                    text = crashToShow!!,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                )
            }

            val isDarkMode by viewModel.isDarkMode.collectAsState()
            val companyName by viewModel.companyName.collectAsState()'''
)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(content)
