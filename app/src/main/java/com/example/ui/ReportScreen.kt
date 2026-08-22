package com.example.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.BackupManager
import com.example.data.ValidationResult
import com.example.data.BackupMetadata
import com.example.data.Employee
import com.example.data.Patient
import com.example.data.Service
import com.example.data.ServiceRegistration
import com.example.data.Expense
import com.example.ui.components.EnterpriseCard
import com.example.ui.components.KPICard
import com.example.ui.components.StatusBadge
import com.example.ui.components.EnterpriseStatusType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

// Brand Color Palette
private val BrandDeepTeal = Color(0xFF0F766E)
private val BrandTurquoise = Color(0xFF14B8A6)
private val BrandLightTeal = Color(0xFFCCFBF1)
private val BrandSurfaceBg = Color(0xFFF5FBFD)

private val ChartColorPalette = listOf(
    Color(0xFF0F766E), // Deep Teal
    Color(0xFF14B8A6), // Turquoise
    Color(0xFFF59E0B), // Amber
    Color(0xFF10B981), // Emerald
    Color(0xFF6366F1), // Indigo
    Color(0xFFEC4899)  // Pink
)

data class BarChartPeriod(
    val label: String,
    val income: Double,
    val expense: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(viewModel: HamrahanViewModel) {
    val registrations by viewModel.registrations.collectAsState()
    val patients by viewModel.patients.collectAsState()
    val employees by viewModel.employees.collectAsState()
    val services by viewModel.services.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val referrals by viewModel.referrals.collectAsState()
    val referralCommissions by viewModel.referralCommissions.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()
    val currency by viewModel.defaultCurrency.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var selectedDateRange by remember { mutableStateOf("همه زمان‌ها") } // "امروز", "۷ روز اخیر", "۳۰ روز اخیر", "همه زمان‌ها"
    var activeReportType by remember { mutableStateOf("مالی") } // "مالی", "همکاران", "خدمات", "بیماران"

    var isOperatingBackup by remember { mutableStateOf(false) }
    var restoreValidationSuccess by remember { mutableStateOf<ValidationResult.Success?>(null) }
    var selectedRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }

    // Date Range Calculations
    val nowMs = System.currentTimeMillis()
    val filterStartMs = remember(selectedDateRange, nowMs) {
        val cal = Calendar.getInstance()
        when (selectedDateRange) {
            "امروز" -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis
            }
            "۷ روز اخیر" -> nowMs - (7 * 86400000L)
            "۳۰ روز اخیر" -> nowMs - (30 * 86400000L)
            else -> 0L
        }
    }

    // Filtered Datasets based on Date Range
    val filteredRegistrations = remember(registrations, filterStartMs) {
        if (filterStartMs == 0L) registrations else registrations.filter { it.dateTime >= filterStartMs }
    }

    val filteredExpenses = remember(expenses, filterStartMs) {
        if (filterStartMs == 0L) expenses else expenses.filter { it.registrationDate >= filterStartMs }
    }

    val filteredTransactions = remember(transactions, filterStartMs) {
        if (filterStartMs == 0L) transactions else transactions.filter { it.date >= filterStartMs }
    }

    val filteredCommissions = remember(referralCommissions, filterStartMs) {
        if (filterStartMs == 0L) referralCommissions else referralCommissions.filter { it.date >= filterStartMs }
    }

    // KPI Metrics
    val totalIncome = remember(filteredRegistrations, filteredTransactions) {
        val regIncome = filteredRegistrations.sumOf { it.finalPrice }
        val txIncome = filteredTransactions.filter { it.type == "درآمد" }.sumOf { it.amount }
        regIncome + txIncome
    }

    val totalExpenseAmount = remember(filteredExpenses, filteredTransactions) {
        val expTotal = filteredExpenses.sumOf { it.amount }
        val txExpenses = filteredTransactions.filter { it.type == "هزینه" }.sumOf { it.amount }
        expTotal + txExpenses
    }

    val totalStaffCommissions = remember(filteredRegistrations) {
        filteredRegistrations.sumOf { it.employeeCommission }
    }

    val netProfit = remember(totalIncome, totalExpenseAmount, totalStaffCommissions) {
        totalIncome - totalExpenseAmount - totalStaffCommissions
    }

    val pendingReceivables = remember(filteredRegistrations) {
        filteredRegistrations.filter { !it.isPaid }.sumOf { it.finalPrice }
    }

    val staffPayables = remember(filteredRegistrations) {
        filteredRegistrations.filter { it.isPaid }.sumOf { it.employeeCommission }
    }

    // SAF Create Document launcher for Excel Export
    val exportExcelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    isOperatingBackup = true
                    val success = context.contentResolver.openOutputStream(uri).use { os ->
                        if (os != null) viewModel.exportDataToExcel(os) else false
                    }
                    withContext(Dispatchers.Main) {
                        isOperatingBackup = false
                        if (success) {
                            Toast.makeText(context, "گزارش جامع اکسل با موفقیت ذخیره شد.", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "خطا در برون‌سپاری فایل اکسل.", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isOperatingBackup = false
                        Toast.makeText(context, "خطا در خروجی اکسل: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // SAF Create Document launcher for Backup (.healthbackup)
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    isOperatingBackup = true
                    val localBackup = viewModel.backupDatabaseFile()
                    if (localBackup != null && localBackup.exists()) {
                        val exported = viewModel.exportBackupToUri(localBackup, uri)
                        withContext(Dispatchers.Main) {
                            isOperatingBackup = false
                            if (exported) {
                                Toast.makeText(context, "نسخه پشتیبان کامل (.healthbackup) با موفقیت ذخیره شد.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "خطا در انتقال فایل پشتیبان.", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            isOperatingBackup = false
                            Toast.makeText(context, "ایجاد فایل پشتیبان محلی با خطا مواجه شد.", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isOperatingBackup = false
                        Toast.makeText(context, "خطا در پشتیبان‌گیری: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // SAF Open Document launcher for Restore
    val restoreFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedRestoreUri = uri
            scope.launch(Dispatchers.IO) {
                try {
                    isOperatingBackup = true
                    val validation = viewModel.validateBackupFromUri(uri)
                    withContext(Dispatchers.Main) {
                        isOperatingBackup = false
                        if (validation is ValidationResult.Success) {
                            restoreValidationSuccess = validation
                            showRestoreConfirmDialog = true
                        } else if (validation is ValidationResult.Failure) {
                            Toast.makeText(context, "خطای اعتبارنامه‌سنجی: ${validation.error}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isOperatingBackup = false
                        Toast.makeText(context, "خطا در بررسي فایل پشتیبان: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // Main Scaffold with Unified Parent Scrolling Architecture (LazyColumn)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Analytics, contentDescription = null, tint = BrandDeepTeal)
                        Text(
                            text = "داشبورد مدیریتی و هوش تجاری",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        modifier = Modifier.testTag("report_screen")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BrandSurfaceBg),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. DATE RANGE SELECTOR BAR
            item {
                EnterpriseCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null, tint = BrandDeepTeal)
                            Text(
                                text = "بازه زمانی تحلیل:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("امروز", "۷ روز اخیر", "۳۰ روز اخیر", "همه زمان‌ها").forEach { range ->
                                val isSelected = selectedDateRange == range
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selectedDateRange = range
                                    },
                                    label = { Text(range, style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BrandDeepTeal,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 2. M3 KPI SUMMARY CARDS GRID
            item {
                Text(
                    text = "شاخص‌های کلیدی عملکرد مالی (KPIs)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = BrandDeepTeal
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            KPICard(
                                title = "درآمد کل فاکتورها",
                                value = totalIncome.formatPrice(currency),
                                icon = Icons.Default.AccountBalanceWallet,
                                iconTint = BrandDeepTeal
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            KPICard(
                                title = "هزینه‌های جاری",
                                value = totalExpenseAmount.formatPrice(currency),
                                icon = Icons.Default.TrendingDown,
                                iconTint = Color(0xFFE11D48)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            KPICard(
                                title = "سود خالص مرکز",
                                value = netProfit.formatPrice(currency),
                                icon = Icons.Default.TrendingUp,
                                iconTint = Color(0xFF10B981)
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            KPICard(
                                title = "مطالبات تسویه‌نشده",
                                value = pendingReceivables.formatPrice(currency),
                                icon = Icons.Default.Warning,
                                iconTint = Color(0xFFF59E0B)
                            )
                        }
                    }

                    KPICard(
                        title = "کارمزد و سهم همکاران (پرداختی/بدهی)",
                        value = staffPayables.formatPrice(currency),
                        icon = Icons.Default.People,
                        iconTint = BrandTurquoise
                    )
                }
            }

            // 3. ADVANCED CANVAS CHARTS SECTION
            item {
                Text(
                    text = "تحلیل‌های بصری و نمودارهای هوش تجاری",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = BrandDeepTeal
                )
            }

            // Chart A: Top Services Donut Chart
            item {
                EnterpriseCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.PieChart, contentDescription = null, tint = BrandDeepTeal)
                            Text(
                                text = "خدمات پردرآمد و پرتقاضا (سهم خدمات)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        ServicesPerformanceDonutChart(
                            registrations = filteredRegistrations,
                            services = services,
                            currency = currency
                        )
                    }
                }
            }

            // Chart B: Monthly Income vs Expense Bar Chart
            item {
                EnterpriseCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.BarChart, contentDescription = null, tint = BrandDeepTeal)
                            Text(
                                text = "مقایسه دوره ای درآمد و هزینه",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IncomeExpenseBarChart(
                            registrations = filteredRegistrations,
                            expenses = filteredExpenses,
                            currency = currency
                        )
                    }
                }
            }

            // Chart C: Staff Performance Ranking Chart
            item {
                EnterpriseCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.People, contentDescription = null, tint = BrandDeepTeal)
                            Text(
                                text = "رتبه‌بندی عملکرد و عایدی همکاران (کادر درمان)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        StaffPerformanceRankingChart(
                            registrations = filteredRegistrations,
                            employees = employees,
                            currency = currency
                        )
                    }
                }
            }

            // 4. ACTION BUTTONS & HARDENED BACKUP OPERATIONS CARD WITH HAPTIC FEEDBACK
            item {
                EnterpriseCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = BrandLightTeal.copy(alpha = 0.3f)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = BrandDeepTeal)
                            Column {
                                Text(
                                    text = "پشتیبان‌گیری، بازیابی و خروجی اکسل (.xlsx / .healthbackup)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "عملیات اتمیک با اعتبارسنجی SHA-256 و محافظت در برابر OOM",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        HorizontalDivider(color = BrandDeepTeal.copy(alpha = 0.2f))

                        if (isOperatingBackup) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = BrandDeepTeal)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Excel Button
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
                                    exportExcelLauncher.launch("HamrahanSalamat_Report_$time.xlsx")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("export_excel_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandDeepTeal),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("گزارش اکسل", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            // Full Backup Button
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
                                    createBackupLauncher.launch("HamrahanSalamat_Backup_$time.healthbackup")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("create_backup_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandTurquoise),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("پشتیبان‌گیری", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            // Restore Button
                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    restoreFileLauncher.launch(arrayOf("*/*", "application/zip", "application/octet-stream"))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("restore_backup_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.5.dp, BrandDeepTeal)
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp), tint = BrandDeepTeal)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("بازیابی فایل", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BrandDeepTeal)
                            }
                        }
                    }
                }
            }

            // 5. DETAILED BREAKDOWN TAB SELECTOR
            item {
                ScrollableTabRow(
                    selectedTabIndex = listOf("مالی", "همکاران", "خدمات", "بیماران").indexOf(activeReportType).coerceAtLeast(0),
                    edgePadding = 0.dp,
                    divider = {}
                ) {
                    listOf("مالی", "همکاران", "خدمات", "بیماران").forEach { t ->
                        Tab(
                            selected = activeReportType == t,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                activeReportType = t
                            },
                            text = { Text("ریز گزارش $t", fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }

            // 6. INLINE DETAILED BREAKDOWN ITEMS (RENDERED IN PARENT LAZYCOLUMN)
            when (activeReportType) {
                "مالی" -> {
                    item { FinancialReportSection(filteredRegistrations, filteredExpenses, currency) }
                }
                "همکاران" -> {
                    items(employees, key = { it.id }) { emp ->
                        EmployeeReportRowItem(emp, filteredRegistrations, currency)
                    }
                }
                "خدمات" -> {
                    items(services, key = { it.id }) { svc ->
                        ServiceReportRowItem(svc, filteredRegistrations, currency)
                    }
                }
                "بیماران" -> {
                    items(patients, key = { it.id }) { pt ->
                        PatientReportRowItem(pt, filteredRegistrations, currency)
                    }
                }
            }
        }
    }

    // Restore Confirmation Dialog with SHA-256 and Metadata Verification
    if (showRestoreConfirmDialog && restoreValidationSuccess != null) {
        val successVal = restoreValidationSuccess
        if (successVal != null) {
            val meta = successVal.metadata
            val restoreFile = successVal.tempBackupFile

        Dialog(onDismissRequest = { showRestoreConfirmDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = BrandDeepTeal)
                        Text("تأیید بازیابی اطلاعات پشتیبان", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        "فایل پشتیبان انتخاب‌شده از لحاظ ساختاری و هش یکپارچگی SHA-256 کاملاً معتبر است.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("نسخه اپلیکیشن:", style = MaterialTheme.typography.labelMedium)
                                Text(meta.appVersion, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("نسخه دیتابیس:", style = MaterialTheme.typography.labelMedium)
                                Text("Schema v${meta.dbSchemaVersion}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("تاریخ پشتیبان‌گیری:", style = MaterialTheme.typography.labelMedium)
                                Text(meta.backupTimestamp.toDateString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("دستگاه مبدأ:", style = MaterialTheme.typography.labelMedium)
                                Text(meta.backupDevice, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("هش SHA-256 دیتابیس:", style = MaterialTheme.typography.labelMedium)
                                Text("تأییدشده ✓", fontWeight = FontWeight.Bold, color = Color(0xFF10B981), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showRestoreConfirmDialog = false }) {
                            Text("انصراف")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                showRestoreConfirmDialog = false
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        isOperatingBackup = true
                                        val success = viewModel.restoreDatabaseFile(restoreFile)
                                        withContext(Dispatchers.Main) {
                                            isOperatingBackup = false
                                            if (success) {
                                                Toast.makeText(context, "بازیابی اطلاعات با موفقیت انجام شد!", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "خطا در عملیات جایگزینی اتمیک دیتابیس.", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            isOperatingBackup = false
                                            Toast.makeText(context, "خطا در بازیابی: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandDeepTeal)
                        ) {
                            Text("شروع بازیابی اتمیک")
                        }
                    }
                }
            }
        }
    }
}
}

// --- CANVAS DONUT CHART FOR TOP SERVICES ---
@Composable
fun ServicesPerformanceDonutChart(
    registrations: List<ServiceRegistration>,
    services: List<Service>,
    currency: String
) {
    if (registrations.isEmpty() || services.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(140.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("اطلاعات ثبت خدمتی در این بازه زمانی وجود ندارد", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        return
    }

    val serviceStats = remember(registrations, services) {
        services.map { svc ->
            val svcRegs = registrations.filter { it.serviceId == svc.id }
            val rev = svcRegs.sumOf { it.finalPrice }
            Triple(svc.name, rev, svcRegs.size)
        }.filter { it.second > 0 }.sortedByDescending { it.second }.take(5)
    }

    val totalRevenue = remember(serviceStats) { serviceStats.sumOf { it.second } }

    if (serviceStats.isEmpty() || totalRevenue <= 0) {
        Box(
            modifier = Modifier.fillMaxWidth().height(140.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("تراکنش خدماتی برای نمایش نمودار یافت نشد", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Canvas Donut Arc
        Box(
            modifier = Modifier.size(130.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                var startAngle = -90f
                val strokeWidth = 24.dp.toPx()

                serviceStats.forEachIndexed { index, stat ->
                    val sweepAngle = ((stat.second / totalRevenue) * 360f).toFloat()
                    val color = ChartColorPalette[index % ChartColorPalette.size]

                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                        size = Size(size.width - strokeWidth, size.height - strokeWidth),
                        topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                    )
                    startAngle += sweepAngle
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${registrations.size}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BrandDeepTeal
                )
                Text(
                    text = "ثبت خدمت",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }

        // Legend List
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            serviceStats.forEachIndexed { index, stat ->
                val color = ChartColorPalette[index % ChartColorPalette.size]
                val pct = ((stat.second / totalRevenue) * 100).toInt()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Text(
                            text = stat.first,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }

                    Text(
                        text = "$pct% (${stat.second.formatPrice(currency)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// --- CANVAS DUAL BAR CHART FOR INCOME VS EXPENSES ---
@Composable
fun IncomeExpenseBarChart(
    registrations: List<ServiceRegistration>,
    expenses: List<Expense>,
    currency: String
) {
    val periods = remember(registrations, expenses) {
        val now = System.currentTimeMillis()
        val dayMs = 86400000L
        val ranges = listOf(
            "هفته اول" to (now - 28 * dayMs to now - 21 * dayMs),
            "هفته دوم" to (now - 21 * dayMs to now - 14 * dayMs),
            "هفته سوم" to (now - 14 * dayMs to now - 7 * dayMs),
            "هفته جاری" to (now - 7 * dayMs to now)
        )
        ranges.map { (label, range) ->
            val inc = registrations.filter { it.dateTime >= range.first && it.dateTime <= range.second }.sumOf { it.finalPrice }
            val exp = expenses.filter { it.registrationDate >= range.first && it.registrationDate <= range.second }.sumOf { it.amount }
            BarChartPeriod(label, inc, exp)
        }
    }

    val maxVal = remember(periods) {
        val maxInc = periods.maxOfOrNull { it.income } ?: 1.0
        val maxExp = periods.maxOfOrNull { it.expense } ?: 1.0
        maxOf(maxInc, maxExp, 1.0)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.size(10.dp).background(BrandDeepTeal, CircleShape))
                Text("درآمد", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.size(10.dp).background(Color(0xFFE11D48), CircleShape))
                Text("هزینه", style = MaterialTheme.typography.labelSmall)
            }
        }

        Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
            val width = size.width
            val height = size.height
            val bottomPadding = 30.dp.toPx()
            val availableHeight = height - bottomPadding
            val barWidth = 14.dp.toPx()

            val groupWidth = width / periods.size

            periods.forEachIndexed { i, item ->
                val centerX = groupWidth * i + groupWidth / 2

                // Income Bar
                val incHeight = ((item.income / maxVal) * availableHeight).toFloat().coerceAtLeast(4f)
                drawRoundRect(
                    color = BrandDeepTeal,
                    topLeft = Offset(centerX - barWidth - 2f, availableHeight - incHeight),
                    size = Size(barWidth, incHeight),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )

                // Expense Bar
                val expHeight = ((item.expense / maxVal) * availableHeight).toFloat().coerceAtLeast(4f)
                drawRoundRect(
                    color = Color(0xFFE11D48),
                    topLeft = Offset(centerX + 2f, availableHeight - expHeight),
                    size = Size(barWidth, expHeight),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
            }

            // Baseline
            drawLine(
                color = Color.LightGray,
                start = Offset(0f, availableHeight),
                end = Offset(width, availableHeight),
                strokeWidth = 1.dp.toPx()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            periods.forEach { item ->
                Text(item.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// --- CANVAS / COMPOSE STAFF PERFORMANCE RANKING CHART ---
@Composable
fun StaffPerformanceRankingChart(
    registrations: List<ServiceRegistration>,
    employees: List<Employee>,
    currency: String
) {
    if (employees.isEmpty()) {
        Text("همکاری ثبت نشده است.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        return
    }

    val rankedStaff = remember(registrations, employees) {
        employees.map { emp ->
            val empRegs = registrations.filter { it.employeeId == emp.id }
            val rev = empRegs.sumOf { it.finalPrice }
            val comm = empRegs.sumOf { it.employeeCommission }
            Triple(emp, rev, comm)
        }.sortedByDescending { it.second }.take(4)
    }

    val topRevenue = remember(rankedStaff) { rankedStaff.maxOfOrNull { it.second } ?: 1.0 }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rankedStaff.forEachIndexed { index, (emp, rev, comm) ->
            val pct = if (topRevenue > 0) (rev / topRevenue).toFloat().coerceIn(0.05f, 1f) else 0.05f

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}. ${emp.fullName} (${emp.profession})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "درآمد: ${rev.formatPrice(currency)} | کارمزد: ${comm.formatPrice(currency)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(pct)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (index == 0) BrandDeepTeal else BrandTurquoise)
                    )
                }
            }
        }
    }
}

// --- DETAILED BREAKDOWN COMPONENTS ---

@Composable
fun FinancialReportSection(
    regs: List<ServiceRegistration>,
    expenses: List<Expense>,
    currency: String
) {
    val totalRevenue = regs.sumOf { it.finalPrice }
    val totalCommission = regs.sumOf { it.employeeCommission }
    val companyProfit = regs.sumOf { it.companyProfit }
    val totalDiscount = regs.sumOf { it.discount }
    val totalExp = expenses.sumOf { it.amount }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReportMetricRowCard("کل درآمد ناخالص خدمات:", totalRevenue.formatPrice(currency))
        ReportMetricRowCard("کل سهم و کارمزد پرداخت‌شده همکاران:", totalCommission.formatPrice(currency))
        ReportMetricRowCard("کل تخفیفات اعطایی به بیماران:", totalDiscount.formatPrice(currency))
        ReportMetricRowCard("کل هزینه‌های ثبت‌شده مرکز:", totalExp.formatPrice(currency))
        ReportMetricRowCard("سود ویژه عملیاتی شرکت:", companyProfit.formatPrice(currency), color = Color(0xFF10B981))
    }
}

@Composable
fun EmployeeReportRowItem(emp: Employee, regs: List<ServiceRegistration>, currency: String) {
    val empRegs = regs.filter { it.employeeId == emp.id }
    val comm = empRegs.sumOf { it.employeeCommission }

    EnterpriseCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(emp.fullName, fontWeight = FontWeight.Bold)
                Text("سمت: ${emp.profession} | خدمات: ${empRegs.size} مورد", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("عایدی خالص همکار:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text(comm.formatPrice(currency), fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
            }
        }
    }
}

@Composable
fun ServiceReportRowItem(svc: Service, regs: List<ServiceRegistration>, currency: String) {
    val svcRegs = regs.filter { it.serviceId == svc.id }
    val totalRevenue = svcRegs.sumOf { it.finalPrice }

    EnterpriseCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(svc.name, fontWeight = FontWeight.Bold)
                Text("دسته: ${svc.category} | ثبت: ${svcRegs.size} بار", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("درآمد خدمت:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text(totalRevenue.formatPrice(currency), fontWeight = FontWeight.Bold, color = BrandDeepTeal)
            }
        }
    }
}

@Composable
fun PatientReportRowItem(patient: Patient, regs: List<ServiceRegistration>, currency: String) {
    val ptRegs = regs.filter { it.patientId == patient.id }
    val totalBilled = ptRegs.sumOf { it.finalPrice }
    val totalPaid = ptRegs.filter { it.isPaid }.sumOf { it.finalPrice }
    val debt = totalBilled - totalPaid

    EnterpriseCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(patient.fullName, fontWeight = FontWeight.Bold)
                Text("تلفن: ${patient.phone} | مراجعات: ${ptRegs.size} بار", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("مانده بدهی جاری:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Text(debt.formatPrice(currency), fontWeight = FontWeight.Bold, color = if (debt > 0) Color(0xFFE11D48) else Color(0xFF10B981))
            }
        }
    }
}

@Composable
fun ReportMetricRowCard(label: String, value: String, color: Color = Color.Unspecified) {
    EnterpriseCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(value, fontWeight = FontWeight.Bold, color = color, style = MaterialTheme.typography.titleMedium)
        }
    }
}
