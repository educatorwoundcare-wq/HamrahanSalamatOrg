package com.example.ui

import com.example.ui.components.ExportBackupButton

import com.example.ui.components.UpdateDialog

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: HamrahanViewModel) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    LaunchedEffect(Unit) {
        viewModel.checkForUpdates(context)
    }
    val scope = rememberCoroutineScope()
    val companyName by viewModel.companyName.collectAsState()
    val taxPercentage by viewModel.taxPercentage.collectAsState()
    val defaultCurrency by viewModel.defaultCurrency.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()

    val autoGenerateFixedExpenses by viewModel.autoGenerateFixedExpenses.collectAsState()
    val fieldActiveSubmitter by viewModel.fieldActiveSubmitter.collectAsState()
    val fieldActiveReceipt by viewModel.fieldActiveReceipt.collectAsState()
    val fieldActiveDescription by viewModel.fieldActiveDescription.collectAsState()
    val fieldActivePaymentMethod by viewModel.fieldActivePaymentMethod.collectAsState()
    val moduleDonutChart by viewModel.moduleDonutChart.collectAsState()
    val moduleDailyAverage by viewModel.moduleDailyAverage.collectAsState()
    val moduleFixedExpensesGenerator by viewModel.moduleFixedExpensesGenerator.collectAsState()
    val expenseCategories by viewModel.expenseCategories.collectAsState()

    val largeAdjustmentPercentage by viewModel.largeAdjustmentPercentage.collectAsState()
    val updateConfig by viewModel.updateConfig.collectAsState()
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsState()
    val largeAdjustmentAmount by viewModel.largeAdjustmentAmount.collectAsState()
    val requireManagerApprovalLarge by viewModel.requireManagerApprovalLargeAdjustments.collectAsState()
    val integrityReport by viewModel.integrityReport.collectAsState()
    val editHistories by viewModel.editHistories.collectAsState()

    // Workspace & Device states
    val companyNameState by viewModel.companyNameState.collectAsState()
    val companySyncCode by viewModel.companySyncCode.collectAsState()
    val companyId by viewModel.companyId.collectAsState()
    val companyNationalCode by viewModel.companyNationalCode.collectAsState()
    val companyPhone by viewModel.companyPhone.collectAsState()
    val activeDeviceName by viewModel.activeDeviceName.collectAsState()
    val activeDeviceId by viewModel.activeDeviceId.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val syncing by viewModel.syncing.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    
    val pendingChangesCount by viewModel.pendingChangesCount.collectAsState()
    val syncSummary by viewModel.syncSummary.collectAsState()
    val isDeveloperMode by viewModel.isDeveloperMode.collectAsState()
    val userRole by viewModel.currentUserRole.collectAsState()

    var editingName by remember { mutableStateOf(companyName) }
    var editingTax by remember { mutableStateOf(taxPercentage.toString()) }
    var editingCurrency by remember { mutableStateOf(defaultCurrency) }

    var editingPct by remember { mutableStateOf("") }
    var editingAmt by remember { mutableStateOf("") }

    LaunchedEffect(largeAdjustmentPercentage) {
        editingPct = largeAdjustmentPercentage.toString()
    }
    LaunchedEffect(largeAdjustmentAmount) {
        editingAmt = largeAdjustmentAmount.toLong().toString()
    }

    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var backupString by remember { mutableStateOf("") }
    var restoreInput by remember { mutableStateOf("") }
    var backupFilesList by remember { mutableStateOf(viewModel.getBackupFilesList()) }
    var backupToRestore by remember { mutableStateOf<File?>(null) }

    var localFileToExport by remember { mutableStateOf<File?>(null) }
    var validatedMetadata by remember { mutableStateOf<com.example.data.BackupMetadata?>(null) }
    var tempRestoreFile by remember { mutableStateOf<File?>(null) }
    
    updateConfig?.let { config ->
        UpdateDialog(
            showDialog = showUpdateDialog,
            onDismiss = { viewModel.dismissUpdateDialog() },
            updateUrl = config.downloadUrl,
            isForceUpdate = config.forceUpdate,
            newVersionName = config.latestVersionName
        )
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val fileToExport = localFileToExport
        if (uri != null && fileToExport != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val success = viewModel.exportBackupToUri(fileToExport, uri)
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (success) {
                            Toast.makeText(context, "فایل پشتیبان با موفقیت صادر شد.", Toast.LENGTH_LONG).show()
                        } else {
                            val logFile = com.example.data.EnterpriseCrashLogger.getLogFile(context)
                            Toast.makeText(context, "Export failed. Technical log saved.\nPath: ${logFile.absolutePath}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (t: Throwable) {
                    com.example.data.EnterpriseCrashLogger.logThrowable(context, "SettingsScreen exportLauncher", t)
                    val logFile = com.example.data.EnterpriseCrashLogger.getLogFile(context)
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "Export failed. Technical log saved.\nPath: ${logFile.absolutePath}", Toast.LENGTH_LONG).show()
                    }
                    throw t
                }
            }
            localFileToExport = null
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val result = viewModel.validateBackupFromUri(uri)
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        when (result) {
                            is com.example.data.ValidationResult.Success -> {
                                validatedMetadata = result.metadata
                                tempRestoreFile = result.tempBackupFile
                                validationError = null
                            }
                            is com.example.data.ValidationResult.Failure -> {
                                validationError = result.error
                                validatedMetadata = null
                                tempRestoreFile = null
                            }
                        }
                    }
                } catch (t: Throwable) {
                    com.example.data.EnterpriseCrashLogger.logThrowable(context, "SettingsScreen importLauncher", t)
                    val logFile = com.example.data.EnterpriseCrashLogger.getLogFile(context)
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        validationError = "خطا در بارگذاری فایل پشتیبان: ${t.localizedMessage}"
                        validatedMetadata = null
                        tempRestoreFile = null
                        Toast.makeText(context, "Restore failed. Technical log saved.\nPath: ${logFile.absolutePath}", Toast.LENGTH_LONG).show()
                    }
                    throw t
                }
            }
        }
    }

    fun validateAndPrepareLocalRestore(file: File) {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val result = viewModel.validateBackupFile(file)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    when (result) {
                        is com.example.data.ValidationResult.Success -> {
                            validatedMetadata = result.metadata
                            tempRestoreFile = result.tempBackupFile
                            validationError = null
                        }
                        is com.example.data.ValidationResult.Failure -> {
                            validationError = result.error
                            validatedMetadata = null
                            tempRestoreFile = null
                        }
                    }
                }
            } catch (t: Throwable) {
                com.example.data.EnterpriseCrashLogger.logThrowable(context, "SettingsScreen validateAndPrepareLocalRestore", t)
                val logFile = com.example.data.EnterpriseCrashLogger.getLogFile(context)
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    validationError = "خطا در تأیید فایل پشتیبان: ${t.localizedMessage}"
                    validatedMetadata = null
                    tempRestoreFile = null
                    Toast.makeText(context, "Validation failed. Technical log saved.\nPath: ${logFile.absolutePath}", Toast.LENGTH_LONG).show()
                }
                throw t
            }
        }
    }

    var versionClicks by remember { mutableStateOf(0) }
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تنظیمات سیستم", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        modifier = Modifier.testTag("settings_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==========================================
            // 1. ACCOUNT SECTION (حساب کاربری و مرکز)
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth().testTag("settings_account_card"),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Business, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("مشخصات حساب و دفتر کار", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    if (companyNameState.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("نام مرکز: $companyNameState", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("شناسه دفتر کار: $companyId", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text("کد ملی مرکز: $companyNationalCode", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Text("شماره پشتیبانی: $companyPhone", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    } else {
                        Text("دفتر کار محلی مستقل", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("نام دستگاه فعال: $activeDeviceName", style = MaterialTheme.typography.bodyMedium)
                        Text("شناسه دستگاه (UID): $activeDeviceId", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        
                        val roleDisplay = when (userRole) {
                            "Mother Account" -> "سرپرست مرکز (Mother Account)"
                            "Admin" -> "مدیر ارشد سیستم (Admin)"
                            "GM", "General Manager" -> "مدیر کل (General Manager)"
                            else -> "مدیر کل (General Manager)"
                        }
                        Text("نقش فعال شما: $roleDisplay", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }

                    Button(
                        onClick = { showResetConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                        modifier = Modifier.fillMaxWidth().testTag("reset_workspace_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("خروج از دفتر کار و راه‌اندازی مجدد")
                    }
                }
            }

            // ==========================================
            // 2. SYNCHRONIZATION SECTION (همگام‌سازی ابری)
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth().testTag("settings_sync_card"),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("همگام‌سازی ابری چند دستگاهی", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Switch(
                            checked = isOnline,
                            onCheckedChange = { viewModel.setOnline(it) },
                            modifier = Modifier.testTag("online_mode_switch")
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    if (companySyncCode.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("شناسه همگام‌سازی مرکز: $companySyncCode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(companySyncCode))
                                    Toast.makeText(context, "کد همگام‌سازی ($companySyncCode) کپی شد.", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "کپی کد همگام‌سازی",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    } else {
                        Text("برنامه در وضعیت آفلاین یا بدون مرکز ثبت شده قرار دارد.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }

                    if (syncing) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("در حال همگام‌سازی با پایگاه‌داده ابری...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val formattedTime = if (lastSyncTime > 0) {
                            val cal = java.util.Calendar.getInstance().apply { timeInMillis = lastSyncTime }
                            String.format("%02d:%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), cal.get(java.util.Calendar.SECOND)).toPersianDigits()
                        } else "هرگز"
                        
                        Text("آخرین همگام‌سازی موفق: $formattedTime", style = MaterialTheme.typography.bodySmall)
                        Text("تغییرات محلی آماده ارسال: $pendingChangesCount", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }

                    syncSummary?.let { summary ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("گزارش تحلیلی صف همگام‌سازی:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("کل: ${summary.total}", style = MaterialTheme.typography.bodySmall)
                                    Text("موفق: ${summary.successful}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF10B981))
                                    Text("در انتظار: ${summary.pending}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFF59E0B))
                                    Text("خطا/مسدود: ${summary.failed + summary.blocked}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }

                                if (summary.details.isNotEmpty()) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    Text("علت باقی ماندن عملیات در صف:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                    summary.details.take(5).forEach { detail ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                                .padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    "${detail.entityType} (${detail.operationType})",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    "کلاس [${detail.classification.code}]",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (detail.classification.code == "B") Color(0xFFF59E0B) else MaterialTheme.colorScheme.error,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                detail.failureReasonDescription,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { viewModel.triggerSync() },
                        enabled = !syncing && isOnline,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth().testTag("sync_now_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("همگام‌سازی فوری")
                    }
                }
            }

            // ==========================================
            // 3. NOTIFICATIONS SECTION (اعلان‌ها)
            // ==========================================
            var largeAdjustmentsNotif by remember { mutableStateOf(true) }
            var patientNotif by remember { mutableStateOf(true) }
            var paymentNotif by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier.fillMaxWidth().testTag("settings_notifications_card"),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("تنظیمات اعلان‌ها و هشدارهای هوشمند", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("دریافت هشدار تعدیلات کلان مالی", style = MaterialTheme.typography.bodyMedium)
                            Text("هشدار هنگام ویرایش اسناد مالی با مقادیر بالا", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Switch(
                            checked = largeAdjustmentsNotif,
                            onCheckedChange = { largeAdjustmentsNotif = it },
                            modifier = Modifier.testTag("notif_large_adjustments_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("اعلان ثبت بیمار جدید", style = MaterialTheme.typography.bodyMedium)
                            Text("ارسال نوتیفیکیشن هنگام ثبت بیمار در دفتر مشترک", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Switch(
                            checked = patientNotif,
                            onCheckedChange = { patientNotif = it },
                            modifier = Modifier.testTag("notif_patient_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("اعلان پرداخت کارمزد پرسنل", style = MaterialTheme.typography.bodyMedium)
                            Text("تایید نهایی تسویه حساب کارمزدهای درمانی", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Switch(
                            checked = paymentNotif,
                            onCheckedChange = { paymentNotif = it },
                            modifier = Modifier.testTag("notif_payment_switch")
                        )
                    }
                }
            }

            // ==========================================
            // 4. BACKUP SECTION (پشتیبان‌گیری)
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth().testTag("settings_backup_card"),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("سیستم پشتیبان‌گیری آفلاین (فایل)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Text(
                            text = "تعداد: ${backupFilesList.size} / 30",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Description
                    Text(
                        text = "فایل‌های پشتیبان شامل اطلاعات پایگاه داده SQLite، تنظیمات سیستم و فایل‌های ضمیمه به صورت فشرده است. سیستم تا ۳۰ فایل اخیر را به صورت چرخشی نگهداری می‌کند. با گزینه‌های زیر می‌توانید فایل پشتیبان جدید ایجاد کرده، آن را صادر کنید یا یک فایل پشتیبان خارجی را بازیابی کنید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                try {
                                    val result = viewModel.backupDatabaseFile()
                                    if (result != null) {
                                        android.widget.Toast.makeText(context, "فایل پشتیبان با موفقیت تهیه شد:\n${result.name}", android.widget.Toast.LENGTH_LONG).show()
                                        backupFilesList = viewModel.getBackupFilesList()
                                    } else {
                                        val logFile = com.example.data.EnterpriseCrashLogger.getLogFile(context)
                                        android.widget.Toast.makeText(context, "Export failed. Technical log saved.\nPath: ${logFile.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                } catch (t: Throwable) {
                                    val logFile = com.example.data.EnterpriseCrashLogger.getLogFile(context)
                                    val details = "SettingsScreen backupDatabaseFile catch: Class=${t.javaClass.name}, Msg=${t.message}, Thread=${Thread.currentThread().name}"
                                    com.example.data.EnterpriseCrashLogger.log(context, details)
                                    com.example.data.EnterpriseCrashLogger.logThrowable(context, "SettingsScreen backupDatabaseFile", t)
                                    android.widget.Toast.makeText(context, "Export failed. Technical log saved.\nPath: ${logFile.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
                                    throw t
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f).testTag("backup_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Backup, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تهیه پشتیبان جدید")
                        }
                        ExportBackupButton(viewModel = viewModel)

                        Button(
                            onClick = { showRestoreDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.weight(1f).testTag("restore_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("بازیابی ابری")
                        }
                    }

                    Button(
                        onClick = {
                            importLauncher.launch(arrayOf("application/octet-stream", "application/zip", "*/*"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        modifier = Modifier.fillMaxWidth().testTag("saf_restore_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("انتخاب و بازیابی فایل خارجی (SAF)")
                    }

                    if (backupFilesList.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text("فایل‌های پشتیبان موجود در دستگاه:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (file in backupFilesList) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(file.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                            val sizeKb = file.length() / 1024
                                            Text("حجم فایل: $sizeKb کیلوبایت", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(
                                                onClick = { validateAndPrepareLocalRestore(file) },
                                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF2E7D32))
                                            ) {
                                                Text("بازیابی")
                                            }
                                            TextButton(
                                                onClick = {
                                                    localFileToExport = file
                                                    exportLauncher.launch(file.name)
                                                },
                                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                            ) {
                                                Text("صادرات")
                                            }
                                            IconButton(
                                                onClick = {
                                                    viewModel.deleteBackupFile(file)
                                                    backupFilesList = viewModel.getBackupFilesList()
                                                    android.widget.Toast.makeText(context, "فایل پشتیبان حذف شد.", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "حذف فایل", tint = Color.Red)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 5. ABOUT SECTION (درباره نرم‌افزار / شناسنامه)
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth().testTag("settings_about_card"),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // App Title & Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MedicalServices,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "شناسنامه رسمی نرم‌افزار",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        text = "نرم افزار مدیریت امور دفاتر خدمات پرستاری",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.clickable {
                            versionClicks++
                            if (versionClicks >= 7) {
                                viewModel.toggleDeveloperMode()
                                versionClicks = 0
                                Toast.makeText(
                                    context,
                                    if (isDeveloperMode) "🔧 حالت توسعه‌دهنده غیرفعال گردید." else "🔧 حالت توسعه‌دهنده با موفقیت فعال شد! ابزارهای پیشرفته ممیزی و دیتابیس در انتهای منو لود شدند.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    ) {
                        Text(
                            text = "ورژن نرم‌افزار: ۲.۴.۰ (Version 2.4.0)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    if (isDeveloperMode) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Text(
                                text = "🔧 حالت توسعه‌دهنده فعال است",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    // Project Lead & Development Team Info
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Badge,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "توسعه و مدیریت پروژه",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "توسعه داده شده در تیم فناوری اطلاعات همراهان سلامت تحت مدیریت پروژه محمد آزادفلاح کارشناس پرستاری فعال در حوزه فناوری اطلاعات و آموزش پرستاری",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 22.sp
                            )
                        }
                    }

                    // Contact Info Box
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "راه‌های ارتباطی و کسب اطلاعات بیشتر",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "برای همکاری و کسب اطلاعات بیشتر از طریق راه‌های ارتباطی زیر اقدام نمایید:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        try {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                                                data = android.net.Uri.parse("tel:09194639587")
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {}
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(6.dp).size(18.dp)
                                    )
                                }
                                Column {
                                    Text(text = "شماره تماس :", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(text = "۰۹۱۹۴۶۳۹۵۸۷", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        try {
                                            val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                                data = android.net.Uri.parse("mailto:Mohammad.azadfallah@gmail.com")
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {}
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(6.dp).size(18.dp)
                                    )
                                }
                                Column {
                                    Text(text = "ایمیل :", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(text = "Mohammad.azadfallah@gmail.com", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val apkSource = java.io.File(context.packageCodePath)
                                    if (!apkSource.exists()) {
                                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            Toast.makeText(context, "فایل برنامه پیدا نشد.", Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        val destinationFile = java.io.File(context.cacheDir, "Hamrahan_Salamat.apk")
                                        apkSource.inputStream().use { input ->
                                            destinationFile.outputStream().use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                        val authority = "${context.packageName}.fileprovider"
                                        val apkUri = androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            authority,
                                            destinationFile
                                        )
                                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "application/vnd.android.package-archive"
                                            putExtra(android.content.Intent.EXTRA_STREAM, apkUri)
                                            clipData = android.content.ClipData.newRawUri("", apkUri)
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            context.startActivity(android.content.Intent.createChooser(intent, "ارسال فایل برنامه"))
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        Toast.makeText(context, "خطا در آماده‌سازی فایل: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("share_apk_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ارسال فایل برنامه (اشتراک‌گذاری APK)")
                    }
                }
            }

            // ==========================================
            // 🔧 DEVELOPER MODE GATED TOOLS
            // ==========================================
            if (isDeveloperMode) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("🔧 منوی ابزارهای توسعه‌دهندگان (Developer Tools)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f))

                // --- Simulated User Access Role Card ---
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("dev_role_simulator_card"),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text("شبیه‌سازی و کنترل سطح دسترسی کاربر", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = "جهت بررسی و آزمون دقیق دسترسی‌های امنیتی سیستم (تست نیازمندی عدم دسترسی کاربران غیرمجاز)، می‌توانید نقش کاربری فعال خود را در زیر تغییر دهید:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            lineHeight = 20.sp
                        )

                        val roles = listOf(
                            Pair("Mother Account", "سرپرست مرکز (Mother Account)"),
                            Pair("Admin", "مدیر ارشد (Admin)"),
                            Pair("GM", "مدیر کل (GM)")
                        )

                        roles.forEach { (roleKey, roleLabel) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.updateCurrentUserRole(roleKey) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = userRole == roleKey,
                                    onClick = { viewModel.updateCurrentUserRole(roleKey) },
                                    modifier = Modifier.testTag("role_radio_$roleKey")
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(roleLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }

                // --- Theme Switcher inside Developer Options ---
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("dev_theme_card"),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("پوسته تاریک (Dark Mode)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text("تغییر ظاهر کلی نرم‌افزار به حالت بهینه شب", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = { viewModel.toggleDarkMode(it) },
                            modifier = Modifier.testTag("dark_mode_switch")
                        )
                    }
                }

                // --- Advanced ERP Configuration Card (User-Driven) ---
                var newCategoryName by remember { mutableStateOf("") }
                var selectedMergeSource by remember { mutableStateOf("") }
                var selectedMergeTarget by remember { mutableStateOf("") }
                var showMergeSourceDropdown by remember { mutableStateOf(false) }
                var showMergeTargetDropdown by remember { mutableStateOf(false) }
                var editingCategoryForRename by remember { mutableStateOf<com.example.data.ExpenseCategory?>(null) }
                var renameCategoryInput by remember { mutableStateOf("") }

                Card(
                    modifier = Modifier.fillMaxWidth().testTag("advanced_erp_card"),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("مدیریت و پیکربندی ماژول‌های ERP پیشرفته", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // 1. Module Management
                        Text("ماژول‌ها و بخش‌های فعال در سیستم مالی", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("نمودار سهم دسته‌بندی مخارج (Donut Chart)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("نمایش دایره‌ای توزیع انواع هزینه‌ها", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = moduleDonutChart,
                                onCheckedChange = { viewModel.updateSystemSetting("module_donut_chart", it.toString()) },
                                modifier = Modifier.testTag("switch_donut_chart")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("ماژول محاسبه میانگین روزانه مخارج", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("نمایش خودکار میانگین هزینه روز در گزارش", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = moduleDailyAverage,
                                onCheckedChange = { viewModel.updateSystemSetting("module_daily_average", it.toString()) },
                                modifier = Modifier.testTag("switch_daily_average")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("سیستم الگوهای هزینه‌های ثابت", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("پشتیبانی از تعاریف و تولید مخارج ثابت", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = moduleFixedExpensesGenerator,
                                onCheckedChange = { viewModel.updateSystemSetting("module_fixed_expenses_generator", it.toString()) },
                                modifier = Modifier.testTag("switch_fixed_generator")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("تولید خودکار هزینه‌های ثابت", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("اجرای خودکار الگوها در ابتدای هر ماه", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = autoGenerateFixedExpenses,
                                onCheckedChange = { viewModel.updateSystemSetting("auto_generate_fixed_expenses", it.toString()) },
                                modifier = Modifier.testTag("switch_auto_fixed_expenses")
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // 2. Form Fields Customization
                        Text("فیلدهای اختیاری فرم ثبت هزینه", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("فیلد کارپرداز / شخص ثبت‌کننده", style = MaterialTheme.typography.bodyMedium)
                            }
                            Switch(
                                checked = fieldActiveSubmitter,
                                onCheckedChange = { viewModel.updateSystemSetting("field_active_submitter", it.toString()) },
                                modifier = Modifier.testTag("switch_field_submitter")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("فیلد تصویر سند / پیوست فاکتور", style = MaterialTheme.typography.bodyMedium)
                            }
                            Switch(
                                checked = fieldActiveReceipt,
                                onCheckedChange = { viewModel.updateSystemSetting("field_active_receipt", it.toString()) },
                                modifier = Modifier.testTag("switch_field_receipt")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("فیلد توضیحات تکمیلی", style = MaterialTheme.typography.bodyMedium)
                            }
                            Switch(
                                checked = fieldActiveDescription,
                                onCheckedChange = { viewModel.updateSystemSetting("field_active_description", it.toString()) },
                                modifier = Modifier.testTag("switch_field_description")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("فیلد روش پرداخت (کارت، نقد و...)", style = MaterialTheme.typography.bodyMedium)
                            }
                            Switch(
                                checked = fieldActivePaymentMethod,
                                onCheckedChange = { viewModel.updateSystemSetting("field_active_payment_method", it.toString()) },
                                modifier = Modifier.testTag("switch_field_payment_method")
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // 3. Category Management
                        Text("مدیریت پویا و کامل دسته‌بندی‌های هزینه مرکز", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Text("تغییر نام، حذف و یکپارچه‌سازی دسته‌بندی‌ها به طور مستقیم از دیتابیس:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        // Add Category
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newCategoryName,
                                onValueChange = { newCategoryName = it },
                                placeholder = { Text("نام دسته جدید را وارد کنید...") },
                                modifier = Modifier.weight(1f).testTag("add_category_input"),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Button(
                                onClick = {
                                    if (newCategoryName.isNotBlank()) {
                                        viewModel.saveExpenseCategory(com.example.data.ExpenseCategory(name = newCategoryName.trim()))
                                        newCategoryName = ""
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("add_category_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "افزودن")
                            }
                        }

                        // Existing categories
                        if (expenseCategories.isEmpty()) {
                            Text("هیچ دسته‌بندی هزینه‌ای در پایگاه‌داده وجود ندارد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                expenseCategories.forEach { category ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), shape = RoundedCornerShape(8.dp))
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (editingCategoryForRename?.id == category.id) {
                                            OutlinedTextField(
                                                value = renameCategoryInput,
                                                onValueChange = { renameCategoryInput = it },
                                                modifier = Modifier.weight(1f).testTag("rename_cat_input_${category.id}"),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            Row {
                                                TextButton(onClick = {
                                                    if (renameCategoryInput.isNotBlank()) {
                                                        viewModel.saveExpenseCategory(category.copy(name = renameCategoryInput.trim()))
                                                        editingCategoryForRename = null
                                                    }
                                                }) {
                                                    Text("ذخیره")
                                                }
                                                TextButton(onClick = { editingCategoryForRename = null }) {
                                                    Text("لغو")
                                                }
                                            }
                                        } else {
                                            Text(category.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                            Row {
                                                IconButton(
                                                    onClick = {
                                                        editingCategoryForRename = category
                                                        renameCategoryInput = category.name
                                                    },
                                                    modifier = Modifier.size(32.dp).testTag("edit_cat_btn_${category.id}")
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = "ویرایش نام", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                }
                                                IconButton(
                                                    onClick = { viewModel.deleteExpenseCategory(category) },
                                                    modifier = Modifier.size(32.dp).testTag("delete_cat_btn_${category.id}")
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "حذف دسته", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // 4. Merge Categories Section (Advanced ERP)
                        Text("یکپارچه‌سازی و ادغام دسته‌ها (ERP)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                        Text("انتقال تمام هزینه‌ها و الگوهای متعلق به دسته مبدا به دسته مقصد، سپس حذف دسته مبدا:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Source category picker
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedButton(
                                        onClick = { showMergeSourceDropdown = true },
                                        modifier = Modifier.fillMaxWidth().testTag("merge_source_picker")
                                    ) {
                                        Text(if (selectedMergeSource.isEmpty()) "انتخاب دسته مبدا" else selectedMergeSource, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    DropdownMenu(
                                        expanded = showMergeSourceDropdown,
                                        onDismissRequest = { showMergeSourceDropdown = false }
                                    ) {
                                        expenseCategories.forEach { category ->
                                            DropdownMenuItem(
                                                text = { Text(category.name) },
                                                onClick = {
                                                    selectedMergeSource = category.name
                                                    showMergeSourceDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Target category picker
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedButton(
                                        onClick = { showMergeTargetDropdown = true },
                                        modifier = Modifier.fillMaxWidth().testTag("merge_target_picker")
                                    ) {
                                        Text(if (selectedMergeTarget.isEmpty()) "انتخاب دسته مقصد" else selectedMergeTarget, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    DropdownMenu(
                                        expanded = showMergeTargetDropdown,
                                        onDismissRequest = { showMergeTargetDropdown = false }
                                    ) {
                                        expenseCategories.filter { it.name != selectedMergeSource }.forEach { category ->
                                            DropdownMenuItem(
                                                text = { Text(category.name) },
                                                onClick = {
                                                    selectedMergeTarget = category.name
                                                    showMergeTargetDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    if (selectedMergeSource.isNotEmpty() && selectedMergeTarget.isNotEmpty()) {
                                        viewModel.mergeExpenseCategories(selectedMergeSource, selectedMergeTarget)
                                        selectedMergeSource = ""
                                        selectedMergeTarget = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth().testTag("merge_categories_btn"),
                                enabled = selectedMergeSource.isNotEmpty() && selectedMergeTarget.isNotEmpty()
                            ) {
                                Text("ادغام و یکپارچه‌سازی دسته‌بندی‌ها")
                            }
                        }
                    }
                }

                // --- Large Adjustments & Audit Configuration Card ---
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("adjustment_config_card"),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("تنظیمات آستانه تعدیلات کلان مالی", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        
                        Text(
                            text = "هنگام ویرایش اسناد مالی، در صورتی که تغییرات از این آستانه‌ها بیشتر باشد، رویداد به عنوان «تعدیل کلان» نشاندار شده و در بخش ممیزی به صورت ویژه ثبت خواهد شد.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            lineHeight = 18.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = editingPct,
                                onValueChange = { editingPct = it },
                                label = { Text("آستانه درصد تغییر کلان (%)") },
                                modifier = Modifier.weight(1f).testTag("adjustment_pct_input"),
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = editingAmt,
                                onValueChange = { editingAmt = it },
                                label = { Text("آستانه مبلغ تغییر کلان ($defaultCurrency)") },
                                modifier = Modifier.weight(1f).testTag("adjustment_amt_input"),
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("الزام تایید مدیریت برای تعدیلات مالی کلان", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("جلوگیری از ثبت نهایی تغییرات بدون ثبت دلیل رسمی", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = requireManagerApprovalLarge,
                                onCheckedChange = { viewModel.updateSystemSetting("require_manager_approval_large", it.toString()) },
                                modifier = Modifier.testTag("require_manager_approval_large_switch")
                            )
                        }

                        Button(
                            onClick = {
                                val pct = editingPct.toDoubleOrNull() ?: 20.0
                                val amt = editingAmt.toDoubleOrNull() ?: 100000.0
                                viewModel.updateSystemSetting("large_adjustment_percentage", pct.toString())
                                viewModel.updateSystemSetting("large_adjustment_amount", amt.toString())
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.align(Alignment.End).testTag("save_adjustment_thresholds_btn")
                        ) {
                            Text("ذخیره آستانه‌ها")
                        }
                    }
                }

                // --- Financial Integrity Monitor Card ---
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("financial_integrity_card"),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("پایش یکپارچگی ساختاری سیستم مالی", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        
                        Text(
                            text = "بررسی خودکار تمامیت روابط دیتابیس شامل عدم تکرار شماره اسناد، هم‌پوشانی مالی دیتابیس، ارجاعات خارجی پرسنل/بیمار و حذف رکوردهای یتیم.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            lineHeight = 18.sp
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (integrityReport.contains("⚠️")) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                            ),
                            border = BorderStroke(1.dp, if (integrityReport.contains("⚠️")) MaterialTheme.colorScheme.error.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = integrityReport,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                                lineHeight = 22.sp
                            )
                        }

                        Button(
                            onClick = { viewModel.runIntegrityCheck() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.fillMaxWidth().testTag("run_integrity_check_btn")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("اجرای پایش و بررسی پایداری تراکنش‌ها")
                        }
                    }
                }

                // --- Financial Adjustment Audit Log History Card ---
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("adjustment_audit_log_card"),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("تاریخچه اصلاحات و تعدیلات مالی (Audit Trail)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        
                        Text(
                            text = "رویدادنگاری تغییر مقادیر اصلی اسناد مالی به انضمام نقش کاربر و توجیه مالی ثبت شده:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        if (editHistories.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("هیچ تعدیل مالی در سیستم ثبت نشده است.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                editHistories.take(15).forEach { history ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "کد سند: ${history.entityType} #${history.entityId}",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "توسط: ${history.editedBy} (${history.userRole})",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                            Text(
                                                text = "علت انتخاب شده: ${history.reason}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.error
                                            )

                                            if (history.comment.isNotEmpty()) {
                                                Text(
                                                    text = "توضیحات: ${history.comment}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("مقدار قبلی:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                                    Text(history.previousValue, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("مقدار جدید:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                                    Text(history.newValue, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "مابه‌التفاوت: " + String.format("%,.0f", history.differenceAmount) + " " + defaultCurrency,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                
                                                val dateStr = history.timestamp.formatDateTime()
                                                Text(
                                                    text = dateStr,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // Dialogs & Alerts
    // ==========================================

    // Reset workspace confirm dialog
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("تایید خروج از دفتر کار اشتراکی ابری") },
            text = { Text("آیا مطمئن هستید که می‌خواهید از این دفتر کار خارج شوید؟ تمام حافظه پنهان محلی و تنظیمات همگام‌سازی پاک شده و به صفحه راه‌اندازی بازخواهید گشت. این کار اطلاعات ذخیره شده در فضای ابری را تغییر نمی‌دهد.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showResetConfirmDialog = false
                        viewModel.resetCompanyWorkspace()
                        Toast.makeText(context, "با موفقیت از دفتر کار خارج شدید.", Toast.LENGTH_LONG).show()
                    }
                ) {
                    Text("خروج و راه‌اندازی مجدد")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    // Backup display dialog
    if (showBackupDialog) {
        Dialog(onDismissRequest = { showBackupDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("فایل پشتیبان تولید شد", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            text = backupString,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Button(
                        onClick = { showBackupDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("تایید")
                    }
                }
            }
        }
    }

    // Restore input dialog
    if (showRestoreDialog) {
        Dialog(onDismissRequest = { showRestoreDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("بازیابی اطلاعات سیستم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = restoreInput,
                        onValueChange = { restoreInput = it },
                        placeholder = { Text("کد پشتیبان یا فایل پشتیبان را وارد کنید...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showRestoreDialog = false }) {
                            Text("انصراف")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val msg = viewModel.restoreData(restoreInput)
                                showRestoreDialog = false
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        ) {
                            Text("بازیابی")
                        }
                    }
                }
            }
        }
    }

    // Hardened Disaster Recovery Validation & Restore Dialogs
    val meta = validatedMetadata
    val fileToRestore = tempRestoreFile
    if (meta != null && fileToRestore != null) {
        val dateStr = try {
            meta.backupTimestamp.formatDateTime()
        } catch (e: Exception) {
            "نامشخص"
        }

        AlertDialog(
            onDismissRequest = {
                validatedMetadata = null
                tempRestoreFile = null
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text("تایید بازیابی اطلاعات پایگاه داده")
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "هشدار: تمام اطلاعات فعلی برنامه شما با اطلاعات داخل فایل پشتیبان رونویسی خواهد شد. این عمل غیرقابل بازگشت است.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("اطلاعات فایل پشتیبان:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text("نسخه پشتیبان: ۲.۰ (تطابق کامل دستگاه‌ها)", style = MaterialTheme.typography.bodySmall)
                            Text("نسخه پایگاه داده: ${meta.dbSchemaVersion}", style = MaterialTheme.typography.bodySmall)
                            Text("نسخه برنامه سازنده: ${meta.appVersion}", style = MaterialTheme.typography.bodySmall)
                            Text("تاریخ ایجاد: $dateStr", style = MaterialTheme.typography.bodySmall)
                            Text("دستگاه مبدا: ${meta.backupDevice}", style = MaterialTheme.typography.bodySmall)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text("تست سلامت و یکپارچگی فایل (SHA-256): موفقیت‌آمیز", color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                val success = viewModel.restoreDatabaseFile(fileToRestore)
                                withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    if (success) {
                                        Toast.makeText(context, "اطلاعات با موفقیت بازیابی شد. اتصال پایگاه داده ریستارت شد.", Toast.LENGTH_LONG).show()
                                        backupFilesList = viewModel.getBackupFilesList()
                                    } else {
                                        Toast.makeText(context, "خطا در بازیابی اطلاعات. ممکن است فایل پشتیبان ناقص یا آسیب دیده باشد.", Toast.LENGTH_LONG).show()
                                    }
                                    validatedMetadata = null
                                    tempRestoreFile = null
                                }
                            } catch (e: Exception) {
                                withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    Toast.makeText(context, "خطا در بازیابی اطلاعات: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    validatedMetadata = null
                                    tempRestoreFile = null
                                }
                            }
                        }
                    }
                ) {
                    Text("بله، رونویسی و بازیابی شود")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        validatedMetadata = null
                        tempRestoreFile = null
                    }
                ) {
                    Text("انصراف")
                }
            }
        )
    }

    // Validation Failure Dialog
    val errMsg = validationError
    if (errMsg != null) {
        AlertDialog(
            onDismissRequest = { validationError = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text("خطا در اعتبارسنجی فایل پشتیبان")
                }
            },
            text = {
                Text(
                    text = errMsg,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { validationError = null }
                ) {
                    Text("متوجه شدم")
                }
            }
        )
    }
}
