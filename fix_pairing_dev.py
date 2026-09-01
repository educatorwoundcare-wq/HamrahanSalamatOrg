import re
with open('app/src/main/java/com/example/ui/dev/DeveloperControlCenterScreen.kt', 'r') as f:
    text = f.read()

states_to_add = """    val pendingRequests by viewModel.pendingDeviceRequests.collectAsState()
    val isMasterDevice by viewModel.isMasterDevice.collectAsState()
"""
text = text.replace('val diagnosticEvents by viewModel.diagnosticEvents.collectAsState()',
                    'val diagnosticEvents by viewModel.diagnosticEvents.collectAsState()\n' + states_to_add)

# Now add the pairing card before diagnostic log
pairing_card = """
            // --- PAIRING DEBUG & CONTROL ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("مدیریت اتصال دستگاه‌ها (Pairing Debug)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE0B2))) {
                        Text("PAIRING DEBUG | Master: ${isMasterDevice} | Pending: ${pendingRequests.size}", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall, color = Color.Black)
                    }

                    if (isMasterDevice && pendingRequests.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        com.example.ui.components.PairingRequestsSection(
                            pendingDevices = pendingRequests,
                            onApprove = { dev -> viewModel.approveDeviceAccess(dev.deviceId) },
                            onReject = { dev -> viewModel.rejectDeviceAccess(dev.deviceId) },
                            onRefresh = { viewModel.refreshPairingRequests() },
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("No pending requests or not Master.", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.refreshPairingRequests() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                        }
                    }
                }
            }
"""

text = text.replace('Card(\n                modifier = Modifier.fillMaxWidth(),\n                shape = RoundedCornerShape(16.dp),\n                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)\n            ) {\n                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {\n                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {\n                        Text("گزارش هوشمند سیستم', 
                    pairing_card + '\n            Card(\n                modifier = Modifier.fillMaxWidth(),\n                shape = RoundedCornerShape(16.dp),\n                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)\n            ) {\n                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {\n                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {\n                        Text("گزارش هوشمند سیستم')

with open('app/src/main/java/com/example/ui/dev/DeveloperControlCenterScreen.kt', 'w') as f:
    f.write(text)
