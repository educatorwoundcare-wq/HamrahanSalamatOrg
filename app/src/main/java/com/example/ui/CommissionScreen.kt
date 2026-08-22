package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.Cashbox
import com.example.data.Employee
import com.example.data.CommissionSettlement
import com.example.data.Referral
import com.example.data.ReferralCommission
import com.example.ui.components.*
import com.example.ui.theme.DesignTokens
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommissionScreen(viewModel: HamrahanViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val employees by viewModel.employees.collectAsState()
    val registrations by viewModel.registrations.collectAsState()
    val settlements by viewModel.settlements.collectAsState()
    val cashboxes by viewModel.cashboxes.collectAsState()
    val currency by viewModel.defaultCurrency.collectAsState()

    // Referral System State Flows
    val referrals by viewModel.referrals.collectAsState()
    val referralCommissions by viewModel.referralCommissions.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: Personnel Settlements, 1: Referral Management

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("مدیریت مالی کارمزد و پورسانت", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("امور تسویه حساب کادر درمان و اشخاص معرف", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        modifier = Modifier.testTag("commission_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Material 3 Primary Navigation Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("تسویه حساب همکاران دفتری", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Diversity3, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("پورسانت پزشکان و معرفین", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Percent, contentDescription = null) }
                )
            }

            if (selectedTab == 0) {
                PersonnelCommissionTab(
                    viewModel = viewModel,
                    employees = employees,
                    registrations = registrations,
                    settlements = settlements,
                    cashboxes = cashboxes,
                    currency = currency
                )
            } else {
                ReferralTab(
                    viewModel = viewModel,
                    referrals = referrals,
                    commissions = referralCommissions,
                    currency = currency
                )
            }
        }
    }
}

