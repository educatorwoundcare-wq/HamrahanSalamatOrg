package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(viewModel: HamrahanViewModel) {
    val registrations by viewModel.registrations.collectAsState()
    val patients by viewModel.patients.collectAsState()
    val employees by viewModel.employees.collectAsState()
    val services by viewModel.services.collectAsState()
    val cashboxes by viewModel.cashboxes.collectAsState()
    val currency by viewModel.defaultCurrency.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedRegForEdit by remember { mutableStateOf<ServiceRegistration?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("دفتر ثبت مراجعات و خدمات پرستاری", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.testTag("add_registration_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PostAdd,
                            contentDescription = "ثبت خدمت",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        modifier = Modifier.testTag("registration_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (registrations.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.MedicalServices,
                    message = "هیچ خدمت ثبت شده‌ای یافت نشد.",
                    description = "برای ثبت خدمت جدید، روی دکمه + گوشه پایین کلیک کنید.",
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(registrations) { reg ->
                        val patientName = patients.find { it.id == reg.patientId }?.fullName ?: "نامشخص"
                        val serviceName = services.find { it.id == reg.serviceId }?.name ?: "نامشخص"
                        val employeeName = employees.find { it.id == reg.employeeId }?.fullName ?: "نامشخص"

                        RegistrationCard(
                            reg = reg,
                            patientName = patientName,
                            serviceName = serviceName,
                            employeeName = employeeName,
                            currency = currency,
                            onEdit = { selectedRegForEdit = reg },
                            onDelete = { viewModel.deleteRegistration(reg) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog || selectedRegForEdit != null) {
        RegisterServiceDialog(
            registration = selectedRegForEdit,
            patients = patients,
            services = services.filter { it.isActive != false || it.id == selectedRegForEdit?.serviceId },
            employees = employees,
            cashboxes = cashboxes,
            currency = currency,
            onDismiss = { 
                showAddDialog = false
                selectedRegForEdit = null
            },
            onSave = { registrationToSave, selectedSvcs, selectedCashboxId, reason, comment ->
                if (selectedRegForEdit != null) {
                    viewModel.editServiceRegistration(
                        id = selectedRegForEdit!!.id,
                        patientId = registrationToSave.patientId,
                        serviceId = registrationToSave.serviceId,
                        employeeId = registrationToSave.employeeId,
                        dateTime = registrationToSave.dateTime,
                        sellingPrice = registrationToSave.sellingPrice,
                        employeeCost = registrationToSave.employeeCost,
                        transportationCost = registrationToSave.transportationCost,
                        otherCosts = registrationToSave.otherCosts,
                        discount = registrationToSave.discount,
                        paymentMethod = registrationToSave.paymentMethod,
                        invoiceNumber = registrationToSave.invoiceNumber,
                        notes = registrationToSave.notes,
                        selectedCashboxId = selectedCashboxId,
                        isPaid = registrationToSave.isPaid,
                        selectedServices = selectedSvcs,
                        consumablesOwner = registrationToSave.consumablesOwner,
                        reason = reason,
                        comment = comment
                    )
                } else {
                    if (selectedSvcs.size > 1) {
                        viewModel.registerPackage(
                            patientId = registrationToSave.patientId,
                            employeeId = registrationToSave.employeeId,
                            selectedServices = selectedSvcs,
                            dateTime = registrationToSave.dateTime,
                            sellingPrice = registrationToSave.sellingPrice,
                            employeeCost = registrationToSave.employeeCost,
                            transportationCost = registrationToSave.transportationCost,
                            otherCosts = registrationToSave.otherCosts,
                            discount = registrationToSave.discount,
                            paymentMethod = registrationToSave.paymentMethod,
                            invoiceNumber = registrationToSave.invoiceNumber,
                            notes = registrationToSave.notes,
                            selectedCashboxId = selectedCashboxId,
                            isPaid = registrationToSave.isPaid,
                            consumablesOwner = registrationToSave.consumablesOwner
                        )
                    } else {
                        viewModel.registerService(
                            patientId = registrationToSave.patientId,
                            serviceId = registrationToSave.serviceId,
                            employeeId = registrationToSave.employeeId,
                            dateTime = registrationToSave.dateTime,
                            sellingPrice = registrationToSave.sellingPrice,
                            employeeCost = registrationToSave.employeeCost,
                            transportationCost = registrationToSave.transportationCost,
                            otherCosts = registrationToSave.otherCosts,
                            discount = registrationToSave.discount,
                            paymentMethod = registrationToSave.paymentMethod,
                            invoiceNumber = registrationToSave.invoiceNumber,
                            notes = registrationToSave.notes,
                            selectedCashboxId = selectedCashboxId,
                            isPaid = registrationToSave.isPaid,
                            consumablesOwner = registrationToSave.consumablesOwner
                        )
                    }
                }
                showAddDialog = false
                selectedRegForEdit = null
            }
        )
    }
}

@Composable
fun RegistrationCard(
    reg: ServiceRegistration,
    patientName: String,
    serviceName: String,
    employeeName: String,
    currency: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    com.example.ui.components.EnterpriseCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "فاکتور #${reg.invoiceNumber}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.titleMedium
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "ثبت: ${reg.dateTime.formatDateTime()}",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "ارائه خدمت: ${reg.serviceDate.formatPersianDate()}",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            
            // Difference warning badge if dates differ
            val startReg = getStartOfDay(reg.dateTime)
            val startSvc = getStartOfDay(reg.serviceDate)
            if (startReg != startSvc) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFEF3C7), shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "تفاوت تاریخ ثبت (${reg.dateTime.formatPersianDate()}) با ارائه خدمت (${reg.serviceDate.formatPersianDate()})",
                            color = Color(0xFFB45309),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = "بیمار: $patientName",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "خدمت ارائه‌شده: $serviceName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            Text(
                text = "همکار مجری: $employeeName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "مبلغ دریافتی بیمار: ${reg.finalPrice.formatPrice(currency)}",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "کمیسیون همکار: ${reg.employeeCommission.formatPrice(currency)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "سود خالص شرکت: ${reg.companyProfit.formatPrice(currency)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .background(
                                if (reg.workflowStatus == "Scheduled") Color(0xFFEFF6FF) else (if (reg.isPaid) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (reg.workflowStatus == "Scheduled") "برنامه‌ریزی شده" else (if (reg.isPaid) "پرداخت شده" else "عدم پرداخت (بدهکار)"),
                            color = if (reg.workflowStatus == "Scheduled") Color(0xFF1D4ED8) else (if (reg.isPaid) Color(0xFF15803D) else Color(0xFFB91C1C)),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "ویرایش فاکتور",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "حذف فاکتور",
                            tint = Color(0xFFEF4444)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterServiceDialog(
    registration: ServiceRegistration? = null,
    patients: List<Patient>,
    services: List<Service>,
    employees: List<Employee>,
    cashboxes: List<Cashbox>,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (ServiceRegistration, List<Service>, Int?, String, String) -> Unit
) {
    var selectedPatient by remember { mutableStateOf<Patient?>(patients.find { it.id == registration?.patientId }) }
    var adjustmentReason by remember { mutableStateOf("اصلاح اشتباه ثبت اطلاعات") }
    var adjustmentComment by remember { mutableStateOf("") }
    
    // Support multiple selected services (packages)
    val selectedServices = remember { 
        mutableStateListOf<Service>().apply {
            if (registration != null) {
                val currentSvc = services.find { it.id == registration.serviceId }
                if (currentSvc != null) add(currentSvc)
            }
        }
    }
    
    var selectedEmployee by remember { mutableStateOf<Employee?>(employees.find { it.id == registration?.employeeId }) }
    var selectedCashbox by remember { mutableStateOf<Cashbox?>(cashboxes.firstOrNull()) }

    var workflowStatus by remember { mutableStateOf(registration?.workflowStatus ?: "Submitted") }
    var scheduledDate by remember { mutableStateOf(registration?.scheduledDate ?: System.currentTimeMillis()) }
    var serviceDate by remember { mutableStateOf(registration?.serviceDate ?: System.currentTimeMillis()) }
    val createdTimestamp = registration?.dateTime ?: System.currentTimeMillis()

    var showScheduledDatePicker by remember { mutableStateOf(false) }
    var showServiceDatePicker by remember { mutableStateOf(false) }
    var showScheduledTimePicker by remember { mutableStateOf(false) }
    var showServiceTimePicker by remember { mutableStateOf(false) }

    var sellingPriceString by remember { mutableStateOf(registration?.sellingPrice?.toString() ?: "") }
    var employeeCostString by remember { mutableStateOf(registration?.employeeCost?.toString() ?: "") }
    var transportationCostString by remember { mutableStateOf(registration?.transportationCost?.toString() ?: "0") }
    var otherCostsString by remember { mutableStateOf(registration?.otherCosts?.toString() ?: "0") }
    var discountString by remember { mutableStateOf(registration?.discount?.toString() ?: "0") }
    var consumablesOwner by remember { mutableStateOf(registration?.consumablesOwner ?: "Nurse") }

    var paymentMethod by remember { mutableStateOf(registration?.paymentMethod ?: "کارت به کارت") }
    var invoiceNumber by remember { mutableStateOf(registration?.invoiceNumber ?: (1000 + Random().nextInt(9000)).toString()) }
    var notes by remember { mutableStateOf(registration?.notes ?: "") }
    var isPaid by remember { mutableStateOf(registration?.isPaid ?: true) }

    // Dialog picker controls
    var showPatientPicker by remember { mutableStateOf(false) }
    var showServicePicker by remember { mutableStateOf(false) }
    var showEmployeePicker by remember { mutableStateOf(false) }

    // Real-time calculations
    val sellingPrice = sellingPriceString.toDoubleOrNull() ?: 0.0
    val transportationCost = transportationCostString.toDoubleOrNull() ?: 0.0
    val discount = discountString.toDoubleOrNull() ?: 0.0
    val employeeCost = employeeCostString.toDoubleOrNull() ?: 0.0
    val otherCosts = otherCostsString.toDoubleOrNull() ?: 0.0

    // Final Invoice Total = Service Total + Consumables Total + Transport - Discount
    val finalPrice = sellingPrice + otherCosts + transportationCost - discount
    val employeeCommission = employeeCost + transportationCost + (if (consumablesOwner == "Nurse") otherCosts else 0.0)
    val companyProfit = finalPrice - employeeCommission

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
                        text = if (registration != null) "ویرایش فاکتور خدمت" else "ثبت خدمت و فاکتور فروش",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // --- Patient Selection ---
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showPatientPicker = true }
                            .testTag("patient_selector"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedPatient?.let { "بیمار: ${it.fullName}" } ?: "انتخاب بیمار پرونده‌دار...",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // --- Service(s) Selection (Package / Single) ---
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "خدمات انتخاب شده (پکیج یا تکی):",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        selectedServices.forEachIndexed { index, svc ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(svc.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text("تعرفه: ${svc.sellingPrice.formatPrice(currency)}", style = MaterialTheme.typography.labelSmall)
                                    }
                                    IconButton(
                                        onClick = {
                                            selectedServices.removeAt(index)
                                            // Recalculate sums
                                            sellingPriceString = selectedServices.sumOf { it.sellingPrice }.toString()
                                            transportationCostString = selectedServices.maxOfOrNull { it.transportationCost }?.toString() ?: "0"
                                            discountString = selectedServices.sumOf { it.discount }.toString()
                                            otherCostsString = selectedServices.sumOf { it.consumablesCost }.toString()
                                            employeeCostString = selectedServices.sumOf { s ->
                                                if (selectedEmployee != null && s.employeeCommission > 0.0) {
                                                    if (s.employeeCommission < 100.0) s.sellingPrice * (s.employeeCommission / 100.0) else s.employeeCommission
                                                } else s.defaultCost
                                            }.toString()
                                        }
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "حذف خدمت", tint = Color.Red, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { showServicePicker = true },
                            modifier = Modifier.fillMaxWidth().testTag("service_selector"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("افزودن خدمت به این فاکتور / پکیج")
                        }
                    }
                }

                // --- Employee Selection ---
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showEmployeePicker = true }
                            .testTag("employee_selector"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedEmployee?.let { "همکار مجری: ${it.fullName}" } ?: "انتخاب همکار / مجری خدمت...",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // --- Service Status & Scheduling Date Pickers ---
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "اطلاعات زمان‌بندی و وضعیت خدمت",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Status Selector Dropdown
                            var statusExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = if (workflowStatus == "Scheduled") "برنامه‌ریزی شده (رزرو آینده)" else "انجام شده (ثبت نهایی)",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("وضعیت ارائه خدمت") },
                                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { statusExpanded = true }
                                )
                                DropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text("انجام شده (ثبت نهایی)") },
                                        onClick = {
                                            workflowStatus = "Submitted"
                                            statusExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("برنامه‌ریزی شده (رزرو آینده)") },
                                        onClick = {
                                            workflowStatus = "Scheduled"
                                            statusExpanded = false
                                        }
                                    )
                                }
                            }

                            // Created Date (Read-only)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("تاریخ ایجاد ثبت (غیرقابل تغییر):", style = MaterialTheme.typography.bodySmall)
                                Text(createdTimestamp.formatPersianDateTime(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            }

                             // Scheduled Date (Past/Present/Future allowed)
                             OutlinedCard(
                                 onClick = { showScheduledDatePicker = true },
                                 modifier = Modifier.fillMaxWidth()
                             ) {
                                 Row(
                                     modifier = Modifier.padding(12.dp),
                                     horizontalArrangement = Arrangement.SpaceBetween,
                                     verticalAlignment = Alignment.CenterVertically
                                 ) {
                                     Column {
                                         Text("تاریخ برنامه‌ریزی (Scheduled Date)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                         Row(verticalAlignment = Alignment.CenterVertically) {
                                             val formattedTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(scheduledDate)).toPersianDigits()
                                             Text("${scheduledDate.formatPersianDate()} ساعت $formattedTime", fontWeight = FontWeight.Bold)
                                             val nowMs = System.currentTimeMillis()
                                             val todayStartMs = getStartOfDay(nowMs)
                                             val todayEndMs = getEndOfToday(nowMs)
                                             val sDateStart = getStartOfDay(scheduledDate)
                                             val (badgeText, badgeBg, badgeTextCol) = when {
                                                 sDateStart < todayStartMs -> Triple("گذشته", Color(0xFFF3F4F6), Color(0xFF4B5563))
                                                 sDateStart in todayStartMs..todayEndMs -> Triple("امروز", Color(0xFFDBEAFE), Color(0xFF1E40AF))
                                                 else -> Triple("آینده", Color(0xFFFEF3C7), Color(0xFF92400E))
                                             }
                                             Surface(
                                                 color = badgeBg,
                                                 shape = RoundedCornerShape(4.dp),
                                                 modifier = Modifier.padding(start = 8.dp)
                                             ) {
                                                 Text(
                                                     text = badgeText,
                                                     color = badgeTextCol,
                                                     style = MaterialTheme.typography.labelSmall,
                                                     fontWeight = FontWeight.Bold,
                                                     modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                 )
                                             }
                                         }
                                     }
                                     Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                 }
                             }
 
                             // Service Date (Past/Present, Future only when status is Scheduled)
                             val todayStart = getStartOfDay(System.currentTimeMillis())
                             val serviceDateStart = getStartOfDay(serviceDate)
                             val isFuture = serviceDateStart > todayStart
                             val isInvalidFuture = isFuture && workflowStatus != "Scheduled"
 
                             OutlinedCard(
                                 onClick = { showServiceDatePicker = true },
                                 modifier = Modifier.fillMaxWidth(),
                                 border = BorderStroke(1.dp, if (isInvalidFuture) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant)
                             ) {
                                 Row(
                                     modifier = Modifier.padding(12.dp),
                                     horizontalArrangement = Arrangement.SpaceBetween,
                                     verticalAlignment = Alignment.CenterVertically
                                 ) {
                                     Column {
                                         Text("تاریخ ارائه خدمت (Service Date)", style = MaterialTheme.typography.labelSmall, color = if (isInvalidFuture) MaterialTheme.colorScheme.error else Color.Gray)
                                         Row(verticalAlignment = Alignment.CenterVertically) {
                                             val formattedTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(serviceDate)).toPersianDigits()
                                             Text(
                                                 text = "${serviceDate.formatPersianDate()} ساعت $formattedTime",
                                                 fontWeight = FontWeight.Bold,
                                                 color = if (isInvalidFuture) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                             )
                                             val nowMs = System.currentTimeMillis()
                                             val todayStartMs = getStartOfDay(nowMs)
                                             val todayEndMs = getEndOfToday(nowMs)
                                             val sDateStart = getStartOfDay(serviceDate)
                                             val (badgeText, badgeBg, badgeTextCol) = when {
                                                 sDateStart < todayStartMs -> Triple("گذشته", Color(0xFFF3F4F6), Color(0xFF4B5563))
                                                 sDateStart in todayStartMs..todayEndMs -> Triple("امروز", Color(0xFFDBEAFE), Color(0xFF1E40AF))
                                                 else -> Triple("آینده", Color(0xFFFEF3C7), Color(0xFF92400E))
                                             }
                                             Surface(
                                                 color = badgeBg,
                                                 shape = RoundedCornerShape(4.dp),
                                                 modifier = Modifier.padding(start = 8.dp)
                                             ) {
                                                 Text(
                                                     text = badgeText,
                                                     color = badgeTextCol,
                                                     style = MaterialTheme.typography.labelSmall,
                                                     fontWeight = FontWeight.Bold,
                                                     modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                 )
                                             }
                                         }
                                     }
                                     Icon(Icons.Default.Event, contentDescription = null, tint = if (isInvalidFuture) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                 }
                             }
 
                             if (isInvalidFuture) {
                                 Text(
                                     text = "⚠️ هشدار: تاریخ ارائه خدمت برای خدمات انجام‌شده در آینده انتخاب شده است. توصیه می‌شود وضعیت خدمت را به 'برنامه‌ریزی شده' تغییر دهید.",
                                     color = MaterialTheme.colorScheme.error,
                                     style = MaterialTheme.typography.labelSmall,
                                     lineHeight = 16.sp,
                                     fontWeight = FontWeight.Bold
                                 )
                             }
 
                             // Date Difference Warning Badge
                             val regDateStart = getStartOfDay(createdTimestamp)
                             if (serviceDateStart != regDateStart) {
                                 Box(
                                     modifier = Modifier
                                         .fillMaxWidth()
                                         .background(Color(0xFFFEF3C7), shape = RoundedCornerShape(8.dp))
                                         .padding(8.dp)
                                 ) {
                                     Row(
                                         verticalAlignment = Alignment.CenterVertically,
                                         horizontalArrangement = Arrangement.spacedBy(6.dp)
                                     ) {
                                         Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                                         Text(
                                             text = "هشدار: تاریخ ارائه خدمت (${serviceDate.formatPersianDate()}) با تاریخ ثبت این سند متفاوت است.",
                                             color = Color(0xFFB45309),
                                             style = MaterialTheme.typography.labelSmall,
                                             fontWeight = FontWeight.SemiBold
                                         )
                                     }
                                 }
                             }
                        }
                    }
                }

                // Pricing Inputs
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = sellingPriceString,
                            onValueChange = { sellingPriceString = it },
                            label = { Text("تعرفه پایه خدمت") },
                            modifier = Modifier.weight(1f).testTag("reg_selling_price_input")
                        )
                        OutlinedTextField(
                            value = employeeCostString,
                            onValueChange = { employeeCostString = it },
                            label = { Text("کمیسیون همکار") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = transportationCostString,
                            onValueChange = { transportationCostString = it },
                            label = { Text("ایاب و ذهاب") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = discountString,
                            onValueChange = { discountString = it },
                            label = { Text("میزان تخفیف") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = otherCostsString,
                        onValueChange = { otherCostsString = it },
                        label = { Text("هزینه لوازم مصرفی فاکتور") },
                        modifier = Modifier.fillMaxWidth().testTag("consumables_price_input")
                    )
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "مالکیت / ذینفع هزینه لوازم مصرفی:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = consumablesOwner == "Nurse",
                                onClick = { consumablesOwner = "Nurse" },
                                label = { Text("همکار / پرستار (پیش‌فرض)") },
                                modifier = Modifier.weight(1f).testTag("owner_nurse_chip")
                            )
                            FilterChip(
                                selected = consumablesOwner == "Company",
                                onClick = { consumablesOwner = "Company" },
                                label = { Text("شرکت") },
                                modifier = Modifier.weight(1f).testTag("owner_company_chip")
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = invoiceNumber,
                            onValueChange = { invoiceNumber = it },
                            label = { Text("شماره فاکتور") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = paymentMethod,
                            onValueChange = { paymentMethod = it },
                            label = { Text("روش پرداخت") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("یادداشت و ملاحظات مراجعه") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // --- Real-Time Calculations Preview ---
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("محاسبه خودکار فاکتور و سود شرکت", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("قیمت نهایی فاکتور بیمار: ${finalPrice.formatPrice(currency)}", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            Text("کمیسیون همکار: ${employeeCommission.formatPrice(currency)}")
                            Text("سود خالص شرکت (کارمزد مرکز): ${companyProfit.formatPrice(currency)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("وضعیت تسویه بیمار:")
                        Spacer(modifier = Modifier.width(16.dp))
                        FilterChip(
                            selected = isPaid,
                            onClick = { isPaid = true },
                            label = { Text("تسویه شده") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = !isPaid,
                            onClick = { isPaid = false },
                            label = { Text("بدهکار") }
                        )
                    }
                }

                // Cashbox Selector if isPaid is true
                if (isPaid && cashboxes.isNotEmpty()) {
                    item {
                        Column {
                            Text("واریز به صندوق / بانک", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            ScrollableTabRow(
                                selectedTabIndex = cashboxes.indexOf(selectedCashbox).coerceAtLeast(0),
                                edgePadding = 0.dp,
                                divider = {}
                            ) {
                                cashboxes.forEach { cb ->
                                    Tab(
                                        selected = selectedCashbox == cb,
                                        onClick = { selectedCashbox = cb },
                                        text = { Text(cb.name) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Manual Adjustment Reason and Comment
                val isFinChanged = registration != null && (
                    sellingPrice != registration.sellingPrice ||
                    employeeCost != registration.employeeCost ||
                    transportationCost != registration.transportationCost ||
                    otherCosts != registration.otherCosts ||
                    discount != registration.discount ||
                    consumablesOwner != registration.consumablesOwner
                )

                if (isFinChanged) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "تعدیل مالی",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        "تعدیل مالی شناسایی شد. علت را انتخاب کنید:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                val presetReasons = listOf(
                                    "اصلاح اشتباه ثبت اطلاعات",
                                    "توافق با بیمار",
                                    "تخفیف مدیریتی",
                                    "اصلاح تعرفه",
                                    "تعدیل حسابداری",
                                    "اصلاح مالکیت مصرفی",
                                    "حذف رکورد تکراری",
                                    "انطباق پرداخت",
                                    "سایر"
                                )
                                var reasonExpanded by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = adjustmentReason,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("علت تعدیل مالی") },
                                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { reasonExpanded = true }
                                    )
                                    DropdownMenu(
                                        expanded = reasonExpanded,
                                        onDismissRequest = { reasonExpanded = false }
                                    ) {
                                        presetReasons.forEach { reasonItem ->
                                            DropdownMenuItem(
                                                text = { Text(reasonItem) },
                                                onClick = {
                                                    adjustmentReason = reasonItem
                                                    reasonExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = adjustmentComment,
                                    onValueChange = { adjustmentComment = it },
                                    label = { Text("توضیحات اختیاری علت تعدیل") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("انصراف")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        val todayStart = getStartOfDay(System.currentTimeMillis())
                        val serviceDateStart = getStartOfDay(serviceDate)
                        val isFuture = serviceDateStart > todayStart
                        val isInvalidFuture = isFuture && workflowStatus != "Scheduled"

                        Button(
                            onClick = {
                                val p = selectedPatient
                                val e = selectedEmployee
                                if (p != null && e != null && selectedServices.isNotEmpty()) {
                                    val finalServiceId = if (selectedServices.size == 1) selectedServices.first().id else (registration?.serviceId ?: 0)
                                    val regData = ServiceRegistration(
                                        id = registration?.id ?: 0,
                                        patientId = p.id,
                                        serviceId = finalServiceId,
                                        employeeId = e.id,
                                        dateTime = createdTimestamp,
                                        sellingPrice = sellingPrice,
                                        employeeCost = employeeCost,
                                        transportationCost = transportationCost,
                                        otherCosts = otherCosts,
                                        discount = discount,
                                        finalPrice = finalPrice,
                                        paymentMethod = paymentMethod,
                                        invoiceNumber = invoiceNumber,
                                        notes = notes,
                                        grossIncome = finalPrice,
                                        employeeCommission = employeeCost,
                                        companyProfit = companyProfit,
                                        isPaid = isPaid,
                                        workflowStatus = workflowStatus,
                                        scheduledDate = scheduledDate,
                                        serviceDate = serviceDate
                                    )
                                    onSave(
                                        regData,
                                        selectedServices.toList(),
                                        if (isPaid) selectedCashbox?.id else null,
                                        adjustmentReason,
                                        adjustmentComment
                                    )
                                }
                            },
                            enabled = selectedPatient != null && selectedEmployee != null && selectedServices.isNotEmpty(),
                            modifier = Modifier.testTag("save_registration_confirm_button")
                        ) {
                            Text(if (registration != null) "اعمال تغییرات" else "ثبت فاکتور")
                        }
                    }
                }
            }
        }
    }

    // --- Sub-Dialog Pickers ---

    if (showPatientPicker) {
        Dialog(onDismissRequest = { showPatientPicker = false }) {
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("انتخاب بیمار", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                        items(patients) { patient ->
                            Text(
                                text = patient.fullName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPatient = patient
                                        showPatientPicker = false
                                    }
                                    .padding(vertical = 12.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }

    if (showServicePicker) {
        Dialog(onDismissRequest = { showServicePicker = false }) {
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("انتخاب خدمت و افزودن", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                        items(services) { svc ->
                            Text(
                                text = svc.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedServices.add(svc)
                                        // Sum up values
                                        sellingPriceString = selectedServices.sumOf { it.sellingPrice }.toString()
                                        transportationCostString = selectedServices.maxOfOrNull { it.transportationCost }?.toString() ?: "0"
                                        discountString = selectedServices.sumOf { it.discount }.toString()
                                        otherCostsString = selectedServices.sumOf { it.consumablesCost }.toString()
                                        
                                        // Compute default employee cost
                                        employeeCostString = selectedServices.sumOf { s ->
                                            if (selectedEmployee != null && s.employeeCommission > 0.0) {
                                                if (s.employeeCommission < 100.0) {
                                                    s.sellingPrice * (s.employeeCommission / 100.0)
                                                } else {
                                                    s.employeeCommission
                                                }
                                            } else {
                                                s.defaultCost
                                            }
                                        }.toString()
                                        showServicePicker = false
                                    }
                                    .padding(vertical = 12.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEmployeePicker) {
        Dialog(onDismissRequest = { showEmployeePicker = false }) {
            Card(shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("انتخاب همکار مجری", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                        items(employees) { emp ->
                            Text(
                                text = emp.fullName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedEmployee = emp
                                        // Recalculate cost based on employee commission rate if applicable
                                        val totalCost = selectedServices.sumOf { s ->
                                            val rate = emp.commissionValue / 100.0
                                            s.sellingPrice * rate
                                        }
                                        employeeCostString = totalCost.toString()
                                        showEmployeePicker = false
                                    }
                                    .padding(vertical = 12.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }

    if (showScheduledDatePicker) {
        PersianDatePickerDialog(
            initialTimestamp = scheduledDate,
            onDismiss = { showScheduledDatePicker = false },
            onConfirm = {
                scheduledDate = it
                showScheduledDatePicker = false
                showScheduledTimePicker = true
            }
        )
    }

    if (showScheduledTimePicker) {
        PersianTimePickerDialog(
            initialTimestamp = scheduledDate,
            onDismiss = { showScheduledTimePicker = false },
            onConfirm = {
                scheduledDate = it
                showScheduledTimePicker = false
            }
        )
    }

    if (showServiceDatePicker) {
        PersianDatePickerDialog(
            initialTimestamp = serviceDate,
            onDismiss = { showServiceDatePicker = false },
            onConfirm = {
                serviceDate = it
                showServiceDatePicker = false
                showServiceTimePicker = true
            }
        )
    }

    if (showServiceTimePicker) {
        PersianTimePickerDialog(
            initialTimestamp = serviceDate,
            onDismiss = { showServiceTimePicker = false },
            onConfirm = {
                serviceDate = it
                showServiceTimePicker = false
            }
        )
    }
}

private fun getStartOfDay(time: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = time
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
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
