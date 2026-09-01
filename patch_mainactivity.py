import re

with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    text = f.read()

# Pending view UI
old_pending_ui = """                                Text("نام مرکز:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(companyName.ifEmpty { "در حال دریافت اطلاعات مرکز..." }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("شناسه درخواست (ID):", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(deviceId, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("نام دستگاه:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(deviceName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("وضعیت فعلی:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(modifier = Modifier.size(8.dp).background(Color(0xFFEAB308), CircleShape))
                                    Text("در انتظار تأیید مدیر دفتر", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFEAB308), fontWeight = FontWeight.Bold)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("آخرین همگام‌سازی:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(formattedSyncTime, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.triggerSync() },
                            modifier = Modifier.fillMaxWidth().testTag("refresh_status_btn")
                        ) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("بررسی مجدد وضعیت دسترسی")
                        }"""

new_pending_ui = """                                val context = androidx.compose.ui.platform.LocalContext.current
                                val pendingName by viewModel.companyNameState.collectAsState() // Actually we need pending name, we can just use the provided companyName, wait companyName is passed in to AppContent, but it might be blank because active workspace is not set. We'll use a local db call for pending name
                                var pName by remember { mutableStateOf("") }
                                LaunchedEffect(Unit) {
                                    val nm = (context.applicationContext as com.example.HamrahanApplication).repository.dao.getSystemSettingByKey("pending_company_name")
                                    if (!nm.isNullOrBlank()) pName = nm
                                }
                                Text("نام مرکز:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(pName.ifEmpty { "در حال دریافت اطلاعات مرکز..." }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("شناسه درخواست (ID):", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(deviceId, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("وضعیت فعلی:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(modifier = Modifier.size(8.dp).background(Color(0xFFEAB308), CircleShape))
                                    Text("در انتظار تأیید مدیر دفتر", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFEAB308), fontWeight = FontWeight.Bold)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("آخرین همگام‌سازی:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(formattedSyncTime, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(
                                onClick = { viewModel.triggerSync() },
                                modifier = Modifier.weight(1f).testTag("refresh_status_btn")
                            ) {
                                Icon(Icons.Default.Refresh, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("بررسی وضعیت")
                            }
                            OutlinedButton(
                                onClick = { viewModel.cancelPairingRequest() },
                                modifier = Modifier.weight(1f).testTag("cancel_pairing_btn"),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Close, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("لغو درخواست اتصال")
                            }
                        }"""
text = text.replace(old_pending_ui, new_pending_ui)

with open('app/src/main/java/com/example/MainActivity.kt', 'w') as f:
    f.write(text)
