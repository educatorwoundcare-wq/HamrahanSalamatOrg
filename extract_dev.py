import re

with open('app/src/main/java/com/example/ui/SettingsScreen.kt', 'r') as f:
    lines = f.readlines()

start_idx = 1008
end_idx = 1675

dev_tools_code = "".join(lines[start_idx:end_idx+1])
with open('dev_tools.txt', 'w') as f:
    f.write(dev_tools_code)

new_lines = lines[:start_idx] + [
    "                // Developer Control Center Button\n",
    "                Button(\n",
    "                    onClick = { navController?.navigate(com.example.ui.navigation.DeveloperControlCenter) },\n",
    "                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),\n",
    "                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)\n",
    "                ) {\n",
    "                    Icon(Icons.Default.Security, contentDescription = null)\n",
    "                    Spacer(modifier = Modifier.width(8.dp))\n",
    "                    Text(\"🚀 ورود به مرکز کنترل توسعه‌دهنده\", fontWeight = FontWeight.Bold)\n",
    "                }\n"
] + lines[end_idx+1:]

with open('app/src/main/java/com/example/ui/SettingsScreen.kt', 'w') as f:
    f.writelines(new_lines)

