package com.example.ui.components

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ConnectedDevice

/**
 * PHASE 3.7B-R15: Persistent Pairing Requests Section
 * Displays all pending device pairing requests for Mother/Admin in Dashboard & Company Profile.
 */
@Composable
fun PairingRequestsSection(
    pendingDevices: List<ConnectedDevice>,
    onApprove: (ConnectedDevice) -> Unit,
    onReject: (ConnectedDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("pairing_requests_section"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (pendingDevices.isNotEmpty()) Color(0xFFFFFBEB) else MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (pendingDevices.isNotEmpty()) Color(0xFFF59E0B) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (pendingDevices.isNotEmpty()) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Icon(
                            imageVector = Icons.Default.DevicesOther,
                            contentDescription = null,
                            tint = if (pendingDevices.isNotEmpty()) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(22.dp)
                        )
                    }
                    Text(
                        text = "درخواست‌های اتصال دستگاه‌ها",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (pendingDevices.isNotEmpty()) Color(0xFF92400E) else MaterialTheme.colorScheme.onSurface
                    )
                }

                if (pendingDevices.isNotEmpty()) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFD97706)
                    ) {
                        Text(
                            text = "${pendingDevices.size}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (pendingDevices.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircleOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "درخواست اتصال جدیدی وجود ندارد.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "دستگاه‌های زیر درخواست اتصال به دفتر شما را دارند:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF78350F)
                )

                pendingDevices.forEach { device ->
                    PairingRequestCard(
                        device = device,
                        onApprove = onApprove,
                        onReject = onReject
                    )
                }
            }
        }
    }
}

@Composable
fun PairingRequestCard(
    device: ConnectedDevice,
    onApprove: (ConnectedDevice) -> Unit,
    onReject: (ConnectedDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(device.deviceId) {
        Log.i(
            "PAIRING_RUNTIME",
            "[PAIRING_RUNTIME] [UI_PENDING_SECTION] deviceId=${device.deviceId} status=Pending rendered=true"
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("pairing_request_card_${device.deviceId}"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Color(0xFFFCD34D))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Device header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (device.deviceType.contains("Tablet", ignoreCase = true)) Icons.Default.Tablet else Icons.Default.Smartphone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = device.deviceName.ifBlank { "دستگاه همراه" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFFEF3C7),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B))
                ) {
                    Text(
                        text = "در انتظار تأیید",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFB45309),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Info rows
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("شناسه دستگاه:", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    Text(
                        text = device.deviceId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("نقش درخواستی:", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                    val roleTitle = when (device.requestedRole) {
                        "Staff" -> "کارمند / پرسنل"
                        "Admin" -> "مدیر سیستم"
                        "GM", "General Manager" -> "مدیر کل"
                        else -> device.requestedRole.ifBlank { "پرسنل" }
                    }
                    Text(
                        text = roleTitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        Log.i(
                            "PAIRING_RUNTIME",
                            "[PAIRING_RUNTIME] [UI_APPROVAL_ACTION] deviceId=${device.deviceId} action=Reject"
                        )
                        onReject(device)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("reject_btn_${device.deviceId}"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("رد درخواست", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        Log.i(
                            "PAIRING_RUNTIME",
                            "[PAIRING_RUNTIME] [UI_APPROVAL_ACTION] deviceId=${device.deviceId} action=Approve"
                        )
                        onApprove(device)
                    },
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("approve_btn_${device.deviceId}"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF16A34A),
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تأیید اتصال", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
