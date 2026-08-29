with open('app/src/main/java/com/example/ui/dev/DeveloperControlCenterScreen.kt', 'r') as f:
    content = f.read()

import_str = "val diagnosticEvents by viewModel.diagnosticEvents.collectAsState()\n    var showLegacyTools"

content = content.replace("var showLegacyTools", import_str)

logs_section = """
            // =====================================
            // SECTION J — 📋 SMART DIAGNOSTICS LOGS
            // =====================================
            Card(
                modifier = Modifier.fillMaxWidth().heightIn(min=200.dp, max=400.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("گزارش هوشمند سیستم (Diagnostics Log)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    HorizontalDivider()
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(diagnosticEvents) { event ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.padding(8.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${event.category} - ${event.level}", fontWeight = FontWeight.Bold, color = if(event.level == "ERROR") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                        Text(event.timestamp.formatDateTime(), style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text(event.summary, style = MaterialTheme.typography.bodySmall)
                                    if(event.details.isNotEmpty()) {
                                        Text(event.details, style = MaterialTheme.typography.labelSmall, color = Color.Gray, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
"""

content = content.replace("// =====================================\n            // SECTION M", logs_section + "\n            // =====================================\n            // SECTION M")

with open('app/src/main/java/com/example/ui/dev/DeveloperControlCenterScreen.kt', 'w') as f:
    f.write(content)
