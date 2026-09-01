import re
with open('app/src/main/java/com/example/ui/dev/DeveloperControlCenterScreen.kt', 'r') as f:
    text = f.read()

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

text = text.replace('            Spacer(modifier = Modifier.height(16.dp))\n            \n            Button(\n                onClick = { showLegacyTools = !showLegacyTools },',
                    pairing_card + '\n            Spacer(modifier = Modifier.height(16.dp))\n            \n            Button(\n                onClick = { showLegacyTools = !showLegacyTools },')

with open('app/src/main/java/com/example/ui/dev/DeveloperControlCenterScreen.kt', 'w') as f:
    f.write(text)
