package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.Employee
import com.example.data.ServiceRegistration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeScreen(viewModel: HamrahanViewModel) {
    val employees by viewModel.employees.collectAsState()
    val registrations by viewModel.registrations.collectAsState()
    val currency by viewModel.defaultCurrency.collectAsState()
    val currentDeepLink by viewModel.currentDeepLink.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var professionFilter by remember { mutableStateOf("همه") } // "همه", "پرستار", "پزشک", "فیزیوتراپ"
    var showAddEditDialog by remember { mutableStateOf(false) }
    var selectedEmployeeForEdit by remember { mutableStateOf<Employee?>(null) }
    var selectedEmployeeForDetail by remember { mutableStateOf<Employee?>(null) }

    LaunchedEffect(currentDeepLink, employees) {
        val link = currentDeepLink
        if (link != null && link.screen == "employees" && link.entityId != null) {
            val emp = employees.find { it.id == link.entityId }
            if (emp != null) {
                selectedEmployeeForDetail = emp
            }
        }
    }

    val filteredEmployees = employees.filter { emp ->
        val matchesSearch = emp.fullName.contains(searchQuery, ignoreCase = true) ||
                emp.phone.contains(searchQuery) ||
                emp.nationalId.contains(searchQuery) ||
                emp.profession.contains(searchQuery, ignoreCase = true)

        val matchesProfession = if (professionFilter == "همه") true else emp.profession.contains(professionFilter)

        matchesSearch && matchesProfession
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مدیریت همکاران و پرسنل", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    IconButton(
                        onClick = {
                            selectedEmployeeForEdit = null
                            showAddEditDialog = true
                        },
                        modifier = Modifier.testTag("add_employee_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAddAlt,
                            contentDescription = "افزودن همکار",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        modifier = Modifier.testTag("employee_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- Search Field ---
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("جستجوی نام همکار، مهارت، شماره پرسنلی...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("employee_search_input"),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            // --- Profession Filter Chips ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = professionFilter == "همه",
                    onClick = { professionFilter = "همه" },
                    label = { Text("همه ردیف‌ها") }
                )
                FilterChip(
                    selected = professionFilter == "پرستار",
                    onClick = { professionFilter = "پرستار" },
                    label = { Text("پرستار") }
                )
                FilterChip(
                    selected = professionFilter == "پزشک",
                    onClick = { professionFilter = "پزشک" },
                    label = { Text("پزشک") }
                )
                FilterChip(
                    selected = professionFilter == "فیزیوتراپ",
                    onClick = { professionFilter = "فیزیوتراپ" },
                    label = { Text("فیزیوتراپ") }
                )
            }

            // --- Employee List ---
            if (filteredEmployees.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.Badge,
                    message = "هیچ همکار منطبقی یافت نشد.",
                    description = "برای ثبت همکار جدید، روی دکمه + گوشه پایین کلیک کنید.",
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredEmployees) { employee ->
                        val empRegs = registrations.filter { it.employeeId == employee.id }
                        EmployeeCard(
                            employee = employee,
                            completedServicesCount = empRegs.size,
                            revenueGenerated = empRegs.sumOf { it.finalPrice },
                            commissionEarned = empRegs.sumOf { it.employeeCommission },
                            currency = currency,
                            onClick = { selectedEmployeeForDetail = employee },
                            onEdit = {
                                selectedEmployeeForEdit = employee
                                showAddEditDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    // --- Add/Edit Dialog ---
    if (showAddEditDialog) {
        val personnelTypes by viewModel.personnelTypes.collectAsState()
        AddEditEmployeeDialog(
            employee = selectedEmployeeForEdit,
            personnelTypes = personnelTypes,
            onAddNewPersonnelType = { newType ->
                viewModel.addPersonnelType(newType)
            },
            onDismiss = { showAddEditDialog = false },
            onSave = { savedEmp ->
                viewModel.saveEmployee(savedEmp)
                showAddEditDialog = false
            },
            onDelete = { employeeToDelete ->
                viewModel.deleteEmployee(employeeToDelete)
                showAddEditDialog = false
            }
        )
    }

    // --- Detail Dialog ---
    selectedEmployeeForDetail?.let { emp ->
        val empRegs = registrations.filter { it.employeeId == emp.id }
        EmployeeDetailDialog(
            employee = emp,
            regs = empRegs,
            currency = currency,
            viewModel = viewModel,
            onDismiss = { 
                selectedEmployeeForDetail = null
                viewModel.clearCurrentDeepLink()
            }
        )
    }
}

@Composable
fun EmployeeCard(
    employee: Employee,
    completedServicesCount: Int,
    revenueGenerated: Double,
    commissionEarned: Double,
    currency: String,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    com.example.ui.components.EnterpriseCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("employee_card_${employee.id}"),
        onClick = onClick,
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = employee.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = employee.profession,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "نوع قرارداد: ${employee.employmentType} | فرمول کارمزد: ${employee.commissionModel} (${employee.commissionValue}%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Mini Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column {
                        Text("خدمات انجام شده", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Text("$completedServicesCount خدمت", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Column {
                        Text("درآمد کل همکار", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Text(commissionEarned.formatPrice(currency), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF10B981))
                    }
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.testTag("edit_employee_button_${employee.id}")) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "ویرایش",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditEmployeeDialog(
    employee: Employee?,
    personnelTypes: List<String> = emptyList(),
    onAddNewPersonnelType: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onSave: (Employee) -> Unit,
    onDelete: ((Employee) -> Unit)? = null
) {
    var fullName by remember { mutableStateOf(employee?.fullName ?: "") }
    var nationalId by remember { mutableStateOf(employee?.nationalId ?: "") }
    var phone by remember { mutableStateOf(employee?.phone ?: "") }
    var profession by remember { mutableStateOf(employee?.profession ?: "کارشناس پرستاری") }
    var position by remember { mutableStateOf(employee?.position ?: "") }
    var skill by remember { mutableStateOf(employee?.skill ?: "") }
    var employmentType by remember { mutableStateOf(employee?.employmentType ?: "پاره وقت") }
    var commissionModel by remember { mutableStateOf(employee?.commissionModel ?: "درصدی") }
    var commissionValueString by remember { mutableStateOf(employee?.commissionValue?.toString() ?: "60") }
    var bankInfo by remember { mutableStateOf(employee?.bankInfo ?: "") }
    var status by remember { mutableStateOf(employee?.status ?: "فعال") }
    var notes by remember { mutableStateOf(employee?.notes ?: "") }

    var hasError by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    if (showDeleteConfirmation && employee != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("حذف همکار و پرسنل", fontWeight = FontWeight.Bold) },
            text = { Text("آیا از حذف همکار «${employee.fullName}» اطمینان دارید؟ این عمل غیرقابل بازگشت است.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(employee)
                        showDeleteConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("بله، حذف شود")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("انصراف")
                }
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = if (employee == null) "ثبت همکار جدید" else "ویرایش پرونده پرسنلی",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("نام و نام خانوادگی همکار") },
                        isError = hasError && fullName.isBlank(),
                        modifier = Modifier.fillMaxWidth().testTag("employee_name_input")
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = nationalId,
                            onValueChange = { nationalId = it },
                            label = { Text("کد ملی") },
                            isError = hasError && nationalId.isBlank(),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("تلفن همراه") },
                            isError = hasError && phone.isBlank(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var dropdownExpanded by remember { mutableStateOf(false) }
                        var showCustomInput by remember { mutableStateOf(false) }
                        var customProfession by remember { mutableStateOf("") }

                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = profession,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("تخصص") },
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                                modifier = Modifier.fillMaxWidth().testTag("employee_profession_dropdown")
                            )
                            IconButton(
                                onClick = { dropdownExpanded = true },
                                modifier = Modifier.matchParentSize()
                            ) {}
                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false }
                            ) {
                                personnelTypes.forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type) },
                                        onClick = {
                                            profession = type
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("سایر (افزودن تخصص جدید)...", color = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        showCustomInput = true
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }

                        if (showCustomInput) {
                            AlertDialog(
                                onDismissRequest = { showCustomInput = false },
                                title = { Text("افزودن تخصص جدید") },
                                text = {
                                    OutlinedTextField(
                                        value = customProfession,
                                        onValueChange = { customProfession = it },
                                        label = { Text("عنوان تخصص") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            if (customProfession.isNotBlank()) {
                                                onAddNewPersonnelType(customProfession)
                                                profession = customProfession
                                                showCustomInput = false
                                            }
                                        }
                                    ) {
                                        Text("افزودن")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showCustomInput = false }) {
                                        Text("انصراف")
                                    }
                                }
                            )
                        }
                        OutlinedTextField(
                            value = position,
                            onValueChange = { position = it },
                            label = { Text("سمت اداری / فنی") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = skill,
                        onValueChange = { skill = it },
                        label = { Text("مهارت‌ها و گواهینامه‌های ویژه") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = commissionValueString,
                            onValueChange = { commissionValueString = it },
                            label = { Text("درصد کمیسیون همکار") },
                            modifier = Modifier.weight(1f)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("نوع قرارداد", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Row {
                                FilterChip(
                                    selected = employmentType == "تمام وقت",
                                    onClick = { employmentType = "تمام وقت" },
                                    label = { Text("مقیم") }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                FilterChip(
                                    selected = employmentType == "پاره وقت",
                                    onClick = { employmentType = "پاره وقت" },
                                    label = { Text("ساعتی") }
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = bankInfo,
                        onValueChange = { bankInfo = it },
                        label = { Text("شماره کارت، شبا و اطلاعات بانکی تسویه") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("یادداشت‌های اداری / پرونده") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (employee != null && onDelete != null) {
                            TextButton(
                                onClick = { showDeleteConfirmation = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("حذف همکار")
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text("انصراف")
                            }
                            Button(
                                onClick = {
                                    if (fullName.isBlank() || nationalId.isBlank() || phone.isBlank()) {
                                        hasError = true
                                    } else {
                                        val saved = Employee(
                                            id = employee?.id ?: 0,
                                            fullName = fullName,
                                            nationalId = nationalId,
                                            phone = phone,
                                            profession = profession,
                                            position = position,
                                            skill = skill,
                                            employmentType = employmentType,
                                            commissionModel = commissionModel,
                                            commissionValue = commissionValueString.toDoubleOrNull() ?: 60.0,
                                            bankInfo = bankInfo,
                                            status = status,
                                            startDate = employee?.startDate ?: System.currentTimeMillis(),
                                            notes = notes,
                                             uuid = employee?.uuid ?: java.util.UUID.randomUUID().toString()
                                        )
                                        onSave(saved)
                                    }
                                },
                                modifier = Modifier.testTag("save_employee_confirm_button")
                            ) {
                                Text("ذخیره همکار")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeDetailDialog(
    employee: Employee,
    regs: List<ServiceRegistration>,
    currency: String,
    viewModel: HamrahanViewModel,
    onDismiss: () -> Unit
) {
    val totalRevenue = regs.sumOf { it.finalPrice }
    val totalCommission = regs.sumOf { it.employeeCommission }
    val contracts by viewModel.contracts.collectAsState()
    val staffProfiles by viewModel.staffProfiles.collectAsState()

    val currentProfile = staffProfiles.find { it.employeeId == employee.id } 
        ?: com.example.data.StaffProfile(employeeId = employee.id)
    val currentContract = contracts.find { it.employeeId == employee.id } 
        ?: com.example.data.Contract(
            employeeId = employee.id, 
            title = "قرارداد همکاری پرسنلی", 
            content = "این قرارداد جهت ارائه خدمات بالینی به بیماران در منزل منعقد می‌گردد.",
            startDate = System.currentTimeMillis(),
            endDate = System.currentTimeMillis() + 31536000000L // 1 year
        )

    var activeTab by remember { mutableStateOf(0) } // 0 = کارکرد مالی, 1 = مدارک و پرونده (HR), 2 = قرارداد همکاری
    val currentDeepLink by viewModel.currentDeepLink.collectAsState()

    LaunchedEffect(currentDeepLink) {
        val link = currentDeepLink
        if (link != null && link.screen == "employees" && link.entityId == employee.id) {
            when (link.tab) {
                "documents" -> activeTab = 1
                "contract" -> activeTab = 2
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 4.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = employee.fullName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${employee.profession} • کد ملی: ${employee.nationalId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "بستن")
                    }
                }

                TabRow(
                    selectedTabIndex = activeTab,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = Color.Transparent
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        text = { Text("کارکرد مالی", style = MaterialTheme.typography.labelMedium) }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        text = { Text("مدارک و پرونده", style = MaterialTheme.typography.labelMedium) }
                    )
                    Tab(
                        selected = activeTab == 2,
                        onClick = { activeTab = 2 },
                        text = { Text("قرارداد", style = MaterialTheme.typography.labelMedium) }
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (activeTab == 0) {
                        // --- TAB 0: FINANCIAL PERFORMANCE ---
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("سمت اداری / فنی: ${employee.position.ifBlank { "ثبت نشده" }}")
                                    Text("نوع همکاری: ${employee.employmentType}")
                                    Text("تاریخ شروع همکاری: ${employee.startDate.formatDate()}")
                                    if (employee.bankInfo.isNotBlank()) {
                                        Text("اطلاعات حساب: ${employee.bankInfo}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (employee.skill.isNotBlank()) {
                                        Text("مهارت‌ها: ${employee.skill}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        item {
                            Text("آمار مالی و کارکرد همکار", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8F6)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("تعداد خدمات", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        Text("${regs.size} بار مراجعت", fontWeight = FontWeight.Bold)
                                    }
                                    Column {
                                        Text("درآمد ناخالص دفتر", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        Text(totalRevenue.formatPrice(currency), fontWeight = FontWeight.Bold)
                                    }
                                    Column {
                                        Text("سهم خالص همکار", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        Text(totalCommission.formatPrice(currency), fontWeight = FontWeight.Bold, color = Color(0xFF00796B))
                                    }
                                }
                            }
                        }

                        item {
                            Text("سوابق مراجعات و فاکتورها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        if (regs.isEmpty()) {
                            item {
                                Text("تاکنون خدمتی برای این همکار در سیستم ثبت نشده است.", color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                            }
                        } else {
                            items(regs) { reg ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("فاکتور #${reg.invoiceNumber}", fontWeight = FontWeight.SemiBold)
                                            Text(reg.dateTime.formatDate(), color = Color.Gray)
                                        }
                                        Text("کارمزد همکار: ${reg.employeeCommission.formatPrice(currency)} (بابت ${reg.sellingPrice.formatPrice(currency)})",
                                            style = MaterialTheme.typography.bodyMedium, color = Color(0xFF00796B)
                                        )
                                    }
                                }
                            }
                        }
                    } else if (activeTab == 1) {
                        // --- TAB 1: DOCUMENTS & DOSSIER (HR Verification) ---
                        item {
                            Text("چک‌لیست مدارک فیزیکی و هویتی همکار", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    val isProfileAlert = currentDeepLink?.alertType == "staff_profile_incomplete"
                                    val highlightNationalId = isProfileAlert && !currentProfile.hasNationalIdCard
                                    val highlightDegree = isProfileAlert && !currentProfile.hasDegree
                                    val highlightLicense = isProfileAlert && !currentProfile.hasLicense
                                    val highlightContract = isProfileAlert && !currentProfile.hasContract

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically, 
                                        modifier = Modifier.fillMaxWidth().then(
                                            if (highlightNationalId) {
                                                Modifier.background(Color(0xFFFFEBEE), shape = RoundedCornerShape(8.dp))
                                                    .border(2.dp, Color(0xFFDC2626), shape = RoundedCornerShape(8.dp))
                                                    .padding(6.dp)
                                            } else Modifier
                                        )
                                    ) {
                                        Checkbox(
                                            checked = currentProfile.hasNationalIdCard,
                                            onCheckedChange = { viewModel.saveStaffProfile(currentProfile.copy(hasNationalIdCard = it)) }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("اصل و تصویر کارت ملی و شناسنامه", style = MaterialTheme.typography.bodyMedium)
                                            if (highlightNationalId) {
                                                Text("⚠️ مدرک مفقوده یا تایید نشده!", style = MaterialTheme.typography.labelSmall, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically, 
                                        modifier = Modifier.fillMaxWidth().then(
                                            if (highlightDegree) {
                                                Modifier.background(Color(0xFFFFEBEE), shape = RoundedCornerShape(8.dp))
                                                    .border(2.dp, Color(0xFFDC2626), shape = RoundedCornerShape(8.dp))
                                                    .padding(6.dp)
                                            } else Modifier
                                        )
                                    ) {
                                        Checkbox(
                                            checked = currentProfile.hasDegree,
                                            onCheckedChange = { viewModel.saveStaffProfile(currentProfile.copy(hasDegree = it)) }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("تصویر مدرک تحصیلی یا گواهی موقت", style = MaterialTheme.typography.bodyMedium)
                                            if (highlightDegree) {
                                                Text("⚠️ مدرک مفقوده یا تایید نشده!", style = MaterialTheme.typography.labelSmall, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically, 
                                        modifier = Modifier.fillMaxWidth().then(
                                            if (highlightLicense) {
                                                Modifier.background(Color(0xFFFFEBEE), shape = RoundedCornerShape(8.dp))
                                                    .border(2.dp, Color(0xFFDC2626), shape = RoundedCornerShape(8.dp))
                                                    .padding(6.dp)
                                            } else Modifier
                                        )
                                    ) {
                                        Checkbox(
                                            checked = currentProfile.hasLicense,
                                            onCheckedChange = { viewModel.saveStaffProfile(currentProfile.copy(hasLicense = it)) }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("پروانه نظام پزشکی/پرستاری فعال", style = MaterialTheme.typography.bodyMedium)
                                            if (highlightLicense) {
                                                Text("⚠️ مدرک مفقوده یا تایید نشده!", style = MaterialTheme.typography.labelSmall, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically, 
                                        modifier = Modifier.fillMaxWidth().then(
                                            if (highlightContract) {
                                                Modifier.background(Color(0xFFFFEBEE), shape = RoundedCornerShape(8.dp))
                                                    .border(2.dp, Color(0xFFDC2626), shape = RoundedCornerShape(8.dp))
                                                    .padding(6.dp)
                                            } else Modifier
                                        )
                                    ) {
                                        Checkbox(
                                            checked = currentProfile.hasContract,
                                            onCheckedChange = { viewModel.saveStaffProfile(currentProfile.copy(hasContract = it)) }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("قرارداد مکتوب امضا شده نزد مرکز", style = MaterialTheme.typography.bodyMedium)
                                            if (highlightContract) {
                                                Text("⚠️ مدرک مفقوده یا تایید نشده!", style = MaterialTheme.typography.labelSmall, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text("تایید صلاحیت و وضعیت پرونده اداری", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = when (currentProfile.status) {
                                        "Approved" -> Color(0xFFDCFCE7)
                                        "Rejected" -> Color(0xFFFEE2E2)
                                        "NeedsCorrection" -> Color(0xFFFEF9C3)
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val statusDisplay = when (currentProfile.status) {
                                        "Approved" -> "تایید شده (فعال جهت اعزام)"
                                        "Rejected" -> "تعلیق همکاری (غیرمجاز)"
                                        "NeedsCorrection" -> "نقص مدارک و نیاز به اصلاح"
                                        else -> "در حال بررسی مدارک (Pending)"
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = when (currentProfile.status) {
                                                "Approved" -> Icons.Default.CheckCircle
                                                "Rejected" -> Icons.Default.Cancel
                                                "NeedsCorrection" -> Icons.Default.Warning
                                                else -> Icons.Default.Pending
                                            },
                                            contentDescription = null,
                                            tint = when (currentProfile.status) {
                                                "Approved" -> Color(0xFF15803D)
                                                "Rejected" -> Color(0xFFB91C1C)
                                                "NeedsCorrection" -> Color(0xFFA16207)
                                                else -> Color.Gray
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("وضعیت پرونده: $statusDisplay", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    }

                                    if (currentProfile.comment.isNotBlank()) {
                                        Text("توضیحات مدیریت: ${currentProfile.comment}", style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
                                    }

                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                    Text("تغییر وضعیت اداری همکار توسط سوپروایزر:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        Button(
                                            onClick = { viewModel.saveStaffProfile(currentProfile.copy(status = "Approved", comment = "مدارک بررسی و مورد تایید است.")) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f).height(36.dp)
                                        ) {
                                            Text("تایید پرسنل", style = MaterialTheme.typography.labelSmall)
                                        }

                                        Button(
                                            onClick = { viewModel.saveStaffProfile(currentProfile.copy(status = "NeedsCorrection", comment = "لطفاً تصویر پروانه پرستاری به روز رسانی شود.")) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f).height(36.dp)
                                        ) {
                                            Text("نیاز به اصلاح", style = MaterialTheme.typography.labelSmall)
                                        }

                                        Button(
                                            onClick = { viewModel.saveStaffProfile(currentProfile.copy(status = "Rejected", comment = "همکاری به دلیل اتمام تاریخ مدارک موقتاً به حالت تعلیق درآمد.")) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f).height(36.dp)
                                        ) {
                                            Text("تعلیق همکار", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // --- TAB 2: CONTRACT MANAGEMENT ---
                        item {
                            Text("اطلاعات و مفاد قرارداد همکاری", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    var titleInput by remember { mutableStateOf(currentContract.title) }
                                    var contentInput by remember { mutableStateOf(currentContract.content) }

                                    OutlinedTextField(
                                        value = titleInput,
                                        onValueChange = { 
                                            titleInput = it
                                            viewModel.saveContract(currentContract.copy(title = it))
                                        },
                                        label = { Text("عنوان قرارداد") },
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    OutlinedTextField(
                                        value = contentInput,
                                        onValueChange = { 
                                            contentInput = it
                                            viewModel.saveContract(currentContract.copy(content = it))
                                        },
                                        label = { Text("شرح مفاد قرارداد همکاری") },
                                        minLines = 4,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("شروع: ${currentContract.startDate.formatDate()}", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.weight(1f))
                                        Text("پایان: ${currentContract.endDate.formatDate()}", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = when (currentContract.status) {
                                        "Approved" -> Color(0xFFDCFCE7)
                                        "NeedsCorrection" -> Color(0xFFFEF9C3)
                                        else -> Color(0xFFF3F4F6)
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val statusDisplay = when (currentContract.status) {
                                        "Approved" -> "قرارداد منعقد شده و رسمی"
                                        "NeedsCorrection" -> "قرارداد نیازمند بازنگری"
                                        else -> "در حال انتظار بررسی (Pending)"
                                    }
                                    Text("وضعیت رسمی قرارداد: $statusDisplay", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)

                                    val isContractAlert = currentDeepLink?.alertType == "contract_pending" || currentDeepLink?.alertType == "expired_contract"
                                    val highlightContractActions = isContractAlert && currentContract.status != "Approved"
                                    if (highlightContractActions) {
                                        Text(
                                            text = "🚨 اقدام فوری: بررسی و تایید امضا جهت فعالسازی پرسنل", 
                                            style = MaterialTheme.typography.labelSmall, 
                                            fontWeight = FontWeight.Bold, 
                                            color = Color(0xFFB45309),
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp), 
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).then(
                                            if (highlightContractActions) {
                                                Modifier.background(Color(0xFFFEF3C7), shape = RoundedCornerShape(12.dp))
                                                    .border(2.5.dp, Color(0xFFD97706), shape = RoundedCornerShape(12.dp))
                                                    .padding(8.dp)
                                            } else Modifier
                                        )
                                    ) {
                                        Button(
                                            onClick = { viewModel.saveContract(currentContract.copy(status = "Approved", comment = "تایید و ثبت نهایی شد.")) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f).height(36.dp)
                                        ) {
                                            Text("امضا و تایید قرارداد", style = MaterialTheme.typography.labelSmall)
                                        }

                                        Button(
                                            onClick = { viewModel.saveContract(currentContract.copy(status = "NeedsCorrection", comment = "نیازمند اصلاح شرایط کمیسیون")) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f).height(36.dp)
                                        ) {
                                            Text("درخواست اصلاح", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("بستن پرونده", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
