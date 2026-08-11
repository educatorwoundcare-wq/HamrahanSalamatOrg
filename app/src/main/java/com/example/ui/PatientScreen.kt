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
import com.example.data.Patient
import com.example.data.ServiceRegistration
import com.example.data.Referral
import com.example.data.VitalSigns
import com.example.data.WoundRecord
import com.example.data.ConsentForm
import com.example.data.Prescription
import com.example.data.ServiceSchedule
import com.example.data.NursingReport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientScreen(viewModel: HamrahanViewModel) {
    val patients by viewModel.patients.collectAsState()
    val registrations by viewModel.registrations.collectAsState()
    val referrals by viewModel.referrals.collectAsState()
    val currency by viewModel.defaultCurrency.collectAsState()
    val currentDeepLink by viewModel.currentDeepLink.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("همه") } // "همه", "فعال", "غیرفعال"
    var sortBy by remember { mutableStateOf("name") } // "name", "date", "age"

    var selectedPatientForDetail by remember { mutableStateOf<Patient?>(null) }
    var selectedPatientForEdit by remember { mutableStateOf<Patient?>(null) }
    var showAddEditDialog by remember { mutableStateOf(false) }

    LaunchedEffect(currentDeepLink, patients) {
        val link = currentDeepLink
        if (link != null && link.screen == "patients") {
            if (link.entityId != null) {
                val pat = patients.find { it.id == link.entityId }
                if (pat != null) {
                    selectedPatientForDetail = pat
                }
            } else if (link.tab == "add_patient") {
                selectedPatientForEdit = null
                showAddEditDialog = true
                viewModel.clearCurrentDeepLink()
            }
        }
    }

    // Filter and Sort Patients
    val filteredPatients = patients.filter { patient ->
        val matchesSearch = patient.fullName.contains(searchQuery, ignoreCase = true) ||
                patient.phone.contains(searchQuery) ||
                patient.address.contains(searchQuery, ignoreCase = true) ||
                patient.tags.contains(searchQuery, ignoreCase = true)

        val matchesStatus = when (statusFilter) {
            "فعال" -> patient.status == "فعال"
            "غیرفعال" -> patient.status == "غیرفعال"
            else -> true
        }

        matchesSearch && matchesStatus
    }.sortedWith { a, b ->
        when (sortBy) {
            "date" -> b.registrationDate.compareTo(a.registrationDate)
            "age" -> b.age.compareTo(a.age)
            else -> a.fullName.compareTo(b.fullName)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .testTag("patient_screen")
    ) {
        val isExpanded = maxWidth >= 720.dp

        if (isExpanded) {
            // --- MASTER-DETAIL LAYOUT (EXPANDED SCREEN) ---
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(com.example.ui.theme.DesignTokens.Spacing.m),
                horizontalArrangement = Arrangement.spacedBy(com.example.ui.theme.DesignTokens.Spacing.m)
            ) {
                // Left Pane: Patient List (~38% width)
                Surface(
                    modifier = Modifier
                        .weight(0.38f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(com.example.ui.theme.DesignTokens.Radius.l),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(com.example.ui.theme.DesignTokens.Spacing.m),
                        verticalArrangement = Arrangement.spacedBy(com.example.ui.theme.DesignTokens.Spacing.s)
                    ) {
                        com.example.ui.components.SectionHeader(
                            title = "پرونده بیماران",
                            badgeCount = filteredPatients.size,
                            icon = Icons.Default.People,
                            actionText = "+ ثبت بیمار",
                            onActionClick = {
                                selectedPatientForEdit = null
                                showAddEditDialog = true
                            }
                        )

                        com.example.ui.components.SearchToolbar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            placeholder = "جستجوی نام بیمار، تلفن، برچسب..."
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(com.example.ui.theme.DesignTokens.Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = statusFilter == "همه",
                                onClick = { statusFilter = "همه" },
                                label = { Text("همه", style = com.example.ui.theme.EnterpriseTypographyStyles.label) }
                            )
                            FilterChip(
                                selected = statusFilter == "فعال",
                                onClick = { statusFilter = "فعال" },
                                label = { Text("فعال", style = com.example.ui.theme.EnterpriseTypographyStyles.label) }
                            )
                            FilterChip(
                                selected = statusFilter == "غیرفعال",
                                onClick = { statusFilter = "غیرفعال" },
                                label = { Text("غیرفعال", style = com.example.ui.theme.EnterpriseTypographyStyles.label) }
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            IconButton(onClick = {
                                sortBy = when (sortBy) {
                                    "name" -> "date"
                                    "date" -> "age"
                                    else -> "name"
                                }
                            }) {
                                Icon(
                                    imageVector = if (sortBy == "name") Icons.Default.SortByAlpha else Icons.Default.CalendarToday,
                                    contentDescription = "مرتب‌سازی"
                                )
                            }
                        }

                        if (filteredPatients.isEmpty()) {
                            com.example.ui.components.EmptyState(
                                icon = Icons.Default.People,
                                message = "هیچ بیماری یافت نشد.",
                                description = "برای ثبت پرونده بیمار جدید، روی دکمه ثبت بیمار کلیک کنید."
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(com.example.ui.theme.DesignTokens.Spacing.s)
                            ) {
                                items(filteredPatients) { patient ->
                                    PatientCard(
                                        patient = patient,
                                        isSelected = selectedPatientForDetail?.id == patient.id,
                                        onClick = { selectedPatientForDetail = patient },
                                        onEdit = {
                                            selectedPatientForEdit = patient
                                            showAddEditDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Right Pane: Clinical Workspace (~62% width)
                Surface(
                    modifier = Modifier
                        .weight(0.62f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(com.example.ui.theme.DesignTokens.Radius.l),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    if (selectedPatientForDetail != null) {
                        val patient = selectedPatientForDetail!!
                        val patientRegs = registrations.filter { it.patientId == patient.id }

                        PatientDetailWorkspace(
                            patient = patient,
                            regs = patientRegs,
                            currency = currency,
                            viewModel = viewModel,
                            onClose = {
                                selectedPatientForDetail = null
                                viewModel.clearCurrentDeepLink()
                            }
                        )
                    } else {
                        com.example.ui.components.EmptyState(
                            icon = Icons.Default.MedicalServices,
                            message = "میز کار بالینی و پرونده بیمار",
                            description = "لطفاً جهت مشاهده پرونده بالینی، علائم حیاتی، گزارش‌ها و صورت‌حساب‌ها، یک بیمار را از لیست انتخاب کنید."
                        )
                    }
                }
            }
        } else {
            // --- COMPACT SINGLE-PANE LAYOUT ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(com.example.ui.theme.DesignTokens.Spacing.m),
                verticalArrangement = Arrangement.spacedBy(com.example.ui.theme.DesignTokens.Spacing.s)
            ) {
                com.example.ui.components.SectionHeader(
                    title = "پرونده بیماران و گیرندگان خدمت",
                    badgeCount = filteredPatients.size,
                    icon = Icons.Default.People,
                    actionText = "+ افزودن بیمار",
                    onActionClick = {
                        selectedPatientForEdit = null
                        showAddEditDialog = true
                    }
                )

                com.example.ui.components.SearchToolbar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "جستجوی نام بیمار، تلفن، برچسب..."
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(com.example.ui.theme.DesignTokens.Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = statusFilter == "همه",
                        onClick = { statusFilter = "همه" },
                        label = { Text("همه", style = com.example.ui.theme.EnterpriseTypographyStyles.label) }
                    )
                    FilterChip(
                        selected = statusFilter == "فعال",
                        onClick = { statusFilter = "فعال" },
                        label = { Text("فعال", style = com.example.ui.theme.EnterpriseTypographyStyles.label) }
                    )
                    FilterChip(
                        selected = statusFilter == "غیرفعال",
                        onClick = { statusFilter = "غیرفعال" },
                        label = { Text("غیرفعال", style = com.example.ui.theme.EnterpriseTypographyStyles.label) }
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(onClick = {
                        sortBy = when (sortBy) {
                            "name" -> "date"
                            "date" -> "age"
                            else -> "name"
                        }
                    }) {
                        Icon(
                            imageVector = if (sortBy == "name") Icons.Default.SortByAlpha else Icons.Default.CalendarToday,
                            contentDescription = "مرتب‌سازی"
                        )
                    }
                }

                if (filteredPatients.isEmpty()) {
                    com.example.ui.components.EmptyState(
                        icon = Icons.Default.People,
                        message = "هیچ بیماری یافت نشد.",
                        description = "برای ثبت پرونده بیمار جدید، روی دکمه افزودن کلیک کنید."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(com.example.ui.theme.DesignTokens.Spacing.s)
                    ) {
                        items(filteredPatients) { patient ->
                            PatientCard(
                                patient = patient,
                                isSelected = false,
                                onClick = { selectedPatientForDetail = patient },
                                onEdit = {
                                    selectedPatientForEdit = patient
                                    showAddEditDialog = true
                                }
                            )
                        }
                    }
                }
            }

            // Detail Dialog for compact devices
            if (selectedPatientForDetail != null) {
                val patient = selectedPatientForDetail!!
                val patientRegs = registrations.filter { it.patientId == patient.id }

                PatientDetailDialog(
                    patient = patient,
                    regs = patientRegs,
                    currency = currency,
                    viewModel = viewModel,
                    onDismiss = { 
                        selectedPatientForDetail = null
                        viewModel.clearCurrentDeepLink()
                    }
                )
            }
        }
    }

    // --- Add/Edit Dialog ---
    if (showAddEditDialog) {
        AddEditPatientDialog(
            patient = selectedPatientForEdit,
            onDismiss = { showAddEditDialog = false },
            onSave = { savedPatient ->
                viewModel.savePatient(savedPatient)
                showAddEditDialog = false
            },
            onDelete = { patientToDelete ->
                viewModel.deletePatient(patientToDelete)
                showAddEditDialog = false
            },
            referrals = referrals,
            viewModel = viewModel
        )
    }
}

@Composable
fun PatientCard(
    patient: Patient,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    com.example.ui.components.EnterpriseCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("patient_card_${patient.id}"),
        onClick = onClick,
        borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
        contentPadding = PaddingValues(com.example.ui.theme.DesignTokens.Spacing.m)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(com.example.ui.theme.DesignTokens.Spacing.s)
                ) {
                    Text(
                        text = patient.fullName.toPersianDigits(),
                        style = com.example.ui.theme.EnterpriseTypographyStyles.sectionHeader,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    com.example.ui.components.StatusBadge(
                        text = patient.status,
                        statusType = if (patient.status == "فعال") com.example.ui.components.EnterpriseStatusType.ACTIVE else com.example.ui.components.EnterpriseStatusType.ERROR
                    )
                }

                Spacer(modifier = Modifier.height(com.example.ui.theme.DesignTokens.Spacing.xs))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(com.example.ui.theme.DesignTokens.Spacing.m)
                ) {
                    Text(
                        text = "سن: ${patient.age} سال".toPersianDigits(),
                        style = com.example.ui.theme.EnterpriseTypographyStyles.supportingText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "تلفن: ${patient.phone}".toPersianDigits(),
                        style = com.example.ui.theme.EnterpriseTypographyStyles.supportingText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (patient.address.isNotBlank()) {
                    Spacer(modifier = Modifier.height(com.example.ui.theme.DesignTokens.Spacing.xs))
                    Text(
                        text = "نشانی: ${patient.address}".toPersianDigits(),
                        style = com.example.ui.theme.EnterpriseTypographyStyles.supportingText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onEdit,
                modifier = Modifier.testTag("edit_patient_button_${patient.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "ویرایش",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
        }
    }
}

fun normalizeString(input: String): String {
    return input.lowercase(java.util.Locale.ROOT)
        .replace("ي", "ی")
        .replace("ك", "ک")
        .replace("‌", " ")
        .trim()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPatientDialog(
    patient: Patient?,
    onDismiss: () -> Unit,
    onSave: (Patient) -> Unit,
    onDelete: ((Patient) -> Unit)? = null,
    referrals: List<Referral> = emptyList(),
    viewModel: HamrahanViewModel
) {
    var fullName by remember { mutableStateOf(patient?.fullName ?: "") }
    var gender by remember { mutableStateOf(patient?.gender ?: "مرد") }
    var ageString by remember { mutableStateOf(patient?.age?.toString() ?: "") }
    var phone by remember { mutableStateOf(patient?.phone ?: "") }
    var address by remember { mutableStateOf(patient?.address ?: "") }
    var referralQuery by remember { mutableStateOf(referrals.find { it.id == patient?.referralId }?.name ?: patient?.referralSource ?: "") }
    var selectedReferral by remember { mutableStateOf<Referral?>(referrals.find { it.id == patient?.referralId }) }
    var referralDropdownExpanded by remember { mutableStateOf(false) }
    var showQuickAddReferralDialog by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf(patient?.status ?: "فعال") }
    var notes by remember { mutableStateOf(patient?.notes ?: "") }
    var tags by remember { mutableStateOf(patient?.tags ?: "") }

    var hasError by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val activeReferrals = referrals.filter { it.isActive || it.id == selectedReferral?.id }
    val filteredReferrals = remember(referralQuery, activeReferrals) {
        val q = normalizeString(referralQuery)
        if (q.isBlank()) {
            activeReferrals
        } else {
            activeReferrals.filter { ref ->
                normalizeString(ref.name).contains(q) || normalizeString(ref.phone).contains(q)
            }
        }
    }

    if (showDeleteConfirmation && patient != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("حذف پرونده بیمار", fontWeight = FontWeight.Bold) },
            text = { Text("آیا از حذف پرونده بیمار «${patient.fullName}» اطمینان دارید؟ این عمل غیرقابل بازگشت است.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(patient)
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

    if (showQuickAddReferralDialog) {
        var quickName by remember { mutableStateOf(referralQuery) }
        var quickType by remember { mutableStateOf("پزشک") }
        var quickPhone by remember { mutableStateOf("") }
        var quickAddress by remember { mutableStateOf("") }
        var quickPercentage by remember { mutableStateOf("0") }
        var quickFixedAmount by remember { mutableStateOf("0") }
        var quickNotes by remember { mutableStateOf("") }

        var quickDuplicateToResolve by remember { mutableStateOf<Referral?>(null) }

        if (quickDuplicateToResolve != null) {
            val existing = quickDuplicateToResolve!!
            AlertDialog(
                onDismissRequest = { quickDuplicateToResolve = null },
                title = { Text("معرف احتمالی تکراری یافت شد") },
                text = {
                    Text("یک معرف با مشخصات مشابه در سیستم وجود دارد:\n\n" +
                         "نام: ${existing.name}\n" +
                         "تلفن: ${existing.phone}\n" +
                         "نوع: ${existing.type}\n\n" +
                         "آیا مایلید از معرف موجود استفاده کنید؟")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            selectedReferral = existing
                            referralQuery = existing.name
                            quickDuplicateToResolve = null
                            showQuickAddReferralDialog = false
                            validationError = null
                        }
                    ) {
                        Text("بله، استفاده از معرف موجود")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewModel.insertReferral(
                                name = quickName,
                                type = quickType,
                                phone = quickPhone,
                                address = quickAddress,
                                commissionPercentage = quickPercentage.toDoubleOrNull() ?: 0.0,
                                commissionFixedAmount = quickFixedAmount.toDoubleOrNull() ?: 0.0,
                                notes = quickNotes,
                                onComplete = { newId ->
                                    val newRef = Referral(
                                        id = newId.toInt(),
                                        name = quickName,
                                        type = quickType,
                                        phone = quickPhone,
                                        address = quickAddress,
                                        commissionPercentage = quickPercentage.toDoubleOrNull() ?: 0.0,
                                        commissionFixedAmount = quickFixedAmount.toDoubleOrNull() ?: 0.0,
                                        notes = quickNotes,
                                        isActive = true
                                    )
                                    selectedReferral = newRef
                                    referralQuery = quickName
                                    validationError = null
                                }
                            )
                            quickDuplicateToResolve = null
                            showQuickAddReferralDialog = false
                        }
                    ) {
                        Text("خیر، ایجاد معرف جدید")
                    }
                }
            )
        }

        Dialog(onDismissRequest = { showQuickAddReferralDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "ثبت سریع معرف جدید",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    item {
                        OutlinedTextField(value = quickName, onValueChange = { quickName = it }, label = { Text("نام و نام خانوادگی معرف") }, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        OutlinedTextField(value = quickType, onValueChange = { quickType = it }, label = { Text("نوع (مثلا: پزشک، درمانگاه)") }, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        OutlinedTextField(value = quickPhone, onValueChange = { quickPhone = it }, label = { Text("شماره تماس") }, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        OutlinedTextField(value = quickAddress, onValueChange = { quickAddress = it }, label = { Text("آدرس مطب / دفتر") }, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        OutlinedTextField(value = quickPercentage, onValueChange = { quickPercentage = it }, label = { Text("درصد پورسانت") }, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        OutlinedTextField(value = quickFixedAmount, onValueChange = { quickFixedAmount = it }, label = { Text("پورسانت ثابت") }, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        OutlinedTextField(value = quickNotes, onValueChange = { quickNotes = it }, label = { Text("یادداشت / توضیحات") }, modifier = Modifier.fillMaxWidth())
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showQuickAddReferralDialog = false }) {
                                Text("انصراف")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = {
                                val normalizedName = normalizeString(quickName)
                                val normalizedPhone = if (quickPhone.isNotBlank()) normalizeString(quickPhone) else ""
                                val probableDuplicate = referrals.find { r ->
                                    normalizeString(r.name) == normalizedName || (normalizedPhone.isNotEmpty() && normalizeString(r.phone) == normalizedPhone)
                                }

                                if (probableDuplicate != null) {
                                    quickDuplicateToResolve = probableDuplicate
                                } else {
                                    viewModel.insertReferral(
                                        name = quickName,
                                        type = quickType,
                                        phone = quickPhone,
                                        address = quickAddress,
                                        commissionPercentage = quickPercentage.toDoubleOrNull() ?: 0.0,
                                        commissionFixedAmount = quickFixedAmount.toDoubleOrNull() ?: 0.0,
                                        notes = quickNotes,
                                        onComplete = { newId ->
                                            val newRef = Referral(
                                                id = newId.toInt(),
                                                name = quickName,
                                                type = quickType,
                                                phone = quickPhone,
                                                address = quickAddress,
                                                commissionPercentage = quickPercentage.toDoubleOrNull() ?: 0.0,
                                                commissionFixedAmount = quickFixedAmount.toDoubleOrNull() ?: 0.0,
                                                notes = quickNotes,
                                                isActive = true
                                            )
                                            selectedReferral = newRef
                                            referralQuery = quickName
                                            validationError = null
                                        }
                                    )
                                    showQuickAddReferralDialog = false
                                }
                            }) {
                                Text("ثبت و انتخاب")
                            }
                        }
                    }
                }
            }
        }
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
                        text = if (patient == null) "ثبت پرونده بیمار جدید" else "ویرایش پرونده بیمار",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("نام و نام خانوادگی بیمار") },
                        isError = hasError && fullName.isBlank(),
                        modifier = Modifier.fillMaxWidth().testTag("patient_name_input")
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = ageString,
                            onValueChange = { ageString = it },
                            label = { Text("سن بیمار (سال)") },
                            modifier = Modifier.weight(1f)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("جنسیت", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = gender == "مرد", onClick = { gender = "مرد" })
                                Text("مرد")
                                Spacer(modifier = Modifier.width(8.dp))
                                RadioButton(selected = gender == "زن", onClick = { gender = "زن" })
                                Text("زن")
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("تلفن همراه بیمار") },
                        isError = hasError && phone.isBlank(),
                        modifier = Modifier.fillMaxWidth().testTag("patient_phone_input")
                    )
                }

                item {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("نشانی دقیق محل سکونت") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = referralQuery,
                            onValueChange = { 
                                referralQuery = it
                                selectedReferral = null
                                referralDropdownExpanded = true
                                validationError = null
                            },
                            label = { Text("منبع ارجاع یا معرف") },
                            modifier = Modifier.fillMaxWidth().testTag("referral_search_input"),
                            isError = validationError != null,
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (referralQuery.isNotEmpty() || selectedReferral != null) {
                                        IconButton(onClick = {
                                            referralQuery = ""
                                            selectedReferral = null
                                            referralDropdownExpanded = false
                                        }) {
                                            Icon(Icons.Default.Clear, contentDescription = "پاک کردن")
                                        }
                                    }
                                    IconButton(onClick = { referralDropdownExpanded = !referralDropdownExpanded }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                }
                            }
                        )
                        DropdownMenu(
                            expanded = referralDropdownExpanded,
                            onDismissRequest = { referralDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            DropdownMenuItem(
                                text = { Text("هیچکدام (بدون معرف)") },
                                onClick = {
                                    selectedReferral = null
                                    referralQuery = ""
                                    referralDropdownExpanded = false
                                    validationError = null
                                }
                            )
                            if (filteredReferrals.isEmpty() && referralQuery.isNotBlank()) {
                                DropdownMenuItem(
                                    text = { Text("➕ ایجاد معرف جدید: «$referralQuery»") },
                                    onClick = {
                                        showQuickAddReferralDialog = true
                                        referralDropdownExpanded = false
                                    }
                                )
                            } else {
                                filteredReferrals.forEach { ref ->
                                    DropdownMenuItem(
                                        text = { Text(ref.name + " (" + ref.type + ")") },
                                        onClick = {
                                            selectedReferral = ref
                                            referralQuery = ref.name
                                            referralDropdownExpanded = false
                                            validationError = null
                                        }
                                    )
                                }
                                if (referralQuery.isNotBlank() && !filteredReferrals.any { normalizeString(it.name) == normalizeString(referralQuery) }) {
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("➕ ایجاد معرف جدید: «$referralQuery»") },
                                        onClick = {
                                            showQuickAddReferralDialog = true
                                            referralDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (validationError != null) {
                        Text(
                            text = validationError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = tags,
                        onValueChange = { tags = it },
                        label = { Text("برچسب‌ها (جدا شده با کاما، مانند: دیابت، قلبی)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("توضیحات و سوابق خاص پزشکی") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("وضعیت پرونده:")
                        Spacer(modifier = Modifier.width(16.dp))
                        FilterChip(
                            selected = status == "فعال",
                            onClick = { status = "فعال" },
                            label = { Text("فعال") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = status == "غیرفعال",
                            onClick = { status = "غیرفعال" },
                            label = { Text("غیرفعال") }
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (patient != null && onDelete != null) {
                            TextButton(
                                onClick = { showDeleteConfirmation = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("حذف پرونده")
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
                                    if (fullName.isBlank() || phone.isBlank()) {
                                        hasError = true
                                    } else if (referralQuery.isNotBlank() && selectedReferral == null) {
                                        validationError = "برای ثبت صحیح پورسانت، لطفاً معرف را از فهرست انتخاب کنید یا معرف جدید ایجاد نمایید."
                                    } else {
                                        val saved = Patient(
                                            id = patient?.id ?: 0,
                                            fullName = fullName,
                                            gender = gender,
                                            age = ageString.toIntOrNull() ?: 0,
                                            phone = phone,
                                            address = address,
                                            referralSource = selectedReferral?.name ?: "",
                                            referralId = selectedReferral?.id,
                                            status = status,
                                            notes = notes,
                                            tags = tags,
                                             uuid = patient?.uuid ?: java.util.UUID.randomUUID().toString(),
                                            registrationDate = patient?.registrationDate ?: System.currentTimeMillis()
                                        )
                                        onSave(saved)
                                    }
                                },
                                modifier = Modifier.testTag("save_patient_confirm_button")
                            ) {
                                Text("ذخیره پرونده")
                            }
                        }
                    }
                }
            }
        }
    }
}

val NursingReport.status: String
    get() = when {
        description.startsWith("[تایید شده]") -> "Approved"
        description.startsWith("[نیاز به اصلاح]") -> "NeedsCorrection"
        description.startsWith("[آرشیو شده]") -> "Archived"
        else -> "Draft"
    }

val NursingReport.clinicalNotes: String
    get() = if (description.contains("] ")) description.substringAfter("] ") else description

fun NursingReport.copyWithStatus(newStatus: String): NursingReport {
    val cleanNotes = this.clinicalNotes
    val prefix = when (newStatus) {
        "Approved" -> "[تایید شده] "
        "NeedsCorrection" -> "[نیاز به اصلاح] "
        "Archived" -> "[آرشیو شده] "
        else -> ""
    }
    return this.copy(description = prefix + cleanNotes)
}

data class TimelineItem(
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val timestamp: Long,
    val status: String,
    val creator: String,
    val color: Color,
    val onClick: () -> Unit
)

@Composable
fun PatientDetailDialog(
    patient: Patient,
    regs: List<ServiceRegistration>,
    currency: String,
    viewModel: HamrahanViewModel,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(com.example.ui.theme.DesignTokens.Radius.l),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            PatientDetailWorkspace(
                patient = patient,
                regs = regs,
                currency = currency,
                viewModel = viewModel,
                onClose = onDismiss
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDetailWorkspace(
    patient: Patient,
    regs: List<ServiceRegistration>,
    currency: String,
    viewModel: HamrahanViewModel,
    onClose: (() -> Unit)? = null
) {
    val totalBilled = regs.sumOf { it.finalPrice }
    val totalPaid = regs.filter { it.isPaid }.sumOf { it.finalPrice }
    val outstandingBalance = totalBilled - totalPaid

    val employees by viewModel.employees.collectAsState()
    val vitalSignsList by viewModel.vitalSigns.collectAsState()
    val woundRecordsList by viewModel.woundRecords.collectAsState()
    val consentFormsList by viewModel.consentForms.collectAsState()
    val prescriptionsList by viewModel.prescriptions.collectAsState()
    val serviceSchedulesList by viewModel.serviceSchedules.collectAsState()
    val nursingReportsList by viewModel.nursingReports.collectAsState()

    val patientVitalSigns = vitalSignsList.filter { it.patientId == patient.id }
    val patientWoundRecords = woundRecordsList.filter { it.patientId == patient.id }
    val patientConsentForms = consentFormsList.filter { it.patientId == patient.id }
    val patientPrescriptions = prescriptionsList.filter { it.patientId == patient.id }
    val patientRegIds = regs.map { it.id }
    val patientSchedules = serviceSchedulesList.filter { it.registrationId in patientRegIds }
    val patientReports = nursingReportsList.filter { it.registrationId in patientRegIds }

    var activeTab by remember { mutableStateOf(0) } // 0 = خلاصه پرونده, 1 = علائم حیاتی و زخم, 2 = نسخ و رضایت‌نامه, 3 = برنامه‌ریزی و گزارشات, 4 = تایم‌لاین پرونده
    val timelineItems = remember(regs, patientVitalSigns, patientWoundRecords, patientConsentForms, patientPrescriptions, patientSchedules, patientReports, employees) {
        val items = mutableListOf<TimelineItem>()
        
        // 1. Services & Financial Events
        regs.forEach { r ->
            val empName = employees.find { it.id == r.employeeId }?.fullName ?: "نامشخص"
            val serviceName = viewModel.services.value.find { it.id == r.serviceId }?.name ?: "خدمت پرستاری"
            
            items.add(
                TimelineItem(
                    title = "ارائه خدمت: $serviceName",
                    subtitle = "جزئیات: ${r.notes.ifBlank { "ثبت بدون یادداشت" }}",
                    icon = Icons.Default.MedicalServices,
                    timestamp = r.serviceDate,
                    status = if (r.workflowStatus == "Scheduled") "برنامه‌ریزی شده" else "انجام شده",
                    creator = empName,
                    color = Color(0xFF3B82F6),
                    onClick = { viewModel.handleDeepLink("services?id=${r.id}") }
                )
            )
            
            items.add(
                TimelineItem(
                    title = "فاکتور مالی: $serviceName",
                    subtitle = "مبلغ: ${r.finalPrice.formatPrice(currency)}",
                    icon = Icons.Default.Payments,
                    timestamp = r.serviceDate,
                    status = if (r.isPaid) "تسویه شده" else "بدهکار",
                    creator = "صندوق / امور مالی",
                    color = if (r.isPaid) Color(0xFF10B981) else Color(0xFFEF4444),
                    onClick = { viewModel.handleDeepLink("accounting?id=${r.id}") }
                )
            )
        }
        
        // 2. Vital Signs
        patientVitalSigns.forEach { v ->
            items.add(
                TimelineItem(
                    title = "ثبت علائم حیاتی",
                    subtitle = "فشار: ${v.bloodPressureSystolic}/${v.bloodPressureDiastolic} | ضربان: ${v.heartRate} | اکسیژن: ${v.oxygenSaturation}% | تب: ${v.temperatureCelsius}°C",
                    icon = Icons.Default.Favorite,
                    timestamp = v.date,
                    status = "نرمال/پایدار",
                    creator = "پرستار مجری",
                    color = Color(0xFFEC4899),
                    onClick = { activeTab = 1 }
                )
            )
        }
        
        // 3. Wounds
        patientWoundRecords.forEach { w ->
            items.add(
                TimelineItem(
                    title = "پایش و پانسمان زخم: ${w.woundType}",
                    subtitle = "درجه: ${w.stage} | توضیحات: ${w.description}",
                    icon = Icons.Default.Healing,
                    timestamp = w.date,
                    status = "در حال درمان",
                    creator = "کارشناس زخم",
                    color = Color(0xFFF59E0B),
                    onClick = { activeTab = 1 }
                )
            )
        }
        
        // 4. Prescriptions
        patientPrescriptions.forEach { p ->
            items.add(
                TimelineItem(
                    title = "ثبت نسخه دارویی پزشک",
                    subtitle = "پزشک: ${p.doctorName} | داروها: ${p.medicineList}",
                    icon = Icons.Default.Medication,
                    timestamp = p.date,
                    status = "ثبت نهایی",
                    creator = "پزشک معالج",
                    color = Color(0xFF8B5CF6),
                    onClick = { activeTab = 2 }
                )
            )
        }
        
        // 5. Consent Forms
        patientConsentForms.forEach { c ->
            items.add(
                TimelineItem(
                    title = "فرم رضایت‌نامه بیمار",
                    subtitle = c.title,
                    icon = Icons.Default.FactCheck,
                    timestamp = c.date,
                    status = if (c.isSigned) "امضا شده" else "در انتظار امضا",
                    creator = "سوپروایزر مرکز",
                    color = Color(0xFF10B981),
                    onClick = { activeTab = 2 }
                )
            )
        }
        
        // 6. Schedules / Appointments
        patientSchedules.forEach { s ->
            val empName = employees.find { it.id == s.employeeId }?.fullName ?: "نامشخص"
            items.add(
                TimelineItem(
                    title = "زمان‌بندی نوبت خدمت",
                    subtitle = "نوبت خدمت ثبت شده",
                    icon = Icons.Default.Event,
                    timestamp = s.scheduledDate,
                    status = if (s.status == "Completed") "انجام شده" else s.status,
                    creator = empName,
                    color = Color(0xFF6366F1),
                    onClick = { activeTab = 3 }
                )
            )
        }
        
        // 7. Nursing Reports
        patientReports.forEach { r ->
            items.add(
                TimelineItem(
                    title = "ثبت گزارش بالینی سوپروایزر",
                    subtitle = r.description,
                    icon = Icons.Default.Description,
                    timestamp = r.date,
                    status = "تایید علمی شده",
                    creator = r.reporterName,
                    color = Color(0xFF0D9488),
                    onClick = { activeTab = 3 }
                )
            )
        }
        
        items.sortedByDescending { it.timestamp }
    }

    val currentDeepLink by viewModel.currentDeepLink.collectAsState()

    LaunchedEffect(currentDeepLink) {
        val link = currentDeepLink
        if (link != null && link.screen == "patients" && link.entityId == patient.id) {
            when (link.tab) {
                "consent" -> activeTab = 2
                "meds" -> activeTab = 2
            }
        }
    }

    // Dialog state for adding clinical forms
    var showAddVitalSigns by remember { mutableStateOf(false) }
    var showAddWoundRecord by remember { mutableStateOf(false) }
    var showAddConsentForm by remember { mutableStateOf(false) }
    var showAddPrescription by remember { mutableStateOf(false) }
    var showAddScheduleForReg by remember { mutableStateOf<ServiceRegistration?>(null) }
    var showAddReportForReg by remember { mutableStateOf<ServiceRegistration?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(com.example.ui.theme.DesignTokens.Spacing.m),
        verticalArrangement = Arrangement.spacedBy(com.example.ui.theme.DesignTokens.Spacing.s)
    ) {
        // Patient Clinical Header Card
        com.example.ui.components.EnterpriseCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
            borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            contentPadding = PaddingValues(com.example.ui.theme.DesignTokens.Spacing.m)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(com.example.ui.theme.DesignTokens.Spacing.m),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), shape = RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(com.example.ui.theme.DesignTokens.Spacing.xs)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(com.example.ui.theme.DesignTokens.Spacing.s)
                        ) {
                            Text(
                                text = patient.fullName.toPersianDigits(),
                                style = com.example.ui.theme.EnterpriseTypographyStyles.sectionHeader,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            com.example.ui.components.StatusBadge(
                                text = patient.status,
                                statusType = if (patient.status == "فعال") com.example.ui.components.EnterpriseStatusType.ACTIVE else com.example.ui.components.EnterpriseStatusType.ERROR
                            )
                        }

                        Text(
                            text = "سن: ${patient.age} سال | جنسیت: ${patient.gender} | تلفن: ${patient.phone}".toPersianDigits(),
                            style = com.example.ui.theme.EnterpriseTypographyStyles.supportingText,
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
        }

        // Financial KPIs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(com.example.ui.theme.DesignTokens.Spacing.s)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                com.example.ui.components.KPICard(
                    title = "کل خدمات",
                    value = totalBilled.formatPrice(currency),
                    icon = Icons.Default.ReceiptLong
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                com.example.ui.components.KPICard(
                    title = "مبلغ دریافتی",
                    value = totalPaid.formatPrice(currency),
                    icon = Icons.Default.Payments,
                    iconTint = Color(0xFF16A34A)
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                com.example.ui.components.KPICard(
                    title = "مانده بدهی",
                    value = outstandingBalance.formatPrice(currency),
                    icon = Icons.Default.AccountBalanceWallet,
                    iconTint = if (outstandingBalance > 0) Color(0xFFDC2626) else Color(0xFF16A34A)
                )
            }
        }

        // Tab Row
        ScrollableTabRow(
            selectedTabIndex = activeTab,
            edgePadding = 0.dp,
            containerColor = Color.Transparent,
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
        ) {
            Tab(selected = activeTab == 0, onClick = { activeTab = 0 }) {
                Text("خلاصه پرونده", modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp), style = com.example.ui.theme.EnterpriseTypographyStyles.sectionHeader)
            }
            Tab(selected = activeTab == 1, onClick = { activeTab = 1 }) {
                Text("علائم حیاتی و زخم", modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp), style = com.example.ui.theme.EnterpriseTypographyStyles.sectionHeader)
            }
            Tab(selected = activeTab == 2, onClick = { activeTab = 2 }) {
                Text("نسخ و رضایت‌نامه", modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp), style = com.example.ui.theme.EnterpriseTypographyStyles.sectionHeader)
            }
            Tab(selected = activeTab == 3, onClick = { activeTab = 3 }) {
                Text("برنامه‌ریزی و گزارشات", modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp), style = com.example.ui.theme.EnterpriseTypographyStyles.sectionHeader)
            }
            Tab(selected = activeTab == 4, onClick = { activeTab = 4 }) {
                Text("تایم‌لاین پرونده", modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp), style = com.example.ui.theme.EnterpriseTypographyStyles.sectionHeader)
            }
        }

        // Tab Content
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(com.example.ui.theme.DesignTokens.Spacing.s)
        ) {
            if (activeTab == 0) {
                // Tab 0: Profile Summary & Registrations
                item {
                    com.example.ui.components.EnterpriseCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(com.example.ui.theme.DesignTokens.Spacing.xs)) {
                            Text("اطلاعات شناسه و نشانی", style = com.example.ui.theme.EnterpriseTypographyStyles.sectionHeader)
                            Text("نشانی: ${patient.address.ifBlank { "ثبت نشده" }}".toPersianDigits(), style = com.example.ui.theme.EnterpriseTypographyStyles.bodyText)
                            Text("معرف: ${patient.referralSource.ifBlank { "مستقیم" }}".toPersianDigits(), style = com.example.ui.theme.EnterpriseTypographyStyles.bodyText)
                            if (patient.notes.isNotBlank()) {
                                Text("سوابق و ملاحظات: ${patient.notes}".toPersianDigits(), style = com.example.ui.theme.EnterpriseTypographyStyles.supportingText, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                item {
                    com.example.ui.components.SectionHeader(
                        title = "فهرست مراجعات و فاکتورها",
                        badgeCount = regs.size,
                        icon = Icons.Default.Receipt
                    )
                }

                if (regs.isEmpty()) {
                    item {
                        Text("هیچ خدمت یا مراجعتی برای این بیمار ثبت نشده است.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    items(regs) { reg ->
                        com.example.ui.components.EnterpriseCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.handleDeepLink("services?id=${reg.id}") }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("فاکتور #${reg.invoiceNumber}".toPersianDigits(), fontWeight = FontWeight.Bold, style = com.example.ui.theme.EnterpriseTypographyStyles.bodyText)
                                    Text("تاریخ: ${reg.serviceDate.formatDate()}".toPersianDigits(), style = com.example.ui.theme.EnterpriseTypographyStyles.supportingText)
                                    Text("مبلغ: ${reg.finalPrice.formatPrice(currency)}".toPersianDigits(), style = com.example.ui.theme.EnterpriseTypographyStyles.bodyText, fontWeight = FontWeight.SemiBold)
                                }
                                com.example.ui.components.StatusBadge(
                                    text = if (reg.isPaid) "تسویه شده" else "بدهکار",
                                    statusType = if (reg.isPaid) com.example.ui.components.EnterpriseStatusType.ACTIVE else com.example.ui.components.EnterpriseStatusType.ERROR
                                )
                            }
                        }
                    }
                }
            } else if (activeTab == 1) {
                // Tab 1: Vitals & Wounds
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("علائم حیاتی ثبت شده", style = com.example.ui.theme.EnterpriseTypographyStyles.sectionHeader)
                        TextButton(onClick = { showAddVitalSigns = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ثبت علائم جدید")
                        }
                    }
                }

                if (patientVitalSigns.isEmpty()) {
                    item {
                        Text("هیچ سابقه علائم حیاتی برای این بیمار ثبت نشده است.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    items(patientVitalSigns) { vitals ->
                        com.example.ui.components.EnterpriseCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("فشار خون: ${vitals.bloodPressureSystolic}/${vitals.bloodPressureDiastolic} | ضربان: ${vitals.heartRate}".toPersianDigits(), fontWeight = FontWeight.Bold)
                                    Text("اکسیژن خون SpO2: ${vitals.oxygenSaturation}% | تب: ${vitals.temperatureCelsius}°C".toPersianDigits(), style = com.example.ui.theme.EnterpriseTypographyStyles.supportingText)
                                    Text("تاریخ: ${vitals.date.formatDate()}".toPersianDigits(), style = com.example.ui.theme.EnterpriseTypographyStyles.supportingText, color = Color.Gray)
                                }
                                IconButton(onClick = { viewModel.deleteVitalSigns(vitals) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Red, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("پایش و بهبود روند زخم", style = com.example.ui.theme.EnterpriseTypographyStyles.sectionHeader)
                        TextButton(onClick = { showAddWoundRecord = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ثبت زخم")
                        }
                    }
                }

                if (patientWoundRecords.isEmpty()) {
                    item {
                        Text("هیچ سابقه پایش زخمی وجود ندارد.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    items(patientWoundRecords) { wound ->
                        com.example.ui.components.EnterpriseCard(modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("نوع زخم: ${wound.woundType} | گرید ${wound.stage}".toPersianDigits(), fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { viewModel.deleteWoundRecord(wound) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Red, modifier = Modifier.size(18.dp))
                                    }
                                }
                                Text("توضیحات بالینی: ${wound.description}".toPersianDigits(), style = com.example.ui.theme.EnterpriseTypographyStyles.bodyText)
                                Text("تاریخ ثبت: ${wound.date.formatDate()}".toPersianDigits(), style = com.example.ui.theme.EnterpriseTypographyStyles.supportingText, color = Color.Gray)
                            }
                        }
                    }
                }
            } else if (activeTab == 2) {
                // Tab 2: Consents & Prescriptions
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("فرم‌های رضایت‌نامه بیمار", style = com.example.ui.theme.EnterpriseTypographyStyles.sectionHeader)
                        TextButton(onClick = { showAddConsentForm = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("افزودن رضایت")
                        }
                    }
                }

                if (patientConsentForms.isEmpty()) {
                    item {
                        Text("هیچ فرم رضایت‌نامه‌ای ثبت نشده است.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    items(patientConsentForms) { consent ->
                        com.example.ui.components.EnterpriseCard(modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(consent.title.toPersianDigits(), fontWeight = FontWeight.Bold)
                                    com.example.ui.components.StatusBadge(
                                        text = if (consent.isSigned) "امضا شده" else "منتظر امضا",
                                        statusType = if (consent.isSigned) com.example.ui.components.EnterpriseStatusType.COMPLETED else com.example.ui.components.EnterpriseStatusType.WARNING
                                    )
                                }
                                Text(consent.content.toPersianDigits(), style = com.example.ui.theme.EnterpriseTypographyStyles.bodyText, color = Color.DarkGray)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("تاریخ: ${consent.date.formatDate()}".toPersianDigits(), style = com.example.ui.theme.EnterpriseTypographyStyles.supportingText, color = Color.Gray)
                                    IconButton(onClick = { viewModel.deleteConsentForm(consent) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Red, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("نسخه‌های تجویز شده پزشک", style = com.example.ui.theme.EnterpriseTypographyStyles.sectionHeader)
                        TextButton(onClick = { showAddPrescription = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("افزودن نسخه")
                        }
                    }
                }

                if (patientPrescriptions.isEmpty()) {
                    item {
                        Text("هیچ نسخه‌ای تاکنون در سیستم ثبت نشده است.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    items(patientPrescriptions) { prescription ->
                        com.example.ui.components.EnterpriseCard(modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("پزشک معالج: ${prescription.doctorName}".toPersianDigits(), fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { viewModel.deletePrescription(prescription) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Red, modifier = Modifier.size(18.dp))
                                    }
                                }
                                Text("اقلام دارویی: ${prescription.medicineList}".toPersianDigits(), style = com.example.ui.theme.EnterpriseTypographyStyles.bodyText)
                                Text("تاریخ ثبت: ${prescription.date.formatDate()}".toPersianDigits(), style = com.example.ui.theme.EnterpriseTypographyStyles.supportingText, color = Color.Gray)
                            }
                        }
                    }
                }
            } else if (activeTab == 3) {
                // Tab 3: Schedules & Nursing Reports
                item {
                    Text("زمان‌بندی مراجعات و تخصیص پرستار", style = com.example.ui.theme.EnterpriseTypographyStyles.sectionHeader)
                }

                if (patientSchedules.isEmpty()) {
                    item {
                        Text("هیچ برنامه‌ریزی فعالی برای مراجعات این بیمار وجود ندارد.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    items(patientSchedules) { sched ->
                        val nurse = employees.find { it.id == sched.employeeId }
                        com.example.ui.components.EnterpriseCard(modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("پرستار معالج: ${nurse?.fullName ?: "نامشخص"}".toPersianDigits(), fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { viewModel.deleteServiceSchedule(sched) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Red, modifier = Modifier.size(18.dp))
                                    }
                                }
                                Text("تاریخ: ${sched.scheduledDate.formatDate()}".toPersianDigits(), style = com.example.ui.theme.EnterpriseTypographyStyles.supportingText)
                                
                                val (statusText, statusType) = when (sched.status) {
                                    "Started" -> "در حال ارائه خدمت" to com.example.ui.components.EnterpriseStatusType.SYNCING
                                    "Completed" -> "تکمیل شده" to com.example.ui.components.EnterpriseStatusType.COMPLETED
                                    "Cancelled" -> "لغو شده" to com.example.ui.components.EnterpriseStatusType.ERROR
                                    else -> "برنامه‌ریزی شده" to com.example.ui.components.EnterpriseStatusType.WARNING
                                }
                                com.example.ui.components.StatusBadge(text = statusText, statusType = statusType)

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                    if (sched.status == "Scheduled" || sched.status.isBlank()) {
                                        Button(
                                            onClick = { viewModel.saveServiceSchedule(sched.copy(status = "Started")) },
                                            modifier = Modifier.weight(1f).height(32.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("شروع خدمت", style = com.example.ui.theme.EnterpriseTypographyStyles.label)
                                        }
                                    }
                                    if (sched.status == "Started") {
                                        Button(
                                            onClick = { viewModel.saveServiceSchedule(sched.copy(status = "Completed")) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                            modifier = Modifier.weight(1f).height(32.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("اتمام خدمت", style = com.example.ui.theme.EnterpriseTypographyStyles.label)
                                        }
                                    }
                                    if (sched.status != "Completed" && sched.status != "Cancelled") {
                                        Button(
                                            onClick = { viewModel.saveServiceSchedule(sched.copy(status = "Cancelled")) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                            modifier = Modifier.weight(1f).height(32.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("لغو", style = com.example.ui.theme.EnterpriseTypographyStyles.label)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("گزارشات بالینی پرستاری", style = com.example.ui.theme.EnterpriseTypographyStyles.sectionHeader)
                }

                if (patientReports.isEmpty()) {
                    item {
                        Text("هیچ گزارش بالینی ثبت نشده است.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    items(patientReports) { report ->
                        com.example.ui.components.EnterpriseCard(modifier = Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("گزارش بالینی", fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { viewModel.deleteNursingReport(report) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Red, modifier = Modifier.size(18.dp))
                                    }
                                }
                                Text("اقدامات: ${report.clinicalNotes}".toPersianDigits(), style = com.example.ui.theme.EnterpriseTypographyStyles.bodyText)
                                
                                val (repText, repType) = when (report.status) {
                                    "Approved" -> "تایید شده سوپروایزر" to com.example.ui.components.EnterpriseStatusType.COMPLETED
                                    "NeedsCorrection" -> "نیازمند اصلاح" to com.example.ui.components.EnterpriseStatusType.WARNING
                                    "Archived" -> "آرشیو شده" to com.example.ui.components.EnterpriseStatusType.NEUTRAL
                                    else -> "در انتظار تایید" to com.example.ui.components.EnterpriseStatusType.NEUTRAL
                                }
                                com.example.ui.components.StatusBadge(text = repText, statusType = repType)

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                    Button(
                                        onClick = { viewModel.saveNursingReport(report.copyWithStatus("Approved")) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                        modifier = Modifier.weight(1f).height(30.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("تایید", style = com.example.ui.theme.EnterpriseTypographyStyles.label)
                                    }
                                    Button(
                                        onClick = { viewModel.saveNursingReport(report.copyWithStatus("NeedsCorrection")) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                        modifier = Modifier.weight(1f).height(30.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("اصلاح", style = com.example.ui.theme.EnterpriseTypographyStyles.label)
                                    }
                                    Button(
                                        onClick = { viewModel.saveNursingReport(report.copyWithStatus("Archived")) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4B5563)),
                                        modifier = Modifier.weight(1f).height(30.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("آرشیو", style = com.example.ui.theme.EnterpriseTypographyStyles.label)
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (activeTab == 4) {
                // Tab 4: Timeline
                if (timelineItems.isEmpty()) {
                    item {
                        com.example.ui.components.EmptyState(
                            icon = Icons.Default.History,
                            message = "هیچ رویدادی یافت نشد.",
                            description = "سوابق بالینی و مالی این بیمار پس از ثبت رویدادها نمایش داده خواهد شد."
                        )
                    }
                } else {
                    items(timelineItems) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { item.onClick() }
                                .padding(com.example.ui.theme.DesignTokens.Spacing.s),
                            horizontalArrangement = Arrangement.spacedBy(com.example.ui.theme.DesignTokens.Spacing.m)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(item.color.copy(alpha = 0.15f), shape = RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(item.icon, contentDescription = null, tint = item.color, modifier = Modifier.size(18.dp))
                            }

                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(item.title.toPersianDigits(), fontWeight = FontWeight.Bold, style = com.example.ui.theme.EnterpriseTypographyStyles.bodyText)
                                    com.example.ui.components.StatusBadge(text = item.status, statusType = com.example.ui.components.EnterpriseStatusType.NEUTRAL)
                                }
                                Text(item.subtitle.toPersianDigits(), style = com.example.ui.theme.EnterpriseTypographyStyles.supportingText, color = Color.DarkGray)
                                Text("مسئول: ${item.creator}".toPersianDigits(), style = com.example.ui.theme.EnterpriseTypographyStyles.supportingText, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal dialogs for adding records
    if (showAddVitalSigns) {
        var sys by remember { mutableStateOf("120") }
        var dia by remember { mutableStateOf("80") }
        var pulse by remember { mutableStateOf("75") }
        var temp by remember { mutableStateOf("36.5") }
        var o2 by remember { mutableStateOf("98") }

        AlertDialog(
            onDismissRequest = { showAddVitalSigns = false },
            title = { Text("ثبت علائم حیاتی جدید", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = sys, onValueChange = { sys = it }, label = { Text("سیستولیک") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = dia, onValueChange = { dia = it }, label = { Text("دیاستولیک") }, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = pulse, onValueChange = { pulse = it }, label = { Text("ضربان قلب") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = temp, onValueChange = { temp = it }, label = { Text("دمای بدن") }, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = o2, onValueChange = { o2 = it }, label = { Text("درصد اکسیژن SpO2") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveVitalSigns(
                            VitalSigns(
                                patientId = patient.id,
                                bloodPressureSystolic = sys.toIntOrNull() ?: 120,
                                bloodPressureDiastolic = dia.toIntOrNull() ?: 80,
                                heartRate = pulse.toIntOrNull() ?: 75,
                                temperatureCelsius = temp.toDoubleOrNull() ?: 36.5,
                                oxygenSaturation = o2.toIntOrNull() ?: 98,
                                date = System.currentTimeMillis()
                            )
                        )
                        showAddVitalSigns = false
                    }
                ) { Text("ذخیره علائم") }
            },
            dismissButton = {
                TextButton(onClick = { showAddVitalSigns = false }) { Text("انصراف") }
            }
        )
    }

    if (showAddWoundRecord) {
        var wType by remember { mutableStateOf("زخم دیابتی") }
        var stage by remember { mutableStateOf("2") }
        var desc by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddWoundRecord = false },
            title = { Text("ثبت و پایش زخم جدید", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = wType, onValueChange = { wType = it }, label = { Text("نوع زخم") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = stage, onValueChange = { stage = it }, label = { Text("درجه / گرید زخم") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("شرح بالینی و ابعاد زخم") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveWoundRecord(
                            WoundRecord(
                                patientId = patient.id,
                                woundType = wType,
                                stage = stage,
                                description = desc,
                                date = System.currentTimeMillis()
                            )
                        )
                        showAddWoundRecord = false
                    }
                ) { Text("ثبت زخم") }
            },
            dismissButton = {
                TextButton(onClick = { showAddWoundRecord = false }) { Text("انصراف") }
            }
        )
    }

    if (showAddConsentForm) {
        var cTitle by remember { mutableStateOf("فرم رضایت آگاهانه خدمات پرستاری در منزل") }
        var cContent by remember { mutableStateOf("اینجانب رضایت کامل خود را جهت ارائه خدمات بالینی توسط کارشناسان مربوطه اعلام می‌دارم.") }
        var isSigned by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showAddConsentForm = false },
            title = { Text("صدور و ثبت فرم رضایت‌نامه", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = cTitle, onValueChange = { cTitle = it }, label = { Text("عنوان رضایت‌نامه") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = cContent, onValueChange = { cContent = it }, label = { Text("متن رضایت‌نامه") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isSigned, onCheckedChange = { isSigned = it })
                        Text("امضا و تایید شده توسط بیمار / ولی قانونی")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveConsentForm(
                            ConsentForm(
                                patientId = patient.id,
                                title = cTitle,
                                content = cContent,
                                isSigned = isSigned,
                                date = System.currentTimeMillis()
                            )
                        )
                        showAddConsentForm = false
                    }
                ) { Text("ثبت رضایت‌نامه") }
            },
            dismissButton = {
                TextButton(onClick = { showAddConsentForm = false }) { Text("انصراف") }
            }
        )
    }

    if (showAddPrescription) {
        var docName by remember { mutableStateOf("پزشک معالج") }
        var meds by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddPrescription = false },
            title = { Text("ثبت نسخه دارویی پزشک", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = docName, onValueChange = { docName = it }, label = { Text("نام پزشک صادرکننده") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = meds, onValueChange = { meds = it }, label = { Text("اقلام دارویی و دستور مصرف") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.savePrescription(
                            Prescription(
                                patientId = patient.id,
                                doctorName = docName,
                                medicineList = meds,
                                date = System.currentTimeMillis()
                            )
                        )
                        showAddPrescription = false
                    }
                ) { Text("ثبت نسخه") }
            },
            dismissButton = {
                TextButton(onClick = { showAddPrescription = false }) { Text("انصراف") }
            }
        )
    }
}
