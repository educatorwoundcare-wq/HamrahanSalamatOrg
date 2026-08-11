package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.Service
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceScreen(viewModel: HamrahanViewModel) {
    val context = LocalContext.current
    val services by viewModel.services.collectAsState()
    val currency by viewModel.defaultCurrency.collectAsState()

    var showAddEditDialog by remember { mutableStateOf(false) }
    var selectedServiceForEdit by remember { mutableStateOf<Service?>(null) }
    var showDetailsDialog by remember { mutableStateOf<Service?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Service?>(null) }

    // Search, Filter and Sort state
    var searchQuery by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf("همه") }
    var activeFilter by remember { mutableStateOf("همه") } // "همه", "فعال", "بایگانی شده"
    var sortBy by remember { mutableStateOf("Recently Modified") } // "Code", "Price", "Category", "Name", "Profit", "Recently Modified"
    var showSortMenu by remember { mutableStateOf(false) }

    val categories = listOf("همه") + services.map { it.category }.distinct()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                if (inputStream != null) {
                    viewModel.importTariffs(inputStream)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val filteredServices = services
        .filter { service ->
            val matchesQuery = service.name.contains(searchQuery, ignoreCase = true) ||
                    service.officialCode.contains(searchQuery, ignoreCase = true) ||
                    service.officialName.contains(searchQuery, ignoreCase = true)
            
            val matchesCategory = categoryFilter == "همه" || service.category == categoryFilter
            
            val matchesActive = when (activeFilter) {
                "فعال" -> service.isActive
                "بایگانی شده" -> !service.isActive
                else -> true
            }
            
            matchesQuery && matchesCategory && matchesActive
        }
        .sortedWith { s1, s2 ->
            when (sortBy) {
                "Code" -> s1.officialCode.compareTo(s2.officialCode)
                "Price" -> s1.sellingPrice.compareTo(s2.sellingPrice)
                "Category" -> s1.category.compareTo(s2.category)
                "Name" -> s1.name.compareTo(s2.name)
                "Profit" -> s1.netProfit.compareTo(s2.netProfit)
                "Recently Modified" -> s2.lastModifiedDate.compareTo(s1.lastModifiedDate)
                else -> s2.id.compareTo(s1.id)
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("کاتالوگ و تعرفه‌نامه خدمات", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    Button(
                        onClick = {
                            try {
                                val resId = context.resources.getIdentifier("tariffs_1405", "raw", context.packageName)
                                if (resId != 0) {
                                    val inputStream = context.resources.openRawResource(resId)
                                    viewModel.resetAllServicesToOfficialTariffs(inputStream)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "بارگذاری تعرفه‌های ۱۴۰۵",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("بارگذاری تعرفه‌های ۱۴۰۵", style = MaterialTheme.typography.labelMedium)
                    }
                    Button(
                        onClick = { importLauncher.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = "وارد کردن تعرفه",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("وارد کردن فایل", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedServiceForEdit = null
                    showAddEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_service_button")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "افزودن خدمت")
            }
        },
        modifier = Modifier.testTag("service_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // --- Search and Sort Bar ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("جستجو بر اساس نام یا کد خدمت...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("service_search_bar"),
                    singleLine = true
                )

                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .size(56.dp)
                    ) {
                        Icon(Icons.Default.Sort, contentDescription = "مرتب‌سازی")
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        listOf(
                            "Recently Modified" to "اخیراً تغییر یافته",
                            "Code" to "کد خدمت",
                            "Name" to "نام خدمت",
                            "Price" to "قیمت پرداختی بیمار",
                            "Category" to "دسته‌بندی",
                            "Profit" to "سود خالص شرکت"
                        ).forEach { (sortKey, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    sortBy = sortKey
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // --- Status and Category Filters ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("وضعیت:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                listOf("همه", "فعال", "بایگانی شده").forEach { status ->
                    FilterChip(
                        selected = activeFilter == status,
                        onClick = { activeFilter = status },
                        label = { Text(status) }
                    )
                }
            }

            ScrollableTabRow(
                selectedTabIndex = categories.indexOf(categoryFilter).coerceAtLeast(0),
                edgePadding = 0.dp,
                divider = {}
            ) {
                categories.forEach { category ->
                    Tab(
                        selected = categoryFilter == category,
                        onClick = { categoryFilter = category },
                        text = { Text(category) }
                    )
                }
            }

            // --- Services List ---
            if (filteredServices.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.HealthAndSafety,
                    message = "هیچ خدمتی با معیارهای مشخص شده یافت نشد.",
                    description = "برای تعریف خدمت جدید، روی دکمه + گوشه پایین کلیک کنید.",
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredServices) { service ->
                        ServiceListItem(
                            service = service,
                            currency = currency,
                            onClick = { showDetailsDialog = service },
                            onEdit = {
                                selectedServiceForEdit = service
                                showAddEditDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    // --- Detail Dialog ---
    showDetailsDialog?.let { service ->
        ServiceDetailsDialog(
            service = service,
            currency = currency,
            onDismiss = { showDetailsDialog = null },
            onEdit = {
                selectedServiceForEdit = service
                showDetailsDialog = null
                showAddEditDialog = true
            },
            onDuplicate = {
                val duplicated = service.copy(
                    id = 0,
                    officialCode = "${service.officialCode}-کپی",
                    name = "${service.name} (کپی)",
                    lastModifiedDate = System.currentTimeMillis()
                )
                viewModel.saveService(duplicated)
                showDetailsDialog = null
            },
            onToggleStatus = {
                val updated = service.copy(
                    isActive = !service.isActive,
                    lastModifiedDate = System.currentTimeMillis()
                )
                viewModel.saveService(updated)
                showDetailsDialog = updated
            },
            onDelete = {
                showDeleteConfirm = service
                showDetailsDialog = null
            }
        )
    }

    // --- Delete Confirmation ---
    showDeleteConfirm?.let { service ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("حذف خدمت") },
            text = { Text("آیا مطمئن هستید که می‌خواهید خدمت «${service.name}» را حذف کنید؟ این عمل غیرقابل بازگشت است.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteService(service)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("انصراف")
                }
            }
        )
    }

    // --- Add/Edit Dialog ---
    if (showAddEditDialog) {
        AddEditServiceDialog(
            service = selectedServiceForEdit,
            onDismiss = { showAddEditDialog = false },
            onSave = { savedSvc ->
                viewModel.saveService(savedSvc)
                showAddEditDialog = false
            }
        )
    }
}

@Composable
fun ServiceListItem(
    service: Service,
    currency: String,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    val statusColor = if (service.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val statusLabel = if (service.isActive) "فعال" else "بایگانی شده"

    com.example.ui.components.EnterpriseCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("service_card_${service.id}"),
        onClick = onClick,
        contentPadding = PaddingValues(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MedicalServices,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = service.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (service.officialCode.isNotBlank()) {
                            Text(
                                text = "کد رسمی: ${service.officialCode}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(statusColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = statusLabel,
                            color = statusColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = service.category,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "قیمت بیمار: ${service.sellingPrice.formatPrice(currency)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "سود خالص: ${service.netProfit.formatPrice(currency)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (service.netProfit > 0) Color(0xFF0F9D58) else Color.Red
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "ویرایش",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = null,
                        tint = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun ServiceDetailsDialog(
    service: Service,
    currency: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onToggleStatus: () -> Unit,
    onDelete: () -> Unit
) {
    val dateString = try {
        service.lastModifiedDate.formatDateTime()
    } catch (e: Exception) {
        "-"
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "جزئیات کامل خدمت",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "بستن")
                    }
                }

                Divider()

                // Information Grid
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
                ) {
                    DetailRow("کد رسمی خدمت", service.officialCode.ifBlank { "-" })
                        DetailRow("نام رسمی خدمت", service.officialName.ifBlank { "-" })
                        DetailRow("نام نمایشی خدمت", service.name)
                        DetailRow("دسته‌بندی خدمت", service.category)
                        DetailRow("واحد قیمت‌گذاری", service.pricingUnit)
                        DetailRow("قابلیت نمایش در اپلیکیشن", if (service.isVisibleInApp) "بله" else "خیر")
                        DetailRow("قابلیت انتخاب توسط بیمار", if (service.isSelectableByPatient) "بله" else "خیر")
                        DetailRow("تعرفه مصوب ۱۴۰۵", service.officialTariff.formatPrice(currency))
                        DetailRow("قیمت فروش مرکز", service.sellingPrice.formatPrice(currency))
                        DetailRow("هزینه دستمزد همکار", service.defaultCost.formatPrice(currency))
                        DetailRow("هزینه ایاب و ذهاب پیش‌فرض", service.transportationCost.formatPrice(currency))
                        DetailRow("هزینه لوازم مصرفی پیش‌فرض", service.consumablesCost.formatPrice(currency))
                        DetailRow("تخفیف پیش‌فرض", service.discount.formatPrice(currency))
                        DetailRow("کارمزد همکار پیش‌فرض", "${service.employeeCommission} (درصد یا مبلغ ثابت)")
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (service.netProfit > 0) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "سود خالص محاسبه شده",
                                    fontWeight = FontWeight.Bold,
                                    color = if (service.netProfit > 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                                )
                                Text(
                                    service.netProfit.formatPrice(currency),
                                    fontWeight = FontWeight.Bold,
                                    color = if (service.netProfit > 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                                )
                            }
                        }

                        DetailRow("مدت زمان حدودی", "${service.durationMinutes} دقیقه")
                        DetailRow("وضعیت خدمت", if (service.isActive) "فعال" else "بایگانی شده")
                        DetailRow("تاریخ آخرین ویرایش", dateString)
                        if (service.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("توضیحات و شرايط:", fontWeight = FontWeight.Bold)
                            Text(service.description, style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                        }
                }

                Divider()

                // Actions Layout
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onEdit,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ویرایش")
                        }
                        OutlinedButton(
                            onClick = onDuplicate,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("کپی/تکرار")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onToggleStatus,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (service.isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (service.isActive) Icons.Default.Archive else Icons.Default.Unarchive,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (service.isActive) "بایگانی خدمت" else "فعال‌سازی مجدد")
                        }
                        OutlinedButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("حذف کامل")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditServiceDialog(
    service: Service?,
    onDismiss: () -> Unit,
    onSave: (Service) -> Unit
) {
    var officialCode by remember { mutableStateOf(service?.officialCode ?: "") }
    var officialName by remember { mutableStateOf(service?.officialName ?: "") }
    var name by remember { mutableStateOf(service?.name ?: "") }
    var category by remember { mutableStateOf(service?.category ?: "پرستاری") }
    var officialTariffString by remember { mutableStateOf(service?.officialTariff?.toString() ?: "0") }
    var sellingPriceString by remember { mutableStateOf(service?.sellingPrice?.toString() ?: "") }
    var defaultCostString by remember { mutableStateOf(service?.defaultCost?.toString() ?: "0") }
    var transportationCostString by remember { mutableStateOf(service?.transportationCost?.toString() ?: "0") }
    var consumablesCostString by remember { mutableStateOf(service?.consumablesCost?.toString() ?: "0") }
    var discountString by remember { mutableStateOf(service?.discount?.toString() ?: "0") }
    var employeeCommissionString by remember { mutableStateOf(service?.employeeCommission?.toString() ?: "0") }
    var durationMinutesString by remember { mutableStateOf(service?.durationMinutes?.toString() ?: "60") }
    var description by remember { mutableStateOf(service?.description ?: "") }
    var isActive by remember { mutableStateOf(service?.isActive ?: true) }
    var pricingUnit by remember { mutableStateOf(service?.pricingUnit ?: "بازدید") }
    var isVisibleInApp by remember { mutableStateOf(service?.isVisibleInApp ?: true) }
    var isSelectableByPatient by remember { mutableStateOf(service?.isSelectableByPatient ?: true) }

    var hasError by remember { mutableStateOf(false) }

    // Dynamic computations for preview
    val sellingPrice = sellingPriceString.toDoubleOrNull() ?: 0.0
    val defaultCost = defaultCostString.toDoubleOrNull() ?: 0.0
    val transportationCost = transportationCostString.toDoubleOrNull() ?: 0.0
    val consumablesCost = consumablesCostString.toDoubleOrNull() ?: 0.0
    val discount = discountString.toDoubleOrNull() ?: 0.0
    val computedNetProfit = sellingPrice - defaultCost - transportationCost - consumablesCost - discount

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = if (service == null) "تعریف خدمت جدید" else "ویرایش مشخصات خدمت",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    Divider()
                }

                // Official parameters
                item {
                    OutlinedTextField(
                        value = officialCode,
                        onValueChange = { officialCode = it },
                        label = { Text("کد رسمی خدمت") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = officialName,
                        onValueChange = { officialName = it },
                        label = { Text("نام رسمی خدمت") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("نام نمایشی خدمت (اجباری)") },
                        isError = hasError && name.isBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("service_name_input")
                    )
                }

                item {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("دسته‌بندی (اجباری)") },
                        isError = hasError && category.isBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("service_category_input")
                    )
                }

                item {
                    OutlinedTextField(
                        value = pricingUnit,
                        onValueChange = { pricingUnit = it },
                        label = { Text("واحد قیمت‌گذاری (مثال: بازدید، ساعت، جلسه، روز...)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = officialTariffString,
                        onValueChange = { officialTariffString = it },
                        label = { Text("تعرفه مصوب ۱۴۰۵ (تومان/ریال - فقط خواندنی)") },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Financial pricing parameters
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = sellingPriceString,
                            onValueChange = { sellingPriceString = it },
                            label = { Text("قیمت بیمار (Center Selling Price)") },
                            isError = hasError && sellingPriceString.isBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("service_selling_price_input")
                        )
                        OutlinedTextField(
                            value = defaultCostString,
                            onValueChange = { defaultCostString = it },
                            label = { Text("دستمزد همکار (Employee Cost)") },
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
                            label = { Text("ایاب ذهاب همکار") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = consumablesCostString,
                            onValueChange = { consumablesCostString = it },
                            label = { Text("هزینه مصرفی پیش‌فرض") },
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
                            value = discountString,
                            onValueChange = { discountString = it },
                            label = { Text("تخفیف پیش‌فرض") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = employeeCommissionString,
                            onValueChange = { employeeCommissionString = it },
                            label = { Text("کمیسیون همکار (درصد یا مبلغ)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Live net profit display banner
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (computedNetProfit > 0) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "سود خالص محاسبه شده:",
                                fontWeight = FontWeight.Bold,
                                color = if (computedNetProfit > 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                            Text(
                                text = computedNetProfit.toString(),
                                fontWeight = FontWeight.Bold,
                                color = if (computedNetProfit > 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = durationMinutesString,
                        onValueChange = { durationMinutesString = it },
                        label = { Text("مدت زمان تقریبی خدمت (دقیقه)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("توضیحات تکمیلی") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("وضعیت خدمت:", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        FilterChip(
                            selected = isActive,
                            onClick = { isActive = true },
                            label = { Text("فعال") }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = !isActive,
                            onClick = { isActive = false },
                            label = { Text("بایگانی") }
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("نمایش در اپلیکیشن بیمار:", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Switch(
                            checked = isVisibleInApp,
                            onCheckedChange = { isVisibleInApp = it }
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("قابلیت انتخاب توسط بیمار:", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Switch(
                            checked = isSelectableByPatient,
                            onCheckedChange = { isSelectableByPatient = it }
                        )
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
                        Button(
                            onClick = {
                                if (name.isBlank() || category.isBlank() || sellingPriceString.isBlank()) {
                                    hasError = true
                                } else {
                                    val saved = Service(
                                        id = service?.id ?: 0,
                                        officialCode = officialCode,
                                        officialName = officialName,
                                        name = name,
                                        category = category,
                                        officialTariff = officialTariffString.toDoubleOrNull() ?: 0.0,
                                        sellingPrice = sellingPriceString.toDoubleOrNull() ?: 0.0,
                                        defaultCost = defaultCostString.toDoubleOrNull() ?: 0.0,
                                        transportationCost = transportationCostString.toDoubleOrNull() ?: 0.0,
                                        consumablesCost = consumablesCostString.toDoubleOrNull() ?: 0.0,
                                        discount = discountString.toDoubleOrNull() ?: 0.0,
                                        employeeCommission = employeeCommissionString.toDoubleOrNull() ?: 0.0,
                                        durationMinutes = durationMinutesString.toIntOrNull() ?: 60,
                                        description = description,
                                        isActive = isActive,
                                        pricingUnit = pricingUnit,
                                        isVisibleInApp = isVisibleInApp,
                                        uuid = service?.uuid ?: java.util.UUID.randomUUID().toString(),
                                        isSelectableByPatient = isSelectableByPatient,
                                        lastModifiedDate = System.currentTimeMillis()
                                    )
                                    onSave(saved)
                                }
                            },
                            modifier = Modifier.testTag("save_service_confirm_button")
                        ) {
                            Text("ذخیره خدمت")
                        }
                    }
                }
            }
        }
    }
}
