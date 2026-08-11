package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.util.Calendar
import com.example.data.*

enum class DrillDownType {
    TODAY_INCOME,
    TODAY_EXPENSE,
    MONTHLY_INCOME,
    MONTHLY_EXPENSE,
    NET_PROFIT,
    RECEIVABLES,
    PAYABLES
}

data class ContributingRecord(
    val id: Int,
    val title: String,
    val amount: Double,
    val date: Long,
    val category: String,
    val sourceModule: String,
    val status: String,
    val deletedStatus: Boolean,
    val referenceId: Int?,
    val originalObject: Any
)

data class TransparencyData(
    val metricName: String,
    val formula: String,
    val tables: String,
    val recordsCount: Int,
    val totalAmount: Double
)

private fun getStartOfDay(time: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = time
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

private fun getEndOfToday(time: Long): Long {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = time
    calendar.set(Calendar.HOUR_OF_DAY, 23)
    calendar.set(Calendar.MINUTE, 59)
    calendar.set(Calendar.SECOND, 59)
    calendar.set(Calendar.MILLISECOND, 999)
    return calendar.timeInMillis
}

private fun getStartOfMonth(time: Long): Long {
    return time.getStartOfJalaliMonth()
}

@Composable
fun DashboardScreen(
    viewModel: HamrahanViewModel,
    onNavigateToRegister: () -> Unit
) {
    val companyId by viewModel.companyId.collectAsState()
    val metrics by viewModel.dashboardMetrics.collectAsState()
    val chartPoints by viewModel.monthlyChartData.collectAsState()
    val currency by viewModel.defaultCurrency.collectAsState()
    val activeAlerts by viewModel.activeAlerts.collectAsState()
    val resolvedAlerts by viewModel.resolvedAlerts.collectAsState()
    var alertFilterTab by remember { mutableStateOf("همه") }
    var dashboardTab by remember { mutableStateOf(0) } // 0 = خلاصه مالی و عملیاتی, 1 = تاریخچه فعالیت‌ها, 2 = صندوق پیام‌ها
    val connectedDevices by viewModel.connectedDevices.collectAsState()
    val pendingDevices = connectedDevices.filter { it.status == "Pending" }
    var isQuickActionsExpanded by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.runAlertDiagnostics(context)
    }

    // --- State Variables for Interactive Drill Down & Transparency Mode ---
    var selectedDrillDownType by remember { mutableStateOf<DrillDownType?>(null) }
    var isAdvancedInvestigation by remember { mutableStateOf(false) }
    var showAllAlertsDialog by remember { mutableStateOf(false) }

    var detailViewObject by remember { mutableStateOf<Any?>(null) }
    var editViewObject by remember { mutableStateOf<Any?>(null) }
    var deleteViewObject by remember { mutableStateOf<Any?>(null) }
    var historyViewObject by remember { mutableStateOf<Any?>(null) }

    // Lists of states observed for drill down
    val registrationsState by viewModel.registrations.collectAsState()
    val expensesState by viewModel.expenses.collectAsState()
    val transactionsState by viewModel.transactions.collectAsState()
    val patientsState by viewModel.patients.collectAsState()
    val employeesState by viewModel.employees.collectAsState()
    val servicesState by viewModel.services.collectAsState()
    val auditState by viewModel.auditLogs.collectAsState()
    val historiesState by viewModel.editHistories.collectAsState()

    val now = System.currentTimeMillis()
    val startOfToday = getStartOfDay(now)
    val endOfToday = getEndOfToday(now)
    val startOfMonth = getStartOfMonth(now)

    val activeExpenses = expensesState.filter {
        !it.isDeleted &&
        (it.workflowStatus == "Approved" || it.workflowStatus == "Submitted" || it.workflowStatus.isEmpty())
    }

    val activeRegistrations = registrationsState.filter {
        !it.isDeleted &&
        (it.workflowStatus == "Approved" || it.workflowStatus == "Submitted" || it.workflowStatus.isEmpty()) &&
        it.serviceDate <= now
    }

    val manualTxs = transactionsState.filter { it.referenceId == null || it.referenceId == 0 }

    val contributingRecords = remember(selectedDrillDownType, registrationsState, expensesState, transactionsState, patientsState, servicesState) {
        val type = selectedDrillDownType ?: return@remember emptyList<ContributingRecord>()
        val list = mutableListOf<ContributingRecord>()

        when (type) {
            DrillDownType.TODAY_INCOME -> {
                activeRegistrations.filter { it.serviceDate in startOfToday..endOfToday }.forEach { reg ->
                    val pName = patientsState.find { it.id == reg.patientId }?.fullName ?: "بیمار"
                    val sName = servicesState.find { it.id == reg.serviceId }?.name ?: "خدمت"
                    list.add(
                        ContributingRecord(
                            id = reg.id,
                            title = "ثبت خدمت: $sName bابت $pName",
                            amount = reg.finalPrice,
                            date = reg.serviceDate,
                            category = "ثبت خدمت",
                            sourceModule = "ServiceRegistrations",
                            status = reg.workflowStatus.ifEmpty { "Approved" },
                            deletedStatus = reg.isDeleted,
                            referenceId = reg.id,
                            originalObject = reg
                        )
                    )
                }
                manualTxs.filter { it.type == "درآمد" && it.date in startOfToday..endOfToday }.forEach { tx ->
                    list.add(
                        ContributingRecord(
                            id = tx.id,
                            title = tx.description,
                            amount = tx.amount,
                            date = tx.date,
                            category = tx.category,
                            sourceModule = "FinancialTransactions",
                            status = "تایید شده",
                            deletedStatus = false,
                            referenceId = null,
                            originalObject = tx
                        )
                    )
                }
            }
            DrillDownType.TODAY_EXPENSE -> {
                activeExpenses.filter { it.paymentDate in startOfToday..endOfToday }.forEach { exp ->
                    val statusText = if (exp.paymentDate <= now) "پرداخت شده" else "زمان‌بندی‌شده (در انتظار)"
                    list.add(
                        ContributingRecord(
                            id = exp.id,
                            title = exp.title,
                            amount = exp.amount,
                            date = exp.paymentDate,
                            category = exp.category,
                            sourceModule = "Expenses",
                            status = statusText,
                            deletedStatus = exp.isDeleted,
                            referenceId = exp.id,
                            originalObject = exp
                        )
                    )
                }
                manualTxs.filter { it.type == "هزینه" && it.date in startOfToday..endOfToday }.forEach { tx ->
                    list.add(
                        ContributingRecord(
                            id = tx.id,
                            title = tx.description,
                            amount = tx.amount,
                            date = tx.date,
                            category = tx.category,
                            sourceModule = "FinancialTransactions",
                            status = "پرداخت شده",
                            deletedStatus = false,
                            referenceId = null,
                            originalObject = tx
                        )
                    )
                }
            }
            DrillDownType.MONTHLY_INCOME -> {
                activeRegistrations.filter { it.serviceDate >= startOfMonth }.forEach { reg ->
                    val pName = patientsState.find { it.id == reg.patientId }?.fullName ?: "بیمار"
                    val sName = servicesState.find { it.id == reg.serviceId }?.name ?: "خدمت"
                    list.add(
                        ContributingRecord(
                            id = reg.id,
                            title = "ثبت خدمت: $sName بابت $pName",
                            amount = reg.finalPrice,
                            date = reg.serviceDate,
                            category = "ثبت خدمت",
                            sourceModule = "ServiceRegistrations",
                            status = reg.workflowStatus.ifEmpty { "Approved" },
                            deletedStatus = reg.isDeleted,
                            referenceId = reg.id,
                            originalObject = reg
                        )
                    )
                }
                manualTxs.filter { it.type == "درآمد" && it.date >= startOfMonth }.forEach { tx ->
                    list.add(
                        ContributingRecord(
                            id = tx.id,
                            title = tx.description,
                            amount = tx.amount,
                            date = tx.date,
                            category = tx.category,
                            sourceModule = "FinancialTransactions",
                            status = "تایید شده",
                            deletedStatus = false,
                            referenceId = null,
                            originalObject = tx
                        )
                    )
                }
            }
            DrillDownType.MONTHLY_EXPENSE -> {
                activeExpenses.filter { it.paymentDate >= startOfMonth }.forEach { exp ->
                    val statusText = if (exp.paymentDate <= now) "پرداخت شده" else "زمان‌بندی‌شده (در انتظار)"
                    list.add(
                        ContributingRecord(
                            id = exp.id,
                            title = exp.title,
                            amount = exp.amount,
                            date = exp.paymentDate,
                            category = exp.category,
                            sourceModule = "Expenses",
                            status = statusText,
                            deletedStatus = exp.isDeleted,
                            referenceId = exp.id,
                            originalObject = exp
                        )
                    )
                }
                manualTxs.filter { it.type == "هزینه" && it.date >= startOfMonth }.forEach { tx ->
                    list.add(
                        ContributingRecord(
                            id = tx.id,
                            title = tx.description,
                            amount = tx.amount,
                            date = tx.date,
                            category = tx.category,
                            sourceModule = "FinancialTransactions",
                            status = "پرداخت شده",
                            deletedStatus = false,
                            referenceId = null,
                            originalObject = tx
                        )
                    )
                }
            }
            DrillDownType.NET_PROFIT -> {
                activeRegistrations.filter { it.serviceDate >= startOfMonth }.forEach { reg ->
                    val pName = patientsState.find { it.id == reg.patientId }?.fullName ?: "بیمار"
                    val sName = servicesState.find { it.id == reg.serviceId }?.name ?: "خدمت"
                    list.add(
                        ContributingRecord(
                            id = reg.id,
                            title = "ثبت خدمت: $sName بابت $pName",
                            amount = reg.finalPrice,
                            date = reg.serviceDate,
                            category = "ثبت خدمت",
                            sourceModule = "ServiceRegistrations",
                            status = reg.workflowStatus.ifEmpty { "Approved" },
                            deletedStatus = reg.isDeleted,
                            referenceId = reg.id,
                            originalObject = reg
                        )
                    )
                }
                manualTxs.filter { it.type == "درآمد" && it.date >= startOfMonth }.forEach { tx ->
                    list.add(
                        ContributingRecord(
                            id = tx.id,
                            title = tx.description,
                            amount = tx.amount,
                            date = tx.date,
                            category = tx.category,
                            sourceModule = "FinancialTransactions",
                            status = "تایید شده",
                            deletedStatus = false,
                            referenceId = null,
                            originalObject = tx
                        )
                    )
                }
                activeExpenses.filter { it.paymentDate >= startOfMonth }.forEach { exp ->
                    val statusText = if (exp.paymentDate <= now) "پرداخت شده" else "زمان‌بندی‌شده (در انتظار)"
                    list.add(
                        ContributingRecord(
                            id = exp.id,
                            title = exp.title,
                            amount = -exp.amount,
                            date = exp.paymentDate,
                            category = exp.category,
                            sourceModule = "Expenses",
                            status = statusText,
                            deletedStatus = exp.isDeleted,
                            referenceId = exp.id,
                            originalObject = exp
                        )
                    )
                }
                manualTxs.filter { it.type == "هزینه" && it.date >= startOfMonth }.forEach { tx ->
                    list.add(
                        ContributingRecord(
                            id = tx.id,
                            title = tx.description,
                            amount = -tx.amount,
                            date = tx.date,
                            category = tx.category,
                            sourceModule = "FinancialTransactions",
                            status = "پرداخت شده",
                            deletedStatus = false,
                            referenceId = null,
                            originalObject = tx
                        )
                    )
                }
            }
            DrillDownType.RECEIVABLES -> {
                registrationsState.filter { !it.isDeleted && !it.isPaid }.forEach { reg ->
                    val pName = patientsState.find { it.id == reg.patientId }?.fullName ?: "بیمار"
                    val sName = servicesState.find { it.id == reg.serviceId }?.name ?: "خدمت"
                    list.add(
                        ContributingRecord(
                            id = reg.id,
                            title = "ثبت خدمت پرداخت‌نشده ($sName) بابت $pName",
                            amount = reg.finalPrice,
                            date = reg.serviceDate,
                            category = "مطالبه بیمار",
                            sourceModule = "ServiceRegistrations",
                            status = "پرداخت نشده",
                            deletedStatus = reg.isDeleted,
                            referenceId = reg.id,
                            originalObject = reg
                        )
                    )
                }
            }
            DrillDownType.PAYABLES -> {
                transactionsState.filter { it.category == "حقوق همکار" && !it.isCleared }.forEach { tx ->
                    list.add(
                        ContributingRecord(
                            id = tx.id,
                            title = tx.description,
                            amount = tx.amount,
                            date = tx.date,
                            category = tx.category,
                            sourceModule = "FinancialTransactions",
                            status = "تسویه نشده",
                            deletedStatus = false,
                            referenceId = tx.referenceId,
                            originalObject = tx
                        )
                    )
                }
            }
        }
        list
    }

    val transparencyInfo = remember(selectedDrillDownType, contributingRecords) {
        val type = selectedDrillDownType ?: return@remember null
        val recordsCount = contributingRecords.size
        val totalAmount = contributingRecords.sumOf { it.amount }

        when (type) {
            DrillDownType.TODAY_INCOME -> TransparencyData(
                metricName = "درآمد امروز",
                formula = "مجموع مبالغ نهایی ثبت خدمات انجام‌شده امروز (تأییدشده و فعال) + مجموع تراکنش‌های درآمدی دستی امروز",
                tables = "service_registrations (فیلتر: فعال، تاریخ امروز، وضعیت تأییدشده/ارسال‌شده) + financial_transactions (فیلتر: نوع درآمد، فاقد شناسه مرجع، تاریخ امروز)",
                recordsCount = recordsCount,
                totalAmount = totalAmount
            )
            DrillDownType.TODAY_EXPENSE -> TransparencyData(
                metricName = "هزینه امروز",
                formula = "مجموع مبالغ هزینه‌های ثبت‌شده امروز (شامل پرداخت‌شده و زمان‌بندی‌شده در انتظار) + مجموع تراکنش‌های هزینه‌ای دستی امروز",
                tables = "expenses (فیلتر: فعال، تاریخ پرداخت امروز) + financial_transactions (فیلتر: نوع هزینه، فاقد شناسه مرجع، تاریخ امروز)",
                recordsCount = recordsCount,
                totalAmount = totalAmount
            )
            DrillDownType.MONTHLY_INCOME -> TransparencyData(
                metricName = "درآمد ناخالص ماه",
                formula = "مجموع مبالغ نهایی ثبت خدمات انجام‌شده از ابتدای ماه جاری تاکنون + مجموع تراکنش‌های درآمدی دستی این ماه",
                tables = "service_registrations (فیلتر: فعال، تاریخ این ماه، وضعیت تأییدشده/ارسال‌شده) + financial_transactions (فیلتر: نوع درآمد، فاقد شناسه مرجع، تاریخ این ماه)",
                recordsCount = recordsCount,
                totalAmount = totalAmount
            )
            DrillDownType.MONTHLY_EXPENSE -> TransparencyData(
                metricName = "هزینه کل ماه",
                formula = "مجموع مبالغ هزینه‌های ثبت‌شده ماه جاری (تفکیک هزینه‌های پرداخت‌شده و هزینه‌های زمان‌بندی‌شده در انتظار) + تراکنش‌های هزینه‌ای دستی ماه",
                tables = "expenses (فیلتر: فعال، تاریخ پرداخت ماه جاری) + financial_transactions (فیلتر: نوع هزینه، فاقد شناسه مرجع، تاریخ ماه جاری)",
                recordsCount = recordsCount,
                totalAmount = totalAmount
            )
            DrillDownType.NET_PROFIT -> TransparencyData(
                metricName = "سود خالص ماه",
                formula = "سود تحقق‌یافته فعلی (درآمد ماه منهای هزینه‌های پرداخت‌شده) | پیش‌بینی سود (درآمد ماه منهای کل هزینه‌ها شامل در انتظار)",
                tables = "service_registrations + expenses + financial_transactions (ترکیب موارد درآمدی و هزینه‌ای فوق)",
                recordsCount = recordsCount,
                totalAmount = totalAmount
            )
            DrillDownType.RECEIVABLES -> TransparencyData(
                metricName = "مطالبات دریافتی (از بیماران)",
                formula = "مجموع مبالغ خدماتی که ثبت شده ولی وضعیت پرداخت آن‌ها «پرداخت نشده» است (بدون محدودیت زمانی)",
                tables = "service_registrations (فیلتر: فعال، پرداخت‌نشده/isPaid = 0)",
                recordsCount = recordsCount,
                totalAmount = totalAmount
            )
            DrillDownType.PAYABLES -> TransparencyData(
                metricName = "بدهی پرداختنی (کارمزد همکاران)",
                formula = "مجموع کارمزدهای انباشته‌شده همکاران بابت خدمات انجام‌شده که هنوز تسویه/پرداخت نشده است",
                tables = "financial_transactions (فیلتر: دسته‌بندی حقوق همکار، تسویه‌نشده/isCleared = 0)",
                recordsCount = recordsCount,
                totalAmount = totalAmount
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("dashboard_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Header Banner ---
            item {
                com.example.ui.components.EnterpriseCard(
                    containerColor = MaterialTheme.colorScheme.primary,
                    borderColor = MaterialTheme.colorScheme.primary,
                    elevation = com.example.ui.theme.DesignTokens.Elevation.medium,
                    cornerRadius = com.example.ui.theme.DesignTokens.Radius.xl,
                    contentPadding = PaddingValues(com.example.ui.theme.DesignTokens.Spacing.xl)
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val isCompact = maxWidth < 600.dp
                        if (isCompact) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(com.example.ui.theme.DesignTokens.Spacing.m)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(com.example.ui.theme.DesignTokens.Spacing.m),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    HamrahanLogo(
                                        size = 48.dp,
                                        primaryColor = Color.White,
                                        secondaryColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "میز مدیریت اجرایی • همراهان سلامت",
                                            style = com.example.ui.theme.EnterpriseTypographyStyles.sectionHeader,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.height(com.example.ui.theme.DesignTokens.Spacing.xs))
                                        Text(
                                            text = "مدیریت جامع دفاتر خدمات پرستاری در منزل",
                                            style = com.example.ui.theme.EnterpriseTypographyStyles.bodyText,
                                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                                        )
                                    }
                                }
                                Text(
                                    text = "پایش آنلاین شاخص‌های کلیدی عملکرد مالی، بالینی و عملیاتی",
                                    style = com.example.ui.theme.EnterpriseTypographyStyles.supportingText,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                                )
                                Button(
                                    onClick = onNavigateToRegister,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary
                                    ),
                                    shape = RoundedCornerShape(com.example.ui.theme.DesignTokens.Radius.m),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("quick_register_service_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(com.example.ui.theme.DesignTokens.Spacing.s))
                                    Text("ثبت خدمت جدید", style = com.example.ui.theme.EnterpriseTypographyStyles.label, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(com.example.ui.theme.DesignTokens.Spacing.l)
                                ) {
                                    HamrahanLogo(
                                        size = 56.dp,
                                        primaryColor = Color.White,
                                        secondaryColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                    Column {
                                        Text(
                                            text = "میز مدیریت اجرایی • همراهان سلامت",
                                            style = com.example.ui.theme.EnterpriseTypographyStyles.screenTitle,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.height(com.example.ui.theme.DesignTokens.Spacing.xs))
                                        Text(
                                            text = "مدیریت جامع دفاتر خدمات پرستاری در منزل",
                                            style = com.example.ui.theme.EnterpriseTypographyStyles.bodyText,
                                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                                        )
                                        Spacer(modifier = Modifier.height(com.example.ui.theme.DesignTokens.Spacing.xs))
                                        Text(
                                            text = "پایش آنلاین شاخص‌های کلیدی عملکرد مالی، بالینی و عملیاتی",
                                            style = com.example.ui.theme.EnterpriseTypographyStyles.supportingText,
                                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(com.example.ui.theme.DesignTokens.Spacing.l))
                                Button(
                                    onClick = onNavigateToRegister,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary
                                    ),
                                    shape = RoundedCornerShape(com.example.ui.theme.DesignTokens.Radius.m),
                                    modifier = Modifier.testTag("quick_register_service_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(com.example.ui.theme.DesignTokens.Spacing.s))
                                    Text("ثبت خدمت جدید", style = com.example.ui.theme.EnterpriseTypographyStyles.label, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            if (companyId == "COMP-LOCAL" || companyId.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("offline_first_banner_card"),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(32.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "همگام‌سازی ابری فعال نیست",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "تمامی داده‌های شما به صورت کاملاً امن روی این دستگاه ذخیره می‌شود. هر زمان که مایل بودید می‌توانید جهت همگام‌سازی بین دستگاه‌ها، همگام‌سازی ابری را فعال کنید.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }

            // --- Sub-Navigation Tabs (Enterprise & Notification Center) ---
            if (pendingDevices.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFEF3C7),
                            contentColor = Color(0xFFD97706)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "درخواست دسترسی جدید (${pendingDevices.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "دستگاه‌های جدیدی درخواست اتصال به دفتر شما را دارند. برای مدیریت و تایید دسترسی به بخش شناسنامه مرکز مراجعه کنید.",
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
            item {
                TabRow(
                    selectedTabIndex = dashboardTab,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Tab(
                        selected = dashboardTab == 0,
                        onClick = { dashboardTab = 0 },
                        text = { Text("خلاصه مالی و عملیاتی", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = dashboardTab == 1,
                        onClick = { dashboardTab = 1 },
                        text = { Text("تاریخچه فعالیت‌ها", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = dashboardTab == 2,
                        onClick = { dashboardTab = 2 },
                        text = { Text("صندوق پیام‌ها (${activeAlerts.size})", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.Mail, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            if (dashboardTab == 0) {

        // --- Smart Alerts Panel -> Smart Task Center (Commercial ERP) ---
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().testTag("dashboard_alerts_panel")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header with a glowing pulse effect
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color(0xFFDC2626), shape = RoundedCornerShape(6.dp))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "میز کار هوشمند و مرکز عملیات (Commercial ERP)",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "${activeAlerts.size} هشدار فعال",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Dashboard KPIs (Top of Alert Panel)
                    val criticalCount = activeAlerts.count { it.type in listOf("staff_profile_incomplete", "patient_consent_missing", "expired_contract") }
                    val todayTasksCount = activeAlerts.count { it.type == "scheduled_service_today" }
                    val pendingApprovalsCount = activeAlerts.count { it.type in listOf("contract_pending", "nursing_report_pending") }
                    val overdueServicesCount = activeAlerts.count { it.type == "expired_contract" }
                    val financialDocsCount = activeAlerts.count { it.type in listOf("accounting_approval_required", "financial_document_missing", "unpaid_commission") }
                    val completedTodayCount = resolvedAlerts.size

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KPIBlock(label = "بحرانی", count = criticalCount, color = Color(0xFFDC2626), bg = Color(0xFFFEE2E2))
                        KPIBlock(label = "مأموریت امروز", count = todayTasksCount, color = Color(0xFF2563EB), bg = Color(0xFFDBEAFE))
                        KPIBlock(label = "در انتظار تایید", count = pendingApprovalsCount, color = Color(0xFFD97706), bg = Color(0xFFFEF3C7))
                        KPIBlock(label = "قرارداد منقضی", count = overdueServicesCount, color = Color(0xFFE11D48), bg = Color(0xFFFFE4E6))
                        KPIBlock(label = "اسناد مالی", count = financialDocsCount, color = Color(0xFF0D9488), bg = Color(0xFFCCFBF1))
                        KPIBlock(label = "اقدام شده امروز", count = completedTodayCount, color = Color(0xFF16A34A), bg = Color(0xFFDCFCE7))
                    }

                    // Smart Filtering Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val filters = listOf("همه", "امروز", "مالی", "پرسنل", "بیماران", "هشدار بحرانی", "تاریخچه اقدامات")
                        filters.forEach { filter ->
                            val isSelected = alertFilterTab == filter
                            FilterChip(
                                selected = isSelected,
                                onClick = { alertFilterTab = filter },
                                label = { Text(filter, style = MaterialTheme.typography.labelMedium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }

                    // Batch Operations
                    if (alertFilterTab != "تاریخچه اقدامات" && activeAlerts.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.approveAllEligibleAlerts(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تایید همه", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { viewModel.dismissAllAlerts() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("حذف همه", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { viewModel.markAllAlertsAsRead() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B7280)),
                                modifier = Modifier.weight(1.2f).height(32.dp),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Drafts, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("خوانده شده همه", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Sorted and Filtered Alerts List / History List
                    if (alertFilterTab == "تاریخچه اقدامات") {
                        if (resolvedAlerts.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.History, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(36.dp))
                                    Text("هیچ اقدام ثبت‌شده‌ای برای امروز یافت نشد.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                resolvedAlerts.forEach { historyItem ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(historyItem.alert.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                Surface(color = Color(0xFFD1FAE5), shape = RoundedCornerShape(4.dp)) {
                                                    Text(
                                                        "✓ موفق",
                                                        color = Color(0xFF065F46),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Text(historyItem.alert.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("اقدام: ${historyItem.actionPerformed}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF0D9488))
                                                Text("توسط: ${historyItem.resolvedBy}", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Regular Alerts filtering and priority sorting
                        val sortedAlerts = remember(activeAlerts, alertFilterTab) {
                            val filtered = when (alertFilterTab) {
                                "امروز" -> activeAlerts.filter { it.type == "scheduled_service_today" }
                                "مالی" -> activeAlerts.filter { it.type in listOf("accounting_approval_required", "financial_document_missing", "unpaid_commission") }
                                "پرسنل" -> activeAlerts.filter { it.type in listOf("staff_profile_incomplete", "contract_pending", "expired_contract") }
                                "بیماران" -> activeAlerts.filter { it.type in listOf("patient_consent_missing", "nursing_report_pending", "medication_review_required") }
                                "هشدار بحرانی" -> activeAlerts.filter { it.type in listOf("staff_profile_incomplete", "patient_consent_missing", "expired_contract") }
                                else -> activeAlerts
                            }
                            
                            fun getPriority(type: String): Int {
                                return when (type) {
                                    "staff_profile_incomplete", "patient_consent_missing", "expired_contract" -> 1
                                    "scheduled_service_today" -> 2
                                    "contract_pending", "nursing_report_pending" -> 3
                                    "accounting_approval_required", "financial_document_missing", "unpaid_commission" -> 4
                                    "medication_review_required" -> 5
                                    else -> 6
                                }
                            }
                            
                            filtered.sortedWith(compareBy({ getPriority(it.type) }, { -it.timestamp }))
                        }

                        if (sortedAlerts.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(42.dp))
                                    Text("تبریک! تمامی هشدارهای بالینی و عملیاتی برطرف شده‌اند.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                                    Text("سیستم در وضعیت پایدار و بهینه قرار دارد.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                sortedAlerts.take(3).forEach { alert ->
                                    EnterpriseAlertItem(
                                        alert = alert,
                                        viewModel = viewModel,
                                        onNavigate = { viewModel.handleDeepLink(alert.relatedScreen) },
                                        onMarkRead = { viewModel.markAlertAsRead(alert) },
                                        onDismiss = { viewModel.dismissAlert(alert) }
                                    )
                                }
                                if (sortedAlerts.size > 3) {
                                    TextButton(
                                        onClick = { showAllAlertsDialog = true },
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Text(
                                            text = "نمایش همه ${sortedAlerts.size} هشدار فعال سیستم...",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Today's Mini Financial Cards ---
        item {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isCompact = maxWidth < 600.dp
                if (isCompact) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DashboardCard(
                            title = "درآمد امروز",
                            value = metrics.todayIncome.formatPrice(currency),
                            icon = Icons.Default.ArrowDownward,
                            iconColor = Color(0xFF10B981),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedDrillDownType = DrillDownType.TODAY_INCOME
                                isAdvancedInvestigation = false
                            },
                            onLongClick = {
                                selectedDrillDownType = DrillDownType.TODAY_INCOME
                                isAdvancedInvestigation = true
                            }
                        )
                        DashboardCard(
                            title = "هزینه امروز",
                            value = metrics.todayExpense.formatPrice(currency),
                            subtitle = if (metrics.todayScheduledExpense > 0) "پرداخت‌شده: ${metrics.todayPaidExpense.formatPrice(currency)} | در انتظار: ${metrics.todayScheduledExpense.formatPrice(currency)}" else null,
                            icon = Icons.Default.ArrowUpward,
                            iconColor = Color(0xFFEF4444),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedDrillDownType = DrillDownType.TODAY_EXPENSE
                                isAdvancedInvestigation = false
                            },
                            onLongClick = {
                                selectedDrillDownType = DrillDownType.TODAY_EXPENSE
                                isAdvancedInvestigation = true
                            }
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DashboardCard(
                            title = "درآمد امروز",
                            value = metrics.todayIncome.formatPrice(currency),
                            icon = Icons.Default.ArrowDownward,
                            iconColor = Color(0xFF10B981),
                            modifier = Modifier.width(220.dp),
                            onClick = {
                                selectedDrillDownType = DrillDownType.TODAY_INCOME
                                isAdvancedInvestigation = false
                            },
                            onLongClick = {
                                selectedDrillDownType = DrillDownType.TODAY_INCOME
                                isAdvancedInvestigation = true
                            }
                        )
                        DashboardCard(
                            title = "هزینه امروز",
                            value = metrics.todayExpense.formatPrice(currency),
                            subtitle = if (metrics.todayScheduledExpense > 0) "پرداخت‌شده: ${metrics.todayPaidExpense.formatPrice(currency)} | در انتظار: ${metrics.todayScheduledExpense.formatPrice(currency)}" else null,
                            icon = Icons.Default.ArrowUpward,
                            iconColor = Color(0xFFEF4444),
                            modifier = Modifier.width(220.dp),
                            onClick = {
                                selectedDrillDownType = DrillDownType.TODAY_EXPENSE
                                isAdvancedInvestigation = false
                            },
                            onLongClick = {
                                selectedDrillDownType = DrillDownType.TODAY_EXPENSE
                                isAdvancedInvestigation = true
                            }
                        )
                    }
                }
            }
        }

        // --- Monthly Broad Financial Cards ---
        item {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isCompact = maxWidth < 600.dp
                if (isCompact) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DashboardCard(
                                title = "درآمد ناخالص ماه",
                                value = metrics.monthlyIncome.formatPrice(currency),
                                icon = Icons.Default.TrendingUp,
                                iconColor = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    selectedDrillDownType = DrillDownType.MONTHLY_INCOME
                                    isAdvancedInvestigation = false
                                },
                                onLongClick = {
                                    selectedDrillDownType = DrillDownType.MONTHLY_INCOME
                                    isAdvancedInvestigation = true
                                }
                            )
                            DashboardCard(
                                title = "هزینه کل ماه",
                                value = metrics.monthlyExpense.formatPrice(currency),
                                subtitle = if (metrics.monthlyScheduledExpense > 0) "پرداخت‌شده: ${metrics.monthlyPaidExpense.formatPrice(currency)} | در انتظار: ${metrics.monthlyScheduledExpense.formatPrice(currency)}" else null,
                                icon = Icons.Default.TrendingDown,
                                iconColor = Color(0xFFEF4444),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    selectedDrillDownType = DrillDownType.MONTHLY_EXPENSE
                                    isAdvancedInvestigation = false
                                },
                                onLongClick = {
                                    selectedDrillDownType = DrillDownType.MONTHLY_EXPENSE
                                    isAdvancedInvestigation = true
                                }
                            )
                        }
                        DashboardCard(
                            title = "سود خالص ماه",
                            value = metrics.netProfit.formatPrice(currency),
                            subtitle = if (metrics.monthlyScheduledExpense > 0) "پیش‌بینی با هزینه در انتظار: ${metrics.projectedNetProfit.formatPrice(currency)}" else null,
                            icon = Icons.Default.AttachMoney,
                            iconColor = Color(0xFF10B981),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                selectedDrillDownType = DrillDownType.NET_PROFIT
                                isAdvancedInvestigation = false
                            },
                            onLongClick = {
                                selectedDrillDownType = DrillDownType.NET_PROFIT
                                isAdvancedInvestigation = true
                            }
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DashboardCard(
                            title = "درآمد ناخالص ماه",
                            value = metrics.monthlyIncome.formatPrice(currency),
                            icon = Icons.Default.TrendingUp,
                            iconColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.width(220.dp),
                            onClick = {
                                selectedDrillDownType = DrillDownType.MONTHLY_INCOME
                                isAdvancedInvestigation = false
                            },
                            onLongClick = {
                                selectedDrillDownType = DrillDownType.MONTHLY_INCOME
                                isAdvancedInvestigation = true
                            }
                        )
                        DashboardCard(
                            title = "هزینه کل ماه",
                            value = metrics.monthlyExpense.formatPrice(currency),
                            subtitle = if (metrics.monthlyScheduledExpense > 0) "پرداخت‌شده: ${metrics.monthlyPaidExpense.formatPrice(currency)} | در انتظار: ${metrics.monthlyScheduledExpense.formatPrice(currency)}" else null,
                            icon = Icons.Default.TrendingDown,
                            iconColor = Color(0xFFEF4444),
                            modifier = Modifier.width(220.dp),
                            onClick = {
                                selectedDrillDownType = DrillDownType.MONTHLY_EXPENSE
                                isAdvancedInvestigation = false
                            },
                            onLongClick = {
                                selectedDrillDownType = DrillDownType.MONTHLY_EXPENSE
                                isAdvancedInvestigation = true
                            }
                        )
                        DashboardCard(
                            title = "سود خالص ماه",
                            value = metrics.netProfit.formatPrice(currency),
                            subtitle = if (metrics.monthlyScheduledExpense > 0) "پیش‌بینی با هزینه در انتظار: ${metrics.projectedNetProfit.formatPrice(currency)}" else null,
                            icon = Icons.Default.AttachMoney,
                            iconColor = Color(0xFF10B981),
                            modifier = Modifier.width(220.dp),
                            onClick = {
                                selectedDrillDownType = DrillDownType.NET_PROFIT
                                isAdvancedInvestigation = false
                            },
                            onLongClick = {
                                selectedDrillDownType = DrillDownType.NET_PROFIT
                                isAdvancedInvestigation = true
                            }
                        )
                    }
                }
            }
        }

        // --- Accounts & Receivables / Payables ---
        item {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isCompact = maxWidth < 600.dp
                if (isCompact) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DashboardCard(
                            title = "مطالبات دریافتی (از بیماران)",
                            value = metrics.outstandingReceivables.formatPrice(currency),
                            icon = Icons.Default.AccountBalanceWallet,
                            iconColor = Color(0xFFF59E0B),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                selectedDrillDownType = DrillDownType.RECEIVABLES
                                isAdvancedInvestigation = false
                            },
                            onLongClick = {
                                selectedDrillDownType = DrillDownType.RECEIVABLES
                                isAdvancedInvestigation = true
                            }
                        )
                        DashboardCard(
                            title = "بدهی پرداختنی (کارمزد همکاران)",
                            value = metrics.outstandingPayables.formatPrice(currency),
                            icon = Icons.Default.HourglassEmpty,
                            iconColor = Color(0xFF8B5CF6),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                selectedDrillDownType = DrillDownType.PAYABLES
                                isAdvancedInvestigation = false
                            },
                            onLongClick = {
                                selectedDrillDownType = DrillDownType.PAYABLES
                                isAdvancedInvestigation = true
                            }
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DashboardCard(
                            title = "مطالبات دریافتی (از بیماران)",
                            value = metrics.outstandingReceivables.formatPrice(currency),
                            icon = Icons.Default.AccountBalanceWallet,
                            iconColor = Color(0xFFF59E0B),
                            modifier = Modifier.width(260.dp),
                            onClick = {
                                selectedDrillDownType = DrillDownType.RECEIVABLES
                                isAdvancedInvestigation = false
                            },
                            onLongClick = {
                                selectedDrillDownType = DrillDownType.RECEIVABLES
                                isAdvancedInvestigation = true
                            }
                        )
                        DashboardCard(
                            title = "بدهی پرداختنی (کارمزد همکاران)",
                            value = metrics.outstandingPayables.formatPrice(currency),
                            icon = Icons.Default.HourglassEmpty,
                            iconColor = Color(0xFF8B5CF6),
                            modifier = Modifier.width(260.dp),
                            onClick = {
                                selectedDrillDownType = DrillDownType.PAYABLES
                                isAdvancedInvestigation = false
                            },
                            onLongClick = {
                                selectedDrillDownType = DrillDownType.PAYABLES
                                isAdvancedInvestigation = true
                            }
                        )
                    }
                }
            }
        }

        // --- Patient & Service Operational KPI Cards ---
        item {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isCompact = maxWidth < 600.dp
                if (isCompact) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DashboardCard(
                                title = "بیماران فعال",
                                value = "${metrics.activePatients} بیمار",
                                icon = Icons.Default.People,
                                iconColor = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.weight(1f)
                            )
                            DashboardCard(
                                title = "همکاران فعال",
                                value = "${metrics.employeeCount} نفر",
                                icon = Icons.Default.Badge,
                                iconColor = Color(0xFF64748B),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        DashboardCard(
                            title = "کل خدمات انجام شده",
                            value = "${metrics.completedVisits} خدمت",
                            icon = Icons.Default.DoneAll,
                            iconColor = Color(0xFF06B6D4),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DashboardCard(
                            title = "بیماران فعال",
                            value = "${metrics.activePatients} بیمار",
                            icon = Icons.Default.People,
                            iconColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.width(200.dp)
                        )
                        DashboardCard(
                            title = "کل خدمات انجام شده",
                            value = "${metrics.completedVisits} خدمت",
                            icon = Icons.Default.DoneAll,
                            iconColor = Color(0xFF06B6D4),
                            modifier = Modifier.width(200.dp)
                        )
                        DashboardCard(
                            title = "همکاران فعال",
                            value = "${metrics.employeeCount} نفر",
                            icon = Icons.Default.Badge,
                            iconColor = Color(0xFF64748B),
                            modifier = Modifier.width(200.dp)
                        )
                    }
                }
            }
        }

        // --- Detailed Breakdown of Invoices & Consumables ---
        item {
            com.example.ui.components.EnterpriseCard(
                modifier = Modifier.testTag("granular_metrics_card"),
                contentPadding = PaddingValues(com.example.ui.theme.DesignTokens.Spacing.l)
            ) {
                com.example.ui.components.SectionHeader(
                    title = "پایش تفکیکی لوازم مصرفی و حق‌العمل",
                    icon = Icons.Default.ReceiptLong,
                    subtitle = "تحلیل سهم درآمدی شرکت و همکاران"
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(com.example.ui.theme.DesignTokens.Spacing.m))

                Column(verticalArrangement = Arrangement.spacedBy(com.example.ui.theme.DesignTokens.Spacing.s)) {
                    MetricDetailRow("مجموع بهای پایه خدمات:", metrics.serviceTotal.formatPrice(currency))
                    MetricDetailRow("مجموع هزینه لوازم مصرفی:", metrics.consumablesTotal.formatPrice(currency))
                    MetricDetailRow("لوازم مصرفی سهم شرکت (درآمد شرکت):", metrics.companyConsumables.formatPrice(currency), color = MaterialTheme.colorScheme.primary)
                    MetricDetailRow("لوازم مصرفی سهم همکار (پرداخت به همکار):", metrics.nurseConsumables.formatPrice(currency), color = Color(0xFF8B5CF6))
                    MetricDetailRow("حق‌العمل خالص مرکز (سهم درآمدی شرکت):", metrics.companyRevenue.formatPrice(currency), color = Color(0xFF10B981), isBold = true)
                    MetricDetailRow("مجموع کارمزد تعلق‌گرفته به همکاران:", metrics.nurseCommission.formatPrice(currency), color = Color(0xFF8B5CF6), isBold = true)
                }
            }
        }

        // --- Business Leaders Stats ---
        item {
            com.example.ui.components.EnterpriseCard(
                contentPadding = PaddingValues(com.example.ui.theme.DesignTokens.Spacing.l)
            ) {
                com.example.ui.components.SectionHeader(
                    title = "پیشتازان عملکرد شرکت",
                    icon = Icons.Default.Leaderboard,
                    subtitle = "شاخص برترین همکار و پرتقاضاترین خدمت"
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(com.example.ui.theme.DesignTokens.Spacing.m))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "پردرآمدترین همکار ماه:",
                            style = com.example.ui.theme.EnterpriseTypographyStyles.supportingText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(com.example.ui.theme.DesignTokens.Spacing.xs))
                        Text(
                            text = metrics.topEmployee.toPersianDigits(),
                            style = com.example.ui.theme.EnterpriseTypographyStyles.sectionHeader,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "محبوب‌ترین خدمت درخواستی:",
                            style = com.example.ui.theme.EnterpriseTypographyStyles.supportingText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(com.example.ui.theme.DesignTokens.Spacing.xs))
                        Text(
                            text = metrics.mostRequestedService.toPersianDigits(),
                            style = com.example.ui.theme.EnterpriseTypographyStyles.sectionHeader,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }

        // --- Custom Canvas Visual Chart ---
        if (dashboardTab == 0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "نمودار مقایسه‌ای سود و زیان سالانه (ماهانه‌)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "نمودار سبز: سود خالص  |  خط آبی: درآمد  |  خط قرمز: هزینه‌ها",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        // Renting some space for Custom Draw
                        CustomDashboardChart(chartPoints = chartPoints)
                    }
                }
            }
        }

        // ==========================================
        // --- TAB 1: ENTERPRISE ACTIVITY CENTER ---
        // ==========================================
        if (dashboardTab == 1) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "جستجو و فیلتر پیشرفته تاریخچه سیستمی پرسنل",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        var selectedAction by remember { mutableStateOf("همه") }
                        var userQuery by remember { mutableStateOf("") }

                        val filteredLogs = remember(auditState, selectedAction, userQuery) {
                            auditState.filter { log ->
                                (selectedAction == "همه" || log.action == selectedAction) &&
                                (userQuery.isBlank() || log.user.contains(userQuery, ignoreCase = true) || log.details.contains(userQuery, ignoreCase = true) || log.affectedModule.contains(userQuery, ignoreCase = true))
                            }
                        }

                        // Group filtered logs temporally
                        val groupedLogs = remember(filteredLogs) {
                            val today = mutableListOf<AuditLog>()
                            val yesterday = mutableListOf<AuditLog>()
                            val lastWeek = mutableListOf<AuditLog>()
                            val older = mutableListOf<AuditLog>()
                            
                            val nowTime = System.currentTimeMillis()
                            val startOfTodayTime = getStartOfDay(nowTime)
                            val startOfYesterdayTime = startOfTodayTime - 86400000
                            val startOfLastWeekTime = startOfTodayTime - (7 * 86400000)

                            filteredLogs.forEach { log ->
                                when {
                                    log.timestamp in startOfTodayTime..nowTime -> today.add(log)
                                    log.timestamp in startOfYesterdayTime until startOfTodayTime -> yesterday.add(log)
                                    log.timestamp in startOfLastWeekTime until startOfYesterdayTime -> lastWeek.add(log)
                                    else -> older.add(log)
                                }
                            }
                            
                            linkedMapOf(
                                "امروز" to today,
                                "دیروز" to yesterday,
                                "هفته گذشته" to lastWeek,
                                "پیشین" to older
                            )
                        }

                        // Action Filter Chips
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val actions = listOf("همه", "Create", "Edit", "Delete", "Restore", "Backup")
                            actions.forEach { act ->
                                val isSel = selectedAction == act
                                FilterChip(
                                    selected = isSel,
                                    onClick = { selectedAction = act },
                                    label = { Text(if (act == "همه") "همه فعالیت‌ها" else act) }
                                )
                            }
                        }
                        
                        // User filter input
                        OutlinedTextField(
                            value = userQuery,
                            onValueChange = { userQuery = it },
                            label = { Text("جستجوی مسئول ثبت، توضیحات یا بخش") },
                            placeholder = { Text("نام کاربر، ادمین، پرستار، دارو...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Render grouped activity logs
                        groupedLogs.forEach { (title, logs) ->
                            if (logs.isNotEmpty()) {
                                Text(
                                    text = "$title (${logs.size} فعالیت)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    logs.forEach { log ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(16.dp),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        val actionColor = when (log.action) {
                                                            "Create" -> Color(0xFF16A34A)
                                                            "Edit" -> Color(0xFF2563EB)
                                                            "Delete" -> Color(0xFFDC2626)
                                                            else -> Color(0xFF4B5563)
                                                        }
                                                        Box(
                                                            modifier = Modifier
                                                                .size(8.dp)
                                                                .background(actionColor, shape = RoundedCornerShape(4.dp))
                                                        )
                                                        Text(
                                                            text = "اقدام: ${log.action}",
                                                            fontWeight = FontWeight.Bold,
                                                            color = actionColor,
                                                            style = MaterialTheme.typography.bodyMedium
                                                        )
                                                    }
                                                    val formattedTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(log.timestamp)).toPersianDigits()
                                                    Text(
                                                        text = "${log.timestamp.formatDate()} ساعت $formattedTime",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.Gray
                                                    )
                                                }
                                                Text("جزئیات: ${log.details}", style = MaterialTheme.typography.bodyMedium)
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("بخش مربوطه: ${log.affectedModule}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                                    Text("مسئول: ${log.user} (${log.device})", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
        // --- TAB 2: ENTERPRISE NOTIFICATION CENTER (INBOX) ---
        // ==========================================
        if (dashboardTab == 2) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "صندوق پیام‌ها و اعلان‌های سیستم",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (activeAlerts.isNotEmpty()) {
                                TextButton(onClick = { viewModel.dismissAllAlerts() }) {
                                    Text("پاکسازی صندوق", color = Color(0xFFDC2626))
                                }
                            }
                        }
                        
                        if (activeAlerts.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.MailOutline, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                                    Text("صندوق ورودی شما خالی است.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                activeAlerts.forEach { alert ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Icon(Icons.Default.NotificationImportant, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(20.dp))
                                                    Text(alert.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                }
                                                val formattedTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(alert.timestamp)).toPersianDigits()
                                                Text(
                                                    text = "${alert.timestamp.formatDate()} ساعت $formattedTime",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.Gray
                                                )
                                            }
                                            Text(alert.description, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                                            
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = { viewModel.handleDeepLink(alert.relatedScreen) },
                                                    modifier = Modifier.weight(1f).height(32.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("مشاهده و اقدام", style = MaterialTheme.typography.labelSmall)
                                                }
                                                Button(
                                                    onClick = { viewModel.markAlertAsRead(alert) },
                                                    modifier = Modifier.weight(1f).height(32.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("علامت خوانده", style = MaterialTheme.typography.labelSmall)
                                                }
                                                Button(
                                                    onClick = { viewModel.dismissAlert(alert) },
                                                    modifier = Modifier.weight(0.8f).height(32.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("حذف", style = MaterialTheme.typography.labelSmall)
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
    }
    }

    // --- FLOATING QUICK ACTIONS PANEL (Enterprise UX) ---
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(16.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isQuickActionsExpanded) {
                // Action 1: ثبت بیمار جدید
                FloatingActionLabelItem(
                    text = "ثبت بیمار جدید",
                    icon = Icons.Default.PersonAdd,
                    onClick = {
                        isQuickActionsExpanded = false
                        viewModel.handleDeepLink("patients?tab=add_patient")
                    }
                )
                // Action 2: ثبت خدمت جدید
                FloatingActionLabelItem(
                    text = "ثبت خدمت جدید",
                    icon = Icons.Default.PostAdd,
                    onClick = {
                        isQuickActionsExpanded = false
                        onNavigateToRegister()
                    }
                )
                // Action 3: ثبت تراکنش مالی جدید
                FloatingActionLabelItem(
                    text = "ثبت تراکنش مالی",
                    icon = Icons.Default.Payments,
                    onClick = {
                        isQuickActionsExpanded = false
                        viewModel.handleDeepLink("accounting?tab=add")
                    }
                )
                // Action 4: برنامه‌ریزی نوبت خدمت
                FloatingActionLabelItem(
                    text = "برنامه‌ریزی نوبت",
                    icon = Icons.Default.Event,
                    onClick = {
                        isQuickActionsExpanded = false
                        onNavigateToRegister()
                    }
                )
            }

            // Main Toggle FAB with rotate transition
            FloatingActionButton(
                onClick = { isQuickActionsExpanded = !isQuickActionsExpanded },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.size(56.dp).testTag("quick_actions_fab")
            ) {
                Icon(
                    imageVector = if (isQuickActionsExpanded) Icons.Default.Close else Icons.Default.Bolt,
                    contentDescription = "دسترسی سریع",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    // ==========================================
    // --- DIALOG 1: MAIN DRILL-DOWN / TRANSPARENCY DIALOG ---
    // ==========================================
    if (selectedDrillDownType != null) {
        val info = transparencyInfo
        AlertDialog(
            onDismissRequest = { selectedDrillDownType = null },
            confirmButton = {
                TextButton(onClick = { selectedDrillDownType = null }) {
                    Text("بستن")
                }
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = info?.metricName ?: "ریشه‌یابی و شفافیت مالی",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("حالت پیشرفته", style = MaterialTheme.typography.labelMedium)
                        Switch(
                            checked = isAdvancedInvestigation,
                            onCheckedChange = { isAdvancedInvestigation = it }
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                        .testTag("drill_down_dialog_content"),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // --- Financial Transparency & Audit Stats ---
                    if (info != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().testTag("transparency_card")
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "فرمول محاسباتی:",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = info.formula,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "جداول پایگاه داده مبدا:",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = info.tables,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("تعداد اسناد موثر: ${info.recordsCount}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text("مجموع ناخالص: ${info.totalAmount.formatPrice(currency)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Text(
                        text = "اسناد و تراکنش‌های جزئی مبدا:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )

                    if (contributingRecords.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("هیچ سند موثری یافت نشد. مقادیر کاملاً صفر هستند.", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("contributing_records_list"),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(contributingRecords) { rec ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().testTag("contributing_record_item_${rec.id}"),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = rec.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = rec.amount.formatPrice(currency),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (rec.amount >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("شناسه سند: #${rec.id} (${rec.sourceModule})", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                            Text("تاریخ: ${rec.date.formatDate()}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        }

                                        if (isAdvancedInvestigation) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("دسته‌بندی: ${rec.category}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                                Text("وضعیت: ${rec.status}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Button(
                                                    onClick = { detailViewObject = rec.originalObject },
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.testTag("btn_detail_${rec.id}")
                                                ) {
                                                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("جزئیات", style = MaterialTheme.typography.labelSmall)
                                                }

                                                Button(
                                                    onClick = { editViewObject = rec.originalObject },
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.testTag("btn_edit_${rec.id}")
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("ویرایش", style = MaterialTheme.typography.labelSmall)
                                                }

                                                Button(
                                                    onClick = { deleteViewObject = rec.originalObject },
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.testTag("btn_delete_${rec.id}")
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("حذف", style = MaterialTheme.typography.labelSmall)
                                                }

                                                IconButton(
                                                    onClick = { historyViewObject = rec.originalObject },
                                                    modifier = Modifier.size(32.dp).testTag("btn_history_${rec.id}")
                                                ) {
                                                    Icon(Icons.Default.History, contentDescription = "تاریخچه تغییرات", tint = MaterialTheme.colorScheme.primary)
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
        )
    }

    // ==========================================
    // --- DIALOG 2: DETAILED VIEW DIALOG ---
    // ==========================================
    if (detailViewObject != null) {
        val obj = detailViewObject!!
        AlertDialog(
            onDismissRequest = { detailViewObject = null },
            confirmButton = {
                TextButton(onClick = { detailViewObject = null }) {
                    Text("بستن")
                }
            },
            title = {
                Text("جزئیات کامل سند مالی", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (obj) {
                        is ServiceRegistration -> {
                            val pName = patientsState.find { it.id == obj.patientId }?.fullName ?: "نامشخص"
                            val sName = servicesState.find { it.id == obj.serviceId }?.name ?: "نامشخص"
                            val eName = employeesState.find { it.id == obj.employeeId }?.fullName ?: "نامشخص"
                            DetailField("نوع سند:", "ثبت خدمت مرکز درمان")
                            DetailField("شناسه سند:", "#${obj.id}")
                            DetailField("بیمار:", pName)
                            DetailField("خدمت:", sName)
                            DetailField("همکار پرستار:", eName)
                            DetailField("تاریخ ثبت:", obj.dateTime.formatDateTime())
                            DetailField("قیمت پایه خدمات:", obj.sellingPrice.formatPrice(currency))
                            DetailField("هزینه لوازم مصرفی:", obj.otherCosts.formatPrice(currency))
                            DetailField("سهم لوازم همکار:", obj.employeeCost.formatPrice(currency))
                            DetailField("هزینه ایاب ذهاب:", obj.transportationCost.formatPrice(currency))
                            DetailField("تخفیف:", obj.discount.formatPrice(currency))
                            DetailField("مبلغ نهایی دریافتی:", obj.finalPrice.formatPrice(currency))
                            DetailField("حق‌العمل خالص همکار:", obj.employeeCommission.formatPrice(currency))
                            DetailField("سود خالص شرکت:", obj.companyProfit.formatPrice(currency))
                            DetailField("روش پرداخت:", obj.paymentMethod)
                            DetailField("وضعیت تایید فاکتور:", obj.workflowStatus)
                            DetailField("وضعیت پرداخت:", if (obj.isPaid) "پرداخت شده" else "پرداخت نشده")
                            DetailField("وضعیت حذف:", if (obj.isDeleted) "حذف شده (سافت دیلیت)" else "فعال")
                            DetailField("صندوق تراکنش:", obj.cashboxId?.let { "صندوق #$it" } ?: "نامشخص")
                            DetailField("یادداشت:", obj.notes)
                        }
                        is Expense -> {
                            DetailField("نوع سند:", "هزینه ثبت‌شده شرکت")
                            DetailField("شناسه سند:", "#${obj.id}")
                            DetailField("عنوان هزینه:", obj.title)
                            DetailField("دسته‌بندی هزینه:", obj.category)
                            DetailField("مبلغ هزینه:", obj.amount.formatPrice(currency))
                            DetailField("تاریخ فاکتور:", obj.registrationDate.formatDate())
                            DetailField("تاریخ پرداخت:", obj.paymentDate.formatDate())
                            DetailField("روش پرداخت:", obj.paymentMethod)
                            DetailField("توضیحات:", obj.description)
                            DetailField("ثبت‌کننده سند:", obj.submitterName)
                            DetailField("وضعیت تایید:", obj.workflowStatus)
                            DetailField("وضعیت حذف:", if (obj.isDeleted) "حذف شده (سافت دیلیت)" else "فعال")
                        }
                        is FinancialTransaction -> {
                            DetailField("نوع سند:", "تراکنش مالی دستی دفتر")
                            DetailField("شناسه سند:", "#${obj.id}")
                            DetailField("نوع تراکنش:", obj.type)
                            DetailField("دسته‌بندی:", obj.category)
                            DetailField("مبلغ:", obj.amount.formatPrice(currency))
                            DetailField("تاریخ تراکنش:", obj.date.formatDateTime())
                            DetailField("شرح تراکنش:", obj.description)
                            DetailField("روش پرداخت/دریافت:", obj.paymentMethod)
                            DetailField("وضعیت تسویه کارمزد:", if (obj.isCleared) "تسویه شده" else "تسویه نشده")
                            DetailField("شناسه سند مرجع:", obj.referenceId?.let { "#$it" } ?: "فاقد مرجع (تراکنش دستی)")
                        }
                    }
                }
            }
        )
    }

    // ==========================================
    // --- DIALOG 3: INLINE EDIT DIALOG ---
    // ==========================================
    if (editViewObject != null) {
        val obj = editViewObject!!
        var reasonEdit by remember { mutableStateOf("") }
        var commentEdit by remember { mutableStateOf("") }

        // Form Fields
        var titleField by remember(obj) {
            mutableStateOf(
                when (obj) {
                    is Expense -> obj.title
                    is FinancialTransaction -> obj.description
                    else -> ""
                }
            )
        }
        var amountField by remember(obj) {
            mutableStateOf(
                when (obj) {
                    is ServiceRegistration -> obj.sellingPrice.toString()
                    is Expense -> obj.amount.toString()
                    is FinancialTransaction -> obj.amount.toString()
                    else -> "0"
                }
            )
        }
        var otherCostsField by remember(obj) {
            mutableStateOf(
                when (obj) {
                    is ServiceRegistration -> obj.otherCosts.toString()
                    else -> "0"
                }
            )
        }
        var discountField by remember(obj) {
            mutableStateOf(
                when (obj) {
                    is ServiceRegistration -> obj.discount.toString()
                    else -> "0"
                }
            )
        }
        var notesField by remember(obj) {
            mutableStateOf(
                when (obj) {
                    is ServiceRegistration -> obj.notes
                    is Expense -> obj.description
                    is FinancialTransaction -> obj.description
                    else -> ""
                }
            )
        }
        var payMethodField by remember(obj) {
            mutableStateOf(
                when (obj) {
                    is ServiceRegistration -> obj.paymentMethod
                    is Expense -> obj.paymentMethod
                    is FinancialTransaction -> obj.paymentMethod
                    else -> "نقدی"
                }
            )
        }
        var isPaidField by remember(obj) {
            mutableStateOf(
                when (obj) {
                    is ServiceRegistration -> obj.isPaid
                    is FinancialTransaction -> obj.isCleared
                    else -> true
                }
            )
        }
        var categoryField by remember(obj) {
            mutableStateOf(
                when (obj) {
                    is Expense -> obj.category
                    is FinancialTransaction -> obj.category
                    else -> ""
                }
            )
        }
        var statusField by remember(obj) {
            mutableStateOf(
                when (obj) {
                    is ServiceRegistration -> obj.workflowStatus
                    is Expense -> obj.workflowStatus
                    else -> "Approved"
                }
            )
        }

        AlertDialog(
            onDismissRequest = { editViewObject = null },
            confirmButton = {
                Button(
                    modifier = Modifier.testTag("btn_save_edit"),
                    onClick = {
                        val amt = amountField.toDoubleOrNull() ?: 0.0
                        when (obj) {
                            is ServiceRegistration -> {
                                val oCosts = otherCostsField.toDoubleOrNull() ?: 0.0
                                val disc = discountField.toDoubleOrNull() ?: 0.0
                                viewModel.editServiceRegistration(
                                    id = obj.id,
                                    patientId = obj.patientId,
                                    serviceId = obj.serviceId,
                                    employeeId = obj.employeeId,
                                    dateTime = obj.dateTime,
                                    sellingPrice = amt,
                                    employeeCost = obj.employeeCost,
                                    transportationCost = obj.transportationCost,
                                    otherCosts = oCosts,
                                    discount = disc,
                                    paymentMethod = payMethodField,
                                    invoiceNumber = obj.invoiceNumber,
                                    notes = notesField,
                                    selectedCashboxId = obj.cashboxId,
                                    isPaid = isPaidField,
                                    selectedServices = emptyList(),
                                    consumablesOwner = obj.consumablesOwner,
                                    reason = if (reasonEdit.isNotEmpty()) reasonEdit else "اصلاح ثبت اطلاعات",
                                    comment = commentEdit
                                )
                            }
                            is Expense -> {
                                val updatedExp = obj.copy(
                                    title = titleField,
                                    category = categoryField,
                                    amount = amt,
                                    paymentMethod = payMethodField,
                                    description = notesField,
                                    workflowStatus = statusField
                                )
                                viewModel.saveExpense(updatedExp, reasonEdit, commentEdit)
                            }
                            is FinancialTransaction -> {
                                val updatedTx = obj.copy(
                                    amount = amt,
                                    description = notesField,
                                    category = categoryField,
                                    paymentMethod = payMethodField,
                                    isCleared = isPaidField
                                )
                                viewModel.updateFinancialTransaction(updatedTx)
                            }
                        }
                        editViewObject = null
                    }
                ) {
                    Text("ذخیره تغییرات")
                }
            },
            dismissButton = {
                TextButton(onClick = { editViewObject = null }) {
                    Text("انصراف")
                }
            },
            title = {
                Text("ویرایش سریع سند مالی", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("تغییر مستقیم مقادیر و مبالغ سند:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                    if (obj is Expense) {
                        OutlinedTextField(
                            value = titleField,
                            onValueChange = { titleField = it },
                            label = { Text("عنوان هزینه") },
                            modifier = Modifier.fillMaxWidth().testTag("edit_title_field")
                        )
                    }

                    if (obj is Expense || obj is FinancialTransaction) {
                        OutlinedTextField(
                            value = categoryField,
                            onValueChange = { categoryField = it },
                            label = { Text("دسته‌بندی") },
                            modifier = Modifier.fillMaxWidth().testTag("edit_category_field")
                        )
                    }

                    OutlinedTextField(
                        value = amountField,
                        onValueChange = { amountField = it },
                        label = {
                            Text(
                                if (obj is ServiceRegistration) "قیمت پایه خدمات (سهم همکار)"
                                else "مبلغ کل"
                            )
                        },
                        modifier = Modifier.fillMaxWidth().testTag("edit_amount_field")
                    )

                    if (obj is ServiceRegistration) {
                        OutlinedTextField(
                            value = otherCostsField,
                            onValueChange = { otherCostsField = it },
                            label = { Text("هزینه لوازم مصرفی") },
                            modifier = Modifier.fillMaxWidth().testTag("edit_other_costs_field")
                        )

                        OutlinedTextField(
                            value = discountField,
                            onValueChange = { discountField = it },
                            label = { Text("تخفیف") },
                            modifier = Modifier.fillMaxWidth().testTag("edit_discount_field")
                        )
                    }

                    OutlinedTextField(
                        value = payMethodField,
                        onValueChange = { payMethodField = it },
                        label = { Text("روش پرداخت") },
                        modifier = Modifier.fillMaxWidth().testTag("edit_payment_method_field")
                    )

                    OutlinedTextField(
                        value = notesField,
                        onValueChange = { notesField = it },
                        label = { Text("شرح / توضیحات") },
                        modifier = Modifier.fillMaxWidth().testTag("edit_notes_field")
                    )

                    if (obj is ServiceRegistration || obj is Expense) {
                        OutlinedTextField(
                            value = statusField,
                            onValueChange = { statusField = it },
                            label = { Text("وضعیت سند (Approved / Submitted)") },
                            modifier = Modifier.fillMaxWidth().testTag("edit_status_field")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isPaidField,
                            onCheckedChange = { isPaidField = it },
                            modifier = Modifier.testTag("edit_checkbox_paid")
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (obj is ServiceRegistration) "پرداخت شده توسط بیمار"
                            else "وضعیت تسویه / تایید شده"
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(6.dp))

                    Text("علت اصلاح و ثبت ممیزی (جهت ممیزی در تاریخچه):", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    OutlinedTextField(
                        value = reasonEdit,
                        onValueChange = { reasonEdit = it },
                        label = { Text("علت اصلاح فاکتور (مانند: تعدیل توافقی)") },
                        modifier = Modifier.fillMaxWidth().testTag("edit_reason_field")
                    )
                    OutlinedTextField(
                        value = commentEdit,
                        onValueChange = { commentEdit = it },
                        label = { Text("شرح ممیزی اصلاحیه") },
                        modifier = Modifier.fillMaxWidth().testTag("edit_comment_field")
                    )
                }
            }
        )
    }

    // ==========================================
    // --- DIALOG 4: DELETE CONFIRMATION ---
    // ==========================================
    if (deleteViewObject != null) {
        val obj = deleteViewObject!!
        AlertDialog(
            onDismissRequest = { deleteViewObject = null },
            confirmButton = {
                Button(
                    modifier = Modifier.testTag("btn_confirm_delete"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        when (obj) {
                            is ServiceRegistration -> viewModel.deleteRegistration(obj)
                            is Expense -> viewModel.deleteExpense(obj)
                            is FinancialTransaction -> viewModel.deleteFinancialTransaction(obj)
                        }
                        deleteViewObject = null
                    }
                ) {
                    Text("بله، حذف شود")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteViewObject = null }) {
                    Text("انصراف")
                }
            },
            title = {
                Text("تایید حذف دائمی/نرم‌افزاری سند", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("آیا از حذف این سند مالی اطمینان کامل دارید؟ این اقدام بلافاصله ترازهای مالی دفتر و تراکنش‌های بانکی مرتبط را دگرگون و اصلاح می‌کند.")
            }
        )
    }

    // ==========================================
    // --- DIALOG 5: VIEW HISTORIC AUDIT LOGS ---
    // ==========================================
    if (historyViewObject != null) {
        val obj = historyViewObject!!
        val filteredAudits = remember(obj, auditState) {
            val targetId = when (obj) {
                is ServiceRegistration -> obj.id
                is Expense -> obj.id
                is FinancialTransaction -> obj.id
                else -> 0
            }
            val moduleName = when (obj) {
                is ServiceRegistration -> "ServiceRegistrations"
                is Expense -> "Expenses"
                else -> "FinancialTransactions"
            }
            auditState.filter {
                it.affectedModule.equals(moduleName, ignoreCase = true) &&
                (it.details.contains("شناسه: $targetId") || it.details.contains("#$targetId") || it.details.contains("کد: $targetId") || it.details.contains("سند: $targetId") || it.details.contains("هزینه: ${if(obj is Expense) obj.title else ""}") || it.details.contains("خدمت: ${if(obj is ServiceRegistration) obj.id else ""}"))
            }
        }

        val filteredHistories = remember(obj, historiesState) {
            val targetId = when (obj) {
                is ServiceRegistration -> obj.id
                is Expense -> obj.id
                is FinancialTransaction -> obj.id
                else -> 0
            }
            val recordType = when (obj) {
                is ServiceRegistration -> "ServiceRegistration"
                is Expense -> "Expense"
                else -> "FinancialTransaction"
            }
            historiesState.filter {
                it.entityId == targetId && it.entityType.equals(recordType, ignoreCase = true)
            }
        }

        AlertDialog(
            onDismissRequest = { historyViewObject = null },
            confirmButton = {
                TextButton(onClick = { historyViewObject = null }) {
                    Text("بستن")
                }
            },
            title = {
                Text("تاریخچه تغییرات و ممیزی سند", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("رویدادهای ثبت شده بابت سند جاری:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                    if (filteredAudits.isEmpty() && filteredHistories.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("هیچ لاگ ممیزی یا سابقه تغییراتی برای این سند پیدا نشد.", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f).testTag("history_logs_list"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredHistories) { hist ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("اصلاح سند توسط ${hist.editedBy}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("مقدار قدیم: ${hist.previousValue} ➔ مقدار جدید: ${hist.newValue}", style = MaterialTheme.typography.bodySmall)
                                        Text("علت تغییر: ${hist.reason}", style = MaterialTheme.typography.bodySmall)
                                        Text("توضیح: ${hist.comment}", style = MaterialTheme.typography.bodySmall)
                                        Text("زمان ثبت: ${hist.timestamp.formatDateTime()}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                }
                            }

                            items(filteredAudits) { audit ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("ممیزی سیستم: ${audit.action}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(audit.details, style = MaterialTheme.typography.bodySmall)
                                        Text("زمان ثبت ممیزی: ${audit.timestamp.formatDateTime()}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    if (showAllAlertsDialog) {
        Dialog(onDismissRequest = { showAllAlertsDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "هشدارهای پایش بالینی بیمار",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = { viewModel.clearAllAlerts() }) {
                            Text("حذف همه", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(activeAlerts) { alert ->
                            EnterpriseAlertItem(
                                alert = alert,
                                viewModel = viewModel,
                                onNavigate = { 
                                    showAllAlertsDialog = false
                                    viewModel.handleDeepLink(alert.relatedScreen) 
                                },
                                onMarkRead = { viewModel.markAlertAsRead(alert) },
                                onDismiss = { viewModel.dismissAlert(alert) }
                            )
                        }
                    }

                    Button(
                        onClick = { showAllAlertsDialog = false },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("بستن")
                    }
                }
            }
        }
    }
}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) {
        if (onLongClick != null) {
            Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
        } else {
            Modifier.clickable(onClick = onClick)
        }
    } else {
        Modifier
    }

    Card(
        modifier = modifier.then(clickableModifier),
        shape = RoundedCornerShape(com.example.ui.theme.DesignTokens.Radius.l),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = com.example.ui.theme.DesignTokens.Elevation.low)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(com.example.ui.theme.DesignTokens.Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = com.example.ui.theme.EnterpriseTypographyStyles.supportingText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(com.example.ui.theme.DesignTokens.Spacing.xs))
                Text(
                    text = value.toPersianDigits(),
                    style = com.example.ui.theme.EnterpriseTypographyStyles.kpiValue,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!subtitle.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(com.example.ui.theme.DesignTokens.Spacing.xs))
                    Text(
                        text = subtitle.toPersianDigits(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                    )
                }
            }
            Spacer(modifier = Modifier.width(com.example.ui.theme.DesignTokens.Spacing.s))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(com.example.ui.theme.DesignTokens.Radius.m))
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun DetailField(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CustomDashboardChart(
    chartPoints: List<ChartPoint>,
    modifier: Modifier = Modifier
) {
    if (chartPoints.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("اطلاعات تراکنش مالی موجود نیست.", color = Color.Gray)
        }
        return
    }

    val maxAmount = chartPoints.maxOfOrNull { maxOf(it.income, it.expense, 1000.0) } ?: 1000.0

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = 8.dp)
    ) {
        val width = size.width
        val height = size.height
        val pointCount = chartPoints.size
        val stepX = width / (pointCount - 1).coerceAtLeast(1)

        val incomePoints = mutableListOf<Offset>()
        val expensePoints = mutableListOf<Offset>()

        // Generate points
        chartPoints.forEachIndexed { idx, point ->
            val x = idx * stepX
            val yIncome = height - (point.income / maxAmount * height).toFloat()
            val yExpense = height - (point.expense / maxAmount * height).toFloat()
            incomePoints.add(Offset(x, yIncome))
            expensePoints.add(Offset(x, yExpense))
        }

        // Draw helper Grid Lines
        for (i in 1..4) {
            val y = height * (i / 5f)
            drawLine(
                color = Color.LightGray.copy(alpha = 0.5f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
        }

        // Draw Income Fill with Gradient
        if (incomePoints.size > 1) {
            val incomeFillPath = Path().apply {
                moveTo(incomePoints.first().x, height)
                incomePoints.forEach { lineTo(it.x, it.y) }
                lineTo(incomePoints.last().x, height)
                close()
            }
            drawPath(
                path = incomeFillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F766E).copy(alpha = 0.25f),
                        Color(0xFF0F766E).copy(alpha = 0.0f)
                    ),
                    startY = 0f,
                    endY = height
                )
            )
        }

        // Draw Expense Fill with Gradient
        if (expensePoints.size > 1) {
            val expenseFillPath = Path().apply {
                moveTo(expensePoints.first().x, height)
                expensePoints.forEach { lineTo(it.x, it.y) }
                lineTo(expensePoints.last().x, height)
                close()
            }
            drawPath(
                path = expenseFillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFDC2626).copy(alpha = 0.15f),
                        Color(0xFFDC2626).copy(alpha = 0.0f)
                    ),
                    startY = 0f,
                    endY = height
                )
            )
        }

        // Draw Income Line (Deep Teal / HamrahanPrimary)
        val incomePath = Path().apply {
            incomePoints.forEachIndexed { index, offset ->
                if (index == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y)
            }
        }
        drawPath(
            path = incomePath,
            color = Color(0xFF0F766E),
            style = Stroke(width = 4f)
        )

        // Draw Expense Line (Danger Red / HamrahanDanger)
        val expensePath = Path().apply {
            expensePoints.forEachIndexed { index, offset ->
                if (index == 0) moveTo(offset.x, offset.y) else lineTo(offset.x, offset.y)
            }
        }
        drawPath(
            path = expensePath,
            color = Color(0xFFDC2626),
            style = Stroke(width = 4f)
        )

        // Draw points on joints to make it feel premium
        incomePoints.forEach { point ->
            drawCircle(
                color = Color(0xFF0F766E),
                radius = 6f,
                center = point
            )
            drawCircle(
                color = Color.White,
                radius = 3f,
                center = point
            )
        }

        expensePoints.forEach { point ->
            drawCircle(
                color = Color(0xFFDC2626),
                radius = 6f,
                center = point
            )
            drawCircle(
                color = Color.White,
                radius = 3f,
                center = point
            )
        }

        // Draw bar fills for Profits
        chartPoints.forEachIndexed { idx, point ->
            val x = idx * stepX
            if (point.profit > 0) {
                // Draw a small indicator of profit (Success Green)
                drawCircle(
                    color = Color(0xFF22C55E),
                    radius = 8f,
                    center = Offset(x, height - 10f)
                )
                drawCircle(
                    color = Color.White,
                    radius = 4f,
                    center = Offset(x, height - 10f)
                )
            }
        }
    }
}

@Composable
fun MetricDetailRow(
    label: String,
    value: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = color
        )
    }
}

@Composable
fun EnterpriseAlertItem(
    alert: Alert,
    viewModel: HamrahanViewModel,
    onNavigate: () -> Unit,
    onMarkRead: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val config = when (alert.type) {
        "staff_profile_incomplete" -> Quadruple(Color(0xFFDC2626), Color(0xFFFEE2E2), "🔴 بحرانی", Icons.Default.Badge)
        "contract_pending" -> Quadruple(Color(0xFFD97706), Color(0xFFFEF3C7), "⚠️ نیازمند بررسی", Icons.Default.Assignment)
        "scheduled_service_today" -> Quadruple(Color(0xFF2563EB), Color(0xFFDBEAFE), "📅 مأموریت امروز", Icons.Default.Event)
        "accounting_approval_required" -> Quadruple(Color(0xFF0D9488), Color(0xFFCCFBF1), "💳 تایید مالی", Icons.Default.Payments)
        "patient_consent_missing" -> Quadruple(Color(0xFFDC2626), Color(0xFFFEE2E2), "🔴 بحرانی", Icons.Default.FactCheck)
        "nursing_report_pending" -> Quadruple(Color(0xFFD97706), Color(0xFFFEF3C7), "⚠️ نیاز به تایید", Icons.Default.MedicalServices)
        "financial_document_missing" -> Quadruple(Color(0xFFE11D48), Color(0xFFFFE4E6), "🚨 مفقودی سند", Icons.Default.AttachFile)
        "expired_contract" -> Quadruple(Color(0xFFDC2626), Color(0xFFFEE2E2), "🔴 منقضی شده", Icons.Default.AssignmentLate)
        "medication_review_required" -> Quadruple(Color(0xFF9333EA), Color(0xFFF3E8FF), "💊 پایش دارویی", Icons.Default.Medication)
        "unpaid_commission" -> Quadruple(Color(0xFF16A34A), Color(0xFFDCFCE7), "💰 تسویه حساب", Icons.Default.AccountBalanceWallet)
        else -> Quadruple(Color(0xFF4B5563), Color(0xFFF3F4F6), "اطلاع‌رسانی", Icons.Default.Notifications)
    }
    val priorityColor = config.first
    val priorityBg = config.second
    val priorityLabel = config.third
    val icon = config.fourth

    var isExpanded by remember { mutableStateOf(false) }
    var inlineProgress by remember { mutableStateOf(0.6f) }
    var simulateUploadedFile by remember { mutableStateOf(false) }

    var countdownSeconds by remember { mutableStateOf(5700) }
    LaunchedEffect(alert.type) {
        if (alert.type == "scheduled_service_today") {
            while (countdownSeconds > 0) {
                kotlinx.coroutines.delay(1000)
                countdownSeconds--
            }
        }
    }

    val formatTime = { seconds: Int ->
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        String.format("%02d:%02d:%02d", h, m, s)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, priorityColor.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(priorityBg, shape = RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = priorityColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = alert.title,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = priorityBg,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = priorityLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = priorityColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "همین حالا",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onMarkRead,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.DoneAll,
                            contentDescription = "خوانده شده",
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "رد کردن",
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "تغییر وضعیت جزئیات",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = alert.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 2.dp)
            )

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                when (alert.type) {
                    "staff_profile_incomplete" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (simulateUploadedFile) "۱۰۰٪ تکمیل" else "۶۰٪ تکمیل (عکس پرسنلی مفقود)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (simulateUploadedFile) Color(0xFF16A34A) else priorityColor
                                )
                            }
                            LinearProgressIndicator(
                                progress = if (simulateUploadedFile) 1f else inlineProgress,
                                color = if (simulateUploadedFile) Color(0xFF16A34A) else priorityColor,
                                trackColor = priorityColor.copy(alpha = 0.1f),
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    "scheduled_service_today" -> {
                        Surface(
                            color = Color(0xFFEFF6FF),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = priorityColor, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("شروع تا:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = priorityColor)
                                }
                                Text(
                                    text = formatTime(countdownSeconds),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = priorityColor
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                MetricDetailRow(label = "ساعت مأموریت", value = "۱۰:۳۰ صبح", isBold = true)
                                MetricDetailRow(label = "درمانگر مجری", value = "علی رضایی", isBold = true)
                                MetricDetailRow(label = "نوع خدمت", value = "سرم‌تراپی و پایش", isBold = true)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    "expired_contract" -> {
                        Surface(
                            color = Color(0xFFFFF1F2),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = priorityColor, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("وضعیت انقضا:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = priorityColor)
                                }
                                Text(
                                    text = "۳ روز از انقضا گذشته",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = priorityColor
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    "accounting_approval_required" -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                MetricDetailRow(label = "شماره سند هزینه", value = "#EXP-482", isBold = true)
                                MetricDetailRow(label = "مبلغ هزینه", value = "۲,۵۰۰,۰۰۰ ریال", isBold = true)
                                MetricDetailRow(label = "ثبت‌کننده", value = "امور مالی", isBold = true)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (alert.type) {
                        "staff_profile_incomplete" -> {
                            Button(
                                onClick = { 
                                    simulateUploadedFile = true
                                    inlineProgress = 1.0f
                                    viewModel.resolveAlertInline(context, alert, "approve")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = priorityColor),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Publish, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("آپلود سریع عکس", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { viewModel.resolveAlertInline(context, alert, "request_revision") },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.DarkGray),
                                border = BorderStroke(1.dp, Color.LightGray),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("درخواست اصلاح", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        "contract_pending" -> {
                            Button(
                                onClick = { viewModel.resolveAlertInline(context, alert, "approve") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("تایید نهایی", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { viewModel.resolveAlertInline(context, alert, "reject") },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                border = BorderStroke(1.dp, Color(0xFFFEE2E2)),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("رد قرارداد", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { onNavigate() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3F4F6), contentColor = Color.DarkGray),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("مشاهده قرارداد", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        "scheduled_service_today" -> {
                            Button(
                                onClick = { viewModel.resolveAlertInline(context, alert, "start") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("شروع خدمت", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { viewModel.resolveAlertInline(context, alert, "report") },
                                colors = ButtonDefaults.buttonColors(containerColor = priorityColor),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ثبت گزارش", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { onNavigate() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3F4F6), contentColor = Color.DarkGray),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("مشاهده بیمار", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        "accounting_approval_required" -> {
                            Button(
                                onClick = { viewModel.resolveAlertInline(context, alert, "approve") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("تایید پرداخت", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { viewModel.resolveAlertInline(context, alert, "reject") },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                border = BorderStroke(1.dp, Color(0xFFFEE2E2)),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("ابطال سند", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        "patient_consent_missing" -> {
                            Button(
                                onClick = { viewModel.resolveAlertInline(context, alert, "approve") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("اخذ رضایت بالینی", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { viewModel.resolveAlertInline(context, alert, "request_signature") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3F4F6), contentColor = Color.DarkGray),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("ارسال پیامک امضا", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        "nursing_report_pending" -> {
                            Button(
                                onClick = { viewModel.resolveAlertInline(context, alert, "approve") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("تایید سوپروایزر", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { viewModel.resolveAlertInline(context, alert, "reject") },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                border = BorderStroke(1.dp, Color(0xFFFEE2E2)),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("رد و اصلاح", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        "financial_document_missing" -> {
                            Button(
                                onClick = { viewModel.resolveAlertInline(context, alert, "approve") },
                                colors = ButtonDefaults.buttonColors(containerColor = priorityColor),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("آپلود تصویر فاکتور", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { viewModel.resolveAlertInline(context, alert, "bypass") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3F4F6), contentColor = Color.DarkGray),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("تایید دستی بدون سند", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        "expired_contract" -> {
                            Button(
                                onClick = { viewModel.resolveAlertInline(context, alert, "approve") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Loop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("تمدید خودکار ۱ ساله", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { viewModel.resolveAlertInline(context, alert, "reject") },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                border = BorderStroke(1.dp, Color(0xFFFEE2E2)),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("لغو همکاری پرسنلی", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        "medication_review_required" -> {
                            Button(
                                onClick = { viewModel.resolveAlertInline(context, alert, "approve") },
                                colors = ButtonDefaults.buttonColors(containerColor = priorityColor),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.MedicalServices, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("بررسی تداخلات و تایید داروها", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        "unpaid_commission" -> {
                            Button(
                                onClick = { viewModel.resolveAlertInline(context, alert, "approve") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("پرداخت و تسویه پایا", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { viewModel.resolveAlertInline(context, alert, "cash_pay") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEFF6FF), contentColor = Color(0xFF2563EB)),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("سند پرداخت دستی", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        else -> {
                            Button(
                                onClick = onNavigate,
                                colors = ButtonDefaults.buttonColors(containerColor = priorityColor),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("اقدام سریع", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KPIBlock(label: String, count: Int, color: Color, bg: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color.copy(alpha = 0.8f))
        }
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun FloatingActionLabelItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        FloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.size(40.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}
