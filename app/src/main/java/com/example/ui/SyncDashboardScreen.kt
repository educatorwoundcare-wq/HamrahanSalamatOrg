package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import com.example.data.AuditLog
import com.example.data.ConnectedDevice
import com.example.data.SyncQueue
import java.text.SimpleDateFormat
import java.util.*

val DeepTeal = Color(0xFF0F766E)
val CoolGrey = Color(0xFFD1D5DB)
val BackgroundLight = Color(0xFFF9FAFB)
val TextPrimary = Color(0xFF111827)
val TextSecondary = Color(0xFF4B5563)
val DangerRed = Color(0xFFDC2626)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncDashboardScreen(
    viewModel: SyncManagementViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("وضعیت همگام‌سازی", "مدیریت دستگاه‌ها", "تاریخچه تغییرات")

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("داشبورد مدیریت و همگام‌سازی", fontWeight = FontWeight.Bold, color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "بازگشت", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepTeal)
                )
            },
            containerColor = BackgroundLight
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.White,
                    contentColor = DeepTeal,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = DeepTeal
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { 
                                Text(
                                    title, 
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTabIndex == index) DeepTeal else TextSecondary
                                ) 
                            }
                        )
                    }
                }

                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    when (selectedTabIndex) {
                        0 -> SyncStatusSection(uiState, onForceSync = { viewModel.forceSync() })
                        1 -> DeviceManagementSection(uiState, onToggleStatus = { viewModel.toggleDeviceStatus(it) })
                        2 -> AuditTrailSection(uiState)
                    }
                }
            }
        }
    }
}

@Composable
fun SyncStatusSection(uiState: SyncManagementUiState, onForceSync: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val (statusIcon, statusColor, statusText) = when (uiState.syncStatus) {
                    WorkInfo.State.ENQUEUED -> Triple(Icons.Default.Schedule, Color(0xFFF59E0B), "در صف انتظار")
                    WorkInfo.State.RUNNING -> Triple(Icons.Default.Sync, DeepTeal, "در حال همگام‌سازی...")
                    WorkInfo.State.SUCCEEDED -> Triple(Icons.Default.CheckCircle, Color(0xFF10B981), "همگام‌سازی موفق")
                    WorkInfo.State.FAILED -> Triple(Icons.Default.Error, DangerRed, "خطا در همگام‌سازی")
                    WorkInfo.State.BLOCKED -> Triple(Icons.Default.Block, Color(0xFF6B7280), "مسدود شده")
                    WorkInfo.State.CANCELLED -> Triple(Icons.Default.Cancel, Color(0xFF6B7280), "لغو شده")
                    else -> Triple(Icons.Default.CloudQueue, TextSecondary, "وضعیت نامشخص")
                }

                Icon(
                    imageVector = statusIcon,
                    contentDescription = "Sync Status",
                    tint = statusColor,
                    modifier = Modifier.size(64.dp).padding(bottom = 16.dp)
                )
                
                Text(
                    text = statusText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                
                uiState.tenantId?.let {
                    Text(
                        text = "شناسه مرکز: $it",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        Button(
            onClick = onForceSync,
            colors = ButtonDefaults.buttonColors(containerColor = DeepTeal),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.height(48.dp)
        ) {
            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text("همگام‌سازی دستی", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DeviceManagementSection(uiState: SyncManagementUiState, onToggleStatus: (ConnectedDevice) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SectionHeader("دستگاه‌های مجاز (لیست سفید)", Color(0xFF10B981))
        }
        if (uiState.whitelistedDevices.isEmpty()) {
            item { EmptyState("هیچ دستگاه مجازی یافت نشد.") }
        } else {
            items(uiState.whitelistedDevices) { device ->
                DeviceCard(device = device, onToggle = { onToggleStatus(device) })
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(24.dp))
            SectionHeader("دستگاه‌های مسدود شده (لیست سیاه)", DangerRed)
        }
        if (uiState.blacklistedDevices.isEmpty()) {
            item { EmptyState("هیچ دستگاه مسدود شده‌ای یافت نشد.") }
        } else {
            items(uiState.blacklistedDevices) { device ->
                DeviceCard(device = device, onToggle = { onToggleStatus(device) })
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 12.dp, top = 8.dp)
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}

@Composable
fun DeviceCard(device: ConnectedDevice, onToggle: () -> Unit) {
    val isBlocked = device.status == "Revoked" || device.status == "Suspended"
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Smartphone,
                contentDescription = null,
                tint = if (isBlocked) TextSecondary else DeepTeal,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(device.deviceName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                Text("شناسه: ${device.deviceId}", fontSize = 12.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("نسخه: ${device.appVersion} | بازدید: ${formatTimestamp(device.lastOnlineTime)}", fontSize = 12.sp, color = TextSecondary)
            }
            
            OutlinedButton(
                onClick = onToggle,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isBlocked) DeepTeal else DangerRed),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isBlocked) DeepTeal else DangerRed)
            ) {
                Text(if (isBlocked) "مجاز کردن" else "مسدود کردن")
            }
        }
    }
}

@Composable
fun AuditTrailSection(uiState: SyncManagementUiState) {
    val combinedEvents = remember(uiState.auditLogs, uiState.syncQueueEvents) {
        val mappedAudits = uiState.auditLogs.map { AuditTrailItem.Log(it) }
        val mappedSyncs = uiState.syncQueueEvents.map { AuditTrailItem.Sync(it) }
        (mappedAudits + mappedSyncs).sortedByDescending { 
            when (it) {
                is AuditTrailItem.Log -> it.log.timestamp
                is AuditTrailItem.Sync -> it.sync.timestamp
            }
        }
    }

    if (combinedEvents.isEmpty()) {
        EmptyState("هیچ رویدادی ثبت نشده است.")
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(combinedEvents) { item ->
                when (item) {
                    is AuditTrailItem.Log -> AuditLogCard(item.log)
                    is AuditTrailItem.Sync -> SyncQueueCard(item.sync)
                }
                Box(
                    modifier = Modifier
                        .padding(start = 20.dp, bottom = 4.dp, top = 4.dp)
                        .width(2.dp)
                        .height(20.dp)
                        .background(CoolGrey)
                )
            }
        }
    }
}

sealed class AuditTrailItem {
    data class Log(val log: AuditLog) : AuditTrailItem()
    data class Sync(val sync: SyncQueue) : AuditTrailItem()
}

@Composable
fun AuditLogCard(log: AuditLog) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, CoolGrey),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(DeepTeal.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = DeepTeal)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${log.user} - ${log.action}", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(formatTimestamp(log.timestamp), fontSize = 12.sp, color = TextSecondary)
                }
                Text("ماژول: ${log.affectedModule} | دستگاه: ${log.device}", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
                Text(log.details, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun SyncQueueCard(sync: SyncQueue) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BackgroundLight),
        border = androidx.compose.foundation.BorderStroke(1.dp, CoolGrey),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            val (icon, color) = when (sync.status) {
                "COMPLETED" -> Icons.Default.Check to Color(0xFF10B981)
                "FAILED" -> Icons.Default.Warning to DangerRed
                else -> Icons.Default.CloudUpload to Color(0xFFF59E0B)
            }
            
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("عملیات پایگاه داده: ${sync.operationType}", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(formatTimestamp(sync.timestamp), fontSize = 12.sp, color = TextSecondary)
                }
                Text("جدول: ${sync.tableName} | شناسه رکورد: ${sync.recordId}", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
                Text("وضعیت همگام‌سازی: ${sync.status}", fontSize = 13.sp, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = TextSecondary, fontSize = 16.sp)
    }
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp == 0L) return "نامشخص"
    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