enum class EmployeeSort {
    ALPHABETICAL, OUTSTANDING, PROFESSION
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonnelCommissionTab(
    viewModel: HamrahanViewModel,
    employees: List<Employee>,
    registrations: List<com.example.data.ServiceRegistration>,
    settlements: List<CommissionSettlement>,
    cashboxes: List<Cashbox>,
    currency: String
) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf(EmployeeSort.OUTSTANDING) }
    var selectedEmployeeForDetail by remember { mutableStateOf<Employee?>(null) }

    // Aggregate statistics across ALL personnel
    val allActiveRegs = registrations.filter { !it.isDeleted }
    val overallCommissions = allActiveRegs.sumOf { it.employeeCommission }
    val overallSettlements = settlements.sumOf { it.amount }
    val overallOutstanding = overallCommissions - overallSettlements

    // Helper functions to get employee-specific statistics
    val getOutstandingForEmp = { empId: Int ->
        val accrued = registrations.filter { it.employeeId == empId && !it.isDeleted }.sumOf { it.employeeCommission }
        val settled = settlements.filter { it.employeeId == empId }.sumOf { it.amount }
        accrued - settled
    }

    // Filtered and sorted employee list
    val processedEmployees = remember(employees, searchQuery, sortBy, registrations, settlements) {
        employees.filter {
            it.fullName.contains(searchQuery, ignoreCase = true) ||
            it.profession.contains(searchQuery, ignoreCase = true)
        }.sortedWith { a, b ->
            when (sortBy) {
                EmployeeSort.ALPHABETICAL -> a.fullName.compareTo(b.fullName)
                EmployeeSort.OUTSTANDING -> {
                    val balA = getOutstandingForEmp(a.id)
                    val balB = getOutstandingForEmp(b.id)
                    balB.compareTo(balA) // Descending (largest first)
                }
                EmployeeSort.PROFESSION -> a.profession.compareTo(b.profession)
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 840.dp

        if (isExpanded) {
            // Master-Detail Split Workspace for Tablet / Wide Screens
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left/Right Master Personnel List Column
                Column(
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PersonnelListHeaderAndFilter(
                        overallSettlements = overallSettlements,
                        overallOutstanding = overallOutstanding,
                        currency = currency,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        sortBy = sortBy,
                        onSortChange = { sortBy = it }
                    )

                    PersonnelListContent(
                        processedEmployees = processedEmployees,
                        getOutstandingForEmp = getOutstandingForEmp,
                        currency = currency,
                        selectedEmployee = selectedEmployeeForDetail,
                        onSelectEmployee = { selectedEmployeeForDetail = it }
                    )
                }

                // Detail Workspace Column
                Box(
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight()
                ) {
                    val emp = selectedEmployeeForDetail
                    if (emp != null) {
                        PersonnelCommissionDetailWorkspace(
                            employee = emp,
                            registrations = registrations,
                            settlements = settlements,
                            cashboxes = cashboxes,
                            currency = currency,
                            onSettle = { amount, notes, cbId ->
                                viewModel.settleCommission(
                                    employeeId = emp.id,
                                    amount = amount,
                                    periodStart = System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1000L,
                                    periodEnd = System.currentTimeMillis(),
                                    notes = notes,
                                    selectedCashboxId = cbId
                                )
                            },
                            onClose = { selectedEmployeeForDetail = null }
                        )
                    } else {
                        EnterpriseCard(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Diversity3,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "برای مشاهده ریز کارکرد، اسناد تسویه و پرداخت کارمزد، یک همکار را از لیست انتخاب کنید.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.outline,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Compact Mobile View: Master List + Full Detail Bottom Sheet / Dialog
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PersonnelListHeaderAndFilter(
                    overallSettlements = overallSettlements,
                    overallOutstanding = overallOutstanding,
                    currency = currency,
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    sortBy = sortBy,
                    onSortChange = { sortBy = it }
                )

                PersonnelListContent(
                    processedEmployees = processedEmployees,
                    getOutstandingForEmp = getOutstandingForEmp,
                    currency = currency,
                    selectedEmployee = selectedEmployeeForDetail,
                    onSelectEmployee = { selectedEmployeeForDetail = it }
                )
            }

            if (selectedEmployeeForDetail != null) {
                ModalBottomSheet(
                    onDismissRequest = { selectedEmployeeForDetail = null },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.92f)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                    selectedEmployeeForDetail?.let { emp ->
                        PersonnelCommissionDetailWorkspace(
                            employee = emp,
                            registrations = registrations,
                            settlements = settlements,
                            cashboxes = cashboxes,
                            currency = currency,
                            onSettle = { amount, notes, cbId ->
                                viewModel.settleCommission(
                                    employeeId = emp.id,
                                    amount = amount,
                                    periodStart = System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1000L,
                                    periodEnd = System.currentTimeMillis(),
                                    notes = notes,
                                    selectedCashboxId = cbId
                                )
                                selectedEmployeeForDetail = null
                            },
                            onClose = { selectedEmployeeForDetail = null }
                        )
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonnelListHeaderAndFilter(
    overallSettlements: Double,
    overallOutstanding: Double,
    currency: String,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    sortBy: EmployeeSort,
    onSortChange: (EmployeeSort) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Finance Overview KPI Header Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("کل تسویه‌ها", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(overallSettlements.formatPrice(currency), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("مانده بستانکاری کادر", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(overallOutstanding.formatPrice(currency), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Dynamic Search & Filters Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("جستجو در کادر درمان و پرسنل...", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(max = 56.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            // Sort Dropdown Selector
            var sortMenuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { sortMenuExpanded = true },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "مرتب‌سازی",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("بیشترین بستانکاری") },
                        onClick = { onSortChange(EmployeeSort.OUTSTANDING); sortMenuExpanded = false },
                        leadingIcon = { Icon(Icons.Default.TrendingUp, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("حروف الفبا (نام)") },
                        onClick = { onSortChange(EmployeeSort.ALPHABETICAL); sortMenuExpanded = false },
                        leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("تخصص و سمت") },
                        onClick = { onSortChange(EmployeeSort.PROFESSION); sortMenuExpanded = false },
                        leadingIcon = { Icon(Icons.Default.Category, contentDescription = null) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.PersonnelListContent(
    processedEmployees: List<Employee>,
    getOutstandingForEmp: (Int) -> Double,
    currency: String,
    selectedEmployee: Employee?,
    onSelectEmployee: (Employee) -> Unit
) {
    if (processedEmployees.isEmpty()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.PeopleOutline, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                Text("هیچ پرسنل یا همکاری با شرایط جستجو یافت نشد.", color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(processedEmployees) { emp ->
                val outstanding = getOutstandingForEmp(emp.id)
                val isSelected = selectedEmployee?.id == emp.id
                val cardBorderColor = if (isSelected) MaterialTheme.colorScheme.primary else if (outstanding > 0) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                val cardBgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else if (outstanding > 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectEmployee(emp) }
                        .testTag("employee_commission_card_${emp.id}"),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, cardBorderColor),
                    colors = CardDefaults.cardColors(containerColor = cardBgColor)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = emp.fullName.take(1),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Column {
                                Text(emp.fullName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                Text(emp.profession, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (outstanding > 0) "مانده: " + outstanding.formatPrice(currency) else "تسویه شده",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (outstanding > 0) MaterialTheme.colorScheme.error else Color(0xFF10B981)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "مشاهده جزئیات حساب",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PersonnelCommissionDetailWorkspace(
    employee: Employee,
    registrations: List<com.example.data.ServiceRegistration>,
    settlements: List<CommissionSettlement>,
    cashboxes: List<Cashbox>,
    currency: String,
    onSettle: (Double, String, Int?) -> Unit,
    onClose: (() -> Unit)? = null
) {
    val empRegs = remember(registrations, employee.id) {
        registrations.filter { it.employeeId == employee.id && !it.isDeleted }
    }
    val empSettlements = remember(settlements, employee.id) {
        settlements.filter { it.employeeId == employee.id }
    }
    val totalAccrued = empRegs.sumOf { it.employeeCommission }
    val totalSettled = empSettlements.sumOf { it.amount }
    val outstanding = totalAccrued - totalSettled

    var showSettleForm by remember { mutableStateOf(false) }

    EnterpriseCard(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Staff Information Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = employee.fullName.take(1),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = employee.fullName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            StatusBadge(
                                text = if (outstanding > 0) "بستانکار" else "تسویه شده",
                                statusType = if (outstanding > 0) EnterpriseStatusType.ERROR else EnterpriseStatusType.COMPLETED
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "سمت: ${employee.profession} | تماس: ${employee.phone}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (onClose != null) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "بستن")
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // 2. Calculation Summary & Period Hero Cards
            SectionHeader(
                title = "خلاصه محاسبات کارمزد",
                icon = Icons.Default.Calculate
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EnterpriseCard(
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Text("کارکرد انباشته", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(totalAccrued.formatPrice(currency), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }

                EnterpriseCard(
                    modifier = Modifier.weight(1f),
                    containerColor = Color(0xFFF0FDF4),
                    borderColor = Color(0xFF86EFAC),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Text("پرداختی تا‌کنون", style = MaterialTheme.typography.labelSmall, color = Color(0xFF166534))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(totalSettled.formatPrice(currency), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color(0xFF15803D))
                }

                EnterpriseCard(
                    modifier = Modifier.weight(1f),
                    containerColor = if (outstanding > 0) Color(0xFFFEF2F2) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    borderColor = if (outstanding > 0) Color(0xFFFCA5A5) else MaterialTheme.colorScheme.outlineVariant,
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Text("مانده بستانکاری", style = MaterialTheme.typography.labelSmall, color = if (outstanding > 0) Color(0xFF991B1B) else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(outstanding.formatPrice(currency), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = if (outstanding > 0) Color(0xFFB91C1C) else MaterialTheme.colorScheme.onSurface)
                }
            }

            // 3. Service Breakdown
            SectionHeader(
                title = "ریز خدمات و کارکردهای ارائه شده",
                icon = Icons.Default.MedicalServices,
                badgeCount = empRegs.size
            )

            if (empRegs.isEmpty()) {
                EnterpriseCard(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Text(
                        text = "هیچ خدمتی برای این همکار ثبت نشده است.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                EnterpriseCard(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        empRegs.forEachIndexed { index, reg ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "خدمت کد #${reg.id} - ${reg.serviceDate.formatDate()}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "مبلغ خدمت: ${reg.finalPrice.formatPrice(currency)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "کارمزد همکار:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = reg.employeeCommission.formatPrice(currency),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (index < empRegs.size - 1) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Settlement Status & Payment Form
            SectionHeader(
                title = "وضعیت تسویه و ثبت پرداخت",
                icon = Icons.Default.Payments
            )

            if (outstanding <= 0) {
                EnterpriseCard(
                    containerColor = Color(0xFFF0FDF4),
                    borderColor = Color(0xFF86EFAC),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF166534))
                        Text(
                            text = "تمام کارمزدهای این همکار تسویه گردیده است و هیچ مانده بستانکاری وجود ندارد.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF15803D),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                if (!showSettleForm) {
                    Button(
                        onClick = { showSettleForm = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("settle_commission_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("پرداخت کارمزد و ثبت سند تسویه", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                } else {
                    var amountString by remember { mutableStateOf(outstanding.toString()) }
                    var selectedCashbox by remember { mutableStateOf<Cashbox?>(cashboxes.firstOrNull()) }
                    var notes by remember { mutableStateOf("تسویه حساب کارمزد همکار ارجمند ${employee.fullName}") }

                    EnterpriseCard(
                        borderColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "فرم ثبت تراکنش پرداخت خزانه",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            OutlinedTextField(
                                value = amountString,
                                onValueChange = { amountString = it },
                                label = { Text("مبلغ پرداختی") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settle_amount_input"),
                                shape = RoundedCornerShape(10.dp)
                            )

                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                label = { Text("توضیحات و بابت") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )

                            if (cashboxes.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "حساب / صندوق مبدا برای پرداخت:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    ScrollableTabRow(
                                        selectedTabIndex = cashboxes.indexOf(selectedCashbox).coerceAtLeast(0),
                                        edgePadding = 0.dp,
                                        divider = {}
                                    ) {
                                        cashboxes.forEach { cb ->
                                            Tab(
                                                selected = selectedCashbox == cb,
                                                onClick = { selectedCashbox = cb },
                                                text = { Text(cb.name, fontWeight = FontWeight.SemiBold) }
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showSettleForm = false }) {
                                    Text("انصراف")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val amount = amountString.toDoubleOrNull() ?: 0.0
                                        if (amount > 0) {
                                            onSettle(amount, notes, selectedCashbox?.id)
                                            showSettleForm = false
                                        }
                                    },
                                    modifier = Modifier.testTag("save_settlement_confirm_button"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("ثبت قطعی پرداخت", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // 5. Payment History
            SectionHeader(
                title = "تاریخچه اسناد تسویه حساب",
                icon = Icons.Default.History,
                badgeCount = empSettlements.size
            )

            if (empSettlements.isEmpty()) {
                EnterpriseCard(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Text(
                        text = "هیچ سند تسویه حساب تاریخی برای این همکار ثبت نشده است.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    empSettlements.forEach { s ->
                        EnterpriseCard(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            contentPadding = PaddingValues(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "سند تسویه #${s.id}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = s.settlementDate.formatDate(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "مبلغ پرداخت شده: ${s.amount.formatPrice(currency)}",
                                color = Color(0xFF16A34A),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (s.notes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "توضیحات: ${s.notes}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class ReferralSort {
    NAME, COMMISSION_PAID, TOTAL_CASE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferralTab(
    viewModel: HamrahanViewModel,
    referrals: List<Referral>,
    commissions: List<ReferralCommission>,
    currency: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf(ReferralSort.COMMISSION_PAID) }

    var showAddReferralSheet by remember { mutableStateOf(false) }
    var referralToEdit by remember { mutableStateOf<Referral?>(null) }
    var commissionToPay by remember { mutableStateOf<ReferralCommission?>(null) }
    var duplicateToResolve by remember { mutableStateOf<Referral?>(null) }
    var pendingReferralToSave by remember { mutableStateOf<Referral?>(null) }

    // Metrics calculation
    val totalReferrals = referrals.size
    val totalCommissions = commissions.sumOf { it.commissionAmount }
    val paidCommissions = commissions.filter { it.status == "تسویه شده" }.sumOf { it.commissionAmount }
    val pendingCommissions = commissions.filter { it.status == "در انتظار پرداخت" }.sumOf { it.commissionAmount }

    // Aggregate statistics helper
    val getReferralStats = { refId: Int ->
        val list = commissions.filter { it.referralId == refId }
        val cases = list.size
        val paid = list.filter { it.status == "تسویه شده" }.sumOf { it.commissionAmount }
        val pending = list.filter { it.status == "در انتظار پرداخت" }.sumOf { it.commissionAmount }
        Triple(cases, paid, pending)
    }

    // Filtered and sorted referrals list
    val processedReferrals = remember(referrals, searchQuery, sortBy, commissions) {
        referrals.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.type.contains(searchQuery, ignoreCase = true) ||
            it.phone.contains(searchQuery, ignoreCase = true)
        }.sortedWith { a, b ->
            when (sortBy) {
                ReferralSort.NAME -> a.name.compareTo(b.name)
                ReferralSort.COMMISSION_PAID -> {
                    val statsA = getReferralStats(a.id)
                    val statsB = getReferralStats(b.id)
                    statsB.second.compareTo(statsA.second) // Descending paid commission
                }
                ReferralSort.TOTAL_CASE -> {
                    val statsA = getReferralStats(a.id)
                    val statsB = getReferralStats(b.id)
                    statsB.first.compareTo(statsA.first) // Descending total referred cases
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. Referral Overview Metrics Cards ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("خلاصه وضعیت معرفین و پورسانت مطب پزشکان", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    
                    // Export Excel Icon Button
                    IconButton(
                        onClick = {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val file = File(context.cacheDir, "HamrahanSalamat_Referrals_Report.xlsx")
                                    com.example.data.EnterpriseCrashLogger.log(context, "CommissionScreen: Starting referrals report generation")
                                    val success = FileOutputStream(file).use { fos ->
                                        viewModel.exportDataToExcel(fos, listOf("Referrals", "Commissions"))
                                    }

                                    // Verify file before sharing
                                    if (!file.exists() || !file.canRead() || file.length() == 0L) {
                                        com.example.data.EnterpriseCrashLogger.log(context, "CommissionScreen sharing aborted: File is invalid or empty.")
                                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            android.widget.Toast.makeText(context, "کپی فایل ناموفق بود یا فایل خالی است.", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                        return@launch
                                    }

                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        if (success) {
                                            try {
                                                val authority = "${context.packageName}.fileprovider"
                                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                                    context,
                                                    authority,
                                                    file
                                                )
                                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                                    clipData = android.content.ClipData.newRawUri("", uri)
                                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "گزارش کامل معرفین و پورسانت‌ها")
                                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                com.example.data.EnterpriseCrashLogger.log(context, "CommissionScreen launching sharing intent")
                                                context.startActivity(android.content.Intent.createChooser(shareIntent, "اشتراک‌گذاری گزارش معرفین"))
                                                android.widget.Toast.makeText(context, "گزارش اکسل معرفین با موفقیت ایجاد و آماده ارسال شد.", android.widget.Toast.LENGTH_LONG).show()
                                            } catch (ex: Exception) {
                                                android.widget.Toast.makeText(context, "خطا در اشتراک‌گذاری فایل: ${ex.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            val logFile = com.example.data.EnterpriseCrashLogger.getLogFile(context)
                                            android.widget.Toast.makeText(context, "Export failed. Technical log saved.\nPath: ${logFile.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } catch (t: Throwable) {
                                    val logFile = com.example.data.EnterpriseCrashLogger.getLogFile(context)
                                    val details = "CommissionScreen export catch: Class=${t.javaClass.name}, Msg=${t.message}, Thread=${Thread.currentThread().name}"
                                    com.example.data.EnterpriseCrashLogger.log(context, details)
                                    com.example.data.EnterpriseCrashLogger.logThrowable(context, "CommissionScreen export/share", t)
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, "Export failed. Technical log saved.\nPath: ${logFile.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                    throw t
                                }
                            }
                        },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = "خروجی اکسل", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("کل معرفین", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$totalReferrals نفر", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("کل پورسانت", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(totalCommissions.formatPrice(currency), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("پرداخت شده", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(paidCommissions.formatPrice(currency), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color(0xFF2E7D32))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("مانده معوق", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(pendingCommissions.formatPrice(currency), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color(0xFFC62828))
                    }
                }
            }
        }

        // --- 2. Filter & Actions Row ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("جستجو معرف، پزشک، درمانگاه...", style = MaterialTheme.typography.bodyMedium) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                modifier = Modifier.weight(1f).heightIn(max = 56.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            // Filter button
            var sortMenuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { sortMenuExpanded = true },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "مرتب‌سازی",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("بیشترین پورسانت پرداختی") },
                        onClick = { sortBy = ReferralSort.COMMISSION_PAID; sortMenuExpanded = false },
                        leadingIcon = { Icon(Icons.Default.TrendingUp, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("تعداد ارجاع فاکتور") },
                        onClick = { sortBy = ReferralSort.TOTAL_CASE; sortMenuExpanded = false },
                        leadingIcon = { Icon(Icons.Default.People, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("حروف الفبا (نام)") },
                        onClick = { sortBy = ReferralSort.NAME; sortMenuExpanded = false },
                        leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null) }
                    )
                }
            }

            // Add referral button
            IconButton(
                onClick = { showAddReferralSheet = true },
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary),
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Icon(Icons.Default.Add, contentDescription = "افزودن معرف")
            }
        }

        // Split lists: Left Column / Right Column on Tablets, or Stacked LazyColumn with Header on mobile
        Text("معرفین همکار و پورسانت‌های اخیر", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

        if (processedReferrals.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("هیچ معرف ثبت شده‌ای وجود ندارد. با زدن + معرف جدید ثبت کنید.", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // First Section: Referrals List
                items(processedReferrals) { ref ->
                    val stats = getReferralStats(ref.id) // first: cases, second: paid, third: pending
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(ref.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(ref.type, style = MaterialTheme.typography.labelSmall) },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                                Text("شماره تلفن: ${ref.phone}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                
                                val rateText = if (ref.commissionPercentage > 0) "${ref.commissionPercentage}% درصد پورسانت" else "${ref.commissionFixedAmount.formatPrice(currency)} ثابت"
                                Text("نرخ قرارداد: $rateText", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                                Text("تعداد ارجاع: ${stats.first} پرونده | مانده معوق: ${stats.third.formatPrice(currency)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = { referralToEdit = ref }) {
                                    Icon(Icons.Default.Edit, contentDescription = "ویرایش", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = {
                                    viewModel.deleteReferral(ref) { message ->
                                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                // Divider and Second Section Header
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("ریز مطالبات و اسناد مالی پورسانت", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Second Section: Commissions Logs
                val activeCommissions = commissions
                if (activeCommissions.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            Text("تاکنون هیچ کارکرد پورسانتی برای معرفی ثبت نشده است.", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                } else {
                    items(activeCommissions) { com ->
                        val referralName = referrals.find { it.id == com.referralId }?.name ?: "معرف نامشخص"
                        val isPaid = com.status == "تسویه شده"
                        val cardBg = if (isPaid) Color(0xFFE8F5E9).copy(alpha = 0.5f) else Color(0xFFFFEBEE).copy(alpha = 0.5f)
                        val cardBorder = if (isPaid) Color(0xFF10B981).copy(alpha = 0.3f) else Color(0xFFEF4444).copy(alpha = 0.3f)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, cardBorder),
                            colors = CardDefaults.cardColors(containerColor = cardBg)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("بابت پرونده: $referralName", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("شرح خدمت بالینی: ${com.serviceName} (${com.serviceAmount.formatPrice(currency)})", style = MaterialTheme.typography.bodySmall)
                                    Text("پورسانت: ${com.commissionAmount.formatPrice(currency)} (${com.commissionPercentage}%)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                                    Text("تاریخ ایجاد: ${com.date.formatDate()} | وضعیت: ${com.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (com.documentNumber.isNotBlank()) {
                                        Text("کد مرجع تراکنش: ${com.documentNumber}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                }

                                if (!isPaid) {
                                    Button(
                                        onClick = { commissionToPay = com },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("پرداخت")
                                    }
                                } else {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "پرداخت شده", tint = Color(0xFF10B981))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- 3. Add/Edit Referral Bottom Sheet ---
    if (showAddReferralSheet || referralToEdit != null) {
        val isEdit = referralToEdit != null
        val ref = referralToEdit
        var name by remember { mutableStateOf(ref?.name ?: "") }
        var type by remember { mutableStateOf(ref?.type ?: "پزشک") }
        var phone by remember { mutableStateOf(ref?.phone ?: "") }
        var address by remember { mutableStateOf(ref?.address ?: "") }
        var percentage by remember { mutableStateOf(ref?.commissionPercentage?.toString() ?: "0") }
        var fixedAmount by remember { mutableStateOf(ref?.commissionFixedAmount?.toString() ?: "0") }
        var notes by remember { mutableStateOf(ref?.notes ?: "") }

        val localNormalize = { input: String ->
            input.lowercase(java.util.Locale.ROOT)
                .replace("ي", "ی")
                .replace("ك", "ک")
                .replace("‌", " ")
                .trim()
        }

        ModalBottomSheet(
            onDismissRequest = {
                showAddReferralSheet = false
                referralToEdit = null
            },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (isEdit) "ویرایش پرونده معرف" else "ثبت پرونده معرف جدید",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("نام و نام خانوادگی معرف") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("نوع (پزشک، پیراپزشک، معرف، ترخیص کار)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("شماره تماس") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("نشانی مطب / مرکز") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
                OutlinedTextField(value = percentage, onValueChange = { percentage = it }, label = { Text("درصد پورسانت پیش‌فرض (مثلا 10)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
                OutlinedTextField(value = fixedAmount, onValueChange = { fixedAmount = it }, label = { Text("پورسانت نقدی ثابت (ریال)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("یادداشت و اطلاعات تکمیلی") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        showAddReferralSheet = false
                        referralToEdit = null
                    }) {
                        Text("انصراف")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (isEdit && ref != null) {
                                viewModel.updateReferral(
                                    ref.copy(
                                        name = name,
                                        type = type,
                                        phone = phone,
                                        address = address,
                                        commissionPercentage = percentage.toDoubleOrNull() ?: 0.0,
                                        commissionFixedAmount = fixedAmount.toDoubleOrNull() ?: 0.0,
                                        notes = notes
                                    )
                                )
                                showAddReferralSheet = false
                                referralToEdit = null
                            } else {
                                val normalizedName = localNormalize(name)
                                val normalizedPhone = if (phone.isNotBlank()) localNormalize(phone) else ""
                                val probableDuplicate = referrals.find { r ->
                                    localNormalize(r.name) == normalizedName || (normalizedPhone.isNotEmpty() && localNormalize(r.phone) == normalizedPhone)
                                }

                                if (probableDuplicate != null) {
                                    duplicateToResolve = probableDuplicate
                                    pendingReferralToSave = Referral(
                                        name = name,
                                        type = type,
                                        phone = phone,
                                        address = address,
                                        commissionPercentage = percentage.toDoubleOrNull() ?: 0.0,
                                        commissionFixedAmount = fixedAmount.toDoubleOrNull() ?: 0.0,
                                        notes = notes,
                                        isActive = true
                                    )
                                } else {
                                    viewModel.insertReferral(
                                        name = name,
                                        type = type,
                                        phone = phone,
                                        address = address,
                                        commissionPercentage = percentage.toDoubleOrNull() ?: 0.0,
                                        commissionFixedAmount = fixedAmount.toDoubleOrNull() ?: 0.0,
                                        notes = notes
                                    )
                                    showAddReferralSheet = false
                                    referralToEdit = null
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("ثبت و ذخیره")
                    }
                }
            }
        }
    }

    // Duplicate Check Warning Dialog
    duplicateToResolve?.let { existing ->
        AlertDialog(
            onDismissRequest = { duplicateToResolve = null },
            title = { Text("هشدار تکرار پرونده") },
            text = {
                Text("معرفی با مشخصات مشابه در پایگاه داده یافت شد:\n\n" +
                     "نام: ${existing.name}\n" +
                     "تلفن: ${existing.phone}\n\n" +
                     "آیا تمایل به ادغام یا انصراف دارید؟")
            },
            confirmButton = {
                Button(
                    onClick = {
                        duplicateToResolve = null
                        showAddReferralSheet = false
                        referralToEdit = null
                    }
                ) {
                    Text("بله، ارجاع به معرف موجود")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val p = pendingReferralToSave
                        if (p != null) {
                            viewModel.insertReferral(
                                name = p.name,
                                type = p.type,
                                phone = p.phone,
                                address = p.address,
                                commissionPercentage = p.commissionPercentage,
                                commissionFixedAmount = p.commissionFixedAmount,
                                notes = p.notes
                            )
                        }
                        duplicateToResolve = null
                        showAddReferralSheet = false
                        referralToEdit = null
                    }
                ) {
                    Text("خیر، ثبت به عنوان پرونده مجزا")
                }
            }
        )
    }

    // --- 4. Settle Referral Commission Bottom Sheet ---
    commissionToPay?.let { com ->
        var docNum by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }

        ModalBottomSheet(
            onDismissRequest = { commissionToPay = null },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "ثبت سند پرداخت مالی پورسانت",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )

                Text("شرح پرونده: ${com.serviceName}")
                Text("پورسانت محاسبه شده: ${com.commissionAmount.formatPrice(currency)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

                OutlinedTextField(value = docNum, onValueChange = { docNum = it }, label = { Text("شماره ارجاع بانک / رسید تراکنش") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("توضیحات و بابت") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { commissionToPay = null }) {
                        Text("انصراف")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.payReferralCommission(com.id, docNum, notes)
                            commissionToPay = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("تایید پرداخت")
                    }
                }
            }
        }
    }
}
