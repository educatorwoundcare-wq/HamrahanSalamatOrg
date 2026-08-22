package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.ConnectedDevice
import com.example.data.SystemSetting
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyProfileScreen(viewModel: HamrahanViewModel) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    // Sync state flows
    val companyId by viewModel.companyId.collectAsState()
    val companySyncCode by viewModel.companySyncCode.collectAsState()
    val companyIsSetup by viewModel.companyIsSetup.collectAsState()
    val companyNameState by viewModel.companyNameState.collectAsState()
    val companyNationalCode by viewModel.companyNationalCode.collectAsState()
    val companyPhone by viewModel.companyPhone.collectAsState()
    val companyAddress by viewModel.companyAddress.collectAsState()

    val isOnline by viewModel.isOnline.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val pendingChangesCount by viewModel.pendingChangesCount.collectAsState()
    val failedSyncCount by viewModel.failedSyncCount.collectAsState()
    val connectedDevices by viewModel.connectedDevices.collectAsState()

    val activeDeviceId by viewModel.activeDeviceId.collectAsState()
    val activeDeviceName by viewModel.activeDeviceName.collectAsState()

    val companyJoinError by viewModel.companyJoinError.collectAsState()
    val companyJoinSuccess by viewModel.companyJoinSuccess.collectAsState()

    // Dialog state
    var showQrDialog by remember { mutableStateOf(false) }
    var renameDeviceTarget by remember { mutableStateOf<ConnectedDevice?>(null) }
    var renameNewName by remember { mutableStateOf("") }
    var showSuccessSnackbar by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf("") }

    LaunchedEffect(companyJoinError) {
        companyJoinError?.let {
            snackbarMessage = it
            showSuccessSnackbar = true
            viewModel.clearCompanyJoinStatus()
        }
    }

    LaunchedEffect(companyJoinSuccess) {
        companyJoinSuccess?.let {
            snackbarMessage = it
            showSuccessSnackbar = true
            viewModel.clearCompanyJoinStatus()
        }
    }

    LaunchedEffect(connectedDevices, companyId, companyIsSetup) {
        if (companyIsSetup && companyId.isNotEmpty()) {
            val pendingCount = connectedDevices.count { it.status == "Pending" }
            android.util.Log.i("HamrahanViewModel", "[MOTHER DEVICE UI]\n" +
                    "companyId=$companyId\n" +
                    "firebaseQueryPath=companies/$companyId/devices\n" +
                    "remoteDevicesCount=${connectedDevices.size}\n" +
                    "pendingDevicesCount=$pendingCount\n" +
                    "displayedDevicesCount=${connectedDevices.size}")
        }
    }

    if (!companyIsSetup) {
        // --- ONBOARDING: JOIN OR CREATE WORKSPACE ---
        var selectedTab by remember { mutableStateOf(0) } // 0 = Create, 1 = Join

        // Creation form states
        var inputCenterName by remember { mutableStateOf("") }
        var inputNationalCode by remember { mutableStateOf("") }
        var inputSupportPhone by remember { mutableStateOf("") }
        var inputCenterAddress by remember { mutableStateOf("") }

        // Joining form states
        var inputSyncCode by remember { mutableStateOf("") }
        var selectedJoinRole by remember { mutableStateOf("GM") }
        var showJoinRoleDialog by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .testTag("onboarding_profile_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Title Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "همگام‌سازی چند دستگاهه نسخه ۲.۰",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "بدون نیاز به ساخت حساب‌های کاربری ابری پیچیده، اطلاعات مرکز را با یک کلید همگام‌سازی امن بین تمامی پرسنل، منشی و حسابدار به اشتراک بگذارید.",
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            // Tab Selector
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("ساخت پروفایل جدید مرکز", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.AddBusiness, null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("اتصال به مرکز موجود", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.QrCodeScanner, null) }
                )
            }

            if (selectedTab == 0) {
                // --- CREATE WORKSPACE ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "مشخصات ثبتی و هویتی مرکز سلامت",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        OutlinedTextField(
                            value = inputCenterName,
                            onValueChange = { inputCenterName = it },
                            label = { Text("نام مرکز خدمات درمانی در منزل") },
                            leadingIcon = { Icon(Icons.Default.Business, null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboard_create_name")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = inputSupportPhone,
                                onValueChange = { inputSupportPhone = it },
                                label = { Text("تلفن مرکز") },
                                leadingIcon = { Icon(Icons.Default.Phone, null) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("onboard_create_phone")
                            )

                            OutlinedTextField(
                                value = inputNationalCode,
                                onValueChange = { inputNationalCode = it },
                                label = { Text("شناسه ملی مرکز") },
                                leadingIcon = { Icon(Icons.Default.Badge, null) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("onboard_create_national_code")
                            )
                        }

                        OutlinedTextField(
                            value = inputCenterAddress,
                            onValueChange = { inputCenterAddress = it },
                            label = { Text("آدرس دفتر مرکزی") },
                            leadingIcon = { Icon(Icons.Default.Place, null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboard_create_address")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                if (inputCenterName.isBlank()) {
                                    snackbarMessage = "لطفاً نام مرکز را وارد نمایید."
                                    showSuccessSnackbar = true
                                } else {
                                    viewModel.createCompanyWorkspace(
                                        name = inputCenterName,
                                        nationalCode = if (inputNationalCode.isBlank()) "۱۰۳۲۰۰۰۰۰۰۰" else inputNationalCode,
                                        phone = if (inputSupportPhone.isBlank()) "۰۲۱-۸۸۸۸۸۸۸۸" else inputSupportPhone,
                                        address = if (inputCenterAddress.isBlank()) "تهران، دفتر اصلی مرکز خدمات سلامت" else inputCenterAddress
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("onboard_create_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Check, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ایجاد دفتر کار اشتراکی ابری", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            } else {
                // --- JOIN WORKSPACE ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "پیوستن به کلید همگام‌سازی",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth()
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Text(
                            text = "کد همگام‌سازی (Sync Code) یا کد QR صادر شده از دستگاه مدیر مرکز را در اینجا وارد یا شبیه‌سازی کنید.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = inputSyncCode,
                            onValueChange = { inputSyncCode = it },
                            label = { Text("کد همگام‌سازی (مثال: HAMRAHAN-XXXX-XXXX)") },
                            leadingIcon = { Icon(Icons.Default.Key, null) },
                            placeholder = { Text("HAMRAHAN-XXXX-XXXX") },
                            trailingIcon = {
                                IconButton(onClick = {
                                    val pasted = clipboardManager.getText()?.text
                                    if (!pasted.isNullOrBlank()) {
                                        inputSyncCode = pasted.trim()
                                        snackbarMessage = "کد همگام‌سازی جاگذاری شد."
                                        showSuccessSnackbar = true
                                    }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "جای‌گذاری")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboard_join_input")
                        )

                        // Role Selection Button
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "نقش درخواستی دستگاه:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                            
                            val roleLabel = when (selectedJoinRole) {
                                "Admin" -> "مدیر ارشد (Admin)"
                                "GM", "General Manager" -> "مدیر کل (GM)"
                                else -> selectedJoinRole
                            }
                            
                            OutlinedButton(
                                onClick = { showJoinRoleDialog = true },
                                modifier = Modifier.fillMaxWidth().testTag("onboard_join_role_selector_btn"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(roleLabel, style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                        }

                        if (showJoinRoleDialog) {
                            val rolesList = listOf(
                                "Admin" to "مدیر ارشد (Admin)",
                                "GM" to "مدیر کل (GM)"
                            )
                            AlertDialog(
                                onDismissRequest = { showJoinRoleDialog = false },
                                title = { Text("انتخاب نقش درخواستی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        rolesList.forEach { (role, label) ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedJoinRole = role
                                                        showJoinRoleDialog = false
                                                    }
                                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                RadioButton(
                                                    selected = selectedJoinRole == role,
                                                    onClick = {
                                                        selectedJoinRole = role
                                                        showJoinRoleDialog = false
                                                    }
                                                )
                                                Text(label, style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { showJoinRoleDialog = false }) {
                                        Text("انصراف")
                                    }
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Simulator for QR scanning
                            Button(
                                onClick = {
                                    inputSyncCode = "HAMRAHAN-QR99-SYNC"
                                    viewModel.joinCompanyWorkspace("HAMRAHAN-QR99-SYNC", selectedJoinRole)
                                    snackbarMessage = "با موفقیت از طریق بارگذاری کد QR به مرکز متصل شدید."
                                    showSuccessSnackbar = true
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("onboard_qr_scan_sim"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("شبیه‌ساز اسکن QR", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    if (inputSyncCode.isBlank()) {
                                        snackbarMessage = "لطفاً کد همگام‌سازی را وارد کنید."
                                        showSuccessSnackbar = true
                                    } else {
                                        viewModel.joinCompanyWorkspace(inputSyncCode, selectedJoinRole)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(48.dp)
                                    .testTag("onboard_join_submit"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Link, null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("اتصال و دریافت اطلاعات", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Local Device & Account Cleanup Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "پاک‌سازی ۴۰+ حساب آفلاین محلی و بازنشانی قفل دستگاه",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = "اگر دستگاه در حالت آفلاین/محلی قفل شده یا حساب‌های محلی تکراری ساخته شده‌اند، با دکمه‌های زیر تمامی حساب‌های آفلاین قدیمی را حذف و نقش دستگاه را به «مدیرعامل / سرپرست مرکز» بازنشانی کنید:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.purgeAllLocalOfflineDevices()
                                snackbarMessage = "تمامی حساب‌های آفلاین محلی قدیمی با موفقیت پاک‌سازی شدند."
                                showSuccessSnackbar = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("purge_local_offline_devices_btn"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("حذف حساب‌های محلی", style = MaterialTheme.typography.labelMedium)
                        }

                        Button(
                            onClick = {
                                viewModel.resetCompanyWorkspace()
                                snackbarMessage = "دستگاه از حالت محلی خارج شد و به نقش مدیرعامل بازنشانی گردید."
                                showSuccessSnackbar = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("exit_local_device_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.RestartAlt, null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("خروج و بازنشانی کامل", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    } else {
        // --- WORKSPACE ACTIVE CONSOLE ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .testTag("workspace_console_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Info Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Business, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = companyNameState,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "شناسه همگام‌سازی: $companySyncCode",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                            )
                            if (companySyncCode.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(companySyncCode))
                                        snackbarMessage = "کد همگام‌سازی ($companySyncCode) کپی شد."
                                        showSuccessSnackbar = true
                                    },
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "کپی شناسه",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = { showQrDialog = true },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .testTag("show_qr_code_btn")
                    ) {
                        Icon(Icons.Default.QrCode, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            // Sync Engine Control Dashboard
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "داشبورد مدیریت همگام‌سازی زنده",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Online/Offline switch and status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(if (isOnline) Color(0xFF4CAF50) else Color(0xFFF44336), CircleShape)
                            )
                            Text(
                                text = if (isOnline) "اتصال همگام‌سازی: آنلاین" else "اتصال همگام‌سازی: آفلاین (محلی)",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Switch(
                            checked = isOnline,
                            onCheckedChange = { viewModel.setOnline(it) },
                            modifier = Modifier.testTag("online_toggle")
                        )
                    }

                    // Stats indicators
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("تغییرات در صف ارسال", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (syncing) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    }
                                    Text(
                                        text = "$pendingChangesCount مورد",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (pendingChangesCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("خطاهای همگام‌سازی", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$failedSyncCount خطا",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (failedSyncCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Sync action button
                    Button(
                        onClick = { viewModel.triggerSync() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("trigger_sync_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Sync, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("همگام‌سازی فوری دیتابیس", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // SIMULATOR: Switch Active Role Device Role
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "شبیه‌ساز هویت دستگاه فعلی",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "جهت بررسی دقیق کارکرد همزمان سیستم، می‌توانید هویت دستگاه شبیه‌سازی‌شده فعلی را بین نقش‌های مختلف تغییر دهید:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("نقش فعال دستگاه:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(activeDeviceName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        }

                        var showRoleMenu by remember { mutableStateOf(false) }
                        Box {
                            Button(
                                onClick = { showRoleMenu = true },
                                modifier = Modifier.testTag("change_device_role_btn")
                            ) {
                                Text("تغییر نقش دستگاه")
                                Icon(Icons.Default.ArrowDropDown, null)
                            }

                            DropdownMenu(
                                expanded = showRoleMenu,
                                onDismissRequest = { showRoleMenu = false }
                            ) {
                                listOf(
                                    "DEVICE-CEO" to "تلفن مدیرعامل",
                                    "DEVICE-MGR" to "تلفن مدیر داخلی",
                                    "DEVICE-SEC" to "تبلت منشی",
                                    "DEVICE-ACC" to "تلفن حسابدار"
                                ).forEach { (id, name) ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            viewModel.switchActiveDevice(id, name)
                                            showRoleMenu = false
                                            snackbarMessage = "هویت دستگاه به «$name» انتقال یافت. صف پیام‌ها همگام شد."
                                            showSuccessSnackbar = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Connected Devices list
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "دستگاه‌های متصل مجاز (${connectedDevices.size})",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    val pendingDevices = connectedDevices.filter { it.status == "Pending" }
                    com.example.ui.components.PairingRequestsSection(
                        pendingDevices = pendingDevices,
                        onApprove = { dev ->
                            viewModel.approveDeviceAccess(dev.deviceId)
                            snackbarMessage = "دسترسی دستگاه «${dev.deviceName}» با موفقیت تایید شد."
                            showSuccessSnackbar = true
                        },
                        onReject = { dev ->
                            viewModel.rejectDeviceAccess(dev.deviceId)
                            snackbarMessage = "درخواست دستگاه «${dev.deviceName}» رد شد."
                            showSuccessSnackbar = true
                        },
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (connectedDevices.isEmpty()) {
                        Text(
                            text = "هیچ دستگاهی متصل نیست.",
                            color = Color.Gray,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        connectedDevices.forEach { device ->
                            val isThisDevice = device.deviceId == activeDeviceId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .border(
                                        width = if (isThisDevice) 1.5.dp else 1.dp,
                                        color = if (isThisDevice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .background(
                                        color = if (isThisDevice) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(
                                            imageVector = if (device.deviceType == "Tablet") Icons.Default.TabletMac else Icons.Default.Smartphone,
                                            contentDescription = null,
                                            tint = if (isThisDevice) MaterialTheme.colorScheme.primary else Color.Gray,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = device.deviceName,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (isThisDevice) {
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    "این دستگاه",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "آخرین فعالیت: ${if (isThisDevice) "هم‌اکنون" else "چند دقیقه پیش"} | نسخه ${device.appVersion}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    var showDeviceRoleMenu by remember { mutableStateOf(false) }
                                    Box {
                                        AssistChip(
                                            onClick = {
                                                if (!isThisDevice) {
                                                    showDeviceRoleMenu = true
                                                }
                                            },
                                            label = {
                                                Text(
                                                    text = "نقش: " + when (device.role) {
                                                        "Mother Account" -> "سرپرست مرکز (Mother Account)"
                                                        "Admin" -> "مدیر ارشد (Admin)"
                                                        "GM", "General Manager" -> "مدیر کل (General Manager)"
                                                        else -> "مدیر کل (General Manager)"
                                                    }
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Security,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            },
                                            trailingIcon = {
                                                if (!isThisDevice) {
                                                    Icon(
                                                        Icons.Default.ArrowDropDown,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        )

                                        DropdownMenu(
                                            expanded = showDeviceRoleMenu,
                                            onDismissRequest = { showDeviceRoleMenu = false }
                                        ) {
                                            listOf(
                                                "Mother Account" to "سرپرست مرکز (Mother Account)",
                                                "Admin" to "مدیر ارشد (Admin)",
                                                "GM" to "مدیر کل (General Manager)"
                                            ).forEach { (roleKey, roleLabel) ->
                                                DropdownMenuItem(
                                                    text = { Text(roleLabel) },
                                                    onClick = {
                                                        viewModel.changeDeviceRole(device.deviceId, roleKey)
                                                        showDeviceRoleMenu = false
                                                        snackbarMessage = "نقش دستگاه «${device.deviceName}» به «$roleLabel» تغییر یافت."
                                                        showSuccessSnackbar = true
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    if (device.status == "Revoked") {
                                        Text(
                                            "⚠️ دسترسی توسط سرپرست لغو شده است",
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    if (device.status == "Pending") {
                                        Text(
                                            "⏳ منتظر تایید دسترسی سرپرست",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // Approve (only for Pending status)
                                    if (device.status == "Pending") {
                                        IconButton(
                                            onClick = {
                                                viewModel.approveDeviceAccess(device.deviceId)
                                                snackbarMessage = "دسترسی دستگاه «${device.deviceName}» تایید شد."
                                                showSuccessSnackbar = true
                                            },
                                            modifier = Modifier.testTag("approve_device_${device.deviceId}")
                                        ) {
                                            Icon(Icons.Default.Check, "تایید دسترسی", tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                                        }
                                    }

                                    // Rename
                                    IconButton(
                                        onClick = {
                                            renameDeviceTarget = device
                                            renameNewName = device.deviceName
                                        },
                                        modifier = Modifier.testTag("rename_device_${device.deviceId}")
                                    ) {
                                        Icon(Icons.Default.Edit, "تغییر نام", modifier = Modifier.size(18.dp))
                                    }

                                    if (device.status != "Revoked") {
                                        // Revoke Access
                                        IconButton(
                                            onClick = {
                                                viewModel.revokeDeviceAccess(device.deviceId)
                                                snackbarMessage = "دسترسی دستگاه «${device.deviceName}» لغو شد."
                                                showSuccessSnackbar = true
                                            },
                                            modifier = Modifier.testTag("revoke_device_${device.deviceId}")
                                        ) {
                                            Icon(Icons.Default.Block, "لغو دسترسی", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    } else {
                                        // Delete
                                        IconButton(
                                            onClick = {
                                                viewModel.deleteConnectedDevice(device.deviceId)
                                                snackbarMessage = "دستگاه متوقف شده حذف گردید."
                                                showSuccessSnackbar = true
                                            },
                                            modifier = Modifier.testTag("delete_device_${device.deviceId}")
                                        ) {
                                            Icon(Icons.Default.Delete, "حذف", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Form inputs for local editing of company settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                var centerName by remember { mutableStateOf("") }
                var centerAddress by remember { mutableStateOf("") }
                var supportPhone by remember { mutableStateOf("") }
                var nationalCode by remember { mutableStateOf("") }

                LaunchedEffect(companyNameState, companyAddress, companyPhone, companyNationalCode) {
                    centerName = companyNameState
                    centerAddress = companyAddress
                    supportPhone = companyPhone
                    nationalCode = companyNationalCode
                }

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "ویرایش مشخصات محلی مرکز",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    OutlinedTextField(
                        value = centerName,
                        onValueChange = { centerName = it },
                        label = { Text("نام مرکز خدمات درمانی") },
                        modifier = Modifier.fillMaxWidth().testTag("console_center_name_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = supportPhone,
                            onValueChange = { supportPhone = it },
                            label = { Text("تلفن مرکز") },
                            modifier = Modifier.weight(1f).testTag("console_phone_input")
                        )

                        OutlinedTextField(
                            value = nationalCode,
                            onValueChange = { nationalCode = it },
                            label = { Text("شناسه ثبتی / کد ملی") },
                            modifier = Modifier.weight(1f).testTag("console_national_code_input")
                        )
                    }

                    OutlinedTextField(
                        value = centerAddress,
                        onValueChange = { centerAddress = it },
                        label = { Text("آدرس دفتر مرکزی") },
                        modifier = Modifier.fillMaxWidth().testTag("console_address_input")
                    )

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.updateSettings(
                                    name = centerName,
                                    tax = 9.0,
                                    currency = "ریال"
                                )
                                viewModel.updateSystemSetting("center_name", centerName)
                                viewModel.updateSystemSetting("center_address", centerAddress)
                                viewModel.updateSystemSetting("support_phone", supportPhone)
                                viewModel.updateSystemSetting("national_code", nationalCode)
                                // Trigger sync to distribute this change to other devices!
                                viewModel.triggerSync()
                                snackbarMessage = "تغییرات ثبت شد و برای انتشار در سایر دستگاه‌ها صف‌بندی گردید."
                                showSuccessSnackbar = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("console_save_company_btn")
                    ) {
                        Icon(Icons.Default.Save, null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("به‌روزرسانی و انتشار ابری مشخصات مرکز")
                    }
                }
            }

            // Dangerous Options - Reset
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.05f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "منطقه بحرانی مدیریت سازمان",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "با خروج از این سازمان، این دستگاه ارتباط خود را با کلید ابری فعلی از دست خواهد داد و تمامی فایل‌های تغییر یافته صف همگام‌سازی موقتاً محلی خواهند ماند.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.purgeAllLocalOfflineDevices()
                                snackbarMessage = "تمامی حساب‌های آفلاین محلی تکراری پاک‌سازی شدند."
                                showSuccessSnackbar = true
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("purge_local_devices_console_btn")
                        ) {
                            Icon(Icons.Default.DeleteSweep, null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("حذف ۴۰+ حساب محلی", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = { viewModel.resetCompanyWorkspace() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("reset_workspace_btn")
                        ) {
                            Icon(Icons.Default.ExitToApp, null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("خروج از سازمان ابری", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // --- SUCCESS SNACKBAR ---
    if (showSuccessSnackbar) {
        Snackbar(
            modifier = Modifier
                .padding(16.dp),
            action = {
                TextButton(onClick = { showSuccessSnackbar = false }) {
                    Text("باشه", color = MaterialTheme.colorScheme.inversePrimary)
                }
            }
        ) {
            Text(snackbarMessage)
        }
    }

    // --- DIALOG: MOCK QR CODE ---
    if (showQrDialog) {
        Dialog(onDismissRequest = { showQrDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "اتصال آسان دستگاه جدید",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "کد QR زیر را با تبلت یا تلفن همکار دیگر اسکن کنید تا مستقیماً به مرکز متصل شود:",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )

                    // Let's render our premium custom Canvas QR code vector!
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val sizePx = size.width
                            val blockSize = sizePx / 10f

                            // Draw Top-Left Corner Box
                            drawRect(Color.Black, size = androidx.compose.ui.geometry.Size(blockSize * 3, blockSize * 3))
                            drawRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(blockSize, blockSize), size = androidx.compose.ui.geometry.Size(blockSize, blockSize))

                            // Draw Top-Right Corner Box
                            drawRect(Color.Black, topLeft = androidx.compose.ui.geometry.Offset(sizePx - blockSize * 3, 0f), size = androidx.compose.ui.geometry.Size(blockSize * 3, blockSize * 3))
                            drawRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(sizePx - blockSize * 2, blockSize), size = androidx.compose.ui.geometry.Size(blockSize, blockSize))

                            // Draw Bottom-Left Corner Box
                            drawRect(Color.Black, topLeft = androidx.compose.ui.geometry.Offset(0f, sizePx - blockSize * 3), size = androidx.compose.ui.geometry.Size(blockSize * 3, blockSize * 3))
                            drawRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(blockSize, sizePx - blockSize * 2), size = androidx.compose.ui.geometry.Size(blockSize, blockSize))

                            // Draw Bottom-Right smaller block
                            drawRect(Color.Black, topLeft = androidx.compose.ui.geometry.Offset(sizePx - blockSize * 2, sizePx - blockSize * 2), size = androidx.compose.ui.geometry.Size(blockSize * 2, blockSize * 2))

                            // Draw some mock random QR data pixels
                            for (i in 3..6) {
                                for (j in 3..6) {
                                    if ((i + j) % 2 == 0) {
                                        drawRect(
                                            color = Color.Black,
                                            topLeft = androidx.compose.ui.geometry.Offset(i * blockSize, j * blockSize),
                                            size = androidx.compose.ui.geometry.Size(blockSize, blockSize)
                                        )
                                    }
                                }
                            }
                            // Extra random bits
                            drawRect(Color.Black, topLeft = androidx.compose.ui.geometry.Offset(7 * blockSize, 2 * blockSize), size = androidx.compose.ui.geometry.Size(blockSize, blockSize))
                            drawRect(Color.Black, topLeft = androidx.compose.ui.geometry.Offset(2 * blockSize, 7 * blockSize), size = androidx.compose.ui.geometry.Size(blockSize, blockSize))
                        }
                    }

                    // Selectable Sync Code text
                    OutlinedTextField(
                        value = companySyncCode,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("کد همگام‌سازی متنی (جهت اشتراک‌گذاری با همکاران)") },
                        trailingIcon = {
                            IconButton(onClick = {
                                if (companySyncCode.isNotBlank()) {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(companySyncCode))
                                    snackbarMessage = "کد همگام‌سازی ($companySyncCode) با موفقیت در حافظه کپی شد."
                                    showSuccessSnackbar = true
                                }
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "کپی شناسه")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { showQrDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("بستن")
                    }
                }
            }
        }
    }

    // --- DIALOG: RENAME DEVICE ---
    renameDeviceTarget?.let { target ->
        Dialog(onDismissRequest = { renameDeviceTarget = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "تغییر نام دستگاه متصل",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = renameNewName,
                        onValueChange = { renameNewName = it },
                        label = { Text("نام جدید دستگاه") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rename_device_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            onClick = { renameDeviceTarget = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("انصراف")
                        }

                        Button(
                            onClick = {
                                if (renameNewName.isNotBlank()) {
                                    viewModel.renameDevice(target.deviceId, renameNewName)
                                    snackbarMessage = "نام دستگاه به «$renameNewName» تغییر یافت."
                                    showSuccessSnackbar = true
                                }
                                renameDeviceTarget = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("rename_device_submit"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("ذخیره")
                        }
                    }
                }
            }
        }
    }
}
